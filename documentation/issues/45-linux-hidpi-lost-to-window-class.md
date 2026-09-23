# 45 — Linux window opens at 1x on a HiDPI screen since the window class is set first

**Severity:** visible regression (Linux, X11 and XWayland sessions whose scale is only in `Xft.dpi`) · **Area:**
`:app:desktop` (`CampfireDesktopApplication.kt`)

**Read, not run.** This was found by reading the entry point at HEAD and the Compose / Skiko sources it runs into; it
has not been reproduced on a Linux machine. The "Verification" section below is how to confirm it, and confirming it
is the first step of the work.

**The file had uncommitted work in progress at review time.** `CampfireDesktopApplication.kt` (and
`app/desktop/build.gradle.kts`) were being edited by an end-to-end testing session while this was written, so the
code below is quoted from `git show HEAD:…` (HEAD 2065e47f). Re-locate the lines in the file as it is when you start;
the function names are the anchors, not the line numbers.

## What the user sees

On a Linux desktop that tells applications its scale through the X resource `Xft.dpi` — KDE Plasma at a fractional
or 200 % scale, Xfce, Cinnamon and MATE with a raised DPI, or anybody who set `Xft.dpi` with `xrdb` — Campfire opens
at 1x: tiny text, tiny touch targets, a window that looks like it belongs to a different screen. Before commit
`044d3a55` ("Match the Linux window to its installed desktop entry…") the same machine opened it at 2x (or 1.5x).

Unaffected: macOS and Windows (the function returns at once there), and Linux sessions where the JDK finds the scale
on its own — `GDK_SCALE` / `J2D_UISCALE` in the environment, or GNOME's integer `scaling-factor` setting, which the
JDK reads itself. That is why a stock Ubuntu GNOME at 200 % may well look right and the bug still be there.

## Cause

`main` sets the X11 toolkit's app class name before anything else (HEAD,
`app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt:50-52`):

```kotlin
fun main(args: Array<String>) {
    // Before anything touches AWT, since the toolkit reads the name when its first window is created.
    setLinuxWindowClassName()
```

and doing so starts the toolkit (`:171-180`):

```kotlin
private fun setLinuxWindowClassName() {
    if (!System.getProperty("os.name").orEmpty().lowercase().contains("linux")) return
    runCatching {
        val toolkit = Toolkit.getDefaultToolkit()
        toolkit.javaClass.getDeclaredField("awtAppClassName").apply {
            isAccessible = true
            set(toolkit, "Campfire")
        }
    }
}
```

`Toolkit.getDefaultToolkit()` constructs `XToolkit`, which fetches the local `GraphicsEnvironment`, and
`SunGraphicsEnvironment` reads `sun.java2d.uiScale.enabled` / `sun.java2d.uiScale` once, into static finals.

Compose sets those two properties later, inside `application { }`. Checked against the sources in the Gradle cache
(`ui-desktop-1.12.0-sources.jar`, `skiko-awt-0.150.1-sources.jar`, the versions this project resolves):

- `androidx/compose/ui/window/Application.desktop.kt:105-111` — `application()` calls
  `configureSwingGlobalsForCompose()` when `compose.application.configure.swing.globals` is `"true"`, which the Compose
  Gradle plugin passes to `run` and to every packaged app (`ConfigureJvmApplicationKt` in the 1.12.0 plugin).
- `androidx/compose/ui/ConfigureSwingGlobalsForCompose.desktop.kt:37-50` — sets `skiko.linux.autodpi=true` by default
  and calls `Library.staticLoad()`. Its KDoc: *"Should be called before using any class from `java.swing.*` (even before
  SwingUtilities.invokeLater or MainUIDispatcher)"*.
