<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: smooth transitions on Windows

**Status:** not started. Carried out by an agent alone, in Claude Code on the owner's Windows laptop (Android Studio's
terminal), started with *"Follow `documentation/plans/performance.md`."* When every step is done or decided, the agent
deletes this file and its line in `ROADMAP.md`: nothing else about performance is open.

The goal: the release on Windows shows no dropped frames when a song opens or closes, a tab changes, or a sheet opens.
The release drops them today on the owner's high-end laptop, worst when opening song details. `ui/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, as in the root `CLAUDE.md`.

## Where things stand

- `./gradlew :tools:screenshots:benchmark --args="--suite transitions"` replays every transition offscreen and times
  each frame, split into compose, layout, and draw (`tools/screenshots/CLAUDE.md`; `--hz`, `--runs`, `--csv`, and
  `-Pcampfire.benchmark.jvmArgs="…"` for JVM options). It runs ~5 minutes and must have the machine to itself: another
  Gradle build beside it skews the numbers by up to 2×, so compare only runs taken back to back.
- A transition is one long frame (the second after its trigger), then frames under 1 ms. On an M-series Mac, warm:
  pushes 30–55 ms, pops 40–50 ms, tab switches 40–50 ms, the filter sheet 13–35 ms; 70–85 % of it layout (the incoming
  screen composed inside the pager's or the grid's measure). At 120–165 Hz that is several lost frames on the CPU alone.
- Open lead: the pushes' frame 2 is 5–9 ms longer than with the chrome composed into covered screens
  (`ui/chrome/ChromeLayers.kt`, `ChromeUnderCardTest`), while their frame 1 is 10–18 ms shorter. Step 4.
- Unknown: what the laptop adds — which GPU ANGLE draws on, its renderer, and garbage collection pauses. Steps 2–3.
- Decision D2 (preferring the discrete GPU) and D1 (keeping the covered list composed) wait for numbers. Steps 3 and 7.

## How to work on this machine

- **Shell.** Claude Code's Bash tool on Windows is Git Bash. Run PowerShell as `powershell.exe -NoProfile -Command "…"`
  (or a `.ps1` in `PERF` run with `-File`). `PERF` is `%USERPROFILE%\campfire-perf`, where every script, log, CSV, and
  recording goes; nothing from it is committed.
- **JetBrains Runtime 21.** Release tasks refuse any other JDK (`app/desktop/build.gradle.kts`, `toolchainLauncher`).
  Use Android Studio's own: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` (check the folder; it may
  be under `%LOCALAPPDATA%\Programs` or JetBrains Toolbox). If a release task fails for missing `jmods` or `jpackage`,
  ask the owner to install the JBR 21 SDK (`jbrsdk-21…-windows-x64`); never download a runtime without asking.
- **Never touch the owner's own Campfire.** The Store build's data is
  `%LOCALAPPDATA%\Packages\<family>\LocalState`: read its `campfire.log`, nothing else; never start or stop it. Every
  driven run starts the release image with `APPDATA="$PERF/appdata/<run>"`, so its data, log, and single-instance lock
  live there (`desktopDataDirectory()` reads `APPDATA`).
- **At the start**, ask the owner once (`AskUserQuestion`) to keep the laptop plugged in, unlocked, and otherwise idle
  for about three hours, and which display is the laptop's panel if several are connected. A driven run that produces
  no frames (screen locked, window minimized, display asleep) is a reason to stop and ask, not to retry.
- **Keep the display awake** without changing a setting: once, in the background,
  `powershell.exe -NoProfile -Command "Add-Type -Name P -Namespace W -MemberDefinition '[DllImport(\"kernel32.dll\")] public static extern uint SetThreadExecutionState(uint f);'; [W.P]::SetThreadExecutionState(0x80000003); Start-Sleep -Seconds 14400"`;
  kill it at the end.
- **Repository rules** hold as everywhere: the `code-style` skill before any edit, the `commit-messages` skill before
  any commit, one change one commit, staged by path, the pre-commit hook (`git config core.hooksPath .githooks`) and
  `./gradlew :tools:screenshots:test` for anything in `:presentation`; a desktop build change also
  `:app:desktop:createReleaseDistributable`. A change kept only if the numbers show a gain. Never push.

