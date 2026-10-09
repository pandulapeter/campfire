# Keep the previous reading while a new target candidate is pending

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `tuner/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/implementation/PitchTracker.kt`,
`tuner/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/implementation/PitchTrackerTest.kt`,
`tuner/implementation/CLAUDE.md`

## Problem

`PitchTracker.hear` holds a new target back until it has been heard for `TARGET_HOLD_COUNT` (3) answers, so that the
note does not flicker. But while the candidate is pending and a target already exists, it falls through and reads the
new pitch against the *old* target:

```kotlin
if (candidateCount < TARGET_HOLD_COUNT) {
    if (current == null) return
} else {
    target = heardTarget
    ...
}
...
val note = target ?: return
val cents = Pitch.centsBetween(median, note, config.referencePitch)
smoothedCents += (cents - smoothedCents) * (1 - exp(-elapsed / SMOOTHING_MILLIS)).toFloat()
```

In chromatic mode the target is the nearest semitone, so `TunerReading.cents` is meant to stay within ±50 (the API
documents values beyond ±50 only "where a preset's nearest string is further than half a semitone"). Moving from A3 to
E4 at b5c8ed3b5 (a probe stepping `PitchTracker` 33 ms apart: twelve answers at `frequencyOf(57)`, then eight at
`frequencyOf(64)`) publishes:

```
step 0: 57   0.0
step 1: 57   0.0
step 2: 57 236.6
step 3: 57 393.2
step 4: 64   0.0
```

two frames of "A3, 393 cents sharp", which the meter draws pinned at its end with the sharp arrow, before E4 appears.
The same happens at a boundary: a pitch wobbling across 50 cents is read as 52 cents off the old note while the other
note is pending. With a preset the same fall-through reads a jump to another string against the previous string for
two frames.

## Fix

While a candidate is pending, keep whatever is shown and read nothing new, whether or not there is a current target:

```kotlin
if (candidateCount < TARGET_HOLD_COUNT) return
```

replacing `if (candidateCount < TARGET_HOLD_COUNT) { if (current == null) return } else { … }` with that early return
followed by the take-over block unindented. `lastHeardTime` is set at the top of `hear`, so the held reading is not
released while the new note is being confirmed, and `smoothedCents` is reset when the new target is taken, as today.
Add a KDoc-level or statement comment only if it says why (the reading would otherwise be the new pitch measured
against the old note).

## Tests

In `PitchTrackerTest`:

1. `` `a new note is never read against the old one` `` — `steadyTracker(note = 57)`, then eight steps at
   `frequencyOf(64)`: every non-null reading has `abs(cents) <= 50f` (or is the steady reading of note 57 unchanged),
   and the last reading's note is 64. Fails at b5c8ed3b5 (steps 2 and 3 above).
2. Keep `` `the note does not flicker at a boundary between two` `` and `` `a new note is taken once it has held` ``
   passing unchanged: with the fix the boundary test still sees only note 57 (the 58 candidate never holds for three
   answers), and the new-note test still starts at 57 and ends at 64.
3. Optionally, `` `a jump to another string keeps the previous string until the new one holds` `` with
   `InstrumentTuning.GUITAR`: steady on 45, then steps at `frequencyOf(50)`: every reading is note 45 with its old
   cents, or note 50 with `abs(cents) < 10f`.

## Manual check

Chromatic tuner on any device: play A3 and then E4 straight after. The meter moves from A to E without a frame pinned
at the sharp end.

## Docs

`tuner/implementation/CLAUDE.md`, the `PitchTracker` bullet: "a target that only changes once a new one has held for
three answers" → add ", the previous reading held meanwhile".
