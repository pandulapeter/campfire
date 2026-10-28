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

## Quitting

- The window's close, the Close of `DialogType.ConfirmExit`, and the macOS quit (app menu / Cmd+Q never reach
  `onCloseRequest`; caught with `Desktop.setQuitHandler`) all go through `CampfireViewModel.requestExit`, which waits for
  a pending save and asks the unsaved changes question before letting the process end.
- The macOS quit keeps the `QuitResponse` and answers it later (`performQuit` / `cancelQuit`): cancelling up front aborts
  a logout or shutdown.
- All of them end in `leave`: hide the window, then `settleSynchronizationBeforeExit` — a writing import may finish (up
  to 30 s; songs are written before setlists), then the pending automatic sync run starts and may finish (up to 15 s),
  then it is stopped. The other-instance listener is closed first.

## Window

- Opens at 800x600, minimum 480x480 dp of the interface, both capped to the screen (`fitSizeToScreen`), since a window
  below its minimum is grown past the screen edge.
- macOS and Windows: content is laid out under the title bar, keeping only the window buttons (`TitleBar.kt`), via the
  JBR custom title bar (`jbr-api`, interfaces kept by name). JBR treats the strip as title bar only where nothing
  listens to the mouse, so a toolkit-wide listener marks events over it `forceHitTest(false)` (not in full screen).
  Not `apple.awt.fullWindowContent`: it lets the double click through. Linux and a non-JBR runtime keep the system title
  bar.
- The strip reaches the UI as a top system bar inset (`TitleBarInsets`); none in full screen, which macOS reports
  through `com.apple.eawt.FullScreenUtilities` (`WindowState.placement` only updates on a resize). Set in
  `SwingWindow`'s `init`: the JDK doesn't relayout a shown window.
- Window buttons follow the app's theme (`TitleBarAppearance`), or they are near invisible.
- **The JetBrains Runtime is required** for the above and must be installed locally (foojay has no JBR 21): without
  it a JDK 21 builds with a warning, but every release task fails.
- Area uncovered by a fast resize is painted with the theme background (`setUndrawnAreaColor`), on Windows on every
  component (Swing repaints with its panel gray otherwise).

## Opening files

- Windows/Linux pass the file in `args`; macOS sends an Apple event to `Desktop.setOpenFileHandler`, registered after
  the single-instance check, before Koin, and never removed (the JDK drops events finding it removed).
- `campfire://s/` links arrive the same ways (macOS: `Desktop.setOpenURIHandler`, beside the file handler).
  `openLaunchArguments` sends arguments with a `campfire:`/`https:` scheme to `SharedLinks` via `ShareLinks.parse`,
  never as paths: only a scheme marks a link, since a bare payload looks like `2.cho`. The hand-over makes only paths
  absolute; the holder splits again. Try it: `run --args="campfire://s/<payload>"`.

## Single instance (`SingleInstance.kt`, `java.base` only)

- `instance.lock` via `tryLock()`; the holder listens on loopback and writes port and token to the owner-only
  `instance.endpoint`; a newcomer hands over its args and exits. On Windows the window comes forward by toggling
  `isAlwaysOnTop` (`toFront()` only flashes the taskbar). Fails open.
- Lock held and no endpoint file means the holder is closing: wait up to 60 s, which must stay above the 30 + 15 + 2 s
  of `settleSynchronizationBeforeExit`. An endpoint that doesn't answer: give up after ~5 s.
- An exiting instance stops listening (`stopListeningForOtherInstances`) but keeps the lock, so it never acknowledges
  files it won't open.

## Input

- **Trackpad pinch (macOS)** reaches only a `com.apple.eawt.event` listener on a Swing component
  (`TouchpadMagnification.kt`, reflection plus `--add-exports` on macOS hosts) → `magnifyByTouchpad`. Windows turns a
  pinch into Ctrl + wheel.
- **Touchscreen (`TouchScreen.kt`)**: JBR on Windows/X11 turns a finger into wheel events of its own scroll types plus
  a click for a tap. A pushed `EventQueue` converts them into `PointerType.Touch` events for the window's `ComposeScene`
  (by position, never delta, since a move may be split per axis) and drops a tap's synthetic events. Primary finger
  only.
  - The scene is found by reflection, once, and trusted only if its size equals the content pane's; otherwise touch is
    left to Compose. One queue for all windows, never popped (`EventQueue.pop` removes the topmost, whoever pushed it).
  - **Re-check after every Compose update** (on a Mac, by posting `MouseWheelEvent`s of those scroll types).

## Log (`installProcessLog`, see `ui/platform` in `:presentation`)

- Output and uncaught exceptions are mirrored to `campfire.log` in the data directory. Installed after the
  single-instance check, so a hand-over process never rotates the owner's log.
- It starts with the renderer and the start line; a later `Renderer info` (a Direct3D fallback) wins. It names song
  files.

## Data directory

- `~/Library/Application Support/Campfire` (macOS), `%APPDATA%\Campfire` (Windows; the package's `LocalState` for the
  Store build, see MSIX), `$XDG_DATA_HOME/campfire` or `~/.local/share/campfire` (Linux).
- Mac App Store sandbox: the JDK takes `user.home` from the container, so nothing in the code changes;
  `network.server` covers the two loopback sockets.

## Sync authorization

The browser tab can't be closed by script, so `main` brings the window forward (`bringForward`) once the sync state
leaves `Connecting`. The rest is `data/source/remote/implementation/CLAUDE.md`'s OAuth section.

## Packaging