## 1. Let the benchmark build on Windows

`tools/screenshots/build.gradle.kts` finds `:presentation`'s classes for `-Xfriend-paths` with
`it.absolutePath.contains("/presentation/build/")`, which a Windows path never matches, so the tool cannot reach
`app.navigator` and does not compile. Use `it.invariantSeparatorsPath.contains("/presentation/build/")`, check with
`./gradlew :tools:screenshots:compileKotlin`, commit. Skip if it already compiles.

## 2. Measure the laptop

### 2.1 The machine and the shipped build, read only

Into the Results notes:
`Get-CimInstance Win32_VideoController | Select Name, AdapterCompatibility, DriverVersion, CurrentRefreshRate, MaxRefreshRate, CurrentHorizontalResolution, CurrentVerticalResolution`;
the CPU (`Win32_Processor`), RAM, `Get-CimInstance Win32_Battery | Select BatteryStatus` (2 = on AC; stop and ask
otherwise), `powercfg /getactivescheme`; `reg query "HKCU\Software\Microsoft\DirectX\UserGpuPreferences"`; and from the
Store build's newest `campfire.log`, the `Started in` line and the last `Renderer info` block (the adapter it draws on).

### 2.2 An on-screen driver

Only the real window shows what reaches the panel. A driver inside the release replays the benchmark's scenarios and
records when each frame arrives. It is never committed; revert it before every commit.

1. In `app/desktop/build.gradle.kts`, add the friend-path block of `tools/screenshots/build.gradle.kts` (with step 1's
   filter), so `app/desktop` may reach `navigator` and `dialogHost`. (Step 5 commits this block for good; after it,
   the driver needs only the next two edits.)
2. Add `app/desktop/src/main/kotlin/com/pandulapeter/campfire/PerfRoutine.kt` (license header as every file), and in
   `CampfireDesktopApplication.kt` call `PerfRoutineEffect(viewModel = currentViewModel, exit = exit)` right after
   `SideEffect { viewModel.value = currentViewModel }`:

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

   Check the names against `tools/screenshots/.../TransitionBenchmark.kt`, which triggers the same scenarios, and
   adapt them if they moved.
