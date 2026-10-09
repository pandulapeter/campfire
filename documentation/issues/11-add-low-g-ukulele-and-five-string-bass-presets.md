# Add low-G ukulele and five-string bass presets, so that their lowest strings are heard

**Decided:** the user took this plan on 2026-10-09; its condition below no longer applies.
**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `tuner/api/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/api/model/InstrumentTuning.kt`,
`tuner/api/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/api/InstrumentTuningTest.kt`,
`tuner/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/implementation/PitchDetector.kt`,
`tuner/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/implementation/PitchDetectorTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/InstrumentChoice.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`, `tuner/api/CLAUDE.md`,
`tuner/implementation/CLAUDE.md`

**Conditional on a user decision: drop this plan if the user declines** adding two presets (two more chips in the
instrument choice, two more strings to translate). The B0 half carries a detector change whose effect on the noise
and chord tests is unproven (see Fix 3); the executor drops that half if it cannot be made to pass, and keeps the
low-G half.

## Problem

`PitchDetector.rangeFor` searches from four semitones under a preset's lowest string:

```kotlin
Pitch.frequencyOf(tuning.strings.min() - SEMITONES_UNDER_LOWEST_STRING, referencePitch)..Pitch.frequencyOf(tuning.strings.max() + SEMITONES_OVER_HIGHEST_STRING, referencePitch)
```

At b5c8ed3b5 that makes the ukulele range 207.65–880 Hz (from G♯3, `UKULELE("ukulele", listOf(67, 60, 64, 69))`, lowest
C4) and the bass range 32.70–196 Hz (from C1, `BASS("bass", listOf(28, 33, 38, 43))`, lowest E1). So:

- a **low-G ukulele** (G3, 196 Hz, a common tuning) is outside the range: its fourth string is never read. Lowering
  the floor alone would not help, since `Pitch.nearestString` would read G3 against C4 at −500 cents;
- a **five-string bass** (B0, 30.87 Hz) is outside the range, and there is no B string to read it against.

Both need a preset of their own, whose strings set the range.

## Fix

1. `InstrumentTuning`: add two entries, each after its sibling, strings in the order the tuning is written:

   ```kotlin
   BASS_FIVE_STRING("bass_five_string", listOf(23, 28, 33, 38, 43)),
   ...
   UKULELE_LOW_G("ukulele_low_g", listOf(55, 60, 64, 69)),
   ```

   `rangeFor` then gives 24.50–196 Hz (from G0) and 155.56–880 Hz (from D♯3). At 48 kHz the lowest of these is a lag
   of 1 959 frames, under the detector's `windowSize / 2` cap of 2 048 (`maxLag` is
   `(sampleRate / minFrequency).toInt().coerceAtMost(windowSize / 2)`); 1 800 at 44.1 kHz; 653 of 1 024 at 16 kHz. The
   4 096-frame window holds 2.6 periods of B0, enough for the NSDF.
2. `InstrumentChoice.instrumentLabel`: two new branches (the `when` is exhaustive) with new keys in both
   `strings.xml` files, in the `tuner_instrument_*` group: `tuner_instrument_bass_five_string` "Bass, five-string" /
   "Basszusgitár, öthúros" and `tuner_instrument_ukulele_low_g` "Ukulele, low G" / "Ukulele, mély G".
3. **B0 at 44.1 and 48 kHz needs the detector's key-maximum cap raised.** A probe at b5c8ed3b5 read synthesized strings
   (`TestSignals.pluckedString`, `skip = sampleRate / 10`) against the proposed ranges:
   - low G (G3) — read within 0.2 cents at 16, 44.1 and 48 kHz, every seed; the weak-fundamental harmonic series too;
   - B0 — read within 0.02 cents at 16 kHz, and the weak-fundamental harmonic series at every rate, but the plucked
     string at 44.1 and 48 kHz answered **nothing** (clarity 0.10–0.14, every seed).

   A clarity that low means the true peak (which is ~0.99 for a periodic signal) was never considered: `pickPeak`
   stops after `MAX_KEY_MAXIMA = 64` positive lobes, and a bright string at a lag of ~1 500 frames crosses zero more
   often than that before its period. Raise the cap (try 256), and move the `IntArray(MAX_KEY_MAXIMA)` that
   `pickPeak` allocates on every call into a field next to the other buffers (the class says "every buffer is
   allocated once, here"). Then run the whole `PitchDetectorTest`: `silence, noise and a chord answer nothing` must
   still pass. If B0 still fails or the noise/chord case starts reading a pitch, leave the cap at 64, drop
   `BASS_FIVE_STRING` and its string, and ship only the low-G preset.

## Tests

- `InstrumentTuningTest.the presets are the standard tunings`: add `BASS_FIVE_STRING` → `B0 E1 A1 D2 G2` and
  `UKULELE_LOW_G` → `G3 C4 E4 A4`. The id tests cover the new ids already.
- `PitchDetectorTest.every string of every preset is read as a plucked string` iterates `InstrumentTuning.entries`, so
  it covers both new presets at 16, 44.1 and 48 kHz without a change; it is the test that fails for B0 until Fix 3 is
  in.
- `PitchDetectorTest`, the weak-fundamental case: add `InstrumentTuning.BASS_FIVE_STRING to 23` to its list.
- Run `./gradlew :tuner:api:desktopTest :tuner:implementation:desktopTest :presentation:desktopTest`.

## Manual check

With a five-string bass (or a bass with its E string tuned down to B) and a low-G ukulele, on a phone and on the
desktop: choose the new preset, pluck the lowest string — the chip for it is marked and the meter reads it, matching a
hardware tuner within a couple of cents. The other strings of both instruments read as before. In Hungarian the two new
chips read "Basszusgitár, öthúros" and "Ukulele, mély G" and the chip row still wraps cleanly at 360 dp.

## Docs

`tuner/api/CLAUDE.md`: the preset list "(guitar, drop D, bass, ukulele, violin, mandolin, banjo)" gains the five-string
bass and the low-G ukulele. `tuner/implementation/CLAUDE.md`, the `PitchDetector` bullet: if the cap changed, say how
many key maxima are considered.
