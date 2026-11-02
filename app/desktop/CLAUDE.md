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

Koin is started before the window opens (the graph belongs to the process); the view model is obtained outside
`Window` so resizing doesn't reset it.

## Startup order

`main` first calls `configureSwingGlobalsForCompose`, then on Linux `setLinuxWindowClassName`. **Nothing may start the
AWT toolkit before them**: on Linux that sets the display scale, which AWT reads once (a HiDPI Xft.dpi desktop gets a
1x window otherwise). Asking `Desktop` anything starts AWT, which is why the open-file handler is registered on macOS
only.

## Quitting

- The window's close, `DialogType.ConfirmExit`, and the macOS quit (app menu / Cmd+Q never reach `onCloseRequest`;
  caught with `Desktop.setQuitHandler`) all go through `CampfireViewModel.requestExit`, which waits for a pending save
  and asks the unsaved changes question.
- The macOS quit keeps its response and answers it later: cancelling up front aborts a logout or shutdown.
- All end in `leave`: the other-instance listener closed, the window hidden, then `settleSynchronizationBeforeExit` — a
  writing import may finish (up to 30 s), then the pending automatic sync run may finish (up to 15 s), then it is
  stopped.

## Window

- Size and minimum are capped to the screen (`fitSizeToScreen`), since a window below its minimum is grown past the
  screen edge.
- macOS and Windows: content is laid out under the title bar via the JBR custom title bar (`jbr-api`, interfaces kept
  by name). JBR treats the strip as title bar only where nothing listens to the mouse, so a toolkit-wide listener marks
  events over it `forceHitTest(false)` (not in full screen). Not apple.awt.fullWindowContent: it lets the double
  click through. Linux and a non-JBR runtime keep the system title bar.
- The strip is a top system bar inset (`TitleBarInsets`); none in full screen, which macOS reports through
  `com.apple.eawt.FullScreenUtilities` (`WindowState.placement` only updates on a resize). Set in `SwingWindow`'s
  `init`: the JDK doesn't relayout a shown window. Window buttons follow the app's theme, or they are near invisible.
- **The JetBrains Runtime is required** and must be installed locally (foojay has no JBR 21): without it a JDK 21
  builds with a warning, but every release task fails.
- Area uncovered by a fast resize is painted with the theme background (`setUndrawnAreaColor`), on Windows on every
  component (Swing repaints with its panel gray otherwise).

## Opening files and links

- Windows/Linux pass the file in `args`; macOS sends an Apple event to `Desktop.setOpenFileHandler`, registered after
  the single-instance check, before Koin, and never removed (the JDK drops events finding it removed).
- `campfire://s/` links arrive the same ways (macOS: `Desktop.setOpenURIHandler`). Only a `campfire:`/`https:` scheme
  marks a link, since a bare payload looks like `2.cho`; the hand-over makes only paths absolute. Try it:
  `run --args="campfire://s/<payload>"`.
- Sync authorization: the browser tab can't be closed by script, so the window comes forward once the sync state
  leaves `Connecting` (the rest: `data/source/remote/implementation/CLAUDE.md`).

## Single instance (`SingleInstance.kt`, `java.base` only)

- `instance.lock` via `tryLock()`; the holder listens on loopback and writes port and token to the owner-only
  `instance.endpoint`; a newcomer hands over its args and exits. On Windows the window comes forward by toggling
  `isAlwaysOnTop` (`toFront()` only flashes the taskbar). Fails open.
- Lock held and no endpoint file means the holder is closing: wait up to 60 s, which must stay above the 30 + 15 + 2 s
  of `settleSynchronizationBeforeExit`. An endpoint that doesn't answer: give up after ~5 s.
- An exiting instance stops listening but keeps the lock, so it never acknowledges files it won't open.

## Input

- **Trackpad pinch (macOS)** reaches only a `com.apple.eawt.event` listener on a Swing component
  (`TouchpadMagnification.kt`, reflection plus `--add-exports` on macOS hosts). Windows turns a pinch into Ctrl + wheel.
- **Touchscreen (`TouchScreen.kt`)**: JBR on Windows/X11 turns a finger into wheel events of its own scroll types plus
  a click for a tap. A pushed `EventQueue` converts them into `PointerType.Touch` events for the window's `ComposeScene`
  (by position, never delta, since a move may be split per axis) and drops a tap's synthetic events. Primary finger
  only. The scene is found by reflection, once, and trusted only if its size equals the content pane's. One queue for
  all windows, never popped (`EventQueue.pop` removes the topmost, whoever pushed it). **Re-check after every Compose
  update** (on a Mac, by posting `MouseWheelEvent`s of those scroll types).

## Log and data directory

- The process log is installed after the single-instance check, so a hand-over process never rotates the owner's log.
  A later `Renderer info` (a Direct3D fallback) wins over the first. It names song files.
- Data: `~/Library/Application Support/Campfire` (macOS), `%APPDATA%\Campfire` (Windows; the package's `LocalState` for
  the Store build), `$XDG_DATA_HOME/campfire` or `~/.local/share/campfire` (Linux). In the Mac App Store sandbox the
  JDK takes `user.home` from the container, so nothing in the code changes; `network.server` covers the two loopback
  sockets.

## Packaging

- `campfire.versionName` must be `MAJOR[.MINOR][.PATCH]` (jpackage).
- `modules(...)` = `suggestRuntimeModules` output plus `jdk.localedata` (reached via `ServiceLoader`; without it every
  language name is English) and `jdk.accessibility` (Windows screen readers). A missing module only shows in an
  installed build — rerun `suggestRuntimeModules` after adding a JVM dependency.
