# Changelog

## 1.1.2

Fourth release on Google Play (version code 5).

### New features
- Add apps to a folder by ticking them in a list, or choose all of its apps at once, from the folder's menu.
- A picture beside each Google News article.
- The status ring shows the mobile network (5G, 4G) and, without Wi-Fi, airplane mode or the battery level in its middle.

### Look and feel
- The battery ring turns green while charging and yellow in battery saver.
- A short line in the dock parts your own apps from the recently opened ones.

### Fixes
- Widgets from other apps come back by themselves after the app is updated; if Android lets one go, iDuo reconnects it or offers Reconnect.
- Turning Wi-Fi off removes its symbol from the status ring.

## 1.1.1

Third release on Google Play (version code 4).

### New features
- A separate Home for the cover screen: mirror the inner screen or give the cover its own pages, with up to five columns when its dock slides in or hides.
- Three ways to show the cover screen's dock: always, sliding in from the right edge, or hidden.
- Two new All apps views: Library sorts apps into folders by purpose, like on iPhone, and On Home places every app on Home pages.
- A menu on a long-pressed app with its shortcuts, Remove, Uninstall, Icon and App info.
- Icon packs from Google Play, for all apps or a single one.
- Move and zoom your own photo wallpaper, separately for each screen.
- Folders in the dock, and recently opened apps in its empty places.
- A notification after each update that opens this changelog.

### Look and feel
- A redesigned status ring, at the top or above the dock on the cover screen.
- A blurred wallpaper behind All apps, the news page and folders, and a frosted dock that blurs exactly the wallpaper behind it.
- The cover screen stays upright; rotating it is an experimental option.

### Fixes
- Text in Settings is readable in dark mode again.
- The news page help points to Settings → News page.

## 1.1.0

Second release on Google Play (version code 3).

### Settings
- Redesign Settings. It opens over the whole screen, groups its sections into Customization, Controls and System & support, shows each section's current setting, and finds any setting by name. The inner screen shows the list beside the open section.
- Home keeps a live preview and the Cover or Inner screen choice at the top, the dock has its own section, each Home gesture has its own switch next to the status of the Home gestures service, and the news page is set up directly in Settings. Cards at the top point out when iDuo is not the Home app or Home gestures are off.
- Animate Settings: it rises from the bottom and slides away when closed, pages slide in from the right on the cover screen and cross-fade beside the list on the inner screen, and the predictive back gesture shrinks the page as you swipe.
- Add About, with the version, the developer, the original Duo Launcher project and the licences; an in-app Changelog; and Send feedback, which opens a Google form. They sit under Help & information.

### Search and gestures
- Add Home search: swipe up on Home, find apps as you type (ignoring accents), and send the phrase to Google with the search key or the magnifier.
- The app-search choice of the search button opens Home search instead of All apps.
- Lock the screen with a double tap on empty Home space, through the optional accessibility service (now "Home gestures"), so fingerprint and face unlock stay available.
- Swipe down, double tap and swipe up can each be turned off.

### News page
- Replace Google Discover on the left page with Google News. Discover could not work in Play builds, because Google only lets allow-listed launchers embed it. The news page shows Google News headlines for a chosen edition and sections, or your own RSS feeds.
- Offer 86 Google News editions from around the world, each checked to serve its own headlines, in a searchable list named in the app's language.
- Show each article's publisher and drop summaries that only repeat the headline.
- Remove the Discover host activities, the Google app overlay connection and the AndroidX Window dependency.

## 1.0.0

First release of iDuo Launcher, and the first release on Google Play.

- Rename the app to iDuo Launcher with the package `media.whitewhale.iduo`. Android installs it as a new app beside earlier Duo Launcher builds.
- Translate the app into Czech, Slovak, Polish and German, with a Language setting that follows the system language by default.
- Group Home icons into folders by dropping one onto another. Open folders size to their content up to 6 × 6 icons, page beyond that, grow out of their Home icon, blur Home behind them, and offer rename, move to page and ungroup.
- Add adjustable folder background transparency.
- Show Android's wallpaper on Home, including live wallpapers. A photo or the bundled iDuo dunes chosen in iDuo are set as the Android wallpaper on the Home screen, lock screen or both.
- Offer an RSS reader as an alternative to Google Discover on the left page, with source management behind a settings icon. The reader adds the internet permission; it only contacts the sources you add.
- Add a paged grid view for All apps, selectable under Home layout.
- Fix app search and other text fields: the keyboard opens and typing reaches the field while Discover is ready in the background.
- Keep Home, dock and folder shortcuts of apps that change their icon by switching launch activities.
- Allow four to eight dock apps and a Home grid of 4 × 4, 4 × 5 or 4 × 6 apps chosen on a scrolling wheel; long press on Home opens settings.

## Before iDuo

The entries below describe Duo Launcher, the project iDuo Launcher grew from. Those versions used another package name and cannot be updated to iDuo.

### 0.15.0-beta01

First public-beta preparation release. Tested scope and APK checksums accompany the release package.

- Add a skippable introduction for fresh installations and help through customization; existing layouts open directly.
- Improve recovery choices when Google Discover is unavailable.
- Show distinct Wi-Fi levels across the dot and three arcs.
- Preserve the current wallpaper when photo selection is canceled or fails, and improve interrupted preview recovery and temporary permission cleanup.
- Prepare optimized release builds, external signing, public-source export, and automated build checks.
- Add installation, update, permission, contribution, and compatibility documentation.

### 0.14.7

- Restore long-press pickup in scrollable Android widgets while preserving native vertical scrolling.

### 0.14.6

- Preserve the selected Home page or unfolded pair when returning from an app.

### 0.14.5

- Allow vertical scrolling inside native Android widgets.

## Earlier development

Home/All apps paging; right-side dock; overlapping unfolded pages and an unfolded-only workspace; native widgets and visual selection; cross-page dragging and temporary pages; work/personal profiles; Home folders; local wallpapers and daylight appearance; layout backup; Google search/Discover; long-press customization; and motion/recovery refinements.
