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

Android application entry point. `CampfireAndroidApplication` starts Koin with the `androidContext`.

## `CampfireMainActivity`

- Single `ComponentActivity`, no AppCompat or Material Components. Links open in Custom Tabs (`UrlOpening.kt`), except
  `play.google.com`, handed to the Play Store app by package. A link nothing can open is reported to the UI.
- Files from `ACTION_VIEW` and `ACTION_SEND`/`ACTION_SEND_MULTIPLE` are read off the main thread into a top level
  `Channel` (`AndroidFileImport.kt`); channel and scope belong to the process, so a recreation mid-read loses nothing.
  An unreadable file is passed on empty (reported as skipped).
- **An intent is handled once**: a recreated activity (`savedInstanceState != null`) or one from Recents
  (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) skips it. `singleTask`, so a second file arrives at `onNewIntent`.
- **`android:configChanges` handles most changes in place** (orientation, size, layout, density, font scale, keyboard,
  navigation), but **not locale and `uiMode`**: the System language option reads the JVM default locale (not
  composition state), and the window background and splash colors come from `values-night`, which only a new window
  reads.
- `adjustResize` is required: unspecified means `adjustPan` for a Compose window, stacking on the app's own IME padding.

## Manifest intent filters

- **Open with**: `content://` only (no storage permission, so `file://` couldn't be read). The ChordPro extensions
  (`.cho`, `.chopro`, `.chordpro`, `.crd`, `.chord`, `.pro`) with a wildcard MIME type, since a `content://` URI has no
  extension to the resolver without a declared type. Keep in step with `LibraryFiles.SONG_EXTENSIONS`.
  - API 31+: `pathSuffix`, lower and upper case.
  - Older: `pathPattern`, whose `.*` never backtracks, so one pattern per number of dots in the path, up to four; lower
    case only. `pathAdvancedPattern` doesn't backtrack either.
  - A second filter takes `text/plain` and `application/octet-stream` without a path, for URIs with no file name
    (Downloads' `msf:` ids, attachments). Unrelated files offered this way are reported as skipped (an unknown name is
    kept only if its bytes are a zip, or text with a song's structure).
  - No `application/zip` Open with filter.
- **Share**: `text/plain` (also shared text), `application/octet-stream`, the two zip types, JSON (other apps' song
  exports), PDF, and `.docx`.
- Shared text (`EXTRA_TEXT`, named by `EXTRA_SUBJECT`) reaches `CampfireAndroidApp` as `sharedTexts`; what it becomes
  is `:presentation`'s. A stream wins over text in the same intent (the text is a caption).
- **The activity declares no intent filter of its own**: since Android 15 an explicit intent from another app (Android
  Studio's shell included) must match one of the target's filters, and Studio's carries MAIN/LAUNCHER. A component with
  no filters is exempt. The open, share, and sign-in filters are on `.CampfireIntentActivity`, an always-enabled alias
  that is no launcher entry and not managed by `AppIconSwitcher`.
- `FileProvider` (`${applicationId}.files`, `res/xml/file_paths.xml`) shares songs out.

## Permissions

`INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` (sync);
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `VIBRATE` (metronome); `RECORD_AUDIO` (tuner, asked only by a tap that opens a
tuner; `android.hardware.microphone` is not required, so Play still offers the app without one).

## Sync sign-in

- `CampfireMainActivity.handle` tells the `campfire://oauth` redirect from a file and forwards it to
  `onSyncRedirectReceived`. It runs before `setContent`, so a redirect that started a killed process is waiting for the
  view model. The rest of sign-in is `data/source/remote/implementation/CLAUDE.md`'s OAuth section.

## `sync/CampfireSyncService`

- A foreground service with a Stop action that **does no syncing** — the run lives in `SyncRepository`; it only keeps the
  process alive. Strings arrive in the intent, so the notification follows the in-app language.
- **Runs only while the app is not in front**: otherwise every edit's run would flash a notification. `SyncServiceStarter`
  starts it in `onPause` (not on rotation), or at once for a run handed over after that, and stops it in `onResume`.
- Notification updates at most twice a second (`SyncNotificationScheduler` in `:presentation`'s `ui/platform`, tested);
  Android sheds more than five a second.
- It watches `GetSyncStateUseCase` itself and stops when no run is going (the activity may be gone), after a 2 s grace
  (`scheduleRelease`) so a chained run, typically started in the background where the service couldn't be restarted,
  keeps it.
- **Only ever stops itself after `startForeground`**, or the system treats the missing foreground call as a crash.
  `onTimeout` (Android 15's six-hour data sync budget) stops the run and the service.

## `metronome/CampfireMetronomeService`

- `mediaPlayback` foreground service: a framework `MediaSession` (no Media3; minSdk 28) for the lock screen, headset,
  and Bluetooth, and a `MediaStyle` notification on its own low-importance channel (needs no notification permission).
  Plays nothing: the `Metronome` singleton's audio output owns focus and the noisy receiver. Every stop control means
  `metronome.stop()`.
- Follows the engine's `playback` to stop, starting in `onStartCommand` once in the foreground (same
  `startForeground`-first rule).
- Started via `MetronomeNotifier` when a click starts (always a tap in front); `startService` for new words while
  running.
- Swiping the app away stops the engine and the service in `onTaskRemoved`, which is why `stopWithTask` is false (the
  callback would never come).
- The Play Console needs the `mediaPlayback` type declared.

## Launcher icon (`AppIconSwitcher`)

- The activity isn't a launcher entry; `activity-alias`es are, one per theme color (`CampfireActivity…<Color>`,
  `mipmap-anydpi/ic_launcher_<color>.xml`), plus System (`system_accent1_600`, `values-v31`). The app's own icon is a
  purple to orange gradient drawn as bitmaps. Exactly one is enabled.
- Switches when `CampfireAndroidApp` reports the preference, on its own thread, one at a time. **The first switch waits
  for `onStop`** (not for a screen locked over a playing click).
- `.CampfireActivity` keeps the activity's former name (home screens and restored layouts point at it by name). It is
  the only alias enabled in the manifest, disabled explicitly at the first switch, never re-enabled; every other alias
  only moves between enabled and its manifest default of disabled. **Load bearing**: Android closes tasks of a
  component explicitly disabled but not of one set back to its disabled default, so the user's place is lost only at
  the first switch.
- A switch refused by device policy is logged, not recorded, and retried at the next change or stop.
- Android Studio's "Default Activity" is `.CampfireActivity`, disabled after a switch ("Activity class … does not
  exist"), so use `.run/Android.run.xml`, which starts `CampfireMainActivity`. From the command line,
  `adb shell monkey -p com.pandulapeter.campfire.debug -c android.intent.category.LAUNCHER 1`.

## Backup

`data_extraction_rules.xml` (API 31+) and `full_backup_content.xml` (API 28–30) **must say the same thing**: an
allow-list of `library/` and `preferences/preferences.json`. No `<exclude>`s (lint rejects one outside an included
path). The 25 MB quota is all or nothing.

## Build

- `debug` (`.debug` suffix, the committed standard debug keystore `internal.keystore`) and `release` (R8 + resource
  shrinking), plus `nonMinifiedRelease` and `benchmarkRelease` for `:app:baselineprofile`.
- The release packages the committed `src/main/generated/baselineProfiles` (`baseline-prof.txt`, `startup-prof.txt`)
  and never generates them (`automaticGenerationDuringBuild = false`). `profileinstaller` is a direct dependency; it
  installs the profile for non-Play installs.
- `release` signing reads `campfire.android.*` (see the root `CLAUDE.md`).

## Resources

The only Android XML resources in the project (everything Compose draws is in `:presentation`'s `composeResources`).
The theme is the platform's `Theme.Material` with the window background pinned to Material 3's
(`campfire_window_background`, `#FEF7FF` / `#141218`), painting the starting window, splash, and pre-preferences frame.
A splash icon background color is named only so Android 12+ draws the whole adaptive icon.
