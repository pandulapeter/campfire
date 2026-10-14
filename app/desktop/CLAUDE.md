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

Compose Desktop entry point (`CampfireDesktopApplication.kt`). Koin is started before the window opens (the graph
belongs to the process); the view model is obtained outside `Window` so resizing doesn't reset it.

## Startup order

- `main` first calls `configureSwingGlobalsForCompose`, then on Linux `setLinuxWindowClassName` (X11 class `Campfire`).
  **Nothing may start the AWT toolkit before them**: on Linux that sets the display scale, which AWT reads once
  (a HiDPI `Xft.dpi` desktop gets a 1x window otherwise). Asking `Desktop` anything starts AWT, which is why the macOS
  open-file handler is registered on macOS only.
- Escape goes to `CampfireViewModel.handleKeyEvent`; `onPreviewKeyEvent` drops a held Escape's repeats.

## Quitting

- The window's close, the Close of `DialogType.ConfirmExit`, and the macOS quit (app menu / Cmd+Q never reach
  `onCloseRequest`; caught with `Desktop.setQuitHandler`) all go through `CampfireViewModel.requestExit`, which waits for
  a pending save and asks the unsaved changes question before letting the process end.
- The macOS quit keeps the `QuitResponse` and answers it later (`performQuit` / `cancelQuit`): cancelling up front aborts
  a logout or shutdown, with macOS blaming Campfire.
- All of them end in `leave`: hide the window, then `settleSynchronizationBeforeExit` — a writing import may finish (up
  to 30 s; songs are written before setlists), then the pending automatic sync run starts and may finish (up to 15 s),
  then it is stopped. The other-instance listener is closed first.

## Window

- Opens at 800x600, minimum 480x480 dp of the app's interface (408x408 at `interfaceScale`), both capped to the screen
  (`fitSizeToScreen`), since a window below its minimum is grown past the screen edge. The JetBrains Runtime passes the
  minimum to Windows scaled; OpenJDK does not.
- macOS and Windows: content is laid out under the title bar, keeping only the window buttons (`TitleBar.kt`), via the
  JBR custom title bar (`JBR.getWindowDecorations()`, `jbr-api`; `proguard-rules.pro` keeps its interfaces by name since
  the runtime binds them by name). JBR only treats the strip as title bar where nothing listens to the mouse, and
  Compose listens everywhere, so a toolkit-wide listener marks events over the strip `forceHitTest(false)` (except in
  full screen). macOS's `apple.awt.fullWindowContent` was not used because it lets the double click through to the
  content. Linux, and a non-JBR runtime, keep the system title bar.
- The strip (28pt macOS, 32 Windows) reaches the UI as a top system bar inset (`TitleBarInsets`, providing Compose's
  internal `LocalPlatformWindowInsets`); none in full screen. macOS full screen is read from
  `com.apple.eawt.FullScreenUtilities` notifications (reflection plus `--add-exports`), not `WindowState.placement`,
  which only updates on a resize. Set in `SwingWindow`'s `init`: the JDK doesn't relayout a shown window.
- Window buttons follow the app's theme (`TitleBarAppearance`), or they are near invisible; until preferences load macOS
  follows the system (`apple.awt.application.appearance`; the JDK default is always light).
- **The JetBrains Runtime is required** for the above: `toolchainLauncher` asks for that vendor; foojay has no JBR 21, so
  it must be installed locally (`setup-java` in CI). Without it any JDK 21 builds with a warning, but every
  `createRelease…`, `packageRelease…`, and `runRelease` task fails (`isJetBrainsRuntimeMissing`).
