# Move MetronomeImpl's logic into an internal MetronomeEngine that takes its scope, dispatcher and silent output, and test it

**Challenged:** amended — step 4 follows the build pass that moves `kotlin("test")` into the convention plugin; step 3 keeps `::SilentAudioOutput` a valid `(CoroutineScope) -> AudioOutput` reference; the committed Android startup profile names `MetronomeImpl` methods, noted as stale-not-wrong.

**Kind:** testability  ·  **Severity:** medium  ·  **Effort:** M  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `metronome/implementation/src/commonMain/.../MetronomeImpl.kt` (`MetronomeImpl`); new `MetronomeEngine.kt`; `SilentAudioOutput.kt` (`SilentAudioOutput`, its `TimeSource.Monotonic` and `Dispatchers.Default`); `metronome/implementation/build.gradle.kts` (commonTest dependency `libs.kotlin.coroutines.test`); new `metronome/implementation/src/commonTest/.../MetronomeEngineTest.kt`, `FakeAudioOutput.kt`, optionally `SilentAudioOutputTest.kt`; `metronome/implementation/CLAUDE.md` (Tests line); root `CLAUDE.md` (the "Only pure logic is tested" list names `:metronome:*`' tested classes)
**Depends on:** none

## Problem

`MetronomeImpl` holds the whole state machine of the click — start, restart while playing, refusal, the fallback to a
silent output, previews holding the output for 1.5 s, stale-session listener callbacks, releasing beats as the output's
playback position passes them — and none of it is tested, because the class builds its own collaborators:

```kotlin
@Single
internal class MetronomeImpl(private val output: AudioOutput) : Metronome {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val confined = Dispatchers.Default.limitedParallelism(1)
    private val silentOutput = SilentAudioOutput(scope)
```

and `SilentAudioOutput` in turn reads `TimeSource.Monotonic` and launches on `Dispatchers.Default`. A test cannot drive
virtual time, cannot observe the Unavailable path without a real clock, and the 5 ms polling loop in
`releaseHeardBeats` never idles. The stream factory is also written out three times
(`{ ClickStream(it, pattern).also { stream -> this.stream = stream } }` twice in `start`, once with `pattern = null` in
`preview`).

## Fix

1. **Keep the Koin-facing class exactly as Koin sees it** and move the body into a new internal class:
   ```kotlin
   internal class MetronomeEngine(
       private val output: AudioOutput,
       private val scope: CoroutineScope,
       dispatcher: CoroutineDispatcher,
       createSilentOutput: (CoroutineScope) -> AudioOutput = ::SilentAudioOutput,
   ) : Metronome {
       private val confined = dispatcher.limitedParallelism(1)
       private val silentOutput = createSilentOutput(scope)
       …everything MetronomeImpl has today…
   }

   @Single
   internal class MetronomeImpl(output: AudioOutput) : Metronome by MetronomeEngine(
       output = output,
       scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
       dispatcher = Dispatchers.Default,
   )
   ```
   Delegation rather than a secondary/`internal` constructor on `MetronomeImpl`, so there is no question which
   constructor the Koin compiler plugin resolves: `MetronomeImpl` keeps its one primary constructor `(AudioOutput)`.
   Verify after building that the generated module still calls it with one `get()` — `javap -c -p` the generated
   `MetronomeModule` class under `metronome/implementation/build/classes/kotlin/desktop/main` (see the root
   `CLAUDE.md`'s Koin section); `./gradlew :app:di:compileKotlinDesktop` also re-runs the plugin's graph check. The
   shells (`CampfireMetronomeService`, `IosMetronomeNotifier` via `CampfireViewController`) ask Koin for `Metronome`,
   not `MetronomeImpl`, so they need no change. `app/android/src/main/generated/baselineProfiles` lists
   `MetronomeImpl`'s methods; after this the work is in `MetronomeEngine`, which the profile does not name — less
   useful, never wrong; the prepare-release skill records the profile again before the next release.

2. **Name the stream factory once** inside the engine: `private fun streamFor(pattern: MetronomePattern?): (Int) -> ClickStream = { rate -> ClickStream(rate, pattern).also { stream = it } }`,
   used at all three call sites. Same commit as step 1 or its own.

3. **Let `SilentAudioOutput` take its clock and dispatcher**: `SilentAudioOutput(scope, timeSource: TimeSource = TimeSource.Monotonic, dispatcher: CoroutineDispatcher = Dispatchers.Default)`;
   `startMark` becomes a `TimeMark?`. Behaviour is unchanged for the default arguments. (It is not a Koin definition, so
   default arguments are fine here.) The engine's `createSilentOutput: (CoroutineScope) -> AudioOutput = ::SilentAudioOutput`
   still compiles after this, since a callable reference may leave defaulted parameters out; if the compiler on any
   target refuses it, write the default as `{ SilentAudioOutput(it) }`.

4. **Add `libs.kotlin.coroutines.test`** to `metronome/implementation`'s `commonTest.dependencies` (it is already in the
   catalog and used by five modules) and write the tests below. If the build pass that moves `kotlin("test")` into the
   `campfire-library` convention plugin has landed, the module's `commonTest` block holds only this line; otherwise
   add it next to the existing `implementation(kotlin("test"))`.

5. Update `metronome/implementation/CLAUDE.md`'s Tests line and the root `CLAUDE.md` sentence listing what
   `:metronome:*` tests ("the sequencer, the synthesizer, the mixer, tap tempo and time signatures") to add the engine.

## Tests

`MetronomeEngineTest` with `runTest`, the engine built with `scope = backgroundScope` and
`dispatcher = StandardTestDispatcher(testScheduler)` (backgroundScope's coroutines are cancelled when the test ends, so
the endless 5 ms polling loop does not hang `runTest`; drive time with `advanceTimeBy` / `runCurrent`, never
`advanceUntilIdle`). A `FakeAudioOutput` records `start` calls, returns a configurable `AudioOutputStart`, captures the
`ClickStream` its `createStream(48_000)` builds and the `AudioOutputListener`, and exposes a settable `heardFrame`. Cases:

- `start` with `Started` → `playback` is `Playing(pattern, null)`; with `Started(WAITING_FOR_GESTURE)` the issue is kept.
- `start` with `Refused(reason)` → `Stopped(reason)`, no beats.
- `start` with `Unavailable` → the injected silent output (a second fake) is started, `Playing(pattern, UNAVAILABLE)`.
- `start` while playing → no second `output.start`, the stream gets `update(pattern, restartBar = true)`.
- `update` with an equal pattern and `restartBar = false` is a no-op.
- `listener.onLost(reason)` while playing → `Stopped(reason)`; the same call from a listener of an earlier session
  (captured before a `stop()` + `start()`) changes nothing; `onAudioIssueChanged` likewise ignores a stale session.
- beats: after `stream.schedule(0, 48_000) { … }` on the captured stream, no `MetronomeBeat` is emitted until
  `heardFrame` passes a tick's frame and 5 ms of virtual time elapse; then they come in order.
- `preview` while stopped opens the output with `isPreview = true`, a second preview within 1.5 s does not reopen it,
  and the output is stopped 1.5 s after the last preview.
- `stop` stops the active output and leaves `Stopped(null)`.

Optionally `SilentAudioOutputTest` with a `TestTimeSource`: `heardFrame` is `elapsed µs × 48_000 / 1_000_000`, `-1`
after `stop`.

Run `./gradlew :metronome:implementation:desktopTest :app:di:compileKotlinDesktop`.

## Manual check

Start and stop the click once on the desktop build (`./gradlew :app:desktop:run`, Metronome tab) to confirm the Koin
graph still resolves `Metronome` at runtime; the rest is covered by tests.