- After every jlink, `-Xshare:dump` restores the JDK's CDS archive.
- `-XX:-DontCompileHugeMethods`: a composable is one method, and HotSpot otherwise never compiles one past 8,000 bytes
  of bytecode (`SongDetailsScreen` and `CampfireDialog` were).
- Windows/Linux: `recordClassDataArchive` trains `campfire.jsa` by starting the release image once
  (`-Dcampfire.trainingRun=true` exits itself after the demo library draws; a killed JVM writes no archive). Linux needs
  a display (`xvfb-run` in CI).
- Windows: ANGLE's DLLs are copied into `$APPDIR` from `angleRuntime` (its `skiko` version must equal Compose's), not
  the classpath, where ProGuard would join them into the jar. `SKIKO_RENDER_API=DIRECT3D` is the user's escape hatch.
- QuickJS's library for the host is an app resource, loaded through `com.dokar.quickjs.library.path` (set in `main`),
  and the release jar's `jni/` is stripped: otherwise the loader unpacks it into the temporary folder on every launch
  (left behind on Windows, outside the signed bundle on the Mac App Store).

### ProGuard (`proguard-rules.pro`)

- Type specialization/generalization optimizations are off: they drop a needed `checkcast` (`VerifyError` in
  `NavDisplay`). `SongChordsSectionKt` and `ChordSheetConverter` are left unoptimized (`VerifyError`s a start never
  reaches).
- Keeps `SingleInstance.kt`'s write-only `heldLock`, or GC releases the lock. Carries Coil's keep rules (ProGuard
  doesn't read them from the jar).
- Obfuscation stays off: `TouchScreen.kt` finds the scene by its `androidx.compose.` class names.
- Output is one jar (Windows Defender rescans per file). The merge keeps the first of duplicate resources, so no two
  dependencies may ship the same `META-INF/services` file.
- `verifyReleaseBytecode` links every class in the JetBrains Runtime, so an optimizer `VerifyError` fails the build;
  fix it with a rule here, never by dropping the check. That JDK has every module, so a class needing one the image
  leaves out still passes.
- **Start a release build before shipping it** (`runRelease`): what ProGuard breaks for reflection only shows then.

### macOS

- Asking for a `.pkg` is what turns on Mac App Store packaging (sandbox entitlements, provisioning profiles).
- `NSMicrophoneUsageDescription` (`macos/microphone.plist`) is required: without it macOS kills the process at the
  first mic read.
- Signed when `campfire.mac.signingIdentity` is set, as `Name (TEAMID)`; ad hoc otherwise. A hand-built `.dmg` stays ad
  hoc: a real signature enforces the hardened runtime, and only the store build has the JVM's entitlements.
- The store build's runtime must have no `bin` folder; provisioning profiles lose their quarantine xattrs (ITMS-91109).
- Document types are raw plist (`macos/document-types.plist`): the DSL's `fileAssociation` writes OS type `****`
  (claims every file) and has no rank. `.cho` is `Default`, the rest `Alternate`. `macos/export-compliance.plist` and
  the iOS `Info.plist` both say `ITSAppUsesNonExemptEncryption` `false` — change both if encryption is added.
- `macos/url-types.plist` claims the `campfire` scheme, in bundles only (not `run`); on Windows only the `.msix` does
  (`windows.protocol`).

### Windows

- `.msi`: per user, fixed `upgradeUuid`. **Never change either**: both put a second Campfire next to existing installs.
- File associations: only `.cho`, `.chopro`, `.chordpro` (an MSI can only claim an extension outright); the Store
  package claims the same. Never zip or `.txt`. Keep in step with `LibraryFiles.SONG_EXTENSIONS`.
- **MSIX** (`packageReleaseMsix`): identity from `campfire.windows.*` (Partner Center's, verbatim). Unsigned: Partner
  Center signs. It declares every language of the app, and Partner Center wants a listing for each.
- MSIX AppData writes are virtualized and the Store refuses `unvirtualizedResources`, so the task adds
  `-Dcampfire.packageFamilyName` to `Campfire.cfg`, and `desktopDataDirectory()` (in both `:presentation` and
  `:data:source:local:implementation`) then uses `%LOCALAPPDATA%\Packages\<family>\LocalState`.

### Linux

- `rewriteDebDesktopEntry` adds `StartupWMClass`, which **must equal** `setLinuxWindowClassName`'s value: launchers
  match windows by `WM_CLASS`. It also claims `x-scheme-handler/campfire`; `postinst` runs `update-desktop-database`,
  else the scheme waits for the next login. `postinst` and `prerm` call `xdg-desktop-menu` only where it exists: a
  distrobox container can lack it with xdg-utils counted as installed.
- No file associations (they are keyed by MIME type, which would claim every text file).
- **AppImage** (`packageReleaseAppImage`, Linux host only): the `.deb`'s app image behind the static type-2 runtime (no
  `libfuse2` needed), its desktop entry carrying the same keys. The scheme and menu entry exist only once an
  integration tool copies the entry out. The runtime's libraries are credited for `AppBuild.LINUX`.
- Only on Linux does the running window icon follow the theme color: Windows' Store taskbar uses the package logos,
  and a `Taskbar` Dock image misses macOS's tinted/dark styles.
