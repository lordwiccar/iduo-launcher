# iDuo Launcher user guide

iDuo Launcher is an experimental Android launcher designed around a foldable phone, a four-column Home grid, and a dock on the right that holds four to eight apps. The cover shows one Home page at a time. Unfolding adds an editable workspace on the left: the first view pairs that workspace with Home 1, followed by Home 1 + Home 2, Home 2 + Home 3, and so on.

## Start and switch launchers

On a fresh install, **Welcome to iDuo** offers **Choose Home app**, **Add a widget**, **Explore Home**, and **Not now**. Choosing or skipping setup does not prevent later changes.

To make iDuo the launcher, choose **Set as home app** at the top of **Settings**, or open **Language, backup & Home app**. Android owns the final Home-app chooser. To switch away later, choose **Change home app**, or use Android **Settings → Apps → Default apps → Home app**. The exact Android path may vary by device.

## Move around Home

- Swipe horizontally across Home, the dock, or the right rail to move one page per gesture.
- Swipe right from Home 1 for the news page. Swipe left, press Back, or use its right-pointing arrow to return.
- **News page** in Settings chooses between **Google News** and **My RSS feeds**, and picks the Google News edition and sections or manages your own sources. The small settings icon at the top left of the page does the same: type a feed address, or a website address and iDuo finds its feed. Pull down or use the refresh button to update; tap an article to open it in the browser.
- Swipe past the last Home page for **All apps**. Its **Search apps** field always searches installed apps locally.
- **Home → All apps view** switches All apps between an alphabetical **List** and a **Grid in pages** that you swipe sideways; swiping past its first page returns to Home.
- The dock and its search control stay on the right. The page controls also open the news page or All apps.
- Pressing the system Home control from an app returns to the Home page or unfolded pair you last had visible. From All apps, search, or the news page it returns to the last Home view.

## Customize Home

Long press an empty Home cell or any bare wallpaper on Home, such as the space below or beside the grid, to open **Add to Home**, then choose **Widgets**, **Wallpaper**, or **Customize launcher**. **Settings** opens over the whole screen. On the cover screen it lists its sections and opens one at a time; on the inner screen the list stays on the left beside the open section. Each row in the list shows its current setting, **Search settings** finds any setting by name, and cards at the top point out when iDuo is not the Home app or when Home gestures are off.

- **Customization**
  - **Wallpaper & appearance**: the wallpaper (**iDuo dunes**, **Your photo** or **Android wallpapers**), the **Color mode** and folder transparency.
  - **Home**: **Grid layout** (4 × 4, 4 × 5 or 4 × 6 apps below the widget band, chosen on a scrolling wheel; the unfolded screen shows two pages side by side), icon size, row spacing, app names, the Home apps, the widgets on the current page, the status display and the **All apps view**. A small Home preview stays at the top with the choice of **Cover screen** or **Inner screen**, which the size and spacing settings apply to.
  - **Dock**: the number of dock apps (4–8), its width and its position.
- **Controls**
  - **Gestures & search**: the Home gestures service, a switch for each gesture, and what the search button opens.
  - **News page**: Google News or your own RSS feeds, with their edition, sections or sources.
- **System & support**
  - **Language, backup & Home app**.
  - **Help & information**: the guide, **About** (the version, the developer, the original Duo Launcher project and the licences), **Changelog** and **Send feedback**, which opens a short Google form in your browser.

After a layout edit, **Undo last layout change** appears in customization. It covers the latest supported layout change, so use it before making another edit.

## Apps, folders, and the dock

Hold an app, then drag it to an empty cell, another page, or a vacant dock position. Neighboring Home icons move aside when possible. Pause at the left or right screen edge while holding to turn a page; dragging at the end can create another Home page.

Dragging between Home and the dock moves the shortcut instead of duplicating it. The dock holds four apps by default; choose four to eight under **Dock → Apps in dock**. The dock background grows or shrinks with that number. Choosing fewer positions keeps every app: apps from removed positions fill empty dock positions first, then move to free Home cells. When the dock is full, iDuo shows **Dock full • Move an app out first** and rejects a new arrival; it never evicts an app automatically. Existing dock apps can still be reordered. Drag a Home or dock shortcut to **Remove** to remove the shortcut without uninstalling the app.

