# iDuo Launcher user guide

iDuo Launcher is an experimental Android launcher designed around a foldable phone, a four-column Home grid, and a dock on the right that holds four to eight apps. The cover shows one Home page at a time. Unfolding adds an editable workspace on the left: the first view pairs that workspace with Home 1, followed by Home 1 + Home 2, Home 2 + Home 3, and so on.

## Start and switch launchers

On a fresh install, **Welcome to iDuo** offers **Choose Home app**, **Add a widget**, **Explore Home**, and **Not now**. Choosing or skipping setup does not prevent later changes.

To make iDuo the launcher, choose **Set as home app** in customization, or open **Help & setup** and choose **Set iDuo as Home**. Android owns the final Home-app chooser. To switch away later, choose **Change home app**, or use Android **Settings → Apps → Default apps → Home app**. The exact Android path may vary by device.

## Move around Home

- Swipe horizontally across Home, the dock, or the right rail to move one page per gesture.
- Swipe right from Home 1 for Discover. Swipe left, press Back, or use its right-pointing arrow to return.
- **Gestures & search → Left page** chooses between **Google Discover** and iDuo's **RSS reader**. In the reader, the small settings icon at the top left manages sources: type a feed address, or a website address and iDuo finds its feed. Pull down or use the refresh button to update; tap an article to open it in the browser.
- Swipe past the last Home page for **All apps**. Its **Search apps** field always searches installed apps locally.
- **Home layout → All apps view** switches All apps between an alphabetical **List** and a **Grid in pages** that you swipe sideways; swiping past its first page returns to Home.
- The dock and its search control stay on the right. The page controls also open Discover or All apps.
- Pressing the system Home control from an app returns to the Home page or unfolded pair you last had visible. From All apps, search, or Discover it returns to the last Home view.

## Customize Home

Long press an empty Home cell or any bare wallpaper on Home, such as the space below or beside the grid, to open **Add to Home**, then choose **Widgets**, **Wallpaper**, or **Customize launcher**. In **Make it yours** you can open:

- **Wallpaper & appearance** for the wallpaper and color mode.
- **Home layout** for the number of dock apps (4–8), **Grid layout** (4 × 4, 4 × 5 or 4 × 6 apps below the widget band, chosen on a scrolling wheel; the unfolded screen shows two pages side by side), icon size, row spacing, dock geometry, Home apps, and widgets on the visible page.
- **Gestures & search** for app names, the upper-right status display, and Google search behavior.
- **Backup** to save or restore the layout.
- **Help & setup** for Home selection, widgets, shade gestures, and Discover.

After a layout edit, **Undo last layout change** appears in customization. It covers the latest supported layout change, so use it before making another edit.

## Apps, folders, and the dock

Hold an app, then drag it to an empty cell, another page, or a vacant dock position. Neighboring Home icons move aside when possible. Pause at the left or right screen edge while holding to turn a page; dragging at the end can create another Home page.

Dragging between Home and the dock moves the shortcut instead of duplicating it. The dock holds four apps by default; choose four to eight under **Home layout → Apps in dock**. The dock background grows or shrinks with that number. Choosing fewer positions keeps every app: apps from removed positions fill empty dock positions first, then move to free Home cells. When the dock is full, iDuo shows **Dock full • Move an app out first** and rejects a new arrival; it never evicts an app automatically. Existing dock apps can still be reordered. Drag a Home or dock shortcut to **Remove** to remove the shortcut without uninstalling the app.

To make a folder, drag an app onto the middle of another Home app and hold it there for a moment: the target gains a folder-shaped backdrop, the other icons stop moving aside, and releasing groups both apps in a new folder in the target's place. Drag an app onto the middle of an existing folder to add it straight away. Releasing near a cell's edge still moves the app between neighbours as usual. Tap a folder to open it. Its name is the heading; tap the pencil beside it to rename the folder. The folder menu (⋮) moves the whole folder to another page or ungroups it, returning its apps to Home starting in the folder's place. To take a single app out, hold it and drag it onto Home or the dock. An open folder is sized to its apps, up to six across and six down (fewer on the cover screen if they would not fit); more apps continue on further pages that you swipe between, with dots showing the current page. **Home layout → Folder background transparency** sets how much of Home shows through an open folder. This look setting stays on the device and is not part of layout backups.

