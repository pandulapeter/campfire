# Tell a quiet but live microphone from a dead or gated one before the tuner says nothing at all is heard

**Challenged:** amended — option A does not reach the −100 to −110 dBFS interface this plan is about (its peaks stay
under `SILENT_PEAK` on every platform, float inputs included), and it depends on what Windows' gated input looks like
just as B does; the recommendation is now C, with A only after that input has been recorded. Also: the detector's
existing local `peak` (the NSDF lag) must not be shadowed, and the manual check no longer promises what A cannot do.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all, mostly the desktop with an audio interface (macOS and Linux show the misleading text)
**Follows decision:** D1 in `documentation/issues/README.md` (which option below)
**Files:**
`tuner/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/implementation/PitchDetector.kt`,
`tuner/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/implementation/PitchEstimate.kt`,
`tuner/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/implementation/PitchTracker.kt`,
`tuner/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/implementation/PitchTrackerTest.kt`,
`tuner/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/implementation/PitchDetectorTest.kt`,
`tuner/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/implementation/LatestEstimateTest.kt` (only if `PitchEstimate` gains a parameter without a default),
`tuner/api/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/api/model/TunerInputIssue.kt`,
`tuner/implementation/CLAUDE.md`, `tuner/api/CLAUDE.md`,
`presentation/src/commonMain/composeResources/values/strings.xml` and `values-hu/strings.xml` (option C only)

## Problem

Commit fbf4279d4 changed the tuner's silence test from exact zeros to a level threshold, to catch Windows' audio
enhancements gating a held note down to "a sample or two of dither":

```kotlin
// PitchTracker.kt
private fun updateSilence(estimate: PitchEstimate, timeMillis: Long): Boolean {
    if (estimate.level >= SILENT_LEVEL) {
        silentSince = null
        return false
    }
    val since = silentSince ?: timeMillis.also { silentSince = it }
    return timeMillis - since >= SILENT_MILLIS
}
...
/**
 * The level under which the input is taken for silent, about 1.5 steps of a 16-bit sample (-86 dBFS): not only
 * exact zeros, since Windows' audio enhancements gate what they take for noise down to a sample or two of dither
 * rather than to nothing, and a live microphone's own noise is well above it.
 */
const val SILENT_LEVEL = 5e-5f
```

`level` is not the raw input's RMS: `PitchDetector.detect` removes the mean and runs a 100 Hz high-pass
(`HIGH_PASS_FREQUENCY = 100f`) first, which takes away most of a quiet room's noise (it is mostly rumble):

```kotlin
for (index in 0 until inputSize) samples[index] = (input[index] - mean).toFloat()
highPass.filter(samples, inputSize)
samples.copyInto(samples, 0, inputSize - windowSize, inputSize)
var energy = 0.0
for (index in 0 until windowSize) energy += samples[index] * samples[index]
val level = sqrt(energy / windowSize).toFloat()
```

"A live microphone's own noise is well above it" holds for a laptop's or a phone's microphone, which run with a lot of
gain. It does not hold for a clean audio interface with its gain turned down in a quiet room: a decent 24-bit interface
at minimum gain idles around −100 to −110 dBFS, below −86 dBFS by 15–25 dB, and the high-pass lowers it further. After
two seconds of that the tuner shows `TunerInputIssue.SILENT`, which on macOS and Linux (no `openSoundSettings`) is
`tuner_notice_silent`:

> Nothing at all is heard. Check that the microphone is not muted and that Campfire may use it.

with an Open settings button — sending a guitarist with a working interface to look for a permission problem. The notice
goes away as soon as a string is played, so the harm is a misleading message, hence low.

What the fix has to keep: exact zeros (a refused recording on the desktop, a muted input) and Windows' gated input must
still read as `SILENT`. The commit's test only uses `level = SILENT_LEVEL / 2`; what that gated input looks like
sample by sample is described only as "a sample or two of dither" — it may mean one or two non-zero samples per window,
or every sample within ±1–2 steps (TPDF dither). Note also that the desktop and Android inputs are **16-bit**
(`AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)` in `DesktopAudioInput`, `ENCODING_PCM_16BIT` in
`AndroidAudioInput`), so an interface quieter than about half a 16-bit step reaches the detector as zeros with a stray
±1 now and then, exactly like a gated input: no test on the samples can tell those two apart, and the plan does not try
to.

## Fix

Three options; D1 picks one (A and C can be combined).

