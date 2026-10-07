# Render through ANGLE on Windows, if a measurement on the low-end 2-in-1 shows Direct3D 12 losing to it

**Kind:** performance (frame rate)  ·  **Severity:** medium  ·  **Platforms:** Windows
**Lane:** D  ·  **Files:** `app/desktop/build.gradle.kts`, `app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt`,
`gradle/libs.versions.toml` (a `skiko` version and the ANGLE runtime library), `app/desktop/CLAUDE.md`

## Problem

The trigger of this sweep: on a low-end Windows 11 2-in-1 the web build runs much better than the desktop build. Both
draw the same Skia, so the difference is in how frames reach the screen. Verified in skiko 0.150.1
(`scratchpad/desktop-review/skiko-src`):

- Windows defaults to `DIRECT3D` (12) unless ANGLE is enabled (`SkikoProperties.bestRenderApiForCurrentOS`:
  `OS.Windows -> if (renderingAngleEnabled) GraphicsApi.ANGLE else GraphicsApi.DIRECT3D`), falling back to `OPENGL`,
  `SOFTWARE_FAST`, `SOFTWARE_COMPAT`. The Direct3D blocklist names only Intel HD 520/530/4400/4600 and three NVIDIA
  cards. `Direct3DContextHandler` uses two buffers, and `Direct3DRedrawer` runs the update and then draw + swap
  (with vsync) serially on the same thread.
- Chrome runs Skia via WebGL → ANGLE → Direct3D 11, with the page's main thread and the GPU process working in
  parallel — a mature path on exactly the integrated Intel GPUs such 2-in-1s have.