- `org/jetbrains/skiko/Setup.kt:13-17` (run from `Library`'s loader, `Library.kt:12-13`):

  ```kotlin
  if (hostOs == OS.Linux && autoLinuxDpi) {
      val scale = linuxGetSystemDpiScale()
      System.setProperty("sun.java2d.uiScale.enabled", "true")
      System.setProperty("sun.java2d.uiScale", "$scale")
  }
  ```

By then the graphics environment has already been initialized with no scale, so the properties are set and never
read. The project knew about this exact trap: `OpenedFiles.listenForSystemRequests`
(`app/desktop/src/main/java/com/pandulapeter/campfire/OpenedFiles.kt:43-45`) is limited to macOS *because* "asking
`Desktop` anything starts the AWT toolkit, which on Linux reads the display scale before Compose has had the chance to
set it". Commit `044d3a55` reintroduced it through the other door; its comment ("Before anything touches AWT") is
right about the window class and blind to the scale.

Nothing else in `main` before `application { }` touches AWT: `claimSingleInstance` is `java.base` only,
`desktopDataDirectory()` (`Platform.desktop.kt`, whose top-level initializers read only system properties),
`OpenedFiles.open` and `startCampfireDependencyGraph()` do not.

## The change

Invoke the **`code-style`** skill before the first edit.

Let Compose configure the Swing globals first, the way `application()` would, and only then set the window class.
In `main`, before `setLinuxWindowClassName()`:

```kotlin
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    // Compose's own set-up, which application() would only do later, has to come before anything that starts the
    // AWT toolkit: on Linux it is what puts the display's scale into sun.java2d.uiScale, which the toolkit reads once,
    // when it starts. Behind the same property application() checks, so that this is the same decision made earlier
    // rather than a second one (application() calling it again is harmless - the library loads once).
    if (System.getProperty("compose.application.configure.swing.globals") == "true") configureSwingGlobalsForCompose()
    // After Compose's set-up and before the first window, since the toolkit reads the name when that window is
    // created - and asking for the toolkit is what starts it.
    setLinuxWindowClassName()
    …
```

with the imports `androidx.compose.ui.ExperimentalComposeUiApi` and `androidx.compose.ui.configureSwingGlobalsForCompose`.

Extend `setLinuxWindowClassName`'s KDoc with one sentence: "Asking for the toolkit starts it, and on Linux it reads
the display scale when it starts, so this has to come after `configureSwingGlobalsForCompose`."

Notes for whoever implements it:

- The gate mirrors `Application.desktop.kt:109`. Calling `configureSwingGlobalsForCompose()` unconditionally would also
  work for the packaged app (the property is always `true` there) but would additionally switch the look and feel and
  the DPI handling on for an IDE run of `main` that Compose itself leaves alone; not worth a behaviour difference
  between `run` and a debugger.
- `configureSwingGlobalsForCompose` also sets the system look and feel, which on Linux asks the toolkit whether GTK is
  there — after `Setup.init` has set the scale (`Setup.kt:13-25`), so the order inside it is already right. That is
  the same order the app has always had on every platform.
- **Rejected alternative:** moving `setLinuxWindowClassName()` inside `application { }` before `Window(...)`. It would
  work (the class name is read when the first window's peer is created), but it leaves `main` free to start the
  toolkit again the next time somebody adds an innocent `Toolkit`/`Desktop` call before `application`, which is how
  this regression happened. Configuring first makes the order a property of the entry point, not of what happens to be
  above it.
- `ExperimentalComposeUiApi`: an opt-in at the function is the narrowest place; there is no stable replacement in
  Compose 1.12.

## Tests

- **No unit test is possible.** This is the order of JVM start-up side effects in a `main`; `:app:desktop` has no test
  source set.
- Compile check: `./gradlew :app:desktop:compileKotlin`.
- `./gradlew :app:desktop:createReleaseDistributable` and start the image once on macOS
  (`app/desktop/build/compose/binaries/main-release/app/Campfire.app/Contents/MacOS/Campfire`): the desktop entry has
  to keep starting under ProGuard (`app/desktop/CLAUDE.md`, "A release build has to be started once before it is
  shipped"). `configureSwingGlobalsForCompose` is public API in `ui-desktop`, so no new keep rule is expected.

## Verification

Confirm the bug first — it was read, not run — then the fix, on the same machine.

1. An Ubuntu 22.04 or 24.04 VM with **"Ubuntu on Xorg"** chosen at login (the Linux test machine of
   `documentation/testing/05-linux.md`). Make sure nothing else sets the scale: `env | grep -E 'GDK_SCALE|J2D_UISCALE'`
   prints nothing, and GNOME's Displays scale is 100 %.
2. `echo 'Xft.dpi: 192' | xrdb -merge`, then confirm with `xrdb -query | grep dpi`.
3. Start the release image or the installed `.deb` from a terminal (`/opt/campfire/bin/Campfire`) — not
   `:app:desktop:run` from a Gradle daemon that may have been started with another environment.
   - **Before the fix:** the window and its text are at 1x (compare with a GTK app such as the text editor started
     from the same terminal, which is at 2x).
   - **After the fix:** Campfire is at 2x like the text editor, the text is sharp, and `xprop WM_CLASS` on the window
     still says `"Campfire", "Campfire"` (LIN-011 in the Linux script — the window class must not regress the other
     way).
4. `xrdb -query` → set `Xft.dpi: 144` and start again: 1.5x.
5. `xrdb -remove` (or `Xft.dpi: 96`) and start again: 1x, unchanged from today.
6. If a KDE VM is at hand: Plasma X11 at 150 %, same check. Wayland sessions run the app through XWayland; repeat LIN-015
   there.
7. macOS and Windows: start the app once each. The call is behind a property that is set on those platforms too, and
   there it does what `application()` did a moment later before — the menu bar and the look and feel must look
   exactly as before.

## Docs

- `app/desktop/CLAUDE.md`, line 12, currently: "On Linux it first sets the X11 toolkit's app class name to `Campfire`
  (`setLinuxWindowClassName`), before anything touches AWT." Replace with: "It first lets Compose configure the Swing
  globals (`configureSwingGlobalsForCompose`, which `application` would only call later) — on Linux that is what sets
  the display scale, which the AWT toolkit reads once, when it starts — and then, on Linux, sets the X11 toolkit's app
  class name to `Campfire` (`setLinuxWindowClassName`) before the first window exists. Nothing may start the toolkit
  before the first of those two: a window at 1x on a HiDPI screen is what that costs, and only a Linux desktop whose
  scale is in `Xft.dpi` shows it."
- `documentation/testing/05-linux.md`, LIN-015 (lines 129-133), currently one step under Wayland. Add a step and an
  expectation:
  > 2. In an X11 session with no `GDK_SCALE` in the environment and the desktop at 100 %, run
  >    `echo 'Xft.dpi: 192' | xrdb -merge` and start `/opt/campfire/bin/Campfire` from that terminal.
  >
  > **Expected:** … and in step 2 the window is at 2x, the same as a GTK app started from that terminal. (A window at
  > 1x there means something started the AWT toolkit before Compose set the scale.)

  and mark it 🆕.
- Root `CLAUDE.md`: nothing claims anything about scaling; no change.

## Files touched

- `app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt`
- `app/desktop/CLAUDE.md`
- `documentation/testing/05-linux.md`

## Depends on

Nothing. Plan 47 also edits `app/desktop/CLAUDE.md` (line 42, the library location) and
`documentation/testing/05-linux.md` (lines 33, 54, 91, 179) — different lines, either order.