- Task classes live in `gradle/build-logic`'s `tasks/`.
- `campfire.versionName` must be `MAJOR[.MINOR][.PATCH]` (jpackage).
- `modules(...)` = `suggestRuntimeModules` output plus `jdk.localedata` (reached via `ServiceLoader`; without it every
  language name is English) and `jdk.accessibility` (Windows screen readers). A missing module only shows in an
  installed build — rerun `suggestRuntimeModules` after adding a JVM dependency.
- After every jlink, `-Xshare:dump` restores the JDK's CDS archive.
- `-XX:-DontCompileHugeMethods` (`application.jvmArgs`, so `run` and every package): a composable is one method, and
  HotSpot otherwise never compiles one past 8,000 bytes of bytecode (`SongDetailsScreen` and `CampfireDialog` were).
- Windows/Linux: `recordClassDataArchive` trains `campfire.jsa` by starting the release image once
  (`-Dcampfire.trainingRun=true` exits itself after the demo library draws; a killed JVM writes no archive). Linux needs
  a display (`xvfb-run` in CI); macOS has none.
- Windows: ANGLE's DLLs are copied into `$APPDIR` from `angleRuntime` (its `skiko` version must equal Compose's), not
  the classpath, where ProGuard would join them into the jar. `SKIKO_RENDER_API=DIRECT3D` is the user's escape hatch.
- QuickJS's library for the host is an app resource (`appResourcesRootDir`'s `common/`, extracted from the quickjs-kt
  jar by `extractQuickJsLibrary`; nothing on a host it has none for), loaded through `com.dokar.quickjs.library.path`,
  set in `main`, and the release jar's `jni/` is stripped with every platform's copy: otherwise the loader unpacks it
  into the temporary folder on every launch (left behind on Windows, outside the signed bundle on the Mac App Store).

### ProGuard (`proguard-rules.pro`)

- Type specialization/generalization optimizations are off: they drop a needed `checkcast` (`VerifyError` in
  `NavDisplay`).
- Keeps `SingleInstance.kt`'s write-only `heldLock`, or GC releases the lock.
- Carries Coil's keep rules (ProGuard doesn't read them from the jar).
- Obfuscation stays off: `TouchScreen.kt` finds the scene by its `androidx.compose.` class names.
- Output is one jar (Windows Defender rescans per file). The merge keeps the first of duplicate resources, so no two
  dependencies may ship the same `META-INF/services` file.
- **Start a release build before shipping it** (`runRelease`): ProGuard breakage only shows then.

### macOS

- Asking for a `.pkg` is what turns on Mac App Store packaging (sandbox entitlements, provisioning profiles).
- `device.audio-input` entitlement and `NSMicrophoneUsageDescription` (`macos/microphone.plist`) for the tuner; without
  the latter macOS kills the process at the first mic read.
- Signed when `campfire.mac.signingIdentity` is set, as `Name (TEAMID)` (the plugin adds the prefix); ad hoc otherwise.
  A hand-built `.dmg` stays ad hoc: a real signature enforces the hardened runtime, and only the store build has the
  JVM's entitlements.
- The store build's runtime must have no `bin` folder; provisioning profiles lose their quarantine xattrs (ITMS-91109).
- Document types are raw plist (`macos/document-types.plist`): the DSL's `fileAssociation` writes OS type `****`
  (claims every file) and has no rank. `.cho` is `Default`, the rest `Alternate`. `macos/export-compliance.plist` says
  `ITSAppUsesNonExemptEncryption` `false`, as the iOS `Info.plist` does — change both if encryption is added.
  `macos/url-types.plist` claims the `campfire` scheme (bundles only, not `run`).

### Windows

- `.msi`: per user, fixed `upgradeUuid`. **Never change either**: both put a second Campfire
  next to existing installs.
- File associations: only `.cho`, `.chopro`, `.chordpro` (an MSI can only claim an extension outright); the Store
  package claims the same. Never zip or `.txt`. Keep in step with `LibraryFiles.SONG_EXTENSIONS`.
- Only the `.msix` claims the `campfire` scheme (`windows.protocol`); the `.msi` is published by nothing.
- **MSIX** (`packageReleaseMsix`): identity from `campfire.windows.*` (Partner Center's, verbatim), version
  `campfire.versionName` + `.0`. Unsigned: Partner Center signs. English only, since Partner Center wants a listing for
  every declared language.
- MSIX AppData writes are virtualized and the Store refuses `unvirtualizedResources`, so the task adds `-Dcampfire.packageFamilyName=<name>_<publisher id>` to `Campfire.cfg`, and `desktopDataDirectory()` (in both
  `:presentation` and `:data:source:local:implementation`) then uses `%LOCALAPPDATA%\Packages\<family>\LocalState`.

### Linux

- The `.deb` asks for a `shortcut` (jpackage writes a `.desktop` entry only then). `rewriteDebDesktopEntry` adds
  `StartupWMClass`, which **must equal** `setLinuxWindowClassName`'s value (the Linux-only `--add-opens` is for that):
  launchers match windows by `WM_CLASS`. It also claims `x-scheme-handler/campfire` (`Exec … %u`); `postinst` runs
  `update-desktop-database`, else the scheme waits for the next login.
- No file associations (they are keyed by MIME type, which would claim every text file).

### Icons

- Only on Linux does the running window icon follow the theme color (`AppIcon.kt`): Windows' Store taskbar uses the
  package logos, and a `Taskbar` Dock image misses macOS's tinted/dark styles.

`packageRelease…` tasks run ProGuard.
