<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Find out on the owner's laptop why the Windows release drops frames (GPU choice, renderer, garbage collection, or the app's own frame cost), and fix what that shows

**Challenged:** amended — what ships on Windows is the Store MSIX alone (no MSI is published; one built by hand needs WiX and installs per user under `%LOCALAPPDATA%`, not Program Files), so steps 1 and 3b use the Store build and the rest the release image built and trained on the laptop; options go in `JAVA_TOOL_OPTIONS` (read by the JVM itself) rather than `JDK_JAVA_OPTIONS` (expanded by the `java` command's argument handling, which jpackage's launcher need not run); the Store build's log path; skiko property names re-checked in the 0.156.1 jar (all present).

**Kind:** performance  ·  **Severity:** high  ·  **Platforms:** Windows  ·  **Run on:** Windows
**Files:**
- this plan (the numbers are recorded in it)
- depending on the branch taken: `app/desktop/src/main/kotlin/com/pandulapeter/campfire/CampfireDesktopApplication.kt`,
  `app/desktop/build.gradle.kts` (`compose.desktop.application.jvmArgs`), `app/desktop/CLAUDE.md` (Packaging, Log),
  `app/desktop/src/main/kotlin/com/pandulapeter/campfire/StartupLog.kt`
- nothing else is committed by the investigation itself (init scripts and recordings stay outside the repo)

## Problem

The owner sees dropped frames in 5.5.0's Windows release on a high-end laptop, worst when a song's details open, and
skipped frames in debug transitions too. The offscreen measurement (an M-series Mac, warm JIT) found one long frame
per transition (push 25–45 ms, pop 33–39 ms, tab switch 29–37 ms): on a 120–165 Hz panel (6–8 ms a frame) that is three
to six frames lost on the CPU alone, before the GPU. What the offscreen run cannot see is the laptop's own part:

- **Which GPU ANGLE draws on.** `CampfireDesktopApplication.kt:84-86` turns ANGLE on unless a renderer was chosen:

  ```kotlin
  if (isWindows && System.getenv("SKIKO_RENDER_API") == null && System.getProperty("skiko.renderApi") == null &&
      File(System.getProperty("skiko.library.path").orEmpty(), "libEGL.dll").isFile
  ) System.setProperty("skiko.rendering.angle.enabled", "true")
  ```

  So `SKIKO_RENDER_API` (or `-Dskiko.renderApi`) does override it: ANGLE stays off and skiko uses what was named.
  ANGLE's Direct3D 11 device is created on the default adapter, which on a hybrid laptop is whatever Windows' per-app
  graphics preference says (by default "Let Windows decide", often the integrated GPU). skiko 0.156.1
  (`gradle/libs.versions.toml:51`) has `skiko.gpu.priority` / `skiko.directx.gpu.priority` (`auto`, `integrated`,
  `discrete`), but only its Direct3D 12 renderer reads them (`Direct3DRenderer.chooseAdapter`); `AngleRenderer` names no
  priority at all (checked in the 0.156.1 jar).
- **Garbage collection.** A transition allocates 9–37 MB on the main thread; the release runs the default collector
  (G1 on a machine like this one), whose young pauses can land in the long frame.
- **The renderer and the log.** `main` sets `skiko.hardwareInfo.enabled`, so `campfire.log` carries skiko's
  `Renderer info` block (API and adapter, the GL_RENDERER string under ANGLE), and `StartupLog.kt` prints one line:
  `"Started in … ms: $renderApi rendering, Java …, ${GC names}, N processors, M MB heap at most"`.
- **No profiler in the release.** The image holds only the modules `app/desktop/build.gradle.kts:166` lists:
  `modules("java.instrument", "java.management", "jdk.accessibility", "jdk.localedata", "jdk.unsupported")` (plus
  `java.base` and what Compose adds), so no `jdk.jfr`: `-XX:StartFlightRecording` cannot run in the installed app.

## Fix

First the investigation, step by step on the laptop, each result written into the table at the end; then the branch
it points to. Close Campfire before each run: a second instance hands its arguments to the first and exits.

What ships on Windows is the Store's MSIX alone (`publish-windows.yml`); no MSI is published, and one built by hand
needs the WiX toolset and installs per user (`%LOCALAPPDATA%\Campfire`). So:

- **Steps 1 and 3b on the Store build as installed**: its log is
  `%LOCALAPPDATA%\Packages\<package family>\LocalState\campfire.log` (`app/desktop/CLAUDE.md`), and the Graphics page
  can add a Store app (Add an app → Microsoft Store app).
