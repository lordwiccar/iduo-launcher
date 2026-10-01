# Contributor code map

iDuo is a Kotlin/Jetpack Compose Android Home application with one normal app module. It owns its Home content, dock, editing UI and widget hosts. Android owns the secure lock screen, recents, notification panels and system app transitions. The news page shows headlines fetched from public RSS feeds.

## Where to start

All paths below are relative to `app/src/main/java/media/whitewhale/iduo/`.

| Area | Entry points | Responsibility |
| --- | --- | --- |
| Activity and setup | `MainActivity.kt`, `SetupExperience.kt` | Android intents/results, Home selection, fresh-install setup and optional access |
| App catalog and saved layout | `LauncherModel.kt`, `ProfileSupport.kt` | Observable launcher state, profile-aware identities, package changes and persistence |
| Home and navigation | `LauncherScreen.kt`, `LauncherPager.kt`, `PageGestures.kt`, `WorkspacePageMotion.kt` | Page composition, shared gestures, unfolded pairs and page motion |
| Editing and folders | `HomeEditing.kt`, `HomeDrag.kt`, `FolderEditing.kt`, `FolderPanel.kt` | Placement rules, drag previews, insertion, folders and cancellation |
| Native widgets | `WidgetController.kt`, `WidgetPicker.kt`, `WidgetSizing.kt`, `ZeroPaddingWidgetHost.kt`, `WidgetVerticalGestures.kt` | Provider catalog, binding/configuration, geometry and native touch arbitration |
| News page and search | `NewsPage.kt`, `RssReader.kt`, `RssFeeds.kt`, `GoogleNews.kt`, `GoogleSearch.kt` | Left page UI, feed fetching and caching, RSS/Atom parsing, Google News editions, search intentibility |
| Settings and actions | `Settings.kt`, `AppearanceSettings.kt`, `About.kt`, `LauncherActionSheet.kt` | Full-screen settings (list and pages, two panes when wide, search), long-press actions |
| Wallpaper, appearance and status | `SystemWallpaper.kt`, `LauncherBackground.kt`, `WallpaperStandIn.kt`, `AppearanceSettings.kt`, `SolarSchedule.kt`, `DeviceStatus.kt`, `StatusRail.kt` | Private photo staging, theme scheduling, live status and its presentation |
| Backup and shade access | `LayoutBackup.kt`, `BackupController.kt`, `SystemShadeController.kt` | Portable layout import/export and optional system-panel actions |

## Layout and identity

`LauncherModel` exposes a `StateFlow<LauncherState>`. App discovery happens off the main thread; installed-app identities include their Android profile. A temporarily unavailable package or paused profile must not silently erase its placements.

