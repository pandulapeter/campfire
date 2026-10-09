# Catch a failing input start, and never leave the web's microphone tracks live when the graph cannot be built

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (engine); web (the tracks)
**Files:** `tuner/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/implementation/TunerEngine.kt`,
`tuner/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/tuner/implementation/WebTunerAudio.kt`,
`tuner/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/implementation/TunerEngineTest.kt`,
`tuner/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/implementation/FakeAudioInput.kt`,
`tuner/implementation/CLAUDE.md`

Lands after plan 01 (`TunerEngineTest`, `FakeAudioInput`).

## Problem

**The engine.** `AudioInput.start` is documented "never throws", but nothing enforces it, and `TunerEngine.open` lets
an exception out of the coroutine it launches:

```kotlin
job = scope.launch(confined) {
    when (val result = input.start(listenerFor(sessionId))) {
        ...
    }
}
```

`TunerImpl`'s scope is `CoroutineScope(SupervisorJob() + Dispatchers.Default)` with no exception handler, so a throw
goes to the platform's uncaught-exception handler (on Android and the desktop that ends the process) and, where it
does not, the state stays `Starting` for good: `listen` returns early while `Starting`, so the tuner cannot be started
again until the screen is left. Every platform's start catches most of what it calls, but not all of it (Android's
`startRecording` is guarded only for `IllegalStateException`, the thread start and the callback registration not at
all; the web's start awaits a promise that can reject, below).

**The web.** In `openTunerMicrophone` the success handler of `getUserMedia(...).then(ok, err)` builds the graph after
it has already published the stream:

```js
tuner.stream = stream;
tuner.ended = false;
stream.getAudioTracks().forEach(function (track) { track.addEventListener('ended', ...); });
tuner.source = context.createMediaStreamSource(stream);
tuner.analyser = context.createAnalyser();
tuner.source.connect(tuner.analyser);
return 'ok';
```

`.then(ok, err)` does not route a throw inside `ok` to `err`: the returned promise rejects, `await` in
`WebAudioInput.start` throws a `JsException`, and the engine is in the state above — with the stream's tracks live and
the browser's recording indicator lit for a stream nothing reads. `createMediaStreamSource` does throw in practice
(for example a `NotSupportedError` where the stream's sample rate differs from the context's in Firefox, or an
`InvalidStateError` on a closed context).

## Fix

1. `TunerEngine.open` — catch a failing start (rethrowing cancellation, which is how `stopListening` ends a pending
   start):

   ```kotlin
   job = scope.launch(confined) {
       val result = try {
           input.start(listenerFor(sessionId))
       } catch (exception: CancellationException) {
           throw exception
       } catch (_: Throwable) {
           // The contract says a start never throws; one that does is a failed start, and whatever it opened is closed.
           input.stop()
           if (sessionId == session) setListening(TunerListening.Stopped(TunerStopReason.FAILED))
           return@launch
       }
       when (result) { ... as today ... }
   }
   ```

   with `import kotlin.coroutines.cancellation.CancellationException` (common code: not `java.util.concurrent`).
   `Throwable` rather than `Exception`, since on wasm a rejected promise surfaces from `await` as `kotlin.js.JsException`,
   which extends `Throwable` directly.
2. `WebTunerAudio.kt`, `openTunerMicrophone` — make the success handler unable to leave the tracks live, and publish
   the stream only once the graph is built:

   ```js
   .then(function (stream) {
       var stopTracks = function () { stream.getTracks().forEach(function (track) { track.stop(); }); };
       if (generation !== tuner.generation) {
           stopTracks();
           return 'AbortError';
       }
       var source = null;
       try {
           var context = tuner.ensureContext();
           if (!context) {
               stopTracks();
               return 'NotSupportedError';
           }
           source = context.createMediaStreamSource(stream);
           var analyser = context.createAnalyser();
           source.connect(analyser);
           stream.getAudioTracks().forEach(function (track) { track.addEventListener('ended', function () { if (tuner.stream === stream) tuner.ended = true; }); });
           tuner.ended = false;
           tuner.source = source;
           tuner.analyser = analyser;
           tuner.stream = stream;
           return 'ok';
       } catch (error) {
           // A graph that could not be built must not leave the browser's recording indicator on for a stream nothing reads.
           if (source) { try { source.disconnect(); } catch (ignored) {} }
           stopTracks();
           return (error && error.name) || 'Error';
       }
   }, function (error) { ... unchanged ... })
   ```

   The name returned is mapped by `WebAudioInput.start` as today (`NotSupportedError` → `NOT_SUPPORTED`, anything
   unknown → `FAILED`). Keep the existing comment about the generation, and keep the JS on the same lines the file
   uses.

## Tests

`FakeAudioInput` (plan 01) gains `var startFailure: Exception? = null`, thrown from `start` after counting it. In
`TunerEngineTest`:

1. `` `a start that throws stops as failed and closes the input` `` — `startFailure = IllegalStateException()`;
   `listen`, `runCurrent()` → `Stopped(FAILED)`, `stopCount == 1`, and the test does not fail with an uncaught
   exception (at b5c8ed3b5 `runTest` reports the exception from `backgroundScope`).
2. `` `after a failed start listening can be asked for again` `` — the same, then `startFailure = null`, `listen` →
   `Hearing`, `startCount == 2`.

The JavaScript has no unit test (the module's tests run on the desktop target only).

## Manual check

Web, Firefox: open the tuner with a microphone whose sample rate differs from the output device's (or, in any browser,
temporarily make `createMediaStreamSource` throw from the console by overriding `AudioContext.prototype
.createMediaStreamSource` before tapping the button). The tuner shows the not-supported/failed notice instead of
spinning, and the browser's recording indicator goes out at once.

## Docs

`tuner/implementation/CLAUDE.md`: in the `TunerEngine` bullet, add that a start that throws is a failed one; in the
**Web** bullet, that the stream is kept only once the analyser is connected, and a graph that cannot be built ends
its tracks.
