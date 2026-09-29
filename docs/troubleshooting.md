# Troubleshooting iDuo Launcher

iDuo Launcher is an experimental Fold beta. Device vendors, Android releases, the Google app, widget providers, and work-profile policy can all affect behavior. These steps preserve the current installation wherever possible.

## iDuo is not the Home app

Long press empty Home space, choose **Customize launcher**, then **Set as home app** at the top of Settings. You can also use **Language, backup & Home app**. Complete the choice in Android's Home app settings.

To switch back, choose **Change home app** under **Settings → Language, backup & Home app** or use Android **Settings → Apps → Default apps → Home app**. Installing iDuo never changes the Home app automatically.

## An update will not install

Android only updates an app when the new APK has the same application ID and signing key. iDuo's public beta and developer/debug builds use different keys, so one cannot update the other directly.

Android's [app-signing documentation](https://source.android.com/docs/security/features/apksigning) explains the package signature checks.

Do not uninstall or clear storage from a configured installation just to test a differently signed APK: that removes its layout, selected launcher photo, and Android widget bindings. Keep the configured install and test the other signer on a separate device or disposable emulator. If you deliberately replace the install, save a layout backup first, but expect provider widgets to require **Reconnect** and the launcher photo to be absent from the backup.

## The news page shows no articles

The news page needs an internet connection. Pull down on the list or use the refresh button to try again. If some sources fail, the page says so and keeps their earlier articles.

- **Google News:** open the settings icon on the page and check the edition and sections. At least one section always stays selected.
- **My RSS feeds:** a source marked with a warning could not be loaded. Remove it and add it again, or check that the website still publishes an RSS or Atom feed. Only HTTPS sources can be added.

## The search button does not open Google

In **Settings → Gestures & search**, check that **Search button** is set to **Google**. iDuo asks the Google app to open Android's global search screen. If that activity is missing or blocked, iDuo falls back to **All apps** with its local **Search apps** field. You can turn the setting off to use local app search every time.

## A widget will not add or finish setup

Android controls widget binding, and providers may require their own configuration or permissions.

- If iDuo shows **Widget not added**, acknowledge it and try the provider again.
- If a placeholder says **Finish widget setup**, choose **Finish setup** to resume, or **Cancel** to remove the placeholder.
- If **Widget settings** was interrupted, choose **Resume** or **Cancel**; cancelling keeps the existing widget unchanged.
- If the provider or profile is unavailable, install or enable it in the correct profile, then retry. Otherwise choose **Replace**.

Do not clear iDuo, Google, or provider app data as a shortcut. An Android widget ID without its system binding cannot recreate the widget.

## A restored widget says Reconnect

Layout backups store the widget provider and its rectangle, but not a portable live binding. Choose **Reconnect** on the placeholder and approve Android's binding or provider setup. Choose **Replace** when the saved provider/profile is unavailable.

Work widgets depend on administrator cross-profile policy. A backup restored on another installation cannot automatically map work-profile identities. The review screen reports **Profile attention** for affected widgets; use a provider from the current work profile when reconnecting or replacing them.

## A widget will not move or resize

Hold still on the widget until pickup begins, then drag. On a scrollable provider such as a calendar list, vertical movement before the hold is treated as provider scrolling. Tiny held jitter is allowed, but a real move, release, cancellation, or second pointer before the long-press timeout cancels pickup.

Long press and release to open **Widget options**. Use **Resize on Home** or the width/height controls, then **Apply** or **Apply size**. A red or disabled preview means the proposed rectangle overlaps another item, extends beyond the page, or violates the provider's resize limits. Move the widget into the six-row grid first if iDuo says that is required.

## Paging, widget scrolling, or Home gestures do the wrong thing

- Start a mostly horizontal swipe to change exactly one page. This works over Home, the dock, and the right rail, including over a widget.
- Start a vertical swipe on a scrollable part of a native widget to scroll the provider. Static widget areas still allow Home's own vertical action.
- Home gestures work only on Home: a swipe down on the left 70% opens Notifications, on the right 30% Quick Settings. A double tap locks the screen only on empty space; icons, folders, widgets and the dock keep their own taps. A dock that is already vertically scrolled keeps its downward gesture.
- To drag an item between pages, keep holding at the full left or right window edge until the page turns. Ordinary swipes and held edge paging use different timing.

If Home gestures are off, choose **Turn on** in **Settings → Gestures & search**, then **Open settings** and enable iDuo Launcher yourself. If the service has just started, follow the on-screen request to swipe again. iDuo does not enable Accessibility access automatically.

## An app or work profile is unavailable

Open **All apps** and select **Personal** or **Work**. For a paused managed profile, choose **Turn on work apps**. If Android or the administrator keeps the profile unavailable, iDuo cannot launch its apps or bind its widgets. Removed or temporarily unavailable packages can also remain visibly unavailable until Android reports them again.

Android documents the administrator-controlled activity and storage boundaries in its [work-profile guide](https://developer.android.com/work/managed-profiles).

## The wallpaper did not change

Selecting a photo only stages a preview. Choose **Apply** and then the screens to set it on; **Cancel** intentionally keeps the current wallpaper. If Android refuses the wallpaper, for example under a device policy, iDuo reports that and nothing changes. If iDuo says the picker was interrupted, choose **Resume** or select the photo again after cancelling.

Layout backups exclude photos and cannot restore a photo deleted from its source. If no committed copy or ready preview remains, choose another photo. iDuo always shows the Android wallpaper, so a wallpaper changed in Android's settings appears on Home right away.

## Cellular status says unavailable

The lower status dots represent cellular strength. **Cellular signal unavailable** is expected on a device without a SIM, including the reference Fold used for this beta. SIM-equipped no-service, airplane-mode, and signal transitions have not yet been broadly validated. Battery is the surrounding arc; Wi-Fi uses the inner arcs.

## Before reporting a problem

Include the iDuo version, phone model, Android version, folded or unfolded state, the page and gesture involved, and exact reproduction steps. Review screenshots and logs before sharing them: widgets, account names, work data, and app lists may be visible. iDuo does not upload diagnostics automatically.
