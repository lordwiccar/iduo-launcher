# Contributor code map

iDuo is a Kotlin/Jetpack Compose Android Home application with one normal app module. It owns its Home content, dock, editing UI and widget hosts. Android owns the secure lock screen, recents, notification panels and system app transitions. Google owns the content and input inside its Discover feed.

## Where to start

All paths below are relative to `app/src/main/java/media/whitewhale/iduo/`.

| Area | Entry points | Responsibility |
| --- | --- | --- |
| Activity and setup | `MainActivity.kt`, `SetupExperience.kt` | Android intents/results, Home selection, fresh-install setup and optional access |
| App catalog and saved layout | `LauncherModel.kt`, `ProfileSupport.kt` | Observable launcher state, profile-aware identities, package changes and persistence |
| Home and navigation | `LauncherScreen.kt`, `LauncherPager.kt`, `PageGestures.kt`, `WorkspacePageMotion.kt` | Page composition, shared gestures, unfolded pairs and page motion |
| Editing and folders | `HomeEditing.kt`, `HomeDrag.kt`, `FolderEditing.kt`, `FolderPanel.kt` | Placement rules, drag previews, insertion, folders and cancellation |
| Native widgets | `WidgetController.kt`, `WidgetPicker.kt`, `WidgetSizing.kt`, `ZeroPaddingWidgetHost.kt`, `WidgetVerticalGestures.kt` | Provider catalog, binding/configuration, geometry and native touch arbitration |
| Google features | `GoogleSearch.kt`, `DiscoverClient.kt`, `LiveDiscoverActivity.kt`, `DiscoverBounds.kt` | Search intents, feed protocol, persistent host and embedding compatibility |
| Customization | `CustomizationSheet.kt`, `LauncherActionSheet.kt` | Long-press actions, settings subpages and sheet navigation |
| Wallpaper, appearance and status | `SystemWallpaper.kt`, `LauncherBackground.kt`, `WallpaperStandIn.kt`, `AppearanceSettings.kt`, `SolarSchedule.kt`, `DeviceStatus.kt`, `StatusRail.kt` | Private photo staging, theme scheduling, live status and its presentation |
| Backup and shade access | `LayoutBackup.kt`, `BackupController.kt`, `SystemShadeController.kt` | Portable layout import/export and optional system-panel actions |

## Layout and identity

`LauncherModel` exposes a `StateFlow<LauncherState>`. App discovery happens off the main thread; installed-app identities include their Android profile. A temporarily unavailable package or paused profile must not silently erase its placements.

Each Home page stores a four-column, eight-row grid (`GRID_ROWS`) so cell indices never shift; `HomeLayout.rows` (six or eight, shared by the cover and inner screens) decides how many are visible and usable. `unavailableCells()` treats hidden rows like widget footprints, and `resizeHomeRows` moves anything out of rows that are being hidden. Extra rows first give up row spacing so the page still fits. Saved state before schema 9 and backups before version 3 used six stored rows and are converted on load (`upgradeLegacy*`); the old payload stays in `state_v8_backup`. Apps occupy cells; widgets occupy explicit rectangles with durable slot identities. The dock has four to eight positions (its list length is the saved size) and rejects incoming apps when full. Moving a shortcut between Home and the dock moves that placement; All apps remains the installed-app catalog.

Unfolded navigation uses overlapping pairs: leading workspace + Home 1, Home 1 + Home 2, and so on. The leading workspace has separate `leadingSlots` and durable widget page `-1`; it disappears from the cover view without deleting its contents. **Pager page `-1` separately means Discover.** Use the address helpers in `HomeEditing.kt` rather than treating negative cell indices as missing values.

Saved layouts use explicit JSON fields with migration backups. Layout export is a portable description, not a copy of Android's widget capabilities: an import must reconnect widgets, and cross-installation work profiles may require manual correction. Photo files are outside the layout backup.

## Native widgets and gestures

