# Write the renderer, the graphics adapter and the time to the first frame into campfire.log

**Kind:** diagnostics  ·  **Severity:** high  ·  **Platforms:** desktop (Windows, Linux, macOS)
**Lane:** D  ·  **Files:** `app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt`, `app/desktop/CLAUDE.md`
**Challenged:** amended — `window.renderApi` is read on the event thread before switching to `Dispatchers.IO`, and the docs say skiko's `Renderer info` block is the authoritative renderer: the start line reports the API asked for at the first frame, which a Direct3D context failing on its first draw can still change afterwards.

## Problem

A user who reports that the desktop build is slow — the trigger of this sweep is a low-end Windows 11 2-in-1 on
which the web build runs much better — sends `campfire.log`, and nothing in it says how the app is being drawn or how
long it took to start. Nothing sets or logs the render API (`CampfireDesktopApplication.kt` 144–153 creates the
`SwingWindow` with no renderer handling), and skiko is silent on success: a Direct3D 12 start, an OpenGL fallback and
a software fallback all look the same in the log, unless a failed attempt happened to print a `RenderException` first.
The difference is decisive: measured on a Mac at 1600×1200, software rendering costs 27–31 ms of CPU per frame
(35–39 FPS) where Metal does 120 FPS, so a Windows machine that silently fell back to `SOFTWARE_FAST` would be far
slower than the browser, which draws the same Skia through ANGLE on the GPU. Plans 01 and 03 also need this line to
be judged on a real device.

Verified in skiko 0.150.1 (`scratchpad/desktop-review/skiko-src`): every context handler, GPU and software alike,
calls `JvmContextHandler.onContextInitialized()`, which does

```kotlin
if (System.getProperty("skiko.hardwareInfo.enabled") == "true") {
    Logger.info { "Renderer info:\n ${rendererInfo()}" }
}
```

and skiko's default logger prints `info` to standard output, which `DesktopLog` already mirrors into `campfire.log`.
`rendererInfo()` names the API, the OS and architecture, and the adapter: `Video card` and `Total VRAM` for Direct3D
and Metal, `Vendor` and `Model` for OpenGL and ANGLE. So the adapter name needs no `SkiaLayerAnalytics` and no other
`SwingWindow` overload. `ComposeWindow.renderApi` is public as well.

## Fix

In `main`, before `application { … }` (anything before the first window works; after `DesktopLog.install` so it is
the log's business from the start):

```kotlin
// skiko says nothing about the renderer it settled on unless asked, and a software fallback is the first thing a
// slow desktop's log has to rule out. It prints the API and the graphics adapter once a context is up.
if (System.getProperty("skiko.hardwareInfo.enabled") == null) System.setProperty("skiko.hardwareInfo.enabled", "true")
```

Inside the `SwingWindow` content, one effect that reports the start once the first frame is under way:

```kotlin
// One line a desktop bug report can be read by: how the window is drawn, on what, and how long the start took.
LaunchedEffect(window) {
    withFrameNanos { }
    val sinceStart = ProcessHandle.current().info().startInstant().map { Duration.between(it, Instant.now()).toMillis() }
    // A Swing component's state, so read on the event thread rather than inside the IO block below.
    val renderApi = window.renderApi
    withContext(Dispatchers.IO) {
        val runtime = Runtime.getRuntime()
        println(
            "Started in ${sinceStart.map { "$it ms" }.orElse("an unknown time")}: $renderApi rendering, " +
                "Java ${System.getProperty("java.runtime.version")} (${System.getProperty("java.vm.info")}), " +
                "${ManagementFactory.getGarbageCollectorMXBeans().joinToString { it.name }}, " +
                "${runtime.availableProcessors()} processors, ${runtime.maxMemory() / (1024 * 1024)} MB heap at most",
        )
    }
}
```

Notes for the implementer:

- `ProcessHandle` (java.base) measures from the process's own start, which includes the native launcher and JVM
  boot; the runtime MXBean's start time is a little later. Either is fine; prefer `ProcessHandle` and fall back to
  "an unknown time" where the platform does not say.
- `java.vm.info` contains `sharing` when class data sharing is on, which is what plan 01 changes.
- The GC MXBeans are in `java.management`, which `modules(...)` already includes. Read them off the main thread, since
  the first `ManagementFactory` use loads a few hundred classes; nothing waits for the line.
- `withFrameNanos` resumes on the first frame the frame clock produces, which is where the first frame is drawn; call
  it "Started in" rather than claiming the frame was presented.
- No string resources: this is log text, like everything else `DesktopLog` holds.
- The frame clock ticks before skiko draws, and on Windows the Direct3D redrawer initializes its context on its first
  draw, so a context that fails there falls back (to OpenGL or software) after this line may already have said
  `DIRECT3D`. skiko's `Renderer info` block is printed when a context actually comes up, once per API it settles on,
  so it is the authoritative one; say so in the `CLAUDE.md` sentence below.

Update `app/desktop/CLAUDE.md`'s `campfire.log` paragraph: the log opens with skiko's renderer line (API and adapter)
and the app's start line (time since the process started, render API, Java runtime, GC, processors, heap), which is
the first thing to read in a report about a slow desktop — and where the two disagree about the API, the last `Renderer
info` block is the one in use.

## Tests

None: it prints platform facts; there is no pure logic to test.

## Manual check

1. `./gradlew :app:desktop:runRelease` (or start the release image) and read `campfire.log` in the data directory: a
   `[SKIKO] info: Renderer info:` block naming `METAL` and the Mac's GPU, then `Started in … ms: METAL rendering, Java
   21… (mixed mode, sharing), G1 Young Generation, G1 Concurrent GC, G1 Old Generation, …`.
2. On Windows, the same with `DIRECT3D` and the adapter's name; then with `SKIKO_RENDER_API=SOFTWARE_FAST` set in the
   console the app is started from, the lines say `SOFTWARE_FAST`.
3. Move the window to a second monitor driven by another adapter: a second `Renderer info` block is expected if skiko
   re-creates the context, which is fine.
