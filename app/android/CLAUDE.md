<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :app:android

## `CampfireMainActivity`

- A single `ComponentActivity`, no AppCompat or Material Components. Links open in Custom Tabs, except
  `play.google.com`, handed to the Play Store app by package.
- Files from `ACTION_VIEW` and `ACTION_SEND`/`ACTION_SEND_MULTIPLE` are read off the main thread into a top level
  `Channel` (`AndroidFileImport.kt`) owned by the process, so a recreation mid-read loses nothing. An unreadable file is
  passed on empty (reported as skipped).
- **An intent is handled once**: a recreated activity (`savedInstanceState != null`) or one from Recents
  (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) skips it. `singleTask`, so a second file arrives at `onNewIntent`.
- **`android:configChanges` handles most changes in place, but not locale and `uiMode`**: the System language option
  reads the JVM default locale (not composition state), and the window background and splash colors come from
  `values-night`, which only a new window reads.
- `adjustResize` is required: unspecified means adjustPan for a Compose window, stacking on the app's own IME padding.
- **Meta + /** lists the app's shortcuts under the system's from `onProvideKeyboardShortcuts`, answered with what the
  composition last handed over (`onKeyboardShortcutsChanged`), since the strings are the composition's language.
- `handle` tells the `campfire://oauth` redirect from a file before `setContent`, so a redirect that started a killed
  process is waiting for the view model (the rest of sign-in: `data/source/remote/implementation/CLAUDE.md`).

## Manifest intent filters

- **The activity declares no intent filter of its own**: since Android 15 an explicit intent from another app (Android
  Studio's shell included) must match one of the target's filters, and Studio's carries MAIN/LAUNCHER; a component with
  no filters is exempt. The open, share, link, and sign-in filters are on `.CampfireIntentActivity`, an always-enabled
  alias that is no launcher entry and not managed by `AppIconSwitcher`.
- **Open with**: `content://` only (no storage permission, so `file://` couldn't be read). The ChordPro extensions
  with a wildcard MIME type, since a `content://` URI has no extension to the resolver without a declared type. Keep in
  step with `LibraryFiles.SONG_EXTENSIONS`, plus `.abc` (offered, never claimed as the default: Campfire is no ABC
  editor). No `application/zip` Open with filter.
  - API 31+: `pathSuffix`, lower and upper case.
  - Older: `pathPattern`, whose `.*` never backtracks, so one pattern per number of dots in the path, up to four; lower
    case only. `pathAdvancedPattern` doesn't backtrack either.
  - A second filter takes `text/plain`, `text/vnd.abc`, `application/octet-stream`, and the unregistered ChordPro types
    (`application/x-chordpro`, `text/x-chordpro`, `text/chordpro`, `application/chordpro`) without a path, for URIs with
    no file name (Downloads' `msf:` ids, attachments); unrelated files offered this way are reported as skipped. Share
    takes the same ChordPro types: SongbookPro shares a single `.cho` under a type of its own.
- **Pictures**: PNG, JPEG, GIF, WebP, HEIC, HEIF, and AVIF in their own Open with filter and in Share (a photo alone
  is a new song, shrunk or converted as one added in the editor is);
  Android makes no app a type's default unless the user picks it. A name without a picture's extension gets the one its
  MIME type says (`pictureFileName`), since the import knows a lone picture by its name.
- **Photo picker backport**: a disabled `ModuleDependencies` service asks Play services for its photo picker on Android
  9 and 10, which have none (`ui/platform/CLAUDE.md`).
- **Share**: a stream wins over text in the same intent (the text is a caption). A text that is a page's address (a
  browser's Share, a site's call to action) is read by the app itself in an offscreen `WebView` (`presentation/CLAUDE.md`).
- **Shared links**: an `autoVerify` App Link for `https://campfire-songbook.com/s/` (never `/app/`, the web app) and
  `campfire://s/`. `handle` checks the OAuth redirect first; a link `ShareLinks.parse` reads goes to
  `shareLinksToImport`, one it doesn't only brings the app forward (never the file import). The `.debug` package is not
  in the website's `assetlinks.json` (the debug key is public), so it is given links with
  `adb shell am start -a android.intent.action.VIEW -d '<link>'`.

## Launcher shortcuts (`shortcuts/`)

- Dynamic shortcuts set by `LauncherShortcutPublisher` from what the composition hands over (`ui/shortcuts`), each an
  `ACTION_VIEW` of its `campfire://` link aimed at `.CampfireIntentActivity`, whose scheme-only filter (not BROWSABLE)
  it must match; no flags, so the running task is kept (`onNewIntent`). `handle` takes the app links after the OAuth
  and share links and reports a shortcut's use.
- A pinned shortcut no longer offered is disabled with `app_shortcut_withdrawn`, enabled again once offered.
- A dynamic shortcut belongs to the launcher entry that was the main one when it was published, and the system drops it
  with that entry, so `AppIconSwitcher` has the last list published again after a switch that changed an entry.

## Home screen widgets (`widget/`)

- Glance, over the snapshot the composition hands over (`ui/widget/CLAUDE.md`): `WidgetSnapshotPublisher` writes
  `widget/snapshot.json` (files directory; skipped when unchanged, written beside it and renamed over it) on a process
  scope, then refreshes both widgets. The widgets read only that file (never Koin or the library) and call the common
  decisions (`randomPicksContent`, `setlistContent`); no snapshot yet (the app never ran) says to open the app.
- **The file is read once per process, on IO** (`WidgetSnapshotFile.load` in `provideGlance`, then kept current by its
  `write`): Glance composes on the main thread, once per responsive size.
- **A refresh moves `REFRESHED_AT` in each widget's Glance state before `update`**: a composition still alive is only
  recomposed by an update, and not at all for a state that did not change, so `rememberWidgetSnapshot` reads it, and
  the day is read again.
- The Setlist's songs are a `LazyColumn` (scrolling, so any number; a plain container draws only its first ten
  children), each song opening the setlist at it.
- Random picks: `SizeMode.Responsive`, one layout per row count (up to five). The draw is a seed in the widget's own
  state, drawn again by Shuffle (`ShuffleAction`, public: Glance's R8 rules keep only public `ActionCallback`s) and by
  `provideGlance` once 55 minutes old (the hourly `updatePeriodMillis` alarm repeats from itself, not from the draw).
- Setlist: `SetlistWidgetConfigurationActivity` (`android:configure`, `reconfigurable`; own task affinity, out of
  Recents, so it never raises the app) shows `SetlistWidgetChooser` from `:presentation` and keeps the file name (none
  for Next gig) in the widget's state. Today is `LocalDate.now()`; the receiver also hears `DATE_CHANGED`, `TIME_SET`, and
  `TIMEZONE_CHANGED` (the date's isn't exempt from the background limits everywhere; the hourly update covers it).
- A launcher lays a widget out in the system's direction, so one in an app language written the other way is mirrored
  by hand (`isMirrored`: lines aligned to their end, the header's button first).
- Every tap is `appLinkIntent` (the shortcuts' intent). Text is the system font (a launcher can't load Inter); colors
  are the snapshot's theme (`WidgetTheme.kt`): its palette where it has one, else the wallpaper's from Android 12 (the
  app's `widgetLightColorScheme` before), in a fixed half where the app has one, which also spares a launcher below 12
  the half it resolved at drawing time. Gallery names,
  descriptions, and the open-the-app sentence are `res/values*/strings.xml` in every app language (`values-iw` for
  Hebrew); `previewLayout`s are plain RemoteViews layouts with placeholder lines.

## Permissions

`RECORD_AUDIO` is asked only by a tap that opens a tuner; `android.hardware.microphone` is not required, so Play still
offers the app without one. BIND_GET_INSTALL_REFERRER_SERVICE comes from Play's Install Referrer library: IPC to the
Play Store app only, read on a first launch (`presentation/src/androidMain/CLAUDE.md`).

## `sync/CampfireSyncService`

- A foreground service with a Stop action that **does no syncing** — the run lives in `SyncRepository`; it only keeps the
  process alive. Strings arrive in the intent, so the notification follows the in-app language.
- **Runs only while the app is not in front**: otherwise every edit's run would flash a notification. `SyncServiceStarter`
  starts it in `onPause` (not on rotation), or at once for a run handed over after that, and stops it in `onResume`.
- Notification updates at most twice a second (`BackgroundNotificationScheduler`); Android sheds more than five a second.
- It watches `GetSyncStateUseCase` itself and stops when no run is going (the activity may be gone), after a 2 s grace
  so a chained run, typically started in the background where the service couldn't be restarted, keeps it.
- **Only ever stops itself after `startForeground`**, or the system treats the missing foreground call as a crash.
  `onTimeout` (Android 15's six-hour data sync budget) stops the run and the service.

## `importing/CampfireImportService`

- The sync service's twin for an import that receives, downloads, unpacks, or writes (`ImportWork`), with a notification
  of its own (id 3) beside sync's; a `dataSync` service too, sharing Android 15's six hours a day. It imports nothing.
- Started by `ImportServiceStarter` as sync's is (`onPause`, not on rotation; work that starts after that; dismissed in
  `onResume`); the words arrive in the intent (`ImportNotification.toWords`), the text and bytes come from the work it
  watches (`ImportNotificationText.kt`), at most once a second and only when what it shows changes
  (`ImportNotificationContent`: a re-post can swallow a tap on Cancel), a new phase at once.
- **Cancel only before the write** (`ImportWorkState.isCancellable`); it calls `ImportWork.cancel` and lets the work's
  end stop the service, so the staging is deleted under it. `onTimeout` calls `ImportWork.interrupt` (the import screen
  then says why, with Try again) and stops at once.
- The URI is never passed to it: the activity's copy job opens the stream, which keeps reading after the grant ends
  while the process lives (step 0's spike in the streaming import plan).
- The Play Console's `dataSync` declaration names the import as a use beside sync.

## `metronome/CampfireMetronomeService`

- `mediaPlayback` foreground service: a framework `MediaSession` (no Media3; minSdk 28) and a `MediaStyle`
  notification on its own low-importance channel (needs no notification permission). Plays nothing: the `Metronome`
  singleton's audio output owns focus and the noisy receiver. Every stop control means `metronome.stop()`.
- Follows the engine's `playback` to stop, starting in `onStartCommand` once in the foreground (same
  `startForeground`-first rule).
- Swiping the app away stops the engine and the service in `onTaskRemoved`, which is why `stopWithTask` is false (the
  callback would never come).
- The Play Console needs the `mediaPlayback` type declared.

## Launcher icon (`AppIconSwitcher`)

- The activity isn't a launcher entry; `activity-alias`es are, one per theme color, plus System (`values-v31`). Exactly
  one is enabled.
- **The first switch waits for `onStop`** (not for a screen locked over a playing click).
- `.CampfireActivity` keeps the activity's former name (home screens and restored layouts point at it by name). It is
  the only alias enabled in the manifest, disabled explicitly at the first switch, never re-enabled; every other alias
  only moves between enabled and its manifest default of disabled. **Load bearing**: Android closes tasks of a
  component explicitly disabled but not of one set back to its disabled default, so the user's place is lost only at
  the first switch.
- A switch refused by device policy is logged, not recorded, and retried at the next change or stop.
- Android Studio's "Default Activity" is `.CampfireActivity`, disabled after a switch, so use `.run/Android.run.xml`.
  From the command line, `adb shell monkey -p com.pandulapeter.campfire.debug -c android.intent.category.LAUNCHER 1`.

## Backup

`data_extraction_rules.xml` (API 31+) and `full_backup_content.xml` (API 28–30) **must say the same thing**: an
allow-list of `library/songs/`, `library/setlists/`, and `preferences/preferences.json`. No `<exclude>`s (lint rejects
one outside an included path), so the widgets' `widget/` stays out by not being listed. The 25 MB quota is all or
nothing, which is why the songs' pictures (`library/files/`) stay out: a restored library shows their placeholders until
sync or an import brings them back.

## Build and resources

- The release packages the committed `src/main/generated/baselineProfiles` and never generates them
  (`automaticGenerationDuringBuild = false`); `profileinstaller` installs the profile for non-Play installs.
  `generateBaselineProfile` marks every method rule hot (`H`), which is all ART compiles ahead of time: the recording
  leaves most of the app's own code startup-only (`SP`), which ran interpreted, the first frame of a screen ~20% slower
  on a Galaxy S9.
- The theme is the platform's `Theme.Material` with the window background pinned to Material 3's
  (`campfire_window_background`), painting the starting window, splash, and pre-preferences frame. A splash icon
  background color is named only so Android 12+ draws the whole adaptive icon.