Android widgets render through native `AppWidgetHostView` children inside Compose. Binding and configuration are asynchronous: preserve the pending operation and real binding until it finishes or is explicitly canceled. Restoring a saved numeric widget ID cannot recreate a deleted system binding.

The shared ancestor recognizes horizontal one-page gestures across the page, dock and rail. Native vertical widget content must retain vertical input. A bound widget's scoped long-press recognizer tolerates consumed sub-slop jitter, but scrolling, cancellation, release or another pointer cancels pickup. Do not solve one path by consuming every event: that breaks the others.

Widget size publication uses measured content with `updateAppWidgetOptions`, posted/coalesced after layout. Framework padding behavior is relevant when changing this. Nearby pages remain composed to avoid expensive RemoteViews reinflation during a swipe.

## Discover ownership

The live path keeps a persistent Google window and live Home graphics layers. Healthy Discover backing remains transparent; a recovery surface appears while a status message is present, including a delayed connection or an error. Google reports feed progress but owns native feed gestures, so a timeout or progress reversal is not proof that a finger was released.

`DiscoverBounds.kt` contains an unsupported alignment-hint workaround scoped to audited Window Extensions versions 8–10. Other versions retain normal alignment. This avoids an additional vendor task-fragment transition in the tested configuration; it is not a public SystemUI animation API or a compatibility guarantee. Keep the version guard, host-start recovery and fallback path when changing embedding behavior.

## Localization

All user-facing text lives in `res/values/strings.xml` with translations in `values-cs`, `values-sk`, `values-pl`, and `values-de`; `AppLanguageTest` checks that `AppLanguage.tags` and `res/xml/locales_config.xml` agree. `AppLanguage` applies a per-app choice through `LocaleManager` on Android 13+, and on Android 12 stores it and wraps each context in `attachBaseContext`. Saved data stays language-neutral: profile labels are stored as `Personal`/`Work` and localized at display time with `profileName`, Discover status is a string resource id, and only failures wrapped in `UserFacingException` show their own (already localized) message. Dates use `localizedDateFormatter` skeletons so word order and month forms follow the language.

## Persistence and recovery

Setup uses separate preferences and classifies existing installations before the model creates default state. Completion is stored before closing the welcome sheet, so upgrades do not show onboarding or replace layouts.

Launcher windows show Android's wallpaper (`windowShowWallpaper`); iDuo draws no Home background. Photos and the bundled dunes are set through `WallpaperManager`, and a folder blurs the wallpaper with the window's blur-behind. Apps cannot read the wallpaper's pixels, so surfaces that paint a stand-in (Discover's frame, the customization preview) use a private mirror of a wallpaper iDuo set, trusted only while Android reports the same wallpaper id, and otherwise the wallpaper's colors. A selected photo is decoded into private staging before **Apply** sets it. URI permission belongs to that selection operation and is released when no longer needed. Failed or canceled selection preserves the existing background. Never explicitly recycle a bitmap already published to Compose; rendering may still reference it. Activity and ready-file recovery have tests; provider process death during early decode remains a separate validation gap.

## Testing changes

Pure placement, gesture-decision, sizing, profile, status and setup rules have JVM tests under `app/src/test/`. Native widget/provider behavior, real sheet Back handling, Android results and live Discover require emulator integration checks under `app/src/androidTest/`.

Start with a focused regression that reproduces the behavior, then run the relevant build/unit/lint checks described in [Contributing](../CONTRIBUTING.md). Integration fixtures need a disposable emulator with their expected apps/providers. Record real binding tuples before testing and preserve them during cleanup; preferences alone are not a complete widget backup. Cover/inner dimension overrides test layout behavior, not physical hinge timing or real-device performance.

Debug probes and instrumentation fixtures are separate source sets. The release build uses R8/resource shrinking and excludes debug activities. See [release instructions](public-release.md) for signing and the [tested beta scope](releases/0.15.0-beta01.md) before expanding compatibility claims.