- If the device fell back to software, it is dramatically slower: 27–31 ms of CPU per frame at 1600×1200 measured on a
  Mac (35–39 FPS against Metal's 120).

skiko ships ANGLE as an option: `skiko.rendering.angle.enabled` (`SkikoProperties.renderingAngleEnabled`) makes it the
first API on Windows, with `DIRECT3D`, `OPENGL` and the software renderers after it in the fallback queue, loading
`libEGL.dll` (which loads `libGLESv2.dll`) from `skiko.library.path`, `java.home` or the classpath
(`AngleSupport.jvm.kt`, `LibraryLoader`). The artifact exists on Maven Central for the version Compose 1.12.1 uses:
`org.jetbrains.skiko:skiko-awt-runtime-angle-windows-x64:0.150.1` (and `-windows-arm64`; checked in
`maven-metadata.xml`); its jar holds `libEGL.dll` (0.5 MB) and `libGLESv2.dll` (5.6 MB) with `.sha256` files.

Whether ANGLE (or OpenGL, or vsync off) is actually faster on that device is a **hypothesis**: nobody has measured it
there. The packaging only moves skiko's own `skiko-windows-<arch>.dll` and `icudtl.dat` into `$APPDIR`
(`AbstractJPackageTask`, Compose Gradle plugin 1.12.1); the ANGLE libraries would stay in the joined jar and be
unpacked into `~/.skiko` at run time, so shipping them properly is a build change.

## Fix

**Decided (D-03, 2026-10-07): ship ANGLE now**, without waiting for the measurement on the 2-in-1. Carry out the
steps below. The recipe under Manual check stays the way to confirm the choice there afterwards: if the log shows
`SOFTWARE_FAST`, the problem is a fallback rather than the choice of API, and it needs an investigation of its own.
Do not ship OpenGL or vsync off as a default.

Steps:

1. `gradle/libs.versions.toml`: add `skiko = "0.150.1"` with a comment that it must equal the skiko version Compose
   brings in (`./gradlew :app:desktop:dependencies --configuration runtimeClasspath | grep skiko`), and a library
   `skiko-angle-windows-x64 = { module = "org.jetbrains.skiko:skiko-awt-runtime-angle-windows-x64", version.ref = "skiko" }`.
2. `app/desktop/build.gradle.kts`: do **not** add it to the runtime classpath (ProGuard would join the DLLs into the
   jar). Add a dedicated configuration, `val angleRuntime by configurations.creating`, with that dependency, and on a
   Windows host a `doLast` on `createReleaseDistributable` that unzips `libEGL.dll` and `libGLESv2.dll` from it into
   the image's `app/` folder (`build/compose/binaries/main-release/app/Campfire/app`), which is `$APPDIR`, the
   `skiko.library.path` the launcher already sets (`java-options=-Dskiko.library.path=$APPDIR` in `Campfire.cfg`).
   `packageReleaseMsix` and `packageReleaseMsi` package them from there. If plan 01 has landed, the DLLs must be in
   the image before `recordClassDataArchive` runs (a `doLast` on `createReleaseDistributable` is).
3. `CampfireDesktopApplication.kt`, in `main` before the first window: turn ANGLE on only where its libraries are
   next to skiko's and nobody chose a renderer, so `run` (no DLLs) and a user's own choice are left alone:

   ```kotlin
   // ANGLE draws through Direct3D 11 the way a browser does, a path far more drivers of the integrated GPUs in small
   // Windows machines are tested against than skiko's Direct3D 12. Only where the build put its libraries next to skiko's, and never over
   // a renderer somebody chose; a failed start falls back to Direct3D 12 on its own.
   if (isWindows && System.getenv("SKIKO_RENDER_API") == null && System.getProperty("skiko.renderApi") == null &&
       File(System.getProperty("skiko.library.path").orEmpty(), "libEGL.dll").isFile
   ) System.setProperty("skiko.rendering.angle.enabled", "true")
   ```

   (`isWindows` already exists in the desktop module.) The escape hatch costs nothing: `SKIKO_RENDER_API=DIRECT3D` as
   a user environment variable takes a user back to today's renderer, and fallback is automatic when ANGLE cannot
   start. A Settings switch is not worth it: the renderer is chosen before any preference is read, and the
   environment variable already covers the rare machine where ANGLE misbehaves; mention it in the support page
   rather than in the app.
4. `app/desktop/CLAUDE.md`: a sentence in the Packaging paragraph about the ANGLE libraries in `$APPDIR`, why they are
   copied rather than left in the jar, the `main` switch, and `SKIKO_RENDER_API=DIRECT3D` as the way back.

Cost: +6.1 MB in the Windows package. The CI start check runs with `SKIKO_RENDER_API=SOFTWARE_FAST`, so it neither
exercises nor is disturbed by ANGLE.

## Tests

None: renderer selection and packaging, no pure logic.

## Manual check

**Measurement recipe (before deciding), on the 2-in-1 with the Store build installed.** The JVM reads
`JAVA_TOOL_OPTIONS` itself, and a full-trust packaged app is started with the user's environment, so a user
environment variable reaches the Store build. In PowerShell:

```powershell
setx JAVA_TOOL_OPTIONS "-Dskiko.fps.enabled=true -Dskiko.fps.periodSeconds=2 -Dskiko.fps.longFrames.show=true -Dskiko.hardwareInfo.enabled=true"
```

Then for each run below: sign out and in (or restart Explorer) so the Start menu picks up the change, start Campfire
from the Start menu, scroll the Songs list for 20 seconds and page through a song for 20 seconds at the same window
size, close it, and copy `%LOCALAPPDATA%\Packages\<Campfire package family>\LocalState\campfire.log` aside (FPS
lines `[…] FPS avg (min-max)` and `Long frame N ms` are mirrored there, with the `Renderer info` block naming the API
and adapter).

1. Default (Direct3D 12).
2. `setx SKIKO_RENDER_API OPENGL`.
3. `setx SKIKO_RENDER_API SOFTWARE_FAST` (the floor).
4. Back to the default API (`reg delete HKCU\Environment /v SKIKO_RENDER_API /f`) with `-Dskiko.vsync.enabled=false`
   added to `JAVA_TOOL_OPTIONS` — only to see whether vsync waiting is the bottleneck.
5. ANGLE, which the installed package cannot load: rename the Store package (the `.msix` artifact of the Windows
   workflow, or `app/desktop/build/compose/binaries/main-release/msix/*.msix`) to `.zip`, extract it, delete the
   `java-options=-Dcampfire.packageFamilyName=…` line from `app\Campfire.cfg` (so it uses its own library under
   `%APPDATA%\Campfire`, not the Store app's), copy `libEGL.dll` and `libGLESv2.dll` from
   `skiko-awt-runtime-angle-windows-x64-0.150.1.jar` (Maven Central; a jar is a zip) into `app\`, and start
   `Campfire.exe` from a console with `set SKIKO_RENDER_API=ANGLE` and the same `JAVA_TOOL_OPTIONS`. Its log is in
   `%APPDATA%\Campfire\campfire.log`. Run the default API from the same extracted copy too, so the comparison is fair.
6. For reference, the web build in Chrome on the same screen (DevTools → Rendering → Frame rendering stats).

Finally remove the variables (`reg delete HKCU\Environment /v JAVA_TOOL_OPTIONS /f`, same for `SKIKO_RENDER_API`).

**After shipping:** `campfire.log` on the 2-in-1 names `ANGLE` and the adapter; scrolling and paging match run 5; on
an ordinary Windows machine nothing got worse; with `SKIKO_RENDER_API=DIRECT3D` set the log names `DIRECT3D`.
