<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :app:ios

Kotlin/Native module producing the static `ComposeApp` framework for the Xcode project in `iosApp/` (a SwiftUI wrapper:
`ContentView.swift` embeds the view controller with `.ignoresSafeArea()`, `iOSApp.swift` forwards `.onOpenURL` and
`scenePhase`).

## `CampfireViewController.kt`

- Links open through `UIApplication.openURL`; an address `NSURL` refuses as written (non-ASCII, `|`, which iOS 15–16
  don't encode) is percent-encoded first.
- Sets the window's `overrideUserInterfaceStyle` from the theme (unspecified for System): the status bar, pickers, share
  sheet, and keyboard follow UIKit's style, not Compose's theme.
- **App icon**: alternate `AppIcon-<Color>` sets (light, dark, and tinted each) named in
  `ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES`, switched by `applyAppIcon` (`setAlternateIconName`). iOS shows an
  unsuppressible alert per switch and refuses it while the app is not active, so `CampfireIosApp` asks only after the
  color has been stable for a second while resumed, and again at every resume (no-op if matching; applies a restored
  backup's preference).

## Files in and out

- `IosFilePicker.kt`: a presentation UIKit refuses (something already presented, a dismissing sheet included) counts as
  nothing picked/shared, not done. Export bytes are written on IO; only presenting is on main. Picked files are copies,
  deleted after reading. Export/share temp files are deleted by their maker (the share in the activity controller's
  completion handler); **the temporary directory is never swept**, since a share target may still be reading.
- `IosFileImport.kt`'s `openUrl(url)` (from Swift's `.onOpenURL`): a sync redirect never arrives here
  (`ASWebAuthenticationSession` answers it) but is ignored anyway, so it isn't reported as a failed import. URLs are read
  in order, one at a time, off the main thread; an unreadable file is passed on empty (reported as skipped).
- Files from AirDrop, Mail, or Messages are copies in `Documents/Inbox` and deleted after reading — only there, since a
  file opened in place is the user's original. Originals are read through an `NSFileCoordinator` (`readImportedFile`),
  which downloads an iCloud file and waits for a writer.
- `cleanImportInbox()` runs when the scene goes to the background, **not at start**: a cold start's file is already in
  the inbox before it is handed over, so it couldn't be told from a leftover.

## `Info.plist`

- `CFBundleURLTypes` declares `campfire`: `ASWebAuthenticationSession` doesn't need it, but it is Dropbox's registered
  redirect URI.
- `ITSAppUsesNonExemptEncryption` `false` (system HTTPS, SHA-256, and Keychain are exempt); adding encryption changes
  it, and the Mac `macos/export-compliance.plist` too.
- `UIFileSharingEnabled` + `LSSupportsOpeningDocumentsInPlace` put the library (in Documents; the preferences are not)
  under "On My iPhone → Campfire"; opening in place commits to coordinated reads.
- Backup: `IosFileStorage` marks the sync index, the forget-pending note, the editor draft, and every cover copy as
  excluded each time they are written; credentials are a device-bound Keychain item.
- `UTImportedTypeDeclarations` + `CFBundleDocumentTypes`: `.cho` `Default`, the rest `Alternate` (shared extensions).
  No zip or plain text, deliberately. Keep in step with `LibraryFiles.SONG_EXTENSIONS`.
- `CFBundleLocalizations` (`en`, `hu`) tells UIKit which languages the app speaks (Compose resources are invisible to
  iOS); without it system UI stays English and Settings offers no per-app language. Keep in step with `:presentation`'s
  `values-*`. Each language also needs `<lang>.lproj/InfoPlist.strings` (the microphone prompt; Hungarian is
  `iosApp/iosApp/hu.lproj/InfoPlist.strings`, a variant group).
- `UIBackgroundModes` `audio` is for the metronome; the session is active only while a click plays (what App Review
  checks). The tuner adds only `NSMicrophoneUsageDescription`, never listening out of sight.
- `LaunchScreen.storyboard` is just the catalog's `LaunchBackground` color, matching the first frame.

## `PrivacyInfo.xcprivacy`

Required by App Store Connect. No tracking or collected data; required-reason APIs: file timestamps (C617.1 for the
library and skia/ICU `stat`; 3B52.1 for picked files) and boot time (`mach_absolute_time`, 35F9.1). The framework is
static, so Compose's, skiko's, and the Kotlin runtime's calls are the app's to declare. After updating any of them, run
`nm -u` on a Release `Campfire` executable to spot a new category (`NSUserDefaults` is the likely one).

## `IosSyncNotifier.kt`

- One per process, following `GetSyncStateUseCase` itself (Compose stops collecting once the scene leaves the
  foreground): holds a `beginBackgroundTask` for the run, ended 2 s after it (`SyncNotificationScheduler`, shared with
  Android), kept by a chained run that starts meanwhile.
- Posts a local notification only while in the background (iOS silences foreground ones), at most once a second; taken
  down on return. Permission is asked when a run starts, in front. No progress bar or button.
- A background task buys tens of seconds: on expiry the run is stopped and reports itself interrupted; a killed process
  is found by the index's "a run was going" marker.

## `IosMetronomeNotifier.kt`

`MPNowPlayingInfoCenter` (song or "Metronome", tempo, in the app's language) and `MPRemoteCommandCenter` pause, stop, and
toggle — all stop (no paused state). Draws and answers only: the audio session, background audio, and interruptions
belong to `:metronome:implementation`. Follows the engine's `playback` to take Now Playing down.

## Xcode project and building

- A "Compile Kotlin Framework" phase runs `./gradlew :app:ios:embedAndSignAppleFrameworkForXcode`.
- Team id, bundle id, and app name are in `iosApp/Configuration/Config.xcconfig`; **change the team there, not in Xcode's
  Signing tab**, which writes a literal over `$(TEAM_ID)`.
- Version and build number come from `campfire.versionName` / `campfire.buildNumber` via the "Set version from the
  Gradle properties" phase; `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` are deliberately unset in
  `project.pbxproj`.
- `.run/iOS.run.xml` makes the KMP plugin write a shared `iOS.xcscheme`, which is gitignored: a shared scheme removes the
  automatic `iosApp` scheme that `publish-ios.yml` archives with, and makes `xcodebuild -scheme iosApp` fail locally —
  so commands name the target.
- `xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -sdk iphonesimulator -arch arm64 SYMROOT=<dir>
  OBJROOT=<dir> build`; `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` only checks the Kotlin side.
- Simulator builds exclude `x86_64` (`EXCLUDED_ARCHS[sdk=iphonesimulator*]`): there is no Intel simulator target, and
  an all-architectures build stops with "Unknown iOS simulator arch: 'x86_64'".