3. Build the image as the Store package is built: `./gradlew :app:desktop:recordClassDataArchive` (the release image
   with ANGLE's DLLs and the trained `campfire.jsa`), at
   `app/desktop/build/compose/binaries/main-release/app/Campfire/Campfire.exe`. Variants below change only the
   environment, so one build serves them all.
4. **A run**: a fresh `PERF/appdata/<run>/Campfire` holding `tools/screenshots/library`'s `library` and `covers`, and
   its `preferences/preferences.json` with `language` `"en"`, `isSongLayoutChosen` and `isChordNamesChosen` `true`, and
   `campfire.versionName` (`gradle.properties`) added to `seenWhatsNewVersions` (as `prepareTransitionLibrary` does;
   a PowerShell `ConvertFrom-Json` / `ConvertTo-Json -Depth 20` script), then

   ```bash
   APPDATA="$PERF/appdata/<run>" JAVA_TOOL_OPTIONS="-Dcampfire.perfRoutine=$PERF/out/<run>.csv <variant options>" \
     timeout 600 "app/desktop/build/compose/binaries/main-release/app/Campfire/Campfire.exe"
   ```

   Nothing else on screen meanwhile. Keep the run's `campfire.log` (`Started in`, `Renderer info`) beside the CSV.
5. **Read a run** with a script over the CSV: the frame period is the median `idle` interval; per scenario over passes
   3–10, the worst interval (median over passes), the **dropped frames** (the window's sum of
   `round(interval / period) - 1`, median over passes), and the passes with a GC (`gcCount > 0`) and their `gcMs`.
   Pass 1's `sheet:open` is the first sheet of a process (step 5). Repeat a run that looks disturbed.

### 2.3 Which GPU and renderer

- **gpu-a** as shipped (ANGLE, default adapter).
- **gpu-b** ANGLE on the discrete GPU: `reg query` the value named after the image's full `Campfire.exe` path under
  `HKCU\Software\Microsoft\DirectX\UserGpuPreferences` and keep it; `reg add … /v "<path>" /t REG_SZ /d "GpuPreference=2;" /f`;
  run; check `Renderer info` names the discrete adapter; restore the old value or `reg delete … /v "<path>" /f`.
- **gpu-c** `SKIKO_RENDER_API=DIRECT3D` with `-Dskiko.directx.gpu.priority=discrete`, then `integrated`.
- **gpu-d** `SKIKO_RENDER_API=OPENGL`.

### 2.4 Garbage collection

On gpu-a's renderer, `JAVA_TOOL_OPTIONS` adding `-Xlog:gc*:file=$PERF/out/<run>-gc.log:uptime,level,tags` and: **gc-a**
nothing else (G1), **gc-b** `-XX:+UseZGC -XX:+ZGenerational`, **gc-c** `-XX:MaxGCPauseMillis=5`. Read pauses over 4 ms
and the transitions with a GC in their window. (The archive is recorded with G1, so gc-b starts without it; only the
start time suffers.)

### 2.5 The app's own cost on this CPU

`./gradlew :tools:screenshots:benchmark --args="--suite transitions --hz <panel rate>"`, then with
`-Pcampfire.benchmark.jvmArgs="-XX:+UseZGC -XX:+ZGenerational"`.

### 2.6 A profile, if 2.3–2.5 leave the cause open

The benchmark already runs on the full JBR, which has `jdk.jfr`:
`-Pcampfire.benchmark.jvmArgs="-XX:StartFlightRecording=filename=$PERF/out/bench.jfr,settings=profile"`. Read it with
the JBR's `jfr`: `jfr view hot-methods`, `jfr view gc-pauses`, `jfr view compiler-statistics`, and
`jfr print --events jdk.ExecutionSample --stack-depth 30` aggregated by top Campfire frame. The release image has no
`jdk.jfr` and must keep it that way.

## 3. Fix what the laptop shows

- **The integrated GPU draws as shipped, and gpu-b or gpu-c-discrete drops clearly fewer frames.** Name the adapter's kind in
  `StartupLog.kt`'s line if skiko exposes it, and write the text of a skiko request for ANGLE's
  `EGL_ANGLE_power_preference` into the report (the owner files it; filing is outward-facing). Then ask the owner **D2**
  with the numbers: (1) the app writes the per-app GPU preference value from `main` where none exists (overrides a
  user's choice silently; the Store build's path changes per version), (2) skiko's Direct3D 12 with
  `skiko.directx.gpu.priority=discrete` (gives up ANGLE's driver robustness, why ANGLE was chosen), or (3) nothing more.
  Recommended: (3). Carry out what the owner picks, measured with the driver.
- **GC pauses land in transitions, and gc-b or gc-c removes them.** Prefer gc-c if it does as well. Add the flags to
  `compose.desktop.application.jvmArgs` beside `-XX:-DontCompileHugeMethods`, with the why in a comment and in
  `app/desktop/CLAUDE.md`'s Packaging; rebuild with `recordClassDataArchive`; rerun gpu-a with the driver to show the gain
  and that the log has no archive mismatch warning; commit.
- **Neither**: the long frames are the app's own work; go on.

## 4. The pushes' second frame

`ChromeLayers.kt` (commit `17eb050a2`) draws the navigation chrome into a screen under a card from a recorded layer instead of composing a
copy. With it, the pushes' frame 1 fell 10–18 ms and their frame 2 grew 5–9 ms (`push:long`, `push:setlist`,
`push:tabs` most). On this machine, idle: three benchmark runs with it and three without (revert its commit in a
scratch worktree, `git worktree add --detach`), interleaved, and the driver's dropped frames for gpu-a both ways. If the
total of frames 1 and 2 and the dropped frames are no better with it, find what moved into frame 2 (the `--csv`
compose / layout / draw split, then a JFR of the push) and fix that; if no fix holds, revert the commit with
`git revert` and say why in the report. Keep it if it wins.

## 5. The first sheet of a desktop process

The first sheet opened in a process costs ~90–130 ms (classes loaded, code interpreted), because the class archive's
training run (`TrainingRun.kt`, `recordClassDataArchive`) ends once the demo library is drawn, before any sheet.

1. Make `app/desktop` a friend of `:presentation` for good: the friend-path block of step 2.2, with a comment saying
   it is for the training run.
2. Hand `TrainingRunEffect` the view model. After the demo library is drawn: a training run is a first run, so the
   onboarding is on top; leave it through the navigator the way its skip does. Then open one demo song and go back,
   then open the Preferences sheet (`DialogType.Preferences`, always available) and close it, each through the view
   model with a few frames between, then exit, all inside the run's 15 s bound. Update `app/desktop/CLAUDE.md`'s
   Packaging line on what the training run does.
3. Measure: pass 1's `sheet:open` with the driver, the release built before and after. Keep it only if it falls.

## 6. The filter chips' bounds animation

Every chip of the song filters carries `Modifier.animateBounds(lookaheadScope)`
(`ui/components/songFilters/CollapsibleChipFlow.kt`), which may cost the sheet's first frame its
`RectManager.boundingRectInRoot` share (16 % of its layout on the Mac). Take a JFR of the benchmark (2.6) and look at
`sheet:open`'s samples. Only if `animateBounds` is `boundingRectInRoot`'s caller: hold
`var isPlacementAnimated by remember { mutableStateOf(false) }` in `CollapsibleChipFlow`, turn it on after the first
frame (`LaunchedEffect(Unit) { withFrameNanos { }; isPlacementAnimated = true }`), and apply `animateBounds` only when
on (nothing can move in the first frame); add a UI test in `tools/screenshots`'s `components/songFilters` package, ``a
filter chip whose count moves it past another travels there`` (paused mid-animation, the chip is between its two
places), and a line in `songFilters/CLAUDE.md`. Otherwise drop it.

## 7. D1: the list kept under song details

Going back rebuilds the whole songs list (Navigation 3 drops the covered entry): the pops' worst frame, 25–45 ms on
the Mac, most of it layout. Keeping the list composed under song details (a `SceneStrategy` keeping it as an overlaid
entry, or the list screens outside `NavDisplay`) would make a pop compose nothing, at the cost of a hidden list
recomposing on library changes, its memory, and card transitions that assume the covered screen leaves. With the
driver's numbers after steps 3–6: if pops still drop frames on this laptop while pushes do not, ask the owner **D1**
(recommend trying it behind the measurements, in its own commit, reverted if it costs more than it saves); otherwise
record that pops do not need it.

## Finish

`git status` shows only committed work: the driver reverted, the registry value restored, the background process
ended. Delete this file and its `ROADMAP.md` line in one commit (`Remove the finished performance plan.`). Report to the
owner: the Results table, what each step committed (hashes) or why it did not, the skiko request text if any, and any
decision still open.

## Results (fill in on the laptop, report them; not committed)

Commit measured: · Panel: · GPUs: · CPU / RAM: · Power: · Store build's adapter:

| Run | Renderer / adapter | GC | Dropped frames, worst scenario | Worst interval (ms) | Transitions with a GC | Notes |
|---|---|---|---|---|---|---|
| gpu-a | ANGLE, default | G1 | | | | |
| gpu-b | ANGLE, high performance | G1 | | | | |
| gpu-c | Direct3D 12, discrete / integrated | G1 | | | | |
| gpu-d | OpenGL | G1 | | | | |
| gc-a | as gpu-a | G1, logged | | | | pauses > 4 ms: |
| gc-b | as gpu-a | ZGC | | | | |
| gc-c | as gpu-a | G1, 5 ms | | | | |
| 2.5 | offscreen benchmark | G1 / ZGC | | | | warm worst per scenario: |
| 4 | with / without the recorded chrome | G1 | | | | |
| 5 | first sheet, before / after | G1 | | | | |
