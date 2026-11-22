<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: a random picks widget on Android and iOS

**Status:** not started. The goal is a home screen widget that shows a few random songs from the library, opens one
on a tap, and draws new ones with a shuffle button. `ui/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, as in the root `CLAUDE.md`.

## Decisions

- **The widget draws its own picks.** It doesn't need to show the songs list's random picks, and it doesn't share
  their seed, their hourly redraw, or their `RandomPicksDraw` bookkeeping. Neither side ever changes the other's picks,
  so nothing has to agree on when a redraw happens.
- **Android and iOS only.** The macOS and Windows builds are the JVM desktop app, where a widget would be a native
  project of its own (a WidgetKit extension signed into the jpackage bundle; a COM widget provider in the MSIX). The web
  has nothing comparable.
- **The app writes a snapshot, and the widget reads only that.** A small JSON file, `WidgetSnapshot`, holds every
  song's file name, title, and artist, whether the Songs screen is switched on, and the widget's few strings already in
  the app's language. The widget never reads the library, parses ChordPro, or loads Koin. That keeps the Android widget
  cheap on a cold process and lets the iOS extension link no Kotlin, as the share extension already doesn't.
- **The snapshot isn't a stored document.** The app rewrites it whenever what it holds changes, and the widget always
  ships in the same bundle as the app that writes it. So there's no `version`, no `upgrade-` fixture, and decoding just
  ignores unknown keys.
- **The widget's text comes from the app's language.** Its strings ("Random picks", "Shuffle", and the empty states)
  arrive in the snapshot, so they follow the in-app language picker rather than the system's. Only what the system
  shows before the app has ever run (the widget gallery's name and description) are platform resources, in every app
  language.
- **What it shows:**
  - Up to five songs (fewer when the widget is too small or the library has fewer), each as its title and artist.
    There is no 20-song threshold: a library of three shows all three.
  - Tapping a song opens its details screen. Tapping anywhere else opens the app.
  - Shuffle draws again at once. Otherwise, new picks are drawn once an hour.
  - An empty library shows "Add songs in Campfire". With Songs switched off in Settings → Features, it shows that they
    are switched off. Features only hide, so the widget hides too and never draws from a library the app doesn't show.
  - Settings → Features' "Random picks" switch is about the songs list and doesn't affect the widget: someone who added
    the widget asked for it.
- **Fonts:** the iOS widget bundles Inter, as the app does. Glance can't load a bundled font, so the Android widget is
  set in the system's font, the one exception to the root `CLAUDE.md`'s rule, written down there.
- **Colors:** Android uses `GlanceTheme`, with dynamic colors where the system has them and the app's purple and
  orange otherwise. iOS uses the system's widget background with the app's accent color.
- **No network, and not backed up.** The snapshot names songs, so it's excluded from system backup, as `covers/` is,
  and the root `CLAUDE.md`'s privacy list names it.
- **iOS 17 and later.** The widget extension's deployment target is iOS 17, the first with interactive widget buttons,
  so there's one code path and the shuffle always works. The app itself stays on 15.3.

## Where it hooks in today

- Songs arrive in `CampfireViewModel` through `GetScreenDataUseCase`'s `ScreenData`. The Songs switch is a feature
  switch in `UserPreferences` (`navigation/FeatureSwitches.kt`).
- Songs are addressed by file name everywhere, and `navigation/BrowserRoutes.kt` already turns a web address such as
  `song/{song}` into a back stack. The widget's link reuses that parsing.
- Android: `CampfireMainActivity.handle` already tells the `campfire://oauth` redirect and `campfire://s/` share links
  apart from files. The filters live on `.CampfireIntentActivity` (`app/android/CLAUDE.md`). Backup rules are
  `res/xml/full_backup_content.xml` and `data_extraction_rules.xml`.
