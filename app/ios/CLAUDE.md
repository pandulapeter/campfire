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

iOS entry point. A Kotlin/Native module that produces the static `ComposeApp` framework consumed by the Xcode project in `iosApp/`.

- `CampfireViewController()` (in `src/iosMain`) starts Koin once, through `:app:di`'s `startCampfireDependencyGraph`, and returns a `ComposeUIViewController` hosting `CampfireIosApp`. Links open through `UIApplication.openURL`. The modules are named in `:app:di`, not here.
- `IosFilePicker.kt` — the `FilePicker` actual: `UIDocumentPickerViewController` for importing, `UIActivityViewController` for saving and sharing.
- `IosFileImport.kt` — a top-level `openUrl(url)` that Swift calls from `.onOpenURL`, which in practice means a file opened with Campfire. A sync redirect is answered by the `ASWebAuthenticationSession` itself and never arrives here, but one is recognised and ignored anyway: read as a song it would fail, and the user would be told a file could not be imported that they never tried to import. The URL is read on a background dispatcher, one at a time and in the order it arrived, so a large file does not hold up the launch it caused; a file that cannot be read is passed on empty, so the import reports it as skipped instead of the app coming to the front and saying nothing. A file that came from AirDrop, Mail or Messages is a copy iOS leaves in `Documents/Inbox` — visible in the Files app because of `UIFileSharingEnabled` — and it is deleted once it has been read, and only there, since a file opened in place is the user's original (possibly one in the library itself). `cleanImportInbox()`, called from Swift when the scene goes to the background, removes the rest: the copies that could not be read or that the app was killed before reading. It is not done at start, because the file a cold start was caused by is already in the inbox by then and is only handed over afterwards, so there is no telling it from a leftover.
- `iosApp/iosApp.xcodeproj` — SwiftUI wrapper (`ContentView.swift` embeds the view controller with `.ignoresSafeArea()`, the Compose scaffold applies insets itself; `iOSApp.swift` forwards `.onOpenURL` to `openUrl` and observes `scenePhase` to call `cleanImportInbox()` when the app goes to the background). A "Compile Kotlin Framework" build phase runs `./gradlew :app:ios:embedAndSignAppleFrameworkForXcode` from the repo root. Team id, bundle id and app name live in `iosApp/Configuration/Config.xcconfig`; version and build number come from `gradle.properties` (`campfire.versionName`, `campfire.ios.buildNumber`), written into the built `Info.plist` by the "Set version from gradle.properties" build phase — `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` are deliberately not set in `project.pbxproj`.

`Info.plist` also declares the `campfire` URL scheme under `CFBundleURLTypes`. `ASWebAuthenticationSession` intercepts its own callback scheme and does not need the declaration, but the scheme is what Dropbox has registered as the redirect URI, so it stays declared.

`Info.plist` carries the document integration: `UIFileSharingEnabled` and `LSSupportsOpeningDocumentsInPlace` put the library under "On My iPhone → Campfire" in the Files app (the library is in the documents directory; the preferences are not, so they stay out of the user's way). `UTImportedTypeDeclarations` declares the ChordPro types and `CFBundleDocumentTypes` claims them — `.cho` with `LSHandlerRank` `Default`, the rest of the family `Alternate`, since those extensions are shared with other kinds of file. Zip and plain text are deliberately absent. Keep the list in step with `LibraryFiles.SONG_EXTENSIONS`.

`IosSyncNotifier.kt` holds a `beginBackgroundTask` and posts a local notification while a sync run lasts, handed to `CampfireIosApp` as its `SyncNotifier`. iOS is stricter than Android here: a background task buys tens of seconds, not minutes, so a large library is suspended mid run — which is the case the index's "a run was going" marker exists for, and the next start reports it as interrupted and carries on. The notification is informational (iOS has no progress bar in one, and no button without a registered category), so stopping a run is done in the app.

Because the Files app can change the library behind the app's back, `CampfireApp` rescans on resume here (see `libraryLocation` in `:presentation`).

Build and run from Xcode (or `xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -sdk iphonesimulator -arch arm64 SYMROOT=<dir> OBJROOT=<dir> build`); `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` only checks that the Kotlin side compiles and links.
