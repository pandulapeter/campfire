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

The static `ComposeApp` framework for the Xcode project in `iosApp/` (a SwiftUI wrapper), which also builds the share
extension (`iosApp/ShareExtension/`, Swift only).

## `CampfireViewController.kt`

- An address `NSURL` refuses as written (non-ASCII, `|`, which iOS 15–16 don't encode) is percent-encoded first.
- The window's `overrideUserInterfaceStyle` follows the theme (unspecified for System): the status bar, pickers, share
  sheet, and keyboard follow UIKit's style, not Compose's theme.
- **App icon**: iOS shows an unsuppressible alert per `setAlternateIconName` and refuses it while the app is not
  active, so `CampfireIosApp` asks only after the color has been stable for a second while resumed, and again at every
  resume (no-op if matching; applies a restored backup's preference).

## Files in and out

- `IosFilePicker.kt`: a presentation UIKit refuses (something already presented, a dismissing sheet included) counts as
  nothing picked/shared, not done. Picked files are copies, deleted after reading. Export/share temp files are deleted
  by their maker (the share in the activity controller's completion handler); **the temporary directory is never
  swept**, since a share target may still be reading.
- `IosFileImport.kt`'s `openUrl(url)`: a sync redirect never arrives here (`ASWebAuthenticationSession` answers it) but
  is ignored anyway, so it isn't reported as a failed import. URLs are read in order, one at a time, off the main
  thread; an unreadable file is passed on empty (reported as skipped).
- **Shared links** reach `openUrl` too: the Universal Link (`https://campfire-songbook.com/s/…`, which a SwiftUI app
  gets in `.onOpenURL`; never add `.onContinueUserActivity` beside it, or a link imports twice) and `campfire://s/…`.
  Which address is one is `ShareLinks.isLinkAddress`, on the text, since `NSURL.path` gives `/s/` as `/s`. One
  `ShareLinks.parse` doesn't read only brings the app forward, and no other non-file URL is read (NSData would download
  it). `iosApp.entitlements` names `applinks:campfire-songbook.com`, so the App ID needs the Associated Domains
  capability before a signed build.
- Files from AirDrop, Mail, or Messages are copies in `Documents/Inbox`, deleted after reading — only there, since a
  file opened in place is the user's original. Originals are read through an `NSFileCoordinator`, which downloads an
  iCloud file and waits for a writer.
- `cleanImportInbox()` runs when the scene goes to the background, **not at start**: a cold start's file is already in
  the inbox before it is handed over, so it couldn't be told from a leftover.

## Share extension

- Share → Campfire for selected text and the files the import reads (`NSExtensionActivationRule`; `ShareLimits` lists
  the same types; OnSong's `.onsong` and OpenSongApp's `.osb` have no system type to name, so they arrive only through
  the app's file picker or a drop). It links no Kotlin: it copies what it was given into `ShareInbox/` in the App
  Group container (`APP_GROUP_ID` in `Config.xcconfig`, both targets' entitlements, `CampfireAppGroup` in both
  `Info.plist`s), says so, and is done.
- **It never opens the app**: only a Today widget may, and the responder-chain way round is not API and is refused by
  review. The app drains the inbox on every `.active` scene phase and on the Darwin notification
  `com.pandulapeter.campfire.share-inbox`, for an iPad's Split View where it stays active.
- One folder per share, written as `<time>-<UUID>.partial` and renamed when complete; the app ignores `.partial` ones
  and deletes them after an hour. Its `share.json` is decoded by `:presentation` (`ui/platform/CLAUDE.md`). A share is
  one batch (one `ImportBudget`), and its folder is deleted only once the app has taken it (its import settled, or a
  text's editor open with its draft on disk), so a process ended before that offers it again.
- `ShareInboxWriter.fileName` cannot call `LibraryFiles.normalizedName`, so the copy it writes is only lowercase,
  underscored, free of separators, control, invisible, and bidirectional characters and leading dots, and at most 200
  bytes.
- Files beat text in one share. A Campfire share link (by `isCampfireLink`, a hand copy of `ShareLinks`' regex), as a
  text or a URL attachment beside one-line texts, is written as the share's one text; any other page's address is
  refused with a sentence. The extension mirrors `ImportLimits` before copying and bounds the inbox at 20 shares and
  64 MB. It copies rather than loads (about 120 MB of memory), and reads sizes as URL resource values, so its own
  `PrivacyInfo.xcprivacy` declares no required-reason API.
- `ShareInbox/` is excluded from device backup (by the extension, and again by the app). Its strings are its own
  `Localizable.strings`, in the system's language.
- An extension cannot size or replace the sharing app's sheet, so a saved share closes it by itself after two seconds;
  a refusal waits for Done.

## `Info.plist`

- `CFBundleURLTypes` declares `campfire`: `ASWebAuthenticationSession` doesn't need it, but it is Dropbox's registered
  redirect URI.
- `ITSAppUsesNonExemptEncryption` `false` (system HTTPS, SHA-256, and Keychain are exempt); adding encryption changes
  it, and the Mac `macos/export-compliance.plist` too.
- `UIFileSharingEnabled` + `LSSupportsOpeningDocumentsInPlace` put the library (in Documents; the preferences are not)
  under "On My iPhone → Campfire"; opening in place commits to coordinated reads.
- Document types: `.cho` `Default`, the rest `Alternate`. No zip, plain text, PDF, or Word, deliberately: the share
  extension is where those are accepted. Keep in step with `LibraryFiles.SONG_EXTENSIONS`. SongbookPro's `.sbpbackup` /
  `.sbp` are an imported type with no document type, only so the extension's activation rule can name them. `.abc` is
  a `Viewer` at `Alternate` (Campfire is no editor of it), conforming to plain text, which brings it to the extension.
- `CFBundleLocalizations` tells UIKit which languages the app speaks (Compose resources are invisible to iOS); without
  it system UI stays English and Settings offers no per-app language. Keep in step with `:presentation`'s `values-*`.
  Each language also needs `<lang>.lproj/InfoPlist.strings` (the microphone prompt; a variant group).
- `UIBackgroundModes` `audio` is for the metronome; the session is active only while a click plays (what App Review
  checks). The tuner adds only `NSMicrophoneUsageDescription`, never listening out of sight.
- `LaunchScreen.storyboard` is the catalog's `LaunchBackground` color, matching the first frame.

## `PrivacyInfo.xcprivacy`

No tracking or collected data; required-reason APIs: file timestamps (C617.1 for the library and skia/ICU `stat`;
3B52.1 for picked files) and boot time (`mach_absolute_time`, 35F9.1). The framework is static, so Compose's, skiko's,
and the Kotlin runtime's calls are the app's to declare. After updating any of them, run `nm -u` on a Release
`Campfire` executable to spot a new category (NSUserDefaults is the likely one).

## Sync and metronome in the background

- `IosSyncNotifier.kt`, one per process, follows `GetSyncStateUseCase` itself (Compose stops collecting once the scene
  leaves the foreground): it holds a `beginBackgroundTask` for the run, ended 2 s after it, kept by a chained run. A
  background task buys tens of seconds: on expiry the run is stopped and reports itself interrupted; a killed process
  is found by the index's "a run was going" marker.
- A local notification is posted only while in the background (iOS silences foreground ones), at most once a second,
  taken down on return. Permission is asked when a run starts, in front.
- `IosMetronomeNotifier.kt` only draws Now Playing and answers remote commands (pause, stop, and toggle all stop: no
  paused state); the audio session, background audio, and interruptions belong to `:libs:metronome:implementation`.

## Xcode project and building

- Team id, bundle id, and app name are in `iosApp/Configuration/Config.xcconfig`; **change the team there, not in
  Xcode's Signing tab**, which writes a literal over `$(TEAM_ID)`.
- Version and build number come from `campfire.versionName` / `campfire.buildNumber` via the last build phase of both
  targets (`iosApp/Configuration/set-version.sh`; App Store Connect refuses an extension whose version differs from its
  app's); MARKETING_VERSION and CURRENT_PROJECT_VERSION are deliberately unset in `project.pbxproj`. The "Compile
  Kotlin Framework" phase is the app target's alone.
- `.run/iOS.run.xml` makes the KMP plugin write a shared iOS.xcscheme, which is gitignored: a shared scheme removes the
  automatic `iosApp` scheme that `publish-ios.yml` archives with, and makes `xcodebuild -scheme iosApp` fail locally —
  so commands name the target: `xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -sdk
  iphonesimulator -arch arm64 SYMROOT=<dir> OBJROOT=<dir> build`.
- Simulator builds exclude `x86_64`: there is no Intel simulator target, and an all-architectures build stops with
  "Unknown iOS simulator arch: 'x86_64'".