- **Everything that sets options, on the release image built on the laptop**, which is what the MSIX holds:
  `gradlew.bat :app:desktop:recordClassDataArchive` (it runs `createReleaseDistributable`, copies ANGLE's DLLs in, and
  trains `campfire.jsa`, as the package build does; it needs the JetBrains Runtime 21 installed, as every release task
  does). The image is `app\desktop\build\compose\binaries\main-release\app\Campfire\Campfire.exe`; it keeps its data in
  `%APPDATA%\Campfire`, so copy the Store build's `LocalState\library` there first to test on the same songs.
- Options go in **`JAVA_TOOL_OPTIONS`**, which the JVM reads itself whatever launched it (the output then starts with
  `Picked up JAVA_TOOL_OPTIONS`); `JDK_JAVA_OPTIONS` is expanded by the `java` command's own
  argument handling, which jpackage's `Campfire.exe` need not go through. Where that line is missing, put the options on `java-options=` lines of the image's
  `app\Campfire.cfg` instead.

Everything below is typed into one `cmd` window, from which `Campfire.exe` is started so it inherits the variables.

1. **Read the log.** Open `%APPDATA%\Campfire\campfire.log` after a normal start. Note the `Started in` line (render
   API, GC names, heap) and skiko's `Renderer info` block: the adapter (ANGLE's GL_RENDERER reads like
   `ANGLE (Intel, Intel(R) Iris(R) Xe Graphics Direct3D11 …)` or `ANGLE (NVIDIA, NVIDIA GeForce RTX …)`). Note in Task
   Manager → Performance which GPU shows load while scrolling a song, and the panel's refresh rate (Settings → System
   → Display → Advanced display).
2. **Frame counter.** `set JAVA_TOOL_OPTIONS=-Dskiko.fps.enabled=true -Dskiko.fps.periodSeconds=2
   -Dskiko.fps.longFrames.show=true -Dskiko.fps.longFrames.millis=10` (property names checked in skiko 0.156.1's
   `SkikoProperties`), start `Campfire.exe`, open and close a long song five times, switch tabs five times. skiko prints
   FPS and long frames to standard output, which the process log keeps. If nothing appears, use `app\Campfire.cfg` as
   said above.
