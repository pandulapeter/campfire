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

**Challenged:** amended — the Store MSIX is all that ships (no MSI); options go in `JAVA_TOOL_OPTIONS`; skiko property
names re-checked in the 0.156.1 jar. **Rewritten** (2026-11-24) to be carried out by an agent alone, in Claude Code on
the Windows laptop (Android Studio's terminal): every manual step is now a script, a driven run, or a registry value
the agent sets and puts back.

**Kind:** performance  ·  **Severity:** high  ·  **Platforms:** Windows  ·  **Run on:** the owner's Windows laptop
**Files:**
- this plan (its Results table is filled in and committed)
- `tools/screenshots/build.gradle.kts` (step 0)
- depending on the branch taken: `app/desktop/src/main/kotlin/com/pandulapeter/campfire/CampfireDesktopApplication.kt`,
  `app/desktop/src/main/kotlin/com/pandulapeter/campfire/StartupLog.kt`,
  `app/desktop/build.gradle.kts` (`compose.desktop.application.jvmArgs`), `app/desktop/CLAUDE.md` (Packaging, Log)
- never committed: the driver of step 2 and its build change, the scripts, logs, recordings, and CSVs (all under
  `%USERPROFILE%\campfire-perf`, called `PERF` below)

## How to work on this machine

- **Shell.** Claude Code's Bash tool on Windows is Git Bash. Run PowerShell as
  `powershell.exe -NoProfile -Command "…"` (or write a `.ps1` into `PERF` and run it with `-File`), the build as
  `./gradlew …` from the checkout. Paths handed to Java use forward slashes or doubled backslashes.
- **JetBrains Runtime 21.** Release tasks refuse any other JDK (`app/desktop/build.gradle.kts`, `toolchainLauncher`).
  Use Android Studio's own: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` (check the folder; it may
  be under `%LOCALAPPDATA%\Programs` or JetBrains Toolbox). If a release task then fails for missing `jmods` or
  `jpackage`, stop and ask the owner to install the JBR 21 **SDK** (`jbrsdk-21…-windows-x64`, what `setup-java` with
  `distribution: jetbrains` installs in `publish-windows.yml`); never download a runtime without asking.
- **Never touch the owner's own Campfire.** The Store build keeps its library in
  `%LOCALAPPDATA%\Packages\<family>\LocalState`: read its `campfire.log` (step 1), nothing else, and never start or
  stop it. Every driven run starts the release image with `APPDATA="$PERF/appdata/<run>"`, so its data, its log, and
  its single-instance lock live there (`desktopDataDirectory()` reads `APPDATA`; the image is not the Store build, so
  the package family property is unset).
- **What only the owner can do**, asked once at the start with `AskUserQuestion`: keep the laptop plugged in, unlocked,
  and with nothing heavy running for the session (about 2 hours), and confirm which display is the laptop's panel if
  more than one is connected. If a driven run produces no frames (the screen locked, the window minimized, or the
  display asleep), stop and ask rather than retry blindly.
- **Keep the display awake** for the session without changing a setting: start once, in the background,
  `powershell.exe -NoProfile -Command "Add-Type -Name P -Namespace W -MemberDefinition '[DllImport(\"kernel32.dll\")] public static extern uint SetThreadExecutionState(uint f);'; [W.P]::SetThreadExecutionState(0x80000003); Start-Sleep -Seconds 10800"`
  (display and system required until that process ends; kill it when done).
- **Put everything back**: the GPU preference value of step 3 (restore what was there, or delete the one you added),
  the driver patch (`git checkout -- app/desktop` and delete `PerfRoutine.kt`), the background process above.
  `git status` must show only this plan and the committed changes at the end.

## Problem

The owner sees dropped frames in 5.5.0's Windows release on a high-end laptop, worst when a song's details open, and
skipped frames in debug transitions too. The offscreen benchmark (plan 01, an M-series Mac) found one long frame per
transition, 25–50 ms at the 59th review's start; lanes S, L, and N have since cut it (see `README.md` and the
commits after `6dea075b4`). On a 120–165 Hz panel (6–8 ms a frame) a 25 ms frame is still three or four lost frames
on the CPU alone. What the offscreen run cannot see is the laptop's own part:

