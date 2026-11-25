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
extension (`iosApp/ShareExtension/`) and the widget extension (`iosApp/CampfireWidgets/`), both Swift only.

## `CampfireViewController.kt`

- **The Command-hold overlay**: `ContentView.swift`'s `ShortcutListingController` hosts the Compose controller as a
  child and answers `keyCommands` (a category property Kotlin cannot override) with `listedKeyCommands`. They are only
  listed: nothing implements their action and `canPerformAction` says so, so UIKit delivers the press to Compose,
  which handles every key itself. It forwards the status bar, home indicator, and edge gestures to the child.
- An address `NSURL` refuses as written (non-ASCII, `|`, which iOS 15–16 don't encode) is percent-encoded first.
- The window's `overrideUserInterfaceStyle` follows the theme (unspecified for System): the status bar, pickers, share
  sheet, and keyboard follow UIKit's style, not Compose's theme.
- **App icon**: iOS shows an unsuppressible alert per `setAlternateIconName` and refuses it while the app is not
  active, so `CampfireIosApp` asks only after the color has been stable for a second while resumed, and again at every
  resume (no-op if matching; applies a restored backup's preference).

## Files in and out

- `IosFilePicker.kt`: a presentation UIKit refuses (something already presented, a dismissing sheet included) counts as
  nothing picked/shared, not done. Picked files are copies, deleted after reading. An archive or PDF is handed over as
  its own file in `Caches/exports` (a mail attaches it mapped); bytes are written to the temporary directory first.
  Either is deleted by the picker (the share in the activity controller's completion handler); **the temporary
  directory is never swept**, since a share target may still be reading.
- `IosFileOpener.kt`: a song's attachment in Quick Look (`QLPreviewController`), presented over the topmost presented
  controller, titled with the attachment's name; the data source is held by the opener, since the controller's
  reference is weak. Its copy in `Caches/exports` is left to the launch's emptying.
- `IosPhotoPicker.kt`: PHPicker, out of process, so no photo library permission or usage description. Asked for the
  compatible representation (HEIC arrives as JPEG where Photos can make one), the shown types read first; each photo's
  temporary file is read inside its completion handler (deleted after), one photo at a time, in the order picked. A
  photo that fails to load (iCloud only, offline) is left out and logged. Photos does not dismiss itself; a swipe is
  the presentation controller's.
- `IosFileImport.kt`'s `openUrl(url)`: a sync redirect never arrives here (`ASWebAuthenticationSession` answers it) but
  is ignored anyway, so it isn't reported as a failed import. URLs are read in order, one at a time, off the main
  thread; an unreadable file is passed on empty (reported as skipped).
- **Shared links** reach `openUrl` too: the Universal Link (`https://campfire-songbook.com/s/…`, which a SwiftUI app
  gets in `.onOpenURL`; never add `.onContinueUserActivity` beside it, or a link imports twice) and `campfire://s/…`.
  Which address is one is `ShareLinks.isLinkAddress`, on the text, since `NSURL.path` gives `/s/` as `/s`. One
  `ShareLinks.parse` doesn't read only brings the app forward, and no other non-file URL is read (NSData would download
  it). `iosApp.entitlements` names `applinks:campfire-songbook.com`, so the App ID needs the Associated Domains
  capability before a signed build.
- **Quick actions** (`IosQuickActions.kt`): `shortcutItems` set from the composition's list (`ui/shortcuts`), each
  item's `userInfo` holding its app link. `QuickActions.swift`'s scene delegate (from a `UIApplicationDelegateAdaptor`)
  takes one from the connection options at launch and `performActionFor` while running, and hands it to `openUrl`, which
  queues any other `campfire://` link as an app link.
- Files from AirDrop, Mail, or Messages are copies in `Documents/Inbox`, deleted after reading — only there, since a
  file opened in place is the user's original. Originals are read through an `NSFileCoordinator`, which downloads an
  iCloud file and waits for a writer.
- `cleanImportInbox()` runs when the scene goes to the background, **not at start**: a cold start's file is already in
  the inbox before it is handed over, so it couldn't be told from a leftover. It passes over an inbox copy still being
  received (below).
- **Archives are staged** (`ArchiveReceiving.ios.kt` in `:presentation`), the graph
  started first (`startProcess`, also from `openUrl` and `drainShareInbox`, which a cold start may call before the view
  controller is made):
  `openUrl` queues an archive by its name at once, its copy in its batch's job rather than on the inbox's queue (an
  iCloud file not downloaded yet would hold up every later open); anything else is read there as before, its head
  deciding (a renamed backup is received too). A copy the app owns (`Documents/Inbox`, the picker's `asCopy` file, the
  share inbox's file, a drop's file renamed out of its provider's hands in the handler) is moved into the staging
  folder, never deleted by its reader; one of the user's own is copied with `fread` a MiB at a time inside the
  coordinated read and the security-scoped access. A moved archive leaves the share's `share.json` at once
  (`ShareInboxManifest.withoutFile`; the folder goes when nothing is left), so a share offered again after the process
  ended names only what it still holds: a kill loses that preparation, never reports a moved archive as unreadable.

## Share extension

- Share → Campfire for selected text, a web page, and the files the import reads (`NSExtensionActivationRule`;
  `ShareLimits` lists the same types; OnSong's `.onsong` has no system type to name, so it arrives only through the
  app's file picker or a drop). It links no Kotlin: it copies what it was given into `ShareInbox/` in the App Group
  container (`APP_GROUP_ID` in `Config.xcconfig`, both targets' entitlements, `CampfireAppGroup` in both
  `Info.plist`s), says so, and is done.
- **It never opens the app**: only a Today widget may, and the responder-chain way round is not API and is refused by
  review. The app drains the inbox on every `.active` scene phase and on the Darwin notification
  `com.pandulapeter.campfire.share-inbox`, for an iPad's Split View where it stays active.
- **A notification hands over instead** (`ShareNotification.swift`): where Campfire's notifications are already allowed
  (authorized or provisional; the app asks only when a sync run starts, the extension never asks), it posts one naming
  what waits (a title, a file's name, or "N files"), identified by the share's folder, threaded `share-inbox`, before
  the Darwin notification and awaited for at most a second before `completeRequest`. Opening it brings the app to the
  front, which drains the inbox, so it routes nothing. The `.active` phase (and the Darwin notification, only while the
  app is active) takes down every delivered one by its thread; the Darwin notification also reaches an app left a
  moment ago and not yet suspended, where it stays the way back. The sheet's second line says whether one was posted.
- One folder per share, written as `<time>-<UUID>.partial` and renamed when complete; the app ignores `.partial` ones
  and deletes them after an hour. Its `share.json` is decoded by `:presentation` (`ui/platform/CLAUDE.md`). A share is
  one batch (one `ImportBudget`), and its folder is deleted only once the app has taken it (its import settled, or a
  text's editor open with its draft on disk), so a process ended before that offers it again.
- `ShareInboxWriter.fileName` cannot call `LibraryFiles.normalizedName`, so the copy it writes is only lowercase,
  underscored, free of separators, control, invisible, and bidirectional characters and leading dots, and at most 200
  bytes.
- Files beat text in one share. A Campfire share link (by `isCampfireLink`, a hand copy of `ShareLinks`' regex), as a
  text or a URL attachment beside one-line texts, is written as the share's one text. A page shared from Safari arrives
  as `PageText.js`'s property list (`NSExtensionJavaScriptPreprocessingFile`: `innerText` with `content-visibility`
  forced visible for the read and raised inline chords set as chord rows, the steps of `PageSnapshotScript.kt`; title,
  address) and is written as the share's one text, its title the subject, nothing
  loaded again (a Campfire page's address excepted, which is its link). Any other page's address (a URL attachment that is no file's, alone or beside one-line texts) is
  written as a text of that address, which the app reads itself (`presentation/CLAUDE.md`): a web view does not fit in
  the extension's memory, and the extension never opens the app.
- The extension mirrors `ImportLimits` before copying: a zip archive (by its extension) up to
  `MAX_STAGED_ARCHIVE_SIZE` (16 GiB), copied only where the volume's free space holds it beside `minFreeSpaceFor`'s
  margin, else `share_no_space` (the disk space API, E174.1), and counted against neither a share's 40 MB nor the
  inbox's bound; everything else within its own limit and 40 MB a share, the inbox bounded at 20 shares and 64 MB.
  `share_too_large_detail` names these limits in every language. It copies rather than loads (about 120 MB of memory),
  and reads sizes as URL resource values (no file timestamp API).
- `ShareInbox/` is excluded from device backup (by the extension, and again by the app). Its strings are its own
  `Localizable.strings` (and `Localizable.stringsdict` for the counted line), in the system's language.
- An extension cannot size or replace the sharing app's sheet, so a saved share closes it by itself after two seconds;
  a refusal waits for Done.

## Widget extension

- `CampfireWidgets` (iOS 17, the first with widget buttons and `AppIntentConfiguration`, so one code path; the app
  stays on 15.3): Random picks and Setlist in one `WidgetBundle`, what they show being `ui/widget/CLAUDE.md`'s. It links
  no Kotlin and reads only `Widget/snapshot.json` in the App Group's container (`WidgetSnapshot.swift`, the same keys)
  and the cover thumbnails it names in `Widget/covers/` (`WidgetCover`); the next gig and relative day rules are written
  again in `SetlistWidget.swift`.
- `IosWidgetFiles.kt` writes them atomically on one IO thread, in order, skipping a snapshot equal to the file (every
  launch hands over one; WidgetKit budgets reloads), the `Widget/` folder excluded from backup. **The reload seam**:
  WidgetKit has no Objective-C API, so `CampfireViewController` takes `reloadWidgetTimelines`, which `ContentView.swift`
  passes as `WidgetCenter.shared.reloadAllTimelines()`, called after each write; likewise `readPlacedWidgetKinds`
  (`getCurrentConfigurations`), which tells `IosWidgetPlacement` whether covers are wanted at all.
- Random picks: 25 hourly entries, each drawn by the seed in the group's `UserDefaults` mixed with the hour (SplitMix64's
  finalizer, never added: an added hour slides the same draw along), ranking songs as `pickWidgetSongs` does, so a
  timeline rebuilt within the hour draws the same; Shuffle (`ShuffleIntent`) stores a new seed, shared by
  every such widget, and shows only where the filter leaves more songs than the widget draws. Edit Widget
  (`RandomPicksConfiguration.swift`) gives it a title of its own, the tags and languages it draws from (`TagEntity`,
  `LanguageEntity`, listed from the snapshot), filtered as `WidgetContent.kt`'s `RandomPicksFilter` does, and a number
  of picks (1–10, `RANDOM_PICKS_COUNTS`); a widget from before has no filter and draws `DEFAULT_RANDOM_PICKS_COUNT`.
  Small (no Shuffle: its title needs the width), medium, and large: each family has a row capacity
  (`songRowCapacity`: 3, or 8 on large), a widget showing the first ones of its draw that fit, in slots of that
  capacity's height, so rows are packed alike in every size.
  Both list songs in `WidgetSongRow` (`WidgetStyle.swift`, a 28 pt cover where the song has one), edge to edge: the widgets turn the
  content margins off (`contentMarginsDisabled`) and each part puts back the `widgetContentMargins` it needs; a
  setlist's numbers start at the header's edge, in a column as wide as the list's longest number. Setlist
  (medium and large like Random picks, small and the Lock Screen's its title and day only): an entry now and at each
  of the week's midnights; the choice is a `SetlistEntity`
  (`next-gig`, else a setlist's file name) listed from the snapshot, a missing one kept until changed.
- What a widget shows comes from the snapshot, in the app's language and direction. The gallery's names and
  descriptions, Edit Widget's words, and the line shown before the app has written a snapshot are the extension's
  `Localizable.strings`, keyed by their English text, in every app language. Inter through `UIAppFonts`, referencing
  `:presentation`'s font files, by PostScript name (`Inter Medium` is a family of its own); the snapshot's theme
  as `WidgetColors` in the environment (its palette and fixed half), else the app's purple as `AccentColor` and the
  system's widget background.
- Signed like the share extension: App ID `<bundle id>.widgets` with the App Group, its own App Store profile
  (`.github/CLAUDE.md`).

## `Info.plist`

- `CFBundleURLTypes` declares `campfire`: `ASWebAuthenticationSession` doesn't need it, but it is Dropbox's registered
  redirect URI.
- `ITSAppUsesNonExemptEncryption` `false` (system HTTPS, SHA-256, and Keychain are exempt); adding encryption changes
  it, and the Mac `macos/export-compliance.plist` too.
- `UIFileSharingEnabled` + `LSSupportsOpeningDocumentsInPlace` put the library (in Documents; the preferences are not)
  under "On My iPhone → Campfire"; opening in place commits to coordinated reads.
- Document types: `.cho` `Default`, the rest `Alternate`. No zip, plain text, PDF, or Word, deliberately: the share
  extension is where those are accepted. Keep in step with `LibraryFiles.SONG_EXTENSIONS`. SongbookPro's `.sbpbackup` /
  `.sbp` and OpenSongApp's `.osb` / `.osbs` / `.ossb` (zip archives) and `.osts` (plain text) are imported types with no
  document type, only so the extension's activation rule can name them. JustChords' `.justchords` (conforming to JSON)
  is an imported type with a `Viewer` document type at `Alternate`, so Open with offers Campfire too. `.abc` is
  a `Viewer` at `Alternate` (Campfire is no editor of it), conforming to plain text, which brings it to the extension.
  PNG, JPEG, GIF, WebP, HEIC, and HEIF are a `Viewer` at `Alternate` too, named by the extension's rule: a photo
  alone is a new song.
- `CFBundleLocalizations` tells UIKit which languages the app speaks (Compose resources are invisible to iOS); without
  it system UI stays English and Settings offers no per-app language. Keep in step with `:presentation`'s `values-*`.
  Each language also needs `<lang>.lproj/InfoPlist.strings` (the microphone prompt; a variant group).
- `UIBackgroundModes` `audio` is for the metronome; the session is active only while a click plays (what App Review
  checks). The tuner adds only `NSMicrophoneUsageDescription`, never listening out of sight.
- `LaunchScreen.storyboard` is the catalog's `LaunchBackground` color, matching the first frame.

## `PrivacyInfo.xcprivacy`

No tracking or collected data; required-reason APIs: file timestamps (C617.1 for the library and skia/ICU `stat`;
3B52.1 for picked files), boot time (`mach_absolute_time`, 35F9.1), and disk space (the import staging's free space,
E174.1). The framework is static, so Compose's, skiko's,
and the Kotlin runtime's calls are the app's to declare. After updating any of them, run `nm -u` on a Release
`Campfire` executable to spot a new category (NSUserDefaults is the likely one).

## Sync, imports, and the metronome in the background

- `IosSyncNotifier.kt`, one per process, follows `GetSyncStateUseCase` itself (Compose stops collecting once the scene
  leaves the foreground): it holds a `beginBackgroundTask` for the run, ended 2 s after it, kept by a chained run. A
  background task buys tens of seconds: on expiry the run is stopped and reports itself interrupted; a killed process
  is found by the index's "a run was going" marker.
- A local notification is posted only while in the background (iOS silences foreground ones), at most once a second,
  taken down on return. Permission is asked when a run starts, in front.
- `IosImportKeeper.kt` follows `ImportWork` likewise. iOS 26+: a continued processing task (`ContinuedImportTasks.swift`,
  behind `#available` in `ContentView.swift`) submitted on resign active while the import works, ended on return or when
  idle; a fresh `<bundle>.import.<UUID>` registered just before each submit (the plist's one `.import.*` entry; one
  registered twice, or submitted unregistered, ends the app); its expiry or the user's stop calls `ImportWork.interrupt`.
  A refused submit (always, on the simulator) and iOS before 26 hold a `beginBackgroundTask` instead, whose expiry only
  ends it, synchronously: the app is suspended with the import paused, and it goes on back in front (decisions:
  `ImportKeepAlive`, tested).
- `IosMetronomeNotifier.kt` only draws Now Playing and answers remote commands (pause, stop, and toggle all stop: no
  paused state); the audio session, background audio, and interruptions belong to `:libs:metronome:implementation`.

## Xcode project and building

- Team id, bundle id, and app name are in `iosApp/Configuration/Config.xcconfig`; **change the team there, not in
  Xcode's Signing tab**, which writes a literal over `$(TEAM_ID)`.
- Version and build number come from `campfire.versionName` / `campfire.buildNumber` via the last build phase of every
  target (`iosApp/Configuration/set-version.sh`; App Store Connect refuses an extension whose version differs from its
  app's); MARKETING_VERSION and CURRENT_PROJECT_VERSION are deliberately unset in `project.pbxproj`. The "Compile
  Kotlin Framework" phase is the app target's alone.
- `.run/iOS.run.xml` makes the KMP plugin write a shared iOS.xcscheme, which is gitignored: a shared scheme removes the
  automatic `iosApp` scheme that `publish-ios.yml` archives with, and makes `xcodebuild -scheme iosApp` fail locally —
  so commands name the target: `xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -sdk
  iphonesimulator -arch arm64 SYMROOT=<dir> OBJROOT=<dir> build`.
- Simulator builds exclude `x86_64`: there is no Intel simulator target, and an all-architectures build stops with
  "Unknown iOS simulator arch: 'x86_64'".