Long press and release an app for options such as **Move on Home**, **Create folder**, **App info**, or **Remove from Home**. **All apps** remains the complete installed-app catalog even when a shortcut is removed.

If Android exposes a managed profile, **All apps** shows **Personal** and **Work** filters. A paused profile shows **Work apps are paused** and **Turn on work apps**. Availability and cross-profile widget access remain controlled by the profile administrator.

## Widgets

Open **Widgets** from an empty-space menu, **Add widget to this page** in customization, or **Add a widget** during setup. Search the catalog, select **Personal** or **Work** when those choices exist, then tap a preview to place it or hold it to drag. Android may ask you to allow the binding, and some providers open their own setup screen.

Hold an existing widget to pick it up, then drag it across cells or pages. A small amount of held finger jitter is allowed. Move into the lower-right **Remove** target to delete it from Home. Long press and release without dragging to open **Widget options**, which can include **Widget settings**, **Resize on Home**, page moves, **Replace**, and **Remove**.

For **Resize on Home**, drag the resize handle and choose **Apply**, or choose **Cancel**. The alternate size controls end with **Apply size**. iDuo rejects sizes or moves that overlap another item, exceed the visible grid (four columns by six to eight rows), or violate the provider's allowed sizes.

Scrollable Android widgets keep their native vertical scrolling when the touch begins on scrollable provider content. A horizontal swipe can still change Home pages. Hold still before moving when you intend to pick up the widget.

## Background and appearance

iDuo shows your Android wallpaper, including live wallpapers, and follows any change made in Android's settings. In **Wallpaper & appearance**, **Choose a photo** creates a private preview. **Apply** asks whether to set it on the **Home screen**, the **Lock screen**, or both, then sets it as the Android wallpaper; **Cancel** keeps the current wallpaper. If selection is interrupted, choose **Resume** or **Cancel**. Recovery has been checked for activity recreation and a completed private preview file, but an interruption during the earlier decode step may require selecting the photo again.

**Use iDuo dunes** sets the bundled dunes landscape as the Android wallpaper, asking for the same screens. **Open wallpaper settings** opens Android's own wallpaper picker. While a folder is open, the wallpaper is dimmed and softly blurred.

Appearance choices are **Light**, **Dark**, **Follow system**, and **Sunrise / sunset**. Sunrise/sunset accepts coordinates through **Use this place**, or requests approximate location only when you choose **Use device location**. If location is unavailable, iDuo visibly falls back to the system theme. **Clear location** removes saved coordinates; iDuo does not request location in the background.

## Language

**Language** in the customization sheet lists **System language** first, which follows Android and shows the language it currently resolves to. The other choices are English, Čeština, Slovenčina, Polski, and Deutsch, each shown in its own language. On Android 13 and later the same choice also appears in Android Settings under the app's language.

## Optional shade gestures

On Home, swipe down from the left 70% to open Notifications or from the right 30% to open Quick Settings. The first attempt offers **Turn on shade gestures** because Android requires you to enable iDuo Launcher in Accessibility settings. This is optional and must be enabled by you; **Not now** leaves it off. The service only requests the system panel actions.

## Layout backup

Open **Backup**, choose **Save**, and select a document destination. Choose **Restore** to select a backup, inspect **Review restored layout**, then choose **Restore** again. **Cancel** leaves Home unchanged.

Backups contain Home and dock positions, folders, widget descriptions and spaces, layout presets, labels, search behavior, and status settings. They include the unfolded-only workspace. They do not include the selected background photo or live Android widget bindings. After restore, provider widgets keep their saved space but require **Reconnect**; unavailable apps leave empty positions, and work-profile entries may need manual placement. Review a backup before sharing because it can expose app names, folder names, and profile metadata.
