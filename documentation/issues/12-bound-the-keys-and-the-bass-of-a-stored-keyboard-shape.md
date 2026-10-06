# Bound the keys and the bass of a stored keyboard shape when it is read back

**Kind:** bug (robustness, synced input)  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordVoicings.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordVoicingsTest.kt`, `chordpro/CLAUDE.md`

## Problem

`ChordVoicings.read` takes a keyboard shape with no upper bound on its keys and no bound at all on its bass:

```kotlin
if (instrument == ChordInstrument.KEYBOARD) {
    val separator = words.indexOf(BASS_SEPARATOR)
    val notes = (if (separator < 0) words else words.subList(0, separator)).map { it.toIntOrNull()?.takeIf { note -> note >= 0 } ?: return null }
    val bass = if (separator < 0) null else words.getOrNull(separator + 1)?.toIntOrNull() ?: return null
```

The fretted branch is bounded (`it in 0..MAX_FRET`). Probe at dac1d9d59: `read("0 4 99999999", KEYBOARD)` returns
`Keys(notes=[0, 4, 99999999])`, `read("4 7 / -5", KEYBOARD)` returns `bass = -5`, `read("4 7 / 99", …)` `bass = 99`.

The string comes from `UserPreferences.chordVoicings`, which is **synced** through the cloud folder's
`preferences.json` (`chords.keyboard`), so another device, an older or newer version or a hand-edited file can hand
it in. The diagram sizes itself from the highest key (presentation `ChordDiagramGeometry.kt`:
`octaves = maxOf(MIN_OCTAVES, ((listOfNotNull(shape.bass) + shape.notes).maxOrNull() ?: 0) / 12 + 1)`), so
`99999999` asks for millions of octaves on every song that plays the chord, at every launch, on every synced device.

## Fix

In `read`'s keyboard branch take a key only in `0..MAX_KEY` and a bass only in `0..11`, answering null otherwise
(the callers, presentation `ui/chords/ChordSelection.kt` and `ChordShapeInsertion.kt`, already fall back to
the default shape for null):

```kotlin
val notes = (…).map { it.toIntOrNull()?.takeIf { note -> note in 0..MAX_KEY } ?: return null }
val bass = if (separator < 0) null else words.getOrNull(separator + 1)?.toIntOrNull()?.takeIf { it in 0 until 12 } ?: return null
…
/** The highest key a keyboard shape can press: four octaves above the diagram's C, past anything [keyboard] writes. */
private const val MAX_KEY = 47
```

`keyboard()` never writes past 47 (root ≤ 11, intervals ≤ 11, one inversion's octave and the slash chord's octave)
and always writes its bass as a pitch class, so no shape the app stored is lost. Add a line to the
`ChordVoicings` paragraph of `chordpro/CLAUDE.md`: "`read` takes a keyboard key in four octaves and a bass as a pitch
class, since a stored choice arrives through sync".

## Tests

In `ChordVoicingsTest`'s `a stored shape is read back as it was written`: `read("0 4 48", KEYBOARD)`,
`read("0 4 99999999", KEYBOARD)`, `read("4 7 / -5", KEYBOARD)` and `read("4 7 / 12", KEYBOARD)` are null;
`read("0 4 47", KEYBOARD)` and `read("4 7 / 11", KEYBOARD)` are not; and every `ChordVoicings.all(chord, KEYBOARD)`
over a sweep of roots and qualities (`C`…`B` × `""`, `m7`, `maj9`, `13#11`, `/E`) round-trips through
`write` → `read` unchanged.

## Manual check

On the desktop, quit Campfire, put `"keyboard": {"C:0.4.7": "0 4 99999999"}` under `chords` in a synced
`preferences.json` (or the local one's `chordVoicings`), start it with the keyboard instrument and open a song with a
`[C]`: the C is drawn with its default shape and the app stays responsive.

Cross-lane: the definitions half (`ChordProDefinitions.read`'s `keys` and frets after `base-fret`) and clamping in
`ChordDiagramGeometry` belong to other lanes (R4#3 / R2#1).
