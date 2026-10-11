# Read the web tuner's window through the typed array itself rather than three property lookups per sample

**Challenged:** sound

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** web (the `ToneSynthesizer` part: all)
**Files:** `tuner/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/tuner/implementation/WebAudioInput.kt`,
`tuner/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/tuner/implementation/WebTunerAudio.kt`,
`tuner/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/implementation/ToneSynthesizer.kt`,
`tuner/implementation/CLAUDE.md` (only if the web bullet's wording needs it)

## Problem

On the web, every poll of the tuner (thirty a second while it listens, on the page's one thread) copies the analyser's
window into Kotlin one sample at a time, and each sample is a wasm-to-JS call that walks from the global object down to
the array (`WebAudioInput.kt:63-65` and `WebTunerAudio.kt:151-153` at ab97d3105):

```kotlin
// The analyser holds a power of two, the detector's input is the latest frames of it.
val offset = tunerSampleCount() - window.size
for (index in window.indices) window[index] = tunerSample(offset + index)
```

```kotlin
internal fun tunerSample(index: Int): Float = js("window.__campfireTuner.samples[index]")
```

That is 6,144 calls a poll (the detector's `inputSize` at 48 kHz), about 185,000 a second, each doing three property
lookups (`window`, `__campfireTuner`, `samples`) before the indexed read. The boundary crossing itself cannot be avoided
cheaply (the OPFS bridge's note: "an `Int8Array` is read one call per element"), but the lookups can: they are most of
what each call does in JS.

A smaller, platform-independent waste: `ToneSynthesizer.loopOf` (`ToneSynthesizer.kt:33-39`) keeps the audible
harmonics as a `List<Int>` from `filter` and iterates it once per frame of a second-long loop
(`for (harmonic in audible)`), allocating an iterator and unboxing each index 48,000 times on every tap of a string's
tone button.

What is **not** proposed: `WebToneOutput` also writes its loop into the `AudioBuffer` one `setTunerToneSample` call per
sample, but only once per tone (one tap), the same choice the metronome makes on purpose and documents
(`AudioOutput.wasmJs.kt`'s `loadVoice`: "one crossing per sample, done once per voice, is far cheaper than any of the
ways of handing a whole array over"). Leave it.

## Fix

1. In `WebTunerAudio.kt`, hand the typed array over once per poll and read it by a parameter:

   ```kotlin
   /** The analyser's buffer, which `readTunerWindow` has just filled, to be read by [tunerSampleOf]. */
   internal fun tunerSamples(): JsAny = js("window.__campfireTuner.samples")

   internal fun tunerSampleCountOf(samples: JsAny): Int = js("samples.length")

   internal fun tunerSampleOf(samples: JsAny, index: Int): Float = js("samples[index]")
   ```

   and remove `tunerSample` and `tunerSampleCount`, which nothing else calls.

2. In `WebAudioInput.latest`:

   ```kotlin
   if (!readTunerWindow()) return AudioInput.NO_WINDOW
   val samples = tunerSamples()
   // The analyser holds a power of two, the detector's input is the latest frames of it.
   val offset = tunerSampleCountOf(samples) - window.size
   for (index in window.indices) window[index] = tunerSampleOf(samples, offset + index)
   ```

   Each call is now one indexed read of a monomorphic `Float32Array` argument, which V8 and SpiderMonkey inline.

   Options considered: (a) the above, recommended; (b) a bulk crossing through a string (JS writes each float's bits as
   two UTF-16 code units, Kotlin decodes with `Float.fromBits`), the one bulk route the OPFS bridge uses — rejected
   unless a measurement shows it faster, since it allocates a 12 KB string per poll and depends on lone surrogates
   surviving `JsString.toString()`; (c) `kotlinx-browser`'s `org.khronos.webgl.Float32Array.get`, which is also one JS
   call per element and would add a dependency to the module. Drop this plan if a quick measurement in Chrome's
   performance panel (the tuner listening for ten seconds, before and after) shows no difference in the time spent in
   `latest`.

3. In `ToneSynthesizer.loopOf`, make the audible harmonics an `IntArray`:

   ```kotlin
   val audible = HARMONICS.indices.filter { (it + 1) * playedFrequency < sampleRate / 2 }.toIntArray()
   ```

   The `for (harmonic in audible)` loop over an `IntArray` compiles to an indexed loop with no iterator and no boxing;
   `audible.sumOf { HARMONICS[it].toDouble() }` works unchanged on an `IntArray`. The output is bit-identical.

The window's contents, the position the input answers, the issue and lost-input handling, and every other behaviour of
the web input are unchanged.

## Tests

`ToneSynthesizerTest` already pins the loop (its seam, its level, and the tone read back by the detector) and must
pass unchanged, which covers step 3. Steps 1 and 2 are JS interop on the wasm target, which `desktopTest` does not run
and no test in the repository covers; `:app:web:wasmJsBrowserDistribution` compiling is their check, with the manual
one below.

## Manual check

Run the web build (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`), open the Tuner tab in Chrome, allow the
microphone, and pluck or sing a note: the note, the cents, and the in-tune check read as before. In Chrome's
Performance panel, record ten seconds of listening before and after: the self time under `WebAudioInput.latest` (the
`tunerSample` wrappers) shrinks, and the main thread's busy fraction while the tuner listens drops. Tap a string's tone
button and hear the tone start without a hitch, as before.
