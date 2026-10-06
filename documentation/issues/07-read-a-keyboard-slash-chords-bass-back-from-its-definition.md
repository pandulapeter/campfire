# Read a keyboard slash chord's bass back out of its `{define … keys …}`, so the shape the Chord shape button writes is the shape the preview draws

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProDefinitions.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProDefinitionsTest.kt`, `chordpro/CLAUDE.md`

## Problem

`ChordVoicing.Keys` keeps a slash chord's bass apart from the chord (`bass`, "below [notes] and drawn apart from them"); `ChordVoicings`' keyboard shapes always have one for a slash chord (a pitch class `0..11`, the chord's notes lifted an octave above it). `ChordProDefinitions.shapeOf` writes the bass as the first key, since ChordPro's `keys` has no syntax for it:

```kotlin
is ChordVoicing.Keys -> {
    val root = ChordProChords.parse(name, notation)?.root ?: 0
    val keys = (listOfNotNull(voicing.bass) + voicing.notes).map { it - root }
```

but `read` puts every key into `notes` and never sets a bass:

```kotlin
return Reading.Shape(name, ChordInstrument.KEYBOARD, ChordVoicing.Keys(absolute.map { it + octaves * 12 }.distinct().sorted()))
```

Proved with a probe test at dac1d9d59: `line("D/F#", Keys([14, 18, 21], bass = 6))` → `{define: D/F# keys 4 12 16 19}`, read back as `Keys(notes=[6, 14, 18, 21], bass=null)`. The existing test `the line written for a shape reads back as it` only compares `(bass + notes).sorted()` for keys, so it accepts the loss. A keyboard player who picks a slash-chord shape with the editor's Chord shape button gets a different-looking diagram in the preview and on the page (the bass drawn as one of the chord's keys rather than apart from them).

## Fix

In `read`'s keyboard branch, after computing the sorted absolute notes: where the name is a slash chord (`ChordProChords.parse(name)?.bass != null`), the lowest key is that bass's pitch class (`lowest.mod(12) == bass`), and there is at least one other key, take the lowest key as `bass` and the rest as `notes` **when** either the bass's note sounds again among the keys above it, or the bass is no note of the chord without it (`ChordProChords.parse(name)!!.let { c -> (c.root + interval) % 12 for interval in c.intervals }` does not contain it). That is exactly the shape `ChordVoicings` writes (C/E: `E | C E G`, C/D: `D | C E G`), while a one-hand inversion written by hand (`{define: C/E keys 4 7 12}`, E G C with no E above) keeps reading as three keys played together, as today.

Mind the order with plan 03 (German re-rooting) if it lands: its `renamedFromNotation` must shift `bass` with the notes. Note in `chordpro/CLAUDE.md`'s `ChordProDefinitions` paragraph that a slash chord's lowest key is read back as its bass where it is written the way `line` writes it.

## Tests

In `ChordProDefinitionsTest` `the line written for a shape reads back as it`, compare `Keys` voicings for equality like the fretted ones (drop the `(bass + notes).sorted()` special case), which the `D/F#` entry then checks; add `"C/D" to ChordVoicing.Keys(listOf(12, 16, 19), bass = 2)`; and assert `definitions("{define: C/E keys 4 7 12}").single().voicing == ChordVoicing.Keys(listOf(4, 7, 12))` (no bass).

## Manual check

Settings → Songs → Instrument: Keyboard. In the editor, put the caret on a `[D/F#]`, pick a shape with the Chord shape button: the preview draws its F♯ apart from the D major keys, as the sheet did.