- **Which GPU ANGLE draws on.** `CampfireDesktopApplication.kt` turns ANGLE on unless a renderer was chosen:

  ```kotlin
  if (isWindows && System.getenv("SKIKO_RENDER_API") == null && System.getProperty("skiko.renderApi") == null &&
      File(System.getProperty("skiko.library.path").orEmpty(), "libEGL.dll").isFile
  ) System.setProperty("skiko.rendering.angle.enabled", "true")
  ```

  ANGLE's Direct3D 11 device is created on the default adapter, which on a hybrid laptop is whatever Windows' per-app
  graphics preference says (by default "Let Windows decide", often the integrated GPU). skiko 0.156.1 has
  `skiko.gpu.priority` / `skiko.directx.gpu.priority` (`auto`, `integrated`, `discrete`), but only its Direct3D 12
  renderer reads them; `AngleRenderer` names no priority.
- **Garbage collection.** A transition allocates 9–37 MB on the main thread; the release runs G1, whose young pauses
  can land in the long frame.
- **The log.** `main` sets `skiko.hardwareInfo.enabled`, so `campfire.log` carries skiko's `Renderer info` block (API
  and adapter; under ANGLE the GL_RENDERER string, like `ANGLE (NVIDIA, NVIDIA GeForce RTX … Direct3D11 …)`), and
  `StartupLog.kt` one `Started in … ms: <api> rendering, Java …, <GC names>, N processors, M MB heap at most` line.
- **No profiler in the release.** The image leaves out `jdk.jfr` (`modules(...)` in `app/desktop/build.gradle.kts`),
  and must keep leaving it out; step 6 profiles the same jar on the build's full runtime instead.

## Fix

The investigation first, step by step, each result in the Results table at the end; then the branch it points to.
Keep every raw output in `PERF`. Measure on the checkout's `HEAD` (after lanes S, L, and N), and note the commit.

### 0. Let the benchmark compile on Windows

`tools/screenshots/build.gradle.kts` finds `:presentation`'s output for `-Xfriend-paths` with
`it.absolutePath.contains("/presentation/build/")`, which a Windows path (backslashes) never matches, so the tool cannot
reach `app.navigator` and the benchmark does not compile there. Change the filter to
`it.invariantSeparatorsPath.contains("/presentation/build/")`, run `./gradlew :tools:screenshots:compileKotlin`, and
commit it on its own (`Let the screenshot tool reach the app's internals when it is built on Windows.`). Drop this step
if the compile already passes without it.

### 1. The machine and the shipped build, read only

Record into the table's notes:

