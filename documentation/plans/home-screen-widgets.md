<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: random picks and setlist widgets on Android and iOS

**Status:** not started. The plan adds two home screen widgets:

- **Random picks:** a few random songs from the library, a tap opening one, and a shuffle button that draws new ones.
- **Setlist:** the next gig, or a setlist the user pinned, with a tap opening its first song in setlist mode.

`ui/` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, as in the root `CLAUDE.md`.
The app's own links landed with the app shortcuts (step 2 here says what is left of them).

## Decisions

### Both widgets

- **Android and iOS only.** The macOS and Windows builds are the JVM desktop app, where a widget would be a native
  project of its own (a WidgetKit extension signed into the jpackage bundle; a COM widget provider in the MSIX). The web
  has nothing comparable.
- **The app writes a snapshot, and the widgets read only that.** A small JSON file, `WidgetSnapshot`, holds every
  song's file name, title, and artist; every setlist's file name, title, date, archived state, and songs in order;
  which features are switched on; and the widgets' few strings already in the app's language. The widgets never read
  the library, parse ChordPro, or load Koin. That keeps the Android widgets cheap on a cold process and lets the iOS
  extension link no Kotlin, as the share extension already doesn't.
- **The snapshot isn't a stored document.** The app rewrites it whenever what it holds changes, and the widgets always
  ship in the same bundle as the app that writes it. So there's no `version`, no `upgrade-` fixture, and decoding just
  ignores unknown keys.
- **A widget's text comes from the app's language.** Its strings arrive in the snapshot, so they follow the in-app
  language picker rather than the system's. Only what the system shows before the app has ever run (each widget's
  gallery name and description) are platform resources, in every app language. A relative day ("Today", "In 3 days")
  is one of a few plural strings the snapshot carries, chosen by the widget from the count; iOS gets them as the
  language's plural forms, so the snapshot carries every form the language uses.
- **Every tap is an app link**: `campfire://` followed by the web build's address for the screen (`BrowserRoutes`), so
  one parser serves the web, the widgets, and the app shortcuts.
- **Features only hide, so the widgets hide too.** With Songs switched off, both widgets say so. With Setlists
  switched off, the setlist widget says so. Neither draws from a library the app doesn't show.
- **Fonts:** the iOS widgets bundle Inter, as the app does. Glance can't load a bundled font, so the Android widgets
  are set in the system's font, the one exception to the root `CLAUDE.md`'s rule, written down there.
- **Colors:** Android uses `GlanceTheme`, with dynamic colors where the system has them and the app's purple and
  orange otherwise. iOS uses the system's widget background with the app's accent color.
- **No network, and not backed up.** The snapshot names songs and setlists, so it's excluded from system backup, as
  `covers/` is, and the root `CLAUDE.md`'s privacy list names it.
- **iOS 17 and later.** The widget extension's deployment target is iOS 17, the first with interactive widget buttons
  and the `AppIntent` widget configuration, so there's one code path. The app itself stays on 15.3.

### Random picks

- **The widget draws its own picks.** It doesn't show the songs list's random picks, and it doesn't share their seed,
  their hourly redraw, or their `RandomPicksDraw` bookkeeping. Neither side ever changes the other's picks, so nothing
  has to agree on when a redraw happens.
- Up to five songs (fewer when the widget is too small or the library has fewer), each as its title and artist. There
  is no 20-song threshold: a library of three shows all three.
- Tapping a song opens its details screen (`song/{song}`). Tapping anywhere else opens the app.
- Shuffle draws again at once. Otherwise, new picks are drawn once an hour.
- An empty library shows "Add songs in Campfire".
- Settings → Features' "Random picks" switch is about the songs list and doesn't affect the widget: someone who added
  the widget asked for it.

### Setlist