- Area uncovered by a fast resize is painted with the theme background (`setUndrawnAreaColor`): window, the heavyweight
  render surface, and on Windows every component (Swing repaints with the look and feel's panel gray otherwise).

## Opening files

- Windows/Linux pass the file in `args`; macOS sends an `odoc` Apple event, handled by `OpenedFiles` via
  `Desktop.setOpenFileHandler`, registered after the single-instance check, before Koin, and never removed (the JDK
  queues events before the first handler and drops those finding it removed). All go through `OpenedFiles.open(paths)`.

## Single instance (`SingleInstance.kt`, `java.base` only)

- `instance.lock` via `tryLock()`; the holder listens on `127.0.0.1` (system port) and writes port + random token to the
  owner-only `instance.endpoint`. A newcomer sends token + args, gets `OK`, exits; the window comes forward (on Windows
  by toggling `isAlwaysOnTop`, since `toFront()` only flashes the taskbar). Fails open.
- The lock is retried before each hand-over attempt. Lock held and no endpoint file = holder closing: wait up to 60 s
  (must stay above the 30 + 15 + 2 s of `settleSynchronizationBeforeExit`). An endpoint that doesn't answer: give up
  after ~5 s. A holder whose listener failed still writes an (unanswerable) endpoint so newcomers take the short wait.
- An exiting instance stops listening (`stopListeningForOtherInstances`) but keeps the lock, so it never acknowledges
  files it won't open.

## Input

- **Trackpad pinch (macOS)**: reaches only a `com.apple.eawt.event` listener on a Swing component
  (`TouchpadMagnification.kt`, on the root pane) → `magnifyByTouchpad`. Reached by reflection, with
  `--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED` (and `com.apple.eawt`) on macOS hosts only. Windows turns
  a pinch into Ctrl + wheel, which is already handled.
- **Touchscreen (`TouchScreen.kt`)**: JBR on Windows/X11 turns a finger into wheel events of its own scroll types
  (`sun.awt.event.TouchEvent`: 2 down, 3 move past 10 px, 4 up) plus a click for a tap, which Compose reads as wheel
  notches. A pushed `EventQueue` converts them into `PointerType.Touch` events sent to the window's `ComposeScene`
  (by position, never delta, since a move may be split per axis), and drops the synthetic events following a tap
  (marked as touch on Windows only). Primary finger only.
  - The scene is found by reflection among `androidx.compose.` fields (names survive ProGuard there), once, and trusted
    only if its size equals the content pane's; otherwise it logs and leaves touch to Compose.
  - One queue for all windows, never popped (`EventQueue.pop` removes the topmost queue whoever pushed it).
  - **Re-check after every Compose update**: on a Mac, posting `MouseWheelEvent`s of those scroll types from an in-app
    driver exercises the whole path.

## Log (`DesktopLog`)

- `System.out`/`System.err` and uncaught exceptions are mirrored to `campfire.log` in the data directory (installed
  builds have no console and no crash reporting); rotated to `campfire.log.1` at 1 MB; given up for the session if
  unwritable. Installed after the single-instance check, so a hand-over process never rotates the owner's log.
- Starts with skiko's `Renderer info` (`skiko.hardwareInfo.enabled`) and the app's start line (time to first frame,
  render API, runtime, CDS `sharing`, GCs, heap). A Direct3D fallback after the first draw shows as a second
  `Renderer info`, which wins. It names song files: review before attaching to a bug report.

## Data directory

- `~/Library/Application Support/Campfire` (macOS), `%APPDATA%\Campfire` (Windows; the package's `LocalState` for the
  Store build, see MSIX), `$XDG_DATA_HOME/campfire` or `~/.local/share/campfire` (Linux). The user can edit it, hence
  the rescan on restore and on focus once the last one is 10 s old. Settings doesn't show the path.
- Mac App Store sandbox: the JDK takes `user.home` from the container, so nothing in the code changes;
  `network.server` covers the two loopback sockets.

## Sync authorization

The browser tab can't be closed by script, so `main` brings the window forward (`bringForward`) once the sync state
leaves `Connecting`. The rest is `data/source/remote/implementation/CLAUDE.md`'s OAuth section.

## Packaging

- Task classes (`RecordClassDataArchive`, `AddStartupWmClassToDeb`, `AddLaunchAfterInstallToMsi`, `PackageMsix`) live in
  `gradle/build-logic`'s `tasks/`. Values an action or `onlyIf` reads are copied into locals first (configuration
  cache).
- `campfire.versionName` must be `MAJOR[.MINOR][.PATCH]` (jpackage); `javaHome` is pinned to the toolchain JDK, since the
  Gradle JVM may lack `jpackage`.
- `modules(...)` = `suggestRuntimeModules` output plus `jdk.localedata` (reached via `ServiceLoader`; without it every
  language name is English) and `jdk.accessibility` (Windows screen readers). A missing module only shows in an
  installed build — rerun `suggestRuntimeModules` after adding a JVM dependency.
- After every jlink, `-Xshare:dump` restores the JDK's CDS archive (the toolchain's `java` is copied in for it, since
  `--strip-native-commands` removed the image's).
- Windows/Linux: `recordClassDataArchive` starts the release image once with `-XX:ArchiveClassesAtExit` and
  `-Dcampfire.trainingRun=true` (the app exits itself after the demo library draws; a killed JVM writes no archive),
  producing `campfire.jsa`, named in `Campfire.cfg`. It trains `createReleaseDistributable`'s image, never
  `PackageMsix`'s copy. Linux needs a display (`xvfb-run` in CI). macOS has none (the store `.pkg` only starts in
  TestFlight).
- Windows: `createReleaseDistributable` copies ANGLE's `libEGL.dll`/`libGLESv2.dll` into `$APPDIR` from `angleRuntime`
  (its `skiko` version must equal Compose's), not the runtime classpath, where ProGuard would join them into the jar.
  `main` enables ANGLE only if `libEGL.dll` is on `skiko.library.path` and no renderer was chosen;
  `SKIKO_RENDER_API=DIRECT3D` is the user's escape hatch.

### ProGuard (`proguard-rules.pro`)

- Type specialization/generalization optimizations are off: they drop a needed `checkcast` (`VerifyError` in
  `NavDisplay`).
- Keeps `SingleInstance.kt`'s write-only `heldLock`, or GC releases the lock.
- Carries Coil's keep rules (ProGuard doesn't read them from the jar).
- Output is joined into one jar (`joinOutputJars`): Windows Defender rescans every opened file after signature updates,
  per file. The merge keeps the first of duplicate resources, so check that no two dependencies ship the same
  `META-INF/services` file. `proguardReleaseJars` drops the other CPU's skiko library from the macOS jar.
