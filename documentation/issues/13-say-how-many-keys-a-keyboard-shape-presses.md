# Say truthfully how many keys a keyboard shape presses

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordVoicings.kt`, `chordpro/CLAUDE.md`

## Problem

The `ChordVoicings` KDoc says "The keyboard needs neither: the chord's notes from the root up, at most five, and the
inversions as the variations", and `chordpro/CLAUDE.md` "The keyboard plays the notes from the root up, at most five".
`keyboard()` only drops the fifth and the root:

```kotlin
val omittable = listOfNotNull(7.takeIf { it in chord.intervals }, 0.takeIf { it in chord.intervals })
val intervals = chord.intervals - omittable.take((chord.intervals.size - MAX_KEYS).coerceAtLeast(0)).toSet()
```

so a chord of more than seven notes keeps more than five: probe at dac1d9d59, `C7(b9,#9,#11,b13)` →
`Keys([1, 3, 4, 6, 8, 10])` (six keys), `C(b9,9,#9,11,#11,b13,13,#13,maj7)` → ten keys.

## Fix

Reword rather than cap (a cap would have to drop a tension the name asks for, which a reader would see as a wrong
chord): KDoc — "the chord's notes from the root up, the fifth and then the root left out of a chord of more than five
notes, and the inversions as the variations"; `chordpro/CLAUDE.md` — "The keyboard plays the notes from the root up
(the fifth, then the root, left out where there are more than five)". Also rename nothing; `MAX_KEYS` keeps its value
but gets a KDoc: "How many notes a keyboard shape aims at: the fifth and the root are left out above it, never a note
the name asks for."

## Tests

None needed; `ChordVoicingsTest`'s `the keyboard plays the notes from the root up and their inversions` may add
`assertEquals(6, (ChordVoicings.default(parse("C7(b9,#9,#11,b13)")!!, KEYBOARD) as ChordVoicing.Keys).notes.size)` to pin
the documented behaviour.

## Manual check

None.