- **Next gig or pinned.** When it's added, the widget asks which setlist to show: Next gig (the default) or one of the
  library's setlists by title.
  - Next gig is the non-archived setlist with the earliest date that is today or later; among setlists of the same day,
    the setlists screen's order decides. With no such setlist, the widget says there is no upcoming setlist, and a tap
    opens the setlists screen.
  - A pinned setlist is kept by its file name, the identity setlists have everywhere. An archived one is still shown,
    since archived setlists stay readable. One that has been deleted or renamed says it's no longer in the library, and
    a tap opens the setlists screen. The widget can be pointed at another setlist from its settings (a long press on
    Android, Edit Widget on iOS).
- **What it shows:** the title, the date, and how far away it is ("Today", "Tomorrow", "In 3 days"). The setlist's own
  "Show countdown" setting is about the setlists screen's header, so the widget always shows the relative day, which
  is the point of a next gig widget. Then the song count and, where there is room, the first songs' titles in order.
- **One tap target.** Tapping anywhere on the widget opens the setlist's first song in setlist mode
  (`setlist/{setlist}/{first song}`), over the setlists screen, as if it had been opened from there. A setlist with no
  songs opens the setlists screen with that setlist brought to the top, as a shared link to it does
  (`SetlistRevealEffect`).
- **The day turns over at midnight.** "In 3 days" becomes "In 2 days", and a next gig whose day has passed gives way to
  the next one.

## Where it hooks in today

- Songs and setlists arrive in `CampfireViewModel` through `GetScreenDataUseCase`'s `ScreenData`. The Songs and
  Setlists switches are feature switches (`navigation/FeatureSwitches.kt`).
- `navigation/BrowserRoutes.kt` already turns `song/{song}`, `setlist/{setlist}/{song}`, `setlists`, and the other
  addresses into a back stack. Songs and setlists are addressed by file name everywhere.
- A setlist's songs are read through `CampfireDestination.SongDetails(songFileNames, setlistFileName, initialIndex)`.
- Android: `CampfireMainActivity.handle` already tells the `campfire://oauth` redirect and `campfire://s/` share links
  apart from files. The filters live on `.CampfireIntentActivity` (`app/android/CLAUDE.md`). Backup rules are
  `res/xml/full_backup_content.xml` and `data_extraction_rules.xml`.
- iOS: the App Group (`APP_GROUP_ID`, both targets' entitlements, `CampfireAppGroup` in both `Info.plist`s) already
  holds the share extension's `ShareInbox/`, and the share inbox has its own backup exclusion. `IosFileImport.openUrl`
  receives `campfire://` links.

## Steps

### 1. The snapshot (common code)

- `ui/widget/`: `WidgetSnapshot` (`@Serializable`: songs as `fileName`, `title`, `artist`; setlists as `fileName`,
  `title`, `date`, `isArchived`, and their songs' file names in order; `areSongsEnabled` and `areSetlistsEnabled`; and
  the strings), and `widgetSnapshot(screenData, preferences, strings)`, which builds it.
- `WidgetSnapshotPublisher` is a platform interface with one `publish(snapshot)`. Android and iOS implement it, and
  desktop and web get a no-op. `CampfireViewModel` (or a small collector it owns) publishes when the snapshot changes,
  but only after the library has been read in full (never from a first read's partial batches), debounced, and
  skipping a write whose content equals the last one.
- In common code, for Android: `pickWidgetSongs(songs, count, random)` and `nextGig(setlists, today)`, the second
  using the setlists screen's own ordering. iOS has its own few lines of Swift for both, since nothing needs the
  platforms to agree on a draw, and the next gig rule is short enough to write twice.
- Tests in `commonTest`: the snapshot built from a library, the feature switches, a write skipped when unchanged, the
  draw, and the next gig (today, a later day, a past one, an archived one, two on one day, none).

### 2. App links

- Landed with the app shortcuts: `campfire://<web address>` opens what the web build's address opens (`AppLinks`,
  `AppLinkOpening.kt`, `LaunchState.navigateToLink`), on Android (`.CampfireIntentActivity`'s scheme-only filter,
  `handle`) and iOS (`openUrl`, after the share links); an address naming nothing opens the home screen.
- Left: a song or setlist that's no longer in the library opens the songs or setlists screen rather than the home
  screen, with tests for `song/{song}` and `setlist/{setlist}/{song}` links, a name with spaces and non-ASCII
  characters, and a missing song or setlist.

### 3. The Android widgets

- `androidx.glance:glance-appwidget` in `:app:android`. Credit it and every transitive dependency it brings
  (`androidx.work` and what that pulls in; read `dependencies` for the full list) in the same change, as the root
  `CLAUDE.md`'s Build section says.
- The publisher writes `widget/snapshot.json` in the app's files directory, then calls `updateAll` on both widgets.
- `RandomPicksWidget` (a `GlanceAppWidget`) and its receiver. `SizeMode.Responsive` sets how many rows fit. Each
  widget's picks and draw time live in its own Glance state, so two widgets differ. `updatePeriodMillis` of one hour
  redraws once the stored picks are an hour old, and Shuffle is an `ActionCallback` that draws and updates.
- `SetlistWidget` and its receiver, with a configuration activity (`android:configure`, reconfigurable) that lists Next
  gig and the setlists by title from the snapshot, drawn with Compose in the app's theme. The choice lives in the
  widget's Glance state. The hourly update also covers midnight, and `ACTION_DATE_CHANGED` and
  `ACTION_TIMEZONE_CHANGED` update it at once.
- Each widget's `appwidget-provider` XML: resizable, a gallery preview (`previewLayout`, and a generated preview on
  API 35+), and the name and description strings in every app language.