- `Get-CimInstance Win32_VideoController | Select Name, AdapterCompatibility, DriverVersion, CurrentRefreshRate, MaxRefreshRate, CurrentHorizontalResolution, CurrentVerticalResolution`
  (one row per GPU: integrated and discrete names, the panel's refresh rate);
- `Get-CimInstance Win32_Processor | Select Name, NumberOfLogicalProcessors`, the RAM
  (`Win32_ComputerSystem.TotalPhysicalMemory`), `Get-CimInstance Win32_Battery | Select BatteryStatus`
  (2 = on AC; stop and ask if not), and `powercfg /getactivescheme`;
- the existing per-app GPU preferences:
  `reg query "HKCU\Software\Microsoft\DirectX\UserGpuPreferences"` (an entry for the Store Campfire says the owner
  already chose one);
- the Store build's log, read only: the newest
  `%LOCALAPPDATA%\Packages\*Campfire*\LocalState\campfire.log` (and `.log.1`): its `Started in` lines and the last
  `Renderer info` block — which adapter the shipped app draws on today.

### 2. The release image with an on-screen driver

The offscreen benchmark times the app's work; only the real window shows what reaches the panel. A driver inside the
app replays the benchmark's scenarios on screen and records every frame's arrival, so no click is needed and the
numbers are comparable run to run. It is **never committed**.

1. In `app/desktop/build.gradle.kts`, add the friend-path block `tools/screenshots/build.gradle.kts` has (with the
   step 0 filter), so `app/desktop` may call `navigator` and `dialogHost`.
2. Add `app/desktop/src/main/kotlin/com/pandulapeter/campfire/PerfRoutine.kt` (license header as every file):

   ```kotlin
   package com.pandulapeter.campfire

   import androidx.compose.runtime.Composable
   import androidx.compose.runtime.LaunchedEffect
   import androidx.compose.runtime.withFrameNanos
   import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
   import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
   import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
   import java.io.File
   import java.lang.management.ManagementFactory
import java.util.Locale
   import kotlinx.coroutines.delay
   import kotlinx.coroutines.launch

   /** Replays the benchmark's scenarios on screen and writes each frame's interval to the file the property names. */
   @Composable
   internal fun PerfRoutineEffect(viewModel: CampfireViewModel, exit: () -> Unit) {
       val output = System.getProperty("campfire.perfRoutine") ?: return
       LaunchedEffect(viewModel) {
           val app = viewModel
           while (app.libraryState.isLoading.value != false || app.songListState.songGroups.value.groups.isEmpty()) delay(100)
           delay(3_000)
           val stamps = ArrayList<Long>(100_000)
           val ticker = launch { while (true) withFrameNanos { stamps += System.nanoTime() } }
           val gc = ManagementFactory.getGarbageCollectorMXBeans()
           fun gcTotals() = gc.sumOf { it.collectionCount } to gc.sumOf { it.collectionTime }
           val rows = StringBuilder("pass,scenario,frame,intervalMs,gcCount,gcMs\n")
           suspend fun window(pass: Int, name: String, millis: Long) {
               val from = stamps.size
               val (count0, time0) = gcTotals()
               delay(millis)
               val (count1, time1) = gcTotals()
               val slice = stamps.subList(maxOf(from - 1, 0), stamps.size).toList()
               slice.zipWithNext { a, b -> (b - a) / 1e6 }.forEachIndexed { index, interval ->
                   rows.append("$pass,$name,${index + 1},${String.format(Locale.ROOT, "%.3f", interval)},${count1 - count0},${time1 - time0}\n")
               }
           }
           window(0, "idle", 3_000)
           val songs = app.libraryState.songsByFileName.value
           repeat(PASSES) { pass ->
               suspend fun scenario(name: String, trigger: () -> Unit) {
                   trigger()
                   window(pass + 1, name, 1_500)
               }
               SONGS.forEach { (name, fileName) ->
                   val song = checkNotNull(songs[fileName]) { "No $fileName in the library." }
                   scenario("push:$name") { app.navigator.openSong(song) }
                   delay(1_000)
                   scenario("pop:$name") { app.navigator.popBackStack() }
               }
               scenario("tab:songs>setlists") { app.selectTopLevelDestination(CampfireDestination.Setlists) }
               scenario("tab:setlists>songs") { app.selectTopLevelDestination(CampfireDestination.Songs) }
               scenario("sheet:open") { app.dialogHost.showDialog(DialogType.SongFilters) }
               scenario("sheet:close") { app.dialogHost.dismissDialog() }
           }
           ticker.cancel()
           File(output).writeText(rows.toString())
           exit()
       }
   }

   private const val PASSES = 10
   private val SONGS = linkedMapOf(
       "plain" to "ed_sheeran-thinking_out_loud.cho",
       "long" to "tina_turner-proud_mary.cho",
       "tabs" to "traditional_american-house_of_the_rising_sun.cho",
       "abc" to "john_newton-amazing_grace.cho",
   )
   ```

   Re-check the names it uses against `tools/screenshots/.../TransitionBenchmark.kt` at `HEAD` (it triggers the same
   scenarios the same way) and adapt them if they moved. In `CampfireDesktopApplication.kt`, right after
   `SideEffect { viewModel.value = currentViewModel }`, call `PerfRoutineEffect(viewModel = currentViewModel, exit = exit)`.
3. Build the image as the Store package is built: `./gradlew :app:desktop:recordClassDataArchive` (it runs
   `createReleaseDistributable`, copies ANGLE's DLLs in, and trains `campfire.jsa`). The image is
   `app/desktop/build/compose/binaries/main-release/app/Campfire/Campfire.exe`. Build it once; every variant below
   only changes the environment.
4. A **run** is: a fresh `PERF/appdata/<run>/Campfire` holding the benchmark's library — `tools/screenshots/library`'s
   `library` and `covers` folders, and its `preferences/preferences.json` with `language` set to `"en"`,
   `isSongLayoutChosen` and `isChordNamesChosen` to `true`, and `campfire.versionName` (from `gradle.properties`) added
   to `seenWhatsNewVersions`, as `prepareTransitionLibrary` does (a PowerShell `ConvertFrom-Json` / `ConvertTo-Json
   -Depth 20` script in `PERF`) — then

   ```bash
   APPDATA="$PERF/appdata/<run>" JAVA_TOOL_OPTIONS="-Dcampfire.perfRoutine=$PERF/out/<run>.csv <variant options>" \
     timeout 600 "app/desktop/build/compose/binaries/main-release/app/Campfire/Campfire.exe"
   ```

   The window has to stay visible and unminimized (do nothing else on screen meanwhile). Afterwards keep
   `PERF/appdata/<run>/Campfire/campfire.log` (its `Started in` line and `Renderer info` name the API, the adapter,
   and the collector actually used) beside the CSV.
5. **Read a run** with a small script (PowerShell, or a Kotlin script run with the JBR) over the CSV: the panel's
   frame period is the median `idle` interval. For each scenario, over passes 3–10: the worst interval (median over
   passes), the **dropped frames** (the sum over the window of `round(interval / period) - 1`, median over passes), and
   how many passes had a GC (`gcCount > 0`) and their `gcMs`. Run each variant twice and keep the second if the first
   was disturbed (a dropped count far above the other variants' everywhere).

### 3. Which GPU and renderer

Runs, each its own `<run>` name, all with the frame counter of step 2:

- **3a** as shipped: no variant options (ANGLE on the default adapter).
- **3b** ANGLE on the discrete GPU: first `reg query` the value named after the image's full `Campfire.exe` path under
  `HKCU\Software\Microsoft\DirectX\UserGpuPreferences` and keep what is there; then
  `reg add "HKCU\Software\Microsoft\DirectX\UserGpuPreferences" /v "<full path of the image's Campfire.exe>" /t REG_SZ /d "GpuPreference=2;" /f`
  (what Settings → Graphics → High performance writes), run, check that `Renderer info` now names the discrete
  adapter, then restore the old value or `reg delete … /v "<path>" /f`. If the adapter did not change, note it and go on.
- **3c** skiko's Direct3D 12: `SKIKO_RENDER_API=DIRECT3D` in the environment with
  `-Dskiko.directx.gpu.priority=discrete` in `JAVA_TOOL_OPTIONS`; then again with `integrated`.
- **3d** OpenGL: `SKIKO_RENDER_API=OPENGL`.

(The old step 3e, the panel at 60 Hz, is left out: changing the display mode by script is not worth the risk on the
owner's machine. The idle period and the dropped-frame counts at the panel's own rate say the same.)

### 4. Garbage collection

On the renderer as shipped (3a), runs with `JAVA_TOOL_OPTIONS` adding:

- **4a** `-Xlog:gc*:file=$PERF/out/4a-gc.log:uptime,level,tags` (G1 as shipped, with the log);
- **4b** that and `-XX:+UseZGC -XX:+ZGenerational`;
- **4c** that and `-XX:MaxGCPauseMillis=5` (G1 kept).

Read from the GC logs the pauses over 4 ms and from the CSV the transitions with a GC inside their window. The class
archive is recorded with G1, so 4b starts without it (a warning in the log): only the start time is affected, which is
not what is measured here.

### 5. The app's own frame cost on this CPU

`./gradlew :tools:screenshots:benchmark --args="--suite transitions --hz <the panel's rate>"`, then again with
`-Pcampfire.benchmark.jvmArgs="-XX:+UseZGC -XX:+ZGenerational"`. No other build may run beside it. Note each scenario's
warm frame 1, warm worst, and over-budget count, beside the Mac's numbers in `README.md` (or in lane reports quoted
there).

### 6. A profile, only if steps 1–5 leave the cause open

The release jar on the build's full JetBrains Runtime, which has `jdk.jfr`, through an init script kept in `PERF`
(`PERF/jfr.init.gradle`):

```groovy
allprojects {
    if (path == ":app:desktop") afterEvaluate {
        tasks.matching { it.name == "runRelease" }.configureEach {
            jvmArgs(
                "-XX:StartFlightRecording=filename=<PERF>/out/campfire.jfr,settings=profile,dumponexit=true",
                "-Dcampfire.perfRoutine=<PERF>/out/6.csv",
            )
            environment("APPDATA", "<PERF>/appdata/6")
        }
    }
}
```

(with the driver patch of step 2 still in the tree, and `<PERF>` written out), then
`./gradlew -I "$PERF/jfr.init.gradle" :app:desktop:runRelease`; the driver ends the run itself. If `runRelease` is not
a `JavaExec` and refuses `jvmArgs`, say so in the report and skip this step. Read the recording with the JBR's own
`jfr` tool: `jfr view gc-pauses`, `jfr view hot-methods`, `jfr view compiler-statistics`, and
`jfr print --events jdk.ExecutionSample --stack-depth 30` filtered to the `AWT-EventQueue` thread, aggregated by its
top Campfire frame. No GUI is needed.

### Then, by what the table shows

Revert the driver patch before any commit (`git checkout -- app/desktop/build.gradle.kts app/desktop/src/main/kotlin/com/pandulapeter/campfire/CampfireDesktopApplication.kt`,
delete `PerfRoutine.kt`), then fill in the Results table and commit it with the plan on its own
(`Record the Windows laptop's frame measurements in the review plan.`).

- **(a) The integrated GPU draws as shipped, and 3b (or 3c discrete) clearly drops fewer frames.** The ways to prefer
  the discrete GPU:
  1. *The app writes the per-app GPU preference* (the 3b registry value) from `main` before the window exists, only
     where no value is there yet: overrides a user's choice silently, and the Store build's path changes with every
     version.
  2. *Skiko's Direct3D 12 with `skiko.directx.gpu.priority=discrete`* where a discrete adapter exists: gives up ANGLE's
     driver robustness, the reason ANGLE was chosen (4.7.0).
  3. *`NvOptimusEnablement` / `AmdPowerXpressRequestHighPerformance`*: exported symbols of the `.exe`; jpackage's
     launcher is prebuilt, so impossible without a launcher of the app's own.
  4. *ANGLE's `EGL_ANGLE_power_preference`*: needs skiko's native `createAngleDevice` to pass it; an upstream request.

  Do on your own only the recommended part: name the adapter's kind in `StartupLog.kt`'s line if skiko exposes it (so
  a slow machine's log says it), and write the text of the skiko request (4) into the report for the owner to file;
  filing it is outward-facing, so do not. Options 1 and 2 are decision D2 in `README.md`: ask the owner with the
  measured numbers, recommending no silent override.
- **(b) GC pauses land in transition windows, and 4b or 4c removes them.** Prefer 4c (G1 kept) if it does as well.
  Add the flags to `compose.desktop.application.jvmArgs` beside `-XX:-DontCompileHugeMethods` with a comment saying
  why, and a line in `app/desktop/CLAUDE.md`'s Packaging; rebuild with `recordClassDataArchive` (the archive is
  recorded per collector and its training run uses the image's own launcher, so the flags reach it); rerun 3a with the
  driver patch back in to show the gain and that the first start's log has no archive mismatch warning; revert the
  patch again; commit (`Collect garbage with … so a transition's frame is not held up by a pause, on every desktop.`),
  with `git rm` of this plan.
- **(c) Neither**: the long frames are the app's own work. Write which scenarios still drop frames and by how much,
  beside the step 5 numbers, `git rm` this plan with the Results committed in `README.md`'s Measurements instead, and
  stop: the next performance work starts from those scenarios.

A branch that changes code follows the repository's rules: the `code-style` skill before the edit, the
`commit-messages` skill before the commit, one fix one commit, `desktopTest` and `spotlessCheck` (the pre-commit
hook runs them), and `:app:desktop:createReleaseDistributable` for a build change. Never push.

### Results (fill in)

Commit measured: · Panel: · GPUs: · CPU / RAM: · Power: · Store build's adapter today:

| Run | Renderer / adapter (from `Renderer info`) | GC | Dropped frames, worst scenario | Worst interval (ms) | Transitions with a GC | Notes |
|---|---|---|---|---|---|---|
| 3a | ANGLE, default | G1 | | | | |
| 3b | ANGLE, high performance | G1 | | | | |
| 3c | Direct3D 12, discrete / integrated | G1 | | | | |
| 3d | OpenGL | G1 | | | | |
| 4a | as 3a | G1, logged | | | | pauses > 4 ms: |
| 4b | as 3a | ZGC | | | | |
| 4c | as 3a | G1, 5 ms | | | | |
| 5 | offscreen benchmark | G1 / ZGC | | | | warm worst per scenario: |

## Tests

None for the investigation. Step 0 is checked by the benchmark compiling on Windows. A branch (b) change is a build
flag, checked by the rerun with the driver (the collector named in `Started in`, no archive warning).

## Report

Back to the owner: the filled table, which branch it pointed to and what was committed (hashes), the skiko request
text if (a), the open decision D2 if (a), and confirmation that the registry, the tree, and the background process
are as they were.