To make a folder, drag an app onto the middle of another Home app and hold it there for a moment: the target gains a folder-shaped backdrop, the other icons stop moving aside, and releasing groups both apps in a new folder in the target's place. Drag an app onto the middle of an existing folder to add it straight away. Releasing near a cell's edge still moves the app between neighbours as usual. Tap a folder to open it. Its name is the heading; tap the pencil beside it to rename the folder. The folder menu (⋮) moves the whole folder to another page or ungroups it, returning its apps to Home starting in the folder's place. To take a single app out, hold it and drag it onto Home or the dock. An open folder is sized to its apps, up to six across and six down (fewer on the cover screen if they would not fit); more apps continue on further pages that you swipe between, with dots showing the current page. **Home layout → Folder background transparency** sets how much of Home shows through an open folder. This look setting stays on the device and is not part of layout backups.

Long press and release an app for options such as **Move on Home**, **Create folder**, **App info**, or **Remove from Home**. **All apps** remains the complete installed-app catalog even when a shortcut is removed.

If Android exposes a managed profile, **All apps** shows **Personal** and **Work** filters. A paused profile shows **Work apps are paused** and **Turn on work apps**. Availability and cross-profile widget access remain controlled by the profile administrator.

## Widgets

Open **Widgets** from an empty-space menu, **Add widget to this page** under **Settings → Home**, or **Add a widget** during setup. Search the catalog, select **Personal** or **Work** when those choices exist, then tap a preview to place it or hold it to drag. Android may ask you to allow the binding, and some providers open their own setup screen.

Hold an existing widget to pick it up, then drag it across cells or pages. A small amount of held finger jitter is allowed. Move into the lower-right **Remove** target to delete it from Home. Long press and release without dragging to open **Widget options**, which can include **Widget settings**, **Resize on Home**, page moves, **Replace**, and **Remove**.

For **Resize on Home**, drag the resize handle and choose **Apply**, or choose **Cancel**. The alternate size controls end with **Apply size**. iDuo rejects sizes or moves that overlap another item, exceed the visible grid (four columns by six to eight rows), or violate the provider's allowed sizes.

Scrollable Android widgets keep their native vertical scrolling when the touch begins on scrollable provider content. A horizontal swipe can still change Home pages. Hold still before moving when you intend to pick up the widget.

## Background and appearance

iDuo shows your Android wallpaper, including live wallpapers, and follows any change made in Android's settings. In **Wallpaper & appearance**, **Your photo** creates a private preview. **Apply** asks whether to set it on the **Home screen**, the **Lock screen**, or both, then sets it as the Android wallpaper; **Cancel** keeps the current wallpaper. If selection is interrupted, choose **Resume** or **Cancel**. Recovery has been checked for activity recreation and a completed private preview file, but an interruption during the earlier decode step may require selecting the photo again.

**iDuo dunes** sets the bundled dunes landscape as the Android wallpaper, asking for the same screens. **Android wallpapers** opens Android's own wallpaper picker. While a folder is open, the wallpaper is dimmed and softly blurred.

**Color mode** offers **Light**, **Dark**, **System**, and **Sun**, which follows sunrise and sunset. **Sun** accepts coordinates through **Use this place**, or requests approximate location only when you choose **Use device location**. If location is unavailable, iDuo visibly falls back to the system theme. **Clear location** removes saved coordinates; iDuo does not request location in the background.

## Language

**Language, backup & Home app** in Settings lists **System language** first, which follows Android and shows the language it currently resolves to. The other choices are English, Čeština, Slovenčina, Polski, and Deutsch, each shown in its own language. On Android 13 and later the same choice also appears in Android Settings under the app's language.

## Search

Swipe up on Home to search. Matching apps appear in up to two rows as you type; tap one to open it. The keyboard's search key or the magnifier in the field opens Google's results for the phrase. Back or a tap outside the panel closes it.

## Optional Home gestures

On Home, swipe down from the left 70% to open Notifications or from the right 30% to open Quick Settings, and double-tap empty space to lock the screen. Locking works like the power button, so fingerprint and face unlock stay available. The first attempt offers **Turn on Home gestures** because Android requires you to enable iDuo Launcher in Accessibility settings. This is optional and must be enabled by you; **Not now** leaves it off. The service only requests the system panel and lock actions. Each gesture has its own switch under **Gestures & search**, which also shows whether the service is on.

## Layout backup

Open **Language, backup & Home app**, choose **Save** under **Layout backup**, and select a document destination. Choose **Restore** to select a backup, inspect **Review restored layout**, then choose **Restore** again. **Cancel** leaves Home unchanged.

Backups contain Home and dock positions, folders, widget descriptions and spaces, layout presets, labels, search behavior, and status settings. They include the unfolded-only workspace. They do not include the selected background photo or live Android widget bindings. After restore, provider widgets keep their saved space but require **Reconnect**; unavailable apps leave empty positions, and work-profile entries may need manual placement. Review a backup before sharing because it can expose app names, folder names, and profile metadata.