- Exclude `widget/` from both backup rule files.

### 4. The iOS widgets

- A new widget extension target, `CampfireWidgets` (SwiftUI and WidgetKit, iOS 17, the App Group entitlement), holding
  both widgets in one `WidgetBundle`, with Inter bundled, the MPL header on its Swift files, and its gallery strings in
  every app language.
- The publisher writes `Widget/snapshot.json` into the App Group container, excluded from backup as the share inbox
  is. WidgetKit has no Objective-C API that Kotlin could call, so the Swift app registers a closure at start that
  Kotlin calls after each write to run `WidgetCenter.shared.reloadAllTimelines()`. That's a new seam, documented in
  `app/ios/CLAUDE.md`.
- Random picks: the `TimelineProvider` makes a day of hourly entries, each with its own draw. The Shuffle button is an
  `AppIntent` that stores a new seed in the group's `UserDefaults` and reloads. Families: `systemMedium` (three songs)
  and `systemLarge` (five). A song row is a `Link` to its app link.
- Setlist: an `AppIntentConfiguration` whose parameter is an `AppEntity` listing Next gig and the snapshot's setlists.
  The timeline has an entry at each midnight. Families: `systemSmall` (title and relative day), `systemMedium` (with
  the first songs), and the lock screen's `accessoryRectangular` (title and relative day). The whole widget is one
  `widgetURL`.
- Signing: a new App ID (`<bundle id>.widgets`) with the App Group, its own provisioning profile, and its own entry in
  the publishing workflow. This follows the manual signing in `.github/CLAUDE.md` and the shared signing key, and
  never uses `-allowProvisioningUpdates`.

### 5. Documentation

- Root `CLAUDE.md`: product rule lines for both widgets (`ui/widget`, `app/android`, `app/ios`), the snapshot in the
  library layout and the backup list, and the Android font exception.
- New `ui/widget/CLAUDE.md`. Update `navigation/CLAUDE.md` (app links), `app/android/CLAUDE.md` (the receivers, the
  configuration activity, the link filter, and the backup rules), and `app/ios/CLAUDE.md` (the extension, the reload
  seam, and signing).
- The `add-language` skill: the widgets' gallery strings on both platforms are part of a new language.
