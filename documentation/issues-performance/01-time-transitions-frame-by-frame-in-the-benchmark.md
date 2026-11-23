<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Time every frame of the app's transitions in `:tools:screenshots:benchmark`, split into compose, layout, and draw, on any machine Windows included

**Challenged:** amended — one app per run (pass 2+ are reopens) plus `push:fresh` and `push:setlist` scenarios, which plans 03, 04, 05, and 13 are measured by; GC milliseconds count pause beans only (G1's and ZGC's concurrent beans would make ZGC look worse in plan 02); "between" is the delay's wall-clock overrun, since Windows' thread CPU time moves in ~15.6 ms ticks; tab switches through the view model's one-argument `selectTopLevelDestination`.

**Kind:** tooling  ·  **Severity:** high  ·  **Platforms:** all (the benchmark runs on the desktop JVM)  ·  **Run on:** any machine
**Files:**
- `tools/screenshots/src/main/kotlin/com/pandulapeter/campfire/screenshots/Benchmark.kt` (option parsing, `--suite`, locale-safe formatting)
- `tools/screenshots/src/main/kotlin/com/pandulapeter/campfire/screenshots/TransitionBenchmark.kt` (new: the scenarios and the run loop)
- `tools/screenshots/src/main/kotlin/com/pandulapeter/campfire/screenshots/PhasedFrame.kt` (new: one frame timed by phase)
- `tools/screenshots/src/main/kotlin/com/pandulapeter/campfire/screenshots/TransitionReport.kt` (new: the table, optional CSV)
- `tools/screenshots/build.gradle.kts` (`benchmark` task: library property, `campfire.benchmark.jvmArgs`)
- `gradle.properties` (`campfire.benchmark.jvmArgs=` default, empty)
- `tools/screenshots/CLAUDE.md` (Benchmark section)

Every other plan in this folder is measured by this one, so it lands first.

## Problem

The owner sees dropped frames opening and closing song details, switching tabs, and opening sheets, worst on a
120–165 Hz Windows laptop (a frame budget of 6–8 ms). The existing benchmark cannot show any of it: it times what a user
*waits for* (start, a long song until drawn, PDF, import) as wall-clock totals, e.g. `Benchmark.kt:65-68`:

```kotlin
val opened = System.nanoTime()
app.viewModel.navigator.openSong(longSong)
app.awaitFrame { app.scope.centersOf(BenchmarkLibrary.LONG_SONG_FIRST_LINE).isNotEmpty() }
record("Long song opened until drawn", millis(since = opened))
```

A scratchpad probe of the 59th review (not in the repo) showed what is needed instead: every warm transition is one
long frame and then frames under 1 ms (push to song details 25–45 ms, pop 33–39 ms, tab switch 29–37 ms, the filter
sheet 14 ms warm and 132 ms the first time in a process), and only a per-frame split (compose / layout / draw, bytes
allocated) says which plan helps. The probe timed `ImageComposeScene`'s phases by reflection on its private fields:

```kotlin
private val frameRecomposer = scene.field("frameRecomposer")
private val performFrame = frameRecomposer.javaClass.getMethod("performFrame", Long::class.javaPrimitiveType)
private val composeScene = scene.field("scene")
private val measureAndLayout = sceneClass.getMethod("measureAndLayout")
private val draw = sceneClass.getMethod("draw", androidx.compose.ui.graphics.Canvas::class.java)
```

Two things in the current benchmark also break on the owner's Windows laptop: numbers are formatted with the default
locale (`"%.2f s".format(...)`, `Benchmark.kt:240`; a Hungarian Windows writes `0,25 s`), and the JVM flags of a run
cannot be changed without editing the build (`build.gradle.kts:129-136` hard-codes `-XX:-DontCompileHugeMethods`), which
plan 02 needs for its garbage collector comparison.

## Fix