3. **GPU A/B.** With the same open/close/tab routine and the frame counter on each time:
   - (a) as installed (ANGLE, default adapter);
   - (b) Settings → System → Display → Graphics → add Campfire (the Store app, and the image's `Campfire.exe` for the
     later steps) → **High performance**; restart the app, check the `Renderer info` adapter changed;
   - (c) `set SKIKO_RENDER_API=DIRECT3D` (skiko's Direct3D 12) with `-Dskiko.directx.gpu.priority=discrete` in
     `JAVA_TOOL_OPTIONS`, then with `integrated`;
   - (d) `set SKIKO_RENDER_API=OPENGL`;
   - (e) the panel set to 60 Hz, renderer as in (a).
   Clear the variables (`set SKIKO_RENDER_API=`) and the Graphics entry afterwards.
4. **Garbage collection.** `set JAVA_TOOL_OPTIONS=-Xlog:gc*:file=%TEMP%\campfire-gc.log:uptime,level,tags` (unified
   logging is in `java.base`, so it works in the release), the same routine, then look for pauses over 4 ms and how
   often they fall inside a transition (the uptime stamps against when you clicked). Then the same with
   `-XX:+UseZGC -XX:+ZGenerational` added, and with `-XX:MaxGCPauseMillis=5` instead (G1 kept).
5. **The app's own frame cost on this CPU.** In a checkout on the laptop: `gradlew.bat :tools:screenshots:benchmark
   --args="--suite transitions --hz 144"` (plan 01), then again with
   `-Pcampfire.benchmark.jvmArgs="-XX:+UseZGC -XX:+ZGenerational"`. Note each scenario's warm worst frame and frames over
   budget.
6. **A profile, only if steps 1–5 leave the cause open.** JFR needs `jdk.jfr`, which the image leaves out and must keep
   leaving out. Two ways that ship nothing:
   - (A) Compose's `runRelease` (the ProGuarded jar on the build's own full JetBrains Runtime, which has `jdk.jfr`;
     check the name with `gradlew.bat :app:desktop:tasks --all`) with an init script kept outside the repo, e.g.
     `C:\perf\jfr.init.gradle`:

     ```groovy
     allprojects {
         if (path == ":app:desktop") afterEvaluate {
             tasks.matching { it.name == "runRelease" }.configureEach {
                 jvmArgs("-XX:StartFlightRecording=filename=C:/perf/campfire.jfr,settings=profile,dumponexit=true")
             }
         }
     }
     ```

     `gradlew.bat -I C:\perf\jfr.init.gradle :app:desktop:runRelease`, the routine, then close the window. (If the
     task is not a `JavaExec`, use B.)
   - (B) The installed jar on a full JBR 21 of the bundled runtime's version (`runtime\release` names it; JetBrains'
     GitHub releases): from `app\Campfire.cfg` take `app.mainclass`, the jar, and every `java-options` line with
     `$APPDIR` replaced by the `app` folder, drop `-XX:SharedArchiveFile` (recorded against the bundled runtime), and run
     `<jbr>\bin\java.exe <options> -XX:StartFlightRecording=filename=C:\perf\campfire.jfr,settings=profile,dumponexit=true -cp <jar> <mainclass>`.

   Open the recording in JDK Mission Control: GC pauses (`jdk.GCPhasePause`), compilations over 10 ms
   (`jdk.Compilation`), and the hot methods of the Swing event thread during the clicks.

### Then, by what the table shows

- **(a) The integrated GPU draws, and High performance (step 3b) removes the drops.** Options:
  1. *A per-app GPU preference written by the app*: `HKCU\Software\Microsoft\DirectX\UserGpuPreferences`, value named
     after the full path of `Campfire.exe`, data `GpuPreference=2;` (what the Graphics page writes), set from `main`
     before the window exists, only where no value is there yet. It overrides a user's choice silently and the Store
     build's path changes with every version; the MSI cannot write it either (a per-machine package writing a per-user
     key, and the Compose plugin gives no hook into jpackage's WiX template).
  2. *Skiko's Direct3D 12 renderer with `skiko.directx.gpu.priority=discrete`* where a discrete adapter exists: gives
     back ANGLE's driver robustness, the reason ANGLE was chosen (4.7.0).
  3. *`NvOptimusEnablement` / `AmdPowerXpressRequestHighPerformance`*: exported symbols of the `.exe`; jpackage's
     launcher is prebuilt, so impossible without a launcher of the app's own.
  4. *ANGLE's `EGL_ANGLE_power_preference` (`EGL_HIGH_POWER_ANGLE`)*: needs skiko's native `createAngleDevice` to pass
     it; an upstream skiko request.

  **Recommended:** no silent override. File the skiko request (4), and say it where a slow Windows machine looks:
  `StartupLog.kt` already prints the adapter; add the adapter's kind to that line if skiko exposes it. Option 1 only if
  the owner decides the gain is worth overriding the user's setting, with an opt-out.
- **(b) GC pauses land in transition frames, and ZGC (step 4/5) removes them** without a start or memory cost the
  owner minds: add `-XX:+UseZGC`, `-XX:+ZGenerational` to `compose.desktop.application.jvmArgs` beside
  `-XX:-DontCompileHugeMethods` (every platform's package, since the cost is the same JVM's), note why in a comment and
  in `app/desktop/CLAUDE.md`'s Packaging, and compare `Started in` and the resident set before and after (the CDS
  archive is recorded per collector: `recordClassDataArchive` must train with the same flags, which it does by running
  the image's own launcher). If `-XX:MaxGCPauseMillis=5` on G1 does as well, prefer it (smaller change).
- **(c) Neither**: the long frame is the app's own work, and plans 07–14 are the fix; the step 5 table says how far
  this laptop is from the Mac's numbers and which scenario to start with.

### Results (fill in)

| Step | Setting | Adapter / GC | Long frames per 10 actions | Worst frame | Notes |
|---|---|---|---|---|---|
| 1 | as installed | | | | panel Hz: |
| 3a | ANGLE, default | | | | |
| 3b | High performance | | | | |
| 3c | DIRECT3D discrete / integrated | | | | |
| 3d | OPENGL | | | | |
| 3e | 60 Hz | | | | |
| 4 | G1 / ZGC / G1 5 ms | | | | pauses > 4 ms: |
| 5 | benchmark --hz 144 | | | | |

## Tests

None for the investigation. A branch (b) change is a build flag: verify with the step 4 log that the collector is
ZGC (`StartupLog`'s GC names) and that `recordClassDataArchive` still writes `campfire.jsa` (no "archive mismatch"
warning in the log of the first start).

**Measure:** plan 01's transitions suite on the laptop before and after: for (b), the warm worst frame of every
`push:*`, `pop:*`, and `tab:*` scenario should not grow and `warm GC ms` (pause beans only, plan 01) should drop toward zero.

## Manual check

Everything in this plan is the manual check, on the owner's laptop; after a branch (a) or (b) change, the same routine
on the installed MSI of the next build, and one start on a Windows machine with only an integrated GPU, to see nothing
regressed there.