- **Start a release build before shipping it**: ProGuard breakage only shows then. The publish workflows do (and fail
  if under 80% of classes came from CDS); locally `createReleaseDistributable` then run
  `build/compose/binaries/main-release/app/Campfire.app/Contents/MacOS/Campfire`, or `runRelease`.

### macOS

- Asking for a `.pkg` (`packagePkg`, `packageReleasePkg`) is what turns on Mac App Store packaging (`appStore`, sandbox
  via `app-store.entitlements` and `app-store-runtime.entitlements`, provisioning profiles).
- `device.audio-input` entitlement and `NSMicrophoneUsageDescription` (`macos/microphone.plist`) for the tuner; without
  the latter macOS kills the process at the first mic read.
- Signed when `campfire.mac.signingIdentity` is set, as `Name (TEAMID)` (the plugin adds the prefix); ad hoc otherwise.
  A hand-built `.dmg` stays ad hoc: a real signature enforces the hardened runtime, and only the store build has the
  JVM's entitlements.
- The store build's runtime must have no `bin` folder (the CDS step deletes the one it creates). Provisioning profiles
  have their quarantine xattrs cleared before copying (ITMS-91109).
- Document types are raw plist (`macos/document-types.plist`): the DSL's `fileAssociation` writes OS type `****`
  (claims every file) and has no rank. `.cho` is `Default`, the rest `Alternate`. `macos/export-compliance.plist` says
  `ITSAppUsesNonExemptEncryption` `false`, as the iOS `Info.plist` does — change both if encryption is added. The
  fragments carry no comments, since every line lands in `Info.plist`.

### Windows

- `.msi`: per user, Start menu + desktop shortcut, fixed `upgradeUuid`. **Never change per-user or the upgrade UUID**:
  either puts a second Campfire next to existing installs. Nothing publishes the `.msi` now.
  `addLaunchAfterInstallToMsi` runs `add-launch-after-install.ps1` to add the "Launch Campfire" checkbox to WiX's
  `ExitDialog`.
- File associations: only `.cho`, `.chopro`, `.chordpro` (an MSI can only claim an extension outright); the Store
  package claims the same. Never zip or `.txt`. Keep in step with `LibraryFiles.SONG_EXTENSIONS`.
- **MSIX** (`packageReleaseMsix`): writes `AppxManifest.xml` with identity from `campfire.windows.*` (Partner Center's
  Product identity, verbatim) and version `campfire.versionName` + `.0`; indexes logos alone with makepri; packs with
  makeappx (newest SDK or `campfire.windows.sdkBinDirectory`). Unsigned: Partner Center signs. English only, since
  Partner Center wants a listing for every declared language. Capabilities `runFullTrust` and `microphone`.
- MSIX writes to AppData are virtualized (invisible in Explorer) and the Store refuses `unvirtualizedResources`, so the
  task adds `-Dcampfire.packageFamilyName=<name>_<publisher id>` to `Campfire.cfg`, and `desktopDataDirectory()` (in both
  `:presentation` and `:data:source:local:implementation`) then uses `%LOCALAPPDATA%\Packages\<family>\LocalState`.
- Local test: Developer Mode, then `Add-AppxPackage -Register
  app/desktop/build/tmp/packageReleaseMsix/package/AppxManifest.xml`.

### Linux

- The `.deb` asks for a `shortcut` (jpackage writes a `.desktop` entry only then). `addStartupWmClassToDeb` adds
  `StartupWMClass`, which **must equal** `setLinuxWindowClassName`'s value (the Linux-only `--add-opens` is for that):
  launchers match windows by `WM_CLASS`.
- No file associations (they are keyed by MIME type, which would claim every text file).

### Icons

- `src/main/resources/appIcon.{icns,ico}` and `src/main/composeResources/drawable/app_icon.png` are generated by
  `app/generate_theme_icons.py` from `app/icons` — edit the sources, never these.
- Only on Linux does the running window icon follow the theme color (`AppIcon.kt`). Windows' Store taskbar uses the
  package logos regardless, and a Dock image set via `Taskbar` can't be drawn in macOS's tinted/dark icon styles, so
  neither is set.

## Tasks

`run` (`--args="/path/to/song.cho"` to test opening a file); `packageDistributionForCurrentOS` or `packageDeb` /
`packageDmg` / `packageMsi`; `packageRelease…` variants run ProGuard into `build/compose/binaries/main-release`. CI:
`packageReleaseDeb`, `packageReleaseMsix`, `packageReleasePkg`.