Add a transitions suite to the existing benchmark, built from the probe's frame timer and scenario list, without its
stack sampler, JFR, or composition tracer (those were for finding causes; this is for measuring a fix, and the sampler
alone doubles the run time). Keep the existing measures as they are.

1. **Entry point.** `Benchmark.kt`'s `main` gains `--suite library|transitions|all` (default `all`). `library` is what
   runs today; `transitions` is the new suite. Options stay `--key value` pairs: `--runs` (default 10 for transitions:
   pass 1 cold, passes 3+ warm), `--hz` (default 60; the frame clock's step, so `--hz 144` lets the animation clock
   advance as the owner's panel would), `--width 2400 --height 1500 --density 1.5` (a 16" laptop at 150 %, the probe's),
   and `--csv <path>` (one row per frame; nothing is written without it). Set `Locale.setDefault(Locale.ROOT)` first
   thing in `main` and format every number with an explicit `Locale.ROOT`, the library table's too.
2. **Library.** Use the committed `tools/screenshots/library` (328 songs, covers, tags, languages, setlists), copied
   the way `Render.kt`'s `prepareLibrary` copies it, with the preferences patched as the probe did (`language` English,
   current version in `seenWhatsNewVersions`, `isSongLayoutChosen`, `isChordNamesChosen`). Reason: the costs being
   measured come from what real cards carry (label rows that overflow, covers, keys, tempo, several tags in the filter
   sheet), which the generated `BenchmarkLibrary` (3,000 plain songs, no covers, no labels) does not have, and the song
   kinds needed are in it. The generated library stays for the library suite, whose figures are about size. The task
   gets `systemProperty("campfire.screenshots.library", file("library").absolutePath)`.