**A — call it silent by the window's peak as well (recommended).** Have `PitchDetector.detect` also report the window's
largest absolute sample, taken from the mean-removed samples *before* the high-pass (the input as it arrived, in whole
16-bit steps on the desktop and Android), over the same `windowSize` frames the level is computed from (the last `windowSize` of the `inputSize` frames, taken in
the mean-removal loop before `highPass.filter` overwrites them), and computed before the `LEVEL_FLOOR` early return and
passed to all three `PitchEstimate(...)` constructions in `detect`. `detect` already has a local `val peak` (the lag
`pickPeak` returns); name the new one `largestSample` or rename that one, rather than shadowing it. Add `val peak: Float` to `PitchEstimate` (KDoc: "the largest absolute sample of
the window, full scale being 1"). In `PitchTracker`, take the input for silent only when
`estimate.level < SILENT_LEVEL && estimate.peak <= SILENT_PEAK`, with
`SILENT_PEAK = 2.5f / 32_768` (about 7.6e-5, two and a half 16-bit steps): zeros and ±1–2 steps of dither, sparse or on
every sample, stay silent, while a live input whose noise reaches a few steps — which an input at 1 step RMS does, its
peaks being three to four times its RMS — is no longer called silent even though its level is under −86 dBFS. It also
keeps a quiet live input with its energy below 100 Hz from being called silent. It does not help an interface so quiet
that 16-bit quantization leaves it at zeros (see above); for those, see C.

**B — lower `SILENT_LEVEL`.** For example to a quarter of a 16-bit step (`7.6e-6`, about −102 dBFS). Simpler, but only
safe if Windows' gated input really is a stray sample or two per window (RMS far below a step); if it is dither on every
sample (RMS around half a step), B would stop catching the very case fbf4279d4 was written for. Only choose B after
recording that input on a Windows machine with enhancements on and checking its level.

**What A really reaches (challenge).** One 16-bit step is 3.05e-5 (−90.3 dBFS); `SILENT_LEVEL` is 1.64 steps RMS and
`SILENT_PEAK` 2.5 steps. Broadband noise peaks at about 3.5–4 times its RMS over a 4096-frame window, so A stops calling
an input silent only once its noise (before the high-pass) is above about 0.65 steps RMS, roughly −94 dBFS. The
interface of the Problem idles at −100 to −110 dBFS, 0.33 to 0.1 steps RMS: on the desktop and Android (16-bit) that
arrives as zeros with a stray ±1, and on iOS (`AVAudioEngine`, float) and the web (`Float32Array`), where nothing is
quantized, its peaks are 1.1 to 0.4 steps. Either way it stays under `SILENT_PEAK`, so **A leaves the Problem's own case
showing the notice on every platform**; it only helps the band from about −86 to −94 dBFS, and an input whose noise is
mostly under 100 Hz. And A depends on Windows' gated input as much as B does: today anything under 1.64 steps RMS
counts, and gated noise of 0.7 steps RMS or more peaks above 2.5 steps, so if "a sample or two of dither" is in fact
dither on every sample at that level, A stops catching the case fbf4279d4 was written for. Choose A only after that
input has been recorded on Windows with enhancements on, with `SILENT_PEAK` set above its measured peak.

**C — soften the text where silence is not certain to be a refusal.** Keep the detection and change
`tuner_notice_silent` (both languages) so that it no longer asserts a problem, for example "Nothing is heard yet. If a
string is playing, check that the microphone is not muted or turned all the way down, and that Campfire may use it." —
and the Hungarian to match. It makes no wrong claim whatever the input, but leaves the notice up for an idle
interface, and does nothing for Windows' text, which names enhancements.

Recommended: **C**, the one option that makes no wrong claim whatever the input and the only one that covers the quiet
interface of the Problem; add **A** on top only once the Windows recording above exists. (C changes only
`tuner_notice_silent`, which `TunerInputIssueMessage` shows wherever `openSoundSettings` is null — every platform but
Windows — beside Open settings where the platform offers it; the Windows text, `tuner_notice_silent_enhancements`, is
left alone.) Whichever lands, update `SILENT_LEVEL`'s KDoc, `PitchEstimate`'s
`level` KDoc (its "a few thousandths of a sample step for a gated input" contradicts the "a sample or two of dither" of
the other comments; state what was measured, or say it is not known), `TunerInputIssue.SILENT`'s KDoc, the `PitchTracker`
line of `tuner/implementation/CLAUDE.md` ("`SILENT` after two seconds under −86 dBFS (`SILENT_LEVEL`) …", which with A
becomes "… under −86 dBFS with no sample more than two and a half 16-bit steps from the mean (`SILENT_PEAK`) …"), and the
`TunerInputIssue` line of `tuner/api/CLAUDE.md`.

## Tests

In `PitchTrackerTest` (option A):

- `PitchEstimate(frequency = null, clarity = 0f, level = PitchTracker.SILENT_LEVEL / 2, peak = 1f / 32_768)` held for
  `SILENT_MILLIS` is silent (the existing dither test, given a peak).
- Exact zeros (`level = 0f, peak = 0f`) held for `SILENT_MILLIS` is silent.
- `level = PitchTracker.SILENT_LEVEL / 2, peak = 4f / 32_768` held for `SILENT_MILLIS` is **not** silent: a quiet live
  input.

In `PitchDetectorTest`: a window of zeros with a single sample of `1f / 32_768` reports `peak` of about one step, and a
sine at 0.5 reports `peak` of about 0.5 (proving the peak is taken from the input, not from the filtered window).

Update the existing `PitchEstimate(...)` calls in the tests (or give `peak` a default of `0f` — allowed here, since
`PitchEstimate` is not a Koin definition — and pass it only where it matters).

Option B: change the existing test's level to just under the new constant and add one just above it that is not silent.
Option C: no test (a string).

## Manual check

On a Mac with an audio interface: plug a microphone into the interface, turn its gain all the way down, keep the room
quiet, and open the Tuner tab. Before the fix the "Nothing at all is heard" notice appears after about two seconds; after
option C the notice reads as a hint rather than a fault. With option A, it goes away only where the interface's
noise reaches a few 16-bit steps (raise the gain a little until it does); at the lowest gain it still appears. Then revoke Campfire's microphone permission in
System Settings and reopen the tab: the notice still appears. On Windows with audio enhancements on, hold a note on the
built-in microphone until it is gated away: the enhancements notice still appears.
