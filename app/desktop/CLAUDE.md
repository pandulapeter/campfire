<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :app:desktop

Compose Desktop entry point (`CampfireDesktopApplication.kt`, `main(args)`). Starts Koin through `:app:di`'s `startCampfireDependencyGraph` before the window opens (the graph belongs to the process, so there is no `KoinApplication` composable around the content), then hosts `CampfireDesktopApp` in a `Window` whose `onKeyEvent` is wired to `CampfireViewModel.handleKeyEvent` (Escape dismisses the visible modal, pops the back stack, or exits the application on the root screen). The modules are named in `:app:di`, not here.

Closing the window is a back-navigation as far as unsaved text is concerned: `onCloseRequest`, the Escape that would exit, and the macOS quit (the application menu and Cmd+Q, which never reach `onCloseRequest` and are caught with `Desktop.setQuitHandler` instead) all go through `CampfireViewModel.requestExit`, which asks the editor's unsaved changes question first and waits for a save still being written before `exitApplication` ends the process.

The view model is obtained outside `Window` so window resizing doesn't reset it. Window min size is 400x400.

`main` takes `args` because that is how "open with" reaches a desktop application: the system launches the app with the file as an argument, and every argument is read and imported at startup. Files dropped onto the window take the same path (`Modifier.dragAndDropTarget` in `CampfireDesktopApp`).

Packaging: `compose.desktop` produces Dmg/Exe/Msi/Deb, versioned from the `campfire.versionName` Gradle property. That format is stricter than the app's — `MAJOR[.MINOR][.PATCH]` and nothing else — so a version with a suffix fails the build at configuration time. `javaHome` is pinned to the toolchain JDK because the Gradle JVM may lack `jpackage`. Icons live in `src/main/resources/appIcon.{icns,ico}` (packaging) and `src/main/composeResources/drawable/app_icon.png` (the window icon, also used for the Linux package). The Linux package asks for a `shortcut`, because jpackage writes a `.desktop` entry — and so shows the icon anywhere — only for a package that asks for one or declares file associations, and the Windows installer asks for a Start menu entry for the same reason, installs per user so that it needs no administrator, and carries a fixed `upgradeUuid`, which is what makes a newer installer replace the installed version and so must never change. `modules(...)` lists what `suggestRuntimeModules` finds beyond Compose Desktop's defaults: the packaged runtime holds nothing else, so a missing module is a `NoClassDefFoundError` only an installed build throws — run that task again after adding a JVM dependency. `chordProFileAssociations()` in `build.gradle.kts` registers the six ChordPro extensions as `text/plain` for macOS and Windows — not for Linux, where an association is keyed by the MIME type and that would make Campfire a handler of every text file; deliberately not zip or `.txt`, and kept in step with `LibraryFiles.SONG_EXTENSIONS`, which a build file cannot see.

`proguard-rules.pro` is added to the release build on top of the rules Compose Desktop ships. It turns off ProGuard's type specialization and generalization optimizations, which rewrite a generic function's erased parameter or return type to the one subclass every call site passes without inserting the `checkcast` the verifier then wants — the release build died on its first frame with a `VerifyError` in `NavDisplay` because of it. Compose's own rules already work around the same bug for `**Kt__*` classes; the rules file has the detail. **A release build has to be started once before it is shipped**: this class of breakage exists only after ProGuard runs, so `run` and `assembleDebug` say nothing about it — `./gradlew :app:desktop:createReleaseDistributable` then `app/desktop/build/compose/binaries/main-release/app/Campfire.app/Contents/MacOS/Campfire` (or `:app:desktop:runRelease`), which prints what the window cannot.

The library lives where the platform keeps application data (`~/Library/Application Support/Campfire` on macOS, `%APPDATA%` on Windows, `~/.local/share` elsewhere), which is a folder the user can open — hence the "Location" row in Settings and the rescan when the window regains focus.

For sync, the desktop briefly becomes a web server: `DesktopSyncAuthenticator` opens a socket on `127.0.0.1:53682` for the length of one authorization, because a service only redirects to a URI registered with it character for character and a port chosen by the operating system could not be registered.

`./gradlew :app:desktop:run` to launch (`--args="/path/to/song.cho"` to test opening a file); `:app:desktop:packageDistributionForCurrentOS` to build an installer (`packageDeb`, `packageDmg` and `packageMsi` for one format, which is what `desktop-publish.yml` runs).