3. **Scenarios** (fixed names, which the other plans quote), each started from the settled Songs list, 50 frames after
   the trigger, a settle of 40 frames between (60 before a pop, so a song's chord diagrams and notation have arrived):
   - `push:plain` / `pop:plain` — `ed_sheeran-thinking_out_loud.cho`
   - `push:long` / `pop:long` — `tina_turner-proud_mary.cho`
   - `push:tabs` / `pop:tabs` — `traditional_american-house_of_the_rising_sun.cho`
   - `push:abc` / `pop:abc` — `john_newton-amazing_grace.cho` (the committed library's song with an `abc` block)
   - `tab:songs>setlists`, `tab:setlists>songs` — `viewModel.selectTopLevelDestination(destination)` (the view
     model's one-argument overload; the navigator's takes more)
   - `sheet:open`, `sheet:close` — `dialogHost.showDialog(DialogType.SongFilters)` / `dismissDialog()`
   - `push:setlist` / `pop:setlist` — a song in the middle of one of the committed library's setlists, through
     `navigator.openSongInSetlist(setlistWithSongs, song)`, so both neighbour pages are read and built during the push
     (plans 05 and 13 are measured by it)
   - `push:fresh` / `pop:fresh` — each pass opens a long song this process has not opened before (the next of a fixed
     list of the library's longest songs), so its warm figure is a first open with a warm JIT: plans 03 and 04 only
     help a reopen, and without this the reopen scenarios would hide what a first open still costs

   **One app for the whole run.** Every pass runs in the same app start (unlike the library suite, which starts anew
   for each measure): pass 1 is the cold first open, passes 2+ reopen the same songs, which is what plans 03 and 04
   speed up. Say so in the report's footer. (Plan 04's holder is process-wide and plan 03's cache lives in the view
   model; a fresh app per pass would keep one and drop the other, and the figures would mean different things.)

   Triggers go through the view model as the probe's did (`navigator.openSong(song)`, `navigator.popBackStack()`), so a
   run does not depend on where a card is on screen.
4. **Frame timer** (`PhasedFrame.kt`). One `ImageComposeScene` (not `DeviceScreen`, whose second scene for system
   chrome would add its own cost), impersonating `ImpersonatedPlatform.WINDOWS` with `SystemChrome.None`, on
   `Dispatchers.Main`. A frame = `performFrame(nanoTime)` (compose: clock, recomposition, apply, effects) + `ComposeScene
   .measureAndLayout()` (layout, lazy subcomposition included) + `ComposeScene.draw(canvas)` into a Skia
   `PictureRecorder` (draw, recording only, as a GPU window issues commands), each timed with `System.nanoTime()`; then
   `delay(1000 / hz)`, and "between" is how much later than asked that delay resumes (wall clock: the app's coroutines
   on Main holding the thread). Not the main thread's CPU time: on Windows `getCurrentThreadCpuTime` moves in scheduler
   ticks of ~15.6 ms, so a 6 ms budget could not be judged with it. Per frame: main-thread bytes allocated
   (`com.sun.management.ThreadMXBean.getCurrentThreadAllocatedBytes`, exact on every OS) and garbage collection pause
   milliseconds: the `collectionTime` deltas of the **pause** beans only (`G1 Young Generation`, `G1 Old Generation`,
   `ZGC … Pauses`, `Shenandoah Pauses`; in general any bean whose name says neither `Cycles` nor `Concurrent`).
   `G1 Concurrent GC` and `ZGC … Cycles` report concurrent work that never stops the main thread; counting them would
   make ZGC look worse than G1 in plan 02's comparison, the opposite of what they do to frames. The reflection names private fields of CMP
   1.13; if any lookup fails, fall back to timing `scene.render(nanoTime)` as one figure and print once that the split
   is unavailable, instead of failing the run.
5. **Report** (`TransitionReport.kt`), printed, one row per scenario:
   `cold worst` (pass 1: worst frame, its index, compose/layout/draw), `warm trigger frame` (median over passes 3+ of
   the worst frame, its usual index, compose/layout/draw split, MB allocated), `warm frames over budget` (median count of
   frames whose main + between exceeds `1000 / hz` ms), `warm GC ms` per pass. Then a line with machine, JVM, garbage
   collector names, max heap, window, density, and `hz`, and the probe's note that draw is recording only: a real window
   also rasterizes on the GPU and presents, which this does not see.
6. **JVM flags.** `gradle.properties` gets `campfire.benchmark.jvmArgs=` (empty), and the task appends its
   space-separated words after `-XX:-DontCompileHugeMethods`, read with `project.property(...)` outside the task action
   (configuration cache). Plan 02 runs `-Pcampfire.benchmark.jvmArgs="-XX:+UseZGC -XX:+ZGenerational"` with it.
7. **Size limits.** `Benchmark.kt` is 258 lines; the suite goes into the three new files (each well under 500 lines, no
   function over 120), and `main` only dispatches.

`tools/screenshots/CLAUDE.md`'s Benchmark section: add a paragraph for the transitions suite (what a frame is, the
scenario names, cold vs. warm, "frames over budget" at `--hz`, the committed library and why, `--csv` as the one thing
it may write, `campfire.benchmark.jvmArgs`, and that the phase split reads private fields of `ImageComposeScene`:
re-check after every Compose update). Windows: `gradlew.bat :tools:screenshots:benchmark --args="--suite transitions"`.

## Tests

None: the figures are the machine's, so nothing asserts them, as for the library suite. Before committing, compile
(`./gradlew :tools:screenshots:compileKotlin`) and run `--suite transitions --runs 2` once to see every scenario reach
its trigger frame and the table print; the UI tests are untouched.

**Measure:** this plan is the measure. Record a baseline of the tree before plans 07–14 land (`--runs 10`, `--hz 60`
and `--hz 144`) in the README of this folder or the plan that uses it, and expect, on an M-series Mac, the probe's
order of magnitude: warm worst frame 25–45 ms for pushes and pops, 29–37 ms for tab switches, ~14 ms for `sheet:open`.

## Manual check

Run it once on the owner's Windows laptop (plan 02 step 5) to confirm the reflection lookups resolve on that JBR and
that the table prints dots, not commas.