- iOS: the App Group (`APP_GROUP_ID`, both targets' entitlements, `CampfireAppGroup` in both `Info.plist`s) already
  holds the share extension's `ShareInbox/`, and the share inbox has its own backup exclusion. `IosFileImport.openUrl`
  receives `campfire://` links.

## Steps

### 1. The snapshot (common code)

- `ui/widget/`: `WidgetSnapshot` (`@Serializable`: songs as `fileName`, `title`, `artist`; `isSongsEnabled`; and the
  strings), and `widgetSnapshot(screenData, preferences, strings)`, which builds it.
- `WidgetSnapshotPublisher` is a platform interface with one `publish(snapshot)`. Android and iOS implement it, and
  desktop and web get a no-op. `CampfireViewModel` (or a small collector it owns) publishes when the snapshot changes,
  but only after the library has been read in full (never from a first read's partial batches), debounced, and
  skipping a write whose content equals the last one.
- `pickWidgetSongs(songs, count, random)` is the widget's draw for Android. iOS has its own few lines in Swift, since
  nothing needs the two to agree.
- Tests in `commonTest`: the snapshot built from a library, the Songs switch, a write skipped when unchanged, and the
  draw.

### 2. A link to a song

- `campfire://song/<percent-encoded file name>` opens the song's details screen over the songs list, parsed by the
  same code as the web's `song/{song}` address. A name that's no longer in the library opens the songs list.
- Android: an intent filter on `.CampfireIntentActivity` and a branch in `handle`, before the file import. iOS: a
  branch in `openUrl`, beside the share links.
- Tests: the link parsed and built, a name with spaces and non-ASCII characters, and a missing song.

### 3. The Android widget

- `androidx.glance:glance-appwidget` in `:app:android`. Credit it and every transitive dependency it brings
  (`androidx.work` and what that pulls in; read `dependencies` for the full list) in the same change, as the root
  `CLAUDE.md`'s Build section says.
- `RandomPicksWidget` (a `GlanceAppWidget`) and its receiver. `SizeMode.Responsive` sets how many rows fit. Each
  widget's picks and draw time live in its own Glance state, so two widgets differ. `updatePeriodMillis` of one hour
  redraws once the stored picks are an hour old, and Shuffle is an `ActionCallback` that draws and updates.
- The publisher writes `widget/songs.json` in the app's files directory, then calls `updateAll`. A song tap is the
  link from step 2.
- `appwidget-provider` XML: resizable, a gallery preview (`previewLayout`, and a generated preview on API 35+), and
  the name and description strings in every app language.
- Exclude `widget/` from both backup rule files.

### 4. The iOS widget

- A new widget extension target, `CampfireWidget` (SwiftUI and WidgetKit, iOS 17, the App Group entitlement), with
  Inter bundled, the MPL header on its Swift files, and its gallery strings in every app language.
- The publisher writes `Widget/songs.json` into the App Group container, excluded from backup as the share inbox is.
  WidgetKit has no Objective-C API that Kotlin could call, so the Swift app registers a closure at start that Kotlin
  calls after each write to run `WidgetCenter.shared.reloadAllTimelines()`. That's a new seam, documented in
  `app/ios/CLAUDE.md`.
- The `TimelineProvider` makes a day of hourly entries, each with its own draw, and a timeline ending `.atEnd`. The
  Shuffle button is an `AppIntent` that stores a new seed in the group's `UserDefaults` and reloads. Families:
  `systemMedium` (three songs) and `systemLarge` (five). A song row is a `Link` to the step 2 link.
- Signing: a new App ID (`<bundle id>.widget`) with the App Group, its own provisioning profile, and its own entry in
  the publishing workflow. This follows the manual signing in `.github/CLAUDE.md` and the shared signing key, and
  never uses `-allowProvisioningUpdates`.

### 5. Documentation

- Root `CLAUDE.md`: a product rule line (the widget, its own picks, Android and iOS: `ui/widget`, `app/android`,
  `app/ios`), the snapshot in the library layout and the backup list, and the Android font exception.
- New `ui/widget/CLAUDE.md`. Update `app/android/CLAUDE.md` (the receiver, the link filter, and the backup rules) and
  `app/ios/CLAUDE.md` (the extension, the reload seam, and signing).
- The `add-language` skill: the widgets' gallery strings on both platforms are part of a new language.