Each Home page stores a five-column, eight-row grid (`GRID_COLUMNS`, `GRID_ROWS`) so cell indices never shift; `HomeLayout.rows` (six to eight) and `HomeLayout.columns` (four, or five on the cover's own layout) decide how many are visible and usable. `unavailableCells()` treats hidden rows and columns like widget footprints, and `resizeHomeRows` and `resizeHomeColumns` move anything out of rows or columns that are being hidden. Extra rows first give up row spacing so the page still fits. Saved state before schema 10 and backups before version 4 stored four columns, and before schema 9 and version 3 six rows; they are converted on load (`upgradeLegacy*`), and the old payloads stay in `state_v8_backup` and `state_v9_backup`.

The cover screen mirrors the inner screen's layout unless `separateCover` is on. `LauncherState`'s Home fields always hold the layout on screen; `otherLayout` holds the one that is not (the inner screen's while `showingCover`, else the cover's own, kept even while mirroring). `MainActivity` calls `showDisplay` before the first frame and `LauncherScreen` again when its width crosses 650dp, so every edit applies to the layout the user sees. The dock is shared and lives only in the Home fields. Saved state and backups always store the inner layout at the top level and the cover's own under `cover` (`CoverLayout.kt`). Its first copy turns bound Android widgets into `NEEDS_BINDING_WIDGET` placeholders, because one widget view cannot be shown in two places. Apps occupy cells; widgets occupy explicit rectangles with durable slot identities. The dock has four to eight positions (its list length is the saved size) and rejects incoming apps when full. Moving a shortcut between Home and the dock moves that placement; All apps remains the installed-app catalog.

Unfolded navigation uses overlapping pairs: leading workspace + Home 1, Home 1 + Home 2, and so on. The leading workspace has separate `leadingSlots` and durable widget page `-1`; it disappears from the cover view without deleting its contents. **Pager page `-1` separately means Discover.** Use the address helpers in `HomeEditing.kt` rather than treating negative cell indices as missing values.

Saved layouts use explicit JSON fields with migration backups. Layout export is a portable description, not a copy of Android's widget capabilities: an import must reconnect widgets, and cross-installation work profiles may require manual correction. Photo files are outside the layout backup.

## Native widgets and gestures

Android widgets render through native `AppWidgetHostView` children inside Compose. Binding and configuration are asynchronous: preserve the pending operation and real binding until it finishes or is explicitly canceled. Restoring a saved numeric widget ID cannot recreate a deleted system binding.

The shared ancestor recognizes horizontal one-page gestures across the page, dock and rail. Native vertical widget content must retain vertical input. A bound widget's scoped long-press recognizer tolerates consumed sub-slop jitter, but scrolling, cancellation, release or another pointer cancels pickup. Do not solve one path by consuming every event: that breaks the others.

Widget size publication uses measured content with `updateAppWidgetOptions`, posted/coalesced after layout. Framework padding behavior is relevant when changing this. Nearby pages remain composed to avoid expensive RemoteViews reinflation during a swipe.

## News page

Home's left page is logical page -1 and always exists. `NewsPage` shows one of two `FeedReader`s from `NewsFeeds`: Google News, whose sources `GoogleNewsSettings` derives from an edition and sections, or the user's own RSS sources. Each reader keeps its sources in preferences and its last articles in a private cache file, refreshes when shown after 15 minutes, and fetches all sources in parallel over HTTPS with size and redirect limits. `parseFeed` reads RSS 2.0, RSS 1.0 and Atom with external entities disabled, keeps a per-item publisher (`<source>`) and drops a summary that only repeats the headline.

## Localization

All user-facing text lives in `res/values/strings.xml` with translations in `values-cs`, `values-sk`, `values-pl`, and `values-de`; `AppLanguageTest` checks that `AppLanguage.tags` and `res/xml/locales_config.xml` agree. `AppLanguage` applies a per-app choice through `LocaleManager` on Android 13+, and on Android 12 stores it and wraps each context in `attachBaseContext`. Saved data stays language-neutral: profile labels are stored as `Personal`/`Work` and localized at display time with `profileName`, Discover status is a string resource id, and only failures wrapped in `UserFacingException` show their own (already localized) message. Dates use `localizedDateFormatter` skeletons so word order and month forms follow the language.

## Persistence and recovery

Setup uses separate preferences and classifies existing installations before the model creates default state. Completion is stored before closing the welcome sheet, so upgrades do not show onboarding or replace layouts.

Launcher windows show Android's wallpaper (`windowShowWallpaper`); iDuo draws no Home background. Photos and the bundled dunes are set through `WallpaperManager`, and a folder blurs the wallpaper with the window's blur-behind. Apps cannot read the wallpaper's pixels, so surfaces that paint a stand-in (Discover's frame, the customization preview) use a private mirror of a wallpaper iDuo set, trusted only while Android reports the same wallpaper id, and otherwise the wallpaper's colors. A selected photo is decoded into private staging before **Apply** sets it. URI permission belongs to that selection operation and is released when no longer needed. Failed or canceled selection preserves the existing background. Never explicitly recycle a bitmap already published to Compose; rendering may still reference it. Activity and ready-file recovery have tests; provider process death during early decode remains a separate validation gap.

## Testing changes

Pure placement, gesture-decision, sizing, profile, status and setup rules have JVM tests under `app/src/test/`. Native widget/provider behavior, real sheet Back handling, Android results and live Discover require emulator integration checks under `app/src/androidTest/`.

Start with a focused regression that reproduces the behavior, then run the relevant build/unit/lint checks described in [Contributing](../CONTRIBUTING.md). Integration fixtures need a disposable emulator with their expected apps/providers. Record real binding tuples before testing and preserve them during cleanup; preferences alone are not a complete widget backup. Cover/inner dimension overrides test layout behavior, not physical hinge timing or real-device performance.

Debug probes and instrumentation fixtures are separate source sets. The release build uses R8/resource shrinking and excludes debug activities. See [release instructions](public-release.md) for signing and the [tested beta scope](releases/0.15.0-beta01.md) before expanding compatibility claims.
