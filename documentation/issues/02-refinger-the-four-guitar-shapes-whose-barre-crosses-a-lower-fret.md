# Refinger the four guitar shapes whose one finger lies across a string held at a lower fret, and test that no table shape does

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/GuitarChordTable.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/DropDChordTable.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordVoicingTablesTest.kt`

## Problem

Four fingerings in the standard guitar table name one finger on two strings at the same fret with a string between them
stopped at a *lower* fret by another finger. A finger cannot lie across a string that has to sound lower: the barre
would stop it at its own fret. The diagram draws exactly that impossible barre, since `barresOf`
(`presentation/.../ui/chords/ChordDiagramGeometry.kt`) groups strings by `(finger, fret)` and draws a bar from the
lowest to the highest string of each group:

```kotlin
return fingers.indices.filter { fingers[it] > 0 && (frets[it] ?: 0) > 0 }
    .groupBy { fingers[it] to frets[it]!! }
    .filterValues { it.size >= 2 }
    .map { (finger, strings) -> ChordDiagramGeometry.Fretted.Barre(row = finger.second - baseFret, fromString = strings.min(), toString = strings.max(), finger = finger.first) }
```

The shapes, at 72d48deb7 (the same four lines are in both files, since drop D's table repeats the standard shapes that
mute the sixth string):

| Line | `GuitarChordTable.kt` | `DropDChordTable.kt` | What is wrong |
|---|---|---|---|
| `"C69 frets x 3 2 2 3 3 fingers 0 3 1 1 3 4"` | :289 | :219 | finger 3 on the A and B strings at fret 3, across the D and G strings at fret 2 |
| `"C#mmaj11 frets x 4 2 5 4 2 fingers 0 3 1 4 3 1"` | :560 | :398 | finger 3 on A and B at fret 4, across D at fret 2 |
| `"Ebmmaj11 frets x 6 4 7 6 4 fingers 0 3 1 4 3 1"` | :1087 | :755 | finger 3 on A and B at fret 6, across D at fret 4 |
| `"Abm11 frets x 11 9 11 11 9 fingers 0 3 1 3 4 1"` | :2453 | :1570 | finger 3 on A and G at fret 11, across D at fret 9 |

A probe over all 7,620 lines of every table (guitar, ukulele, mandolin, drop D, double drop D, open G, D, and C,
DADGAD, mandolin GDGD, and the five banjo tables) found these eight lines and no others breaking the rule below, and no
line anywhere that names one finger at two different frets.

## Fix

Replace only the `fingers` part of each line, in both files (the frets stay as they are):

| Shape | New fingering | How it is held |
|---|---|---|
| `C69 frets x 3 2 2 3 3` | `fingers 0 2 1 1 3 4` | index across D and G at 2, middle on A, ring on B, little finger on e |
| `C#mmaj11 frets x 4 2 5 4 2` | `fingers 0 2 1 4 3 1` | index barre at 2 (D and e, the B at 4 and G at 5 above it), middle on A, little finger on G, ring on B |
| `Ebmmaj11 frets x 6 4 7 6 4` | `fingers 0 2 1 4 3 1` | the same shape two frets up |
| `Abm11 frets x 11 9 11 11 9` | `fingers 0 2 1 3 4 1` | index barre at 9 (D and e), middle on A, ring on G, little finger on B |

Each new fingering was checked by the probe: it keeps the number of fingers to four, names a finger exactly where a
fret is stopped (the existing `every fingering names as many fingers as frets are stopped` test), and passes the new
rule. The order of shapes within each chord does not change.

## Tests

Add to `ChordVoicingTablesTest` (it already has the `tables` map of every table):

```kotlin
@Test
fun `a finger laid across several strings has no string between them sounding lower`() {
    tables.forEach { (tuning, lines) ->
        lines.forEach { line ->
            val voicing = (ChordProDefinitions.read(line, tuning.instrument.id) as ChordProDefinitions.Reading.Shape).voicing as ChordVoicing.Fretted
            val fingers = voicing.fingers ?: return@forEach
            voicing.frets.indices.filter { fingers[it] > 0 && (voicing.frets[it] ?: 0) > 0 }
                .groupBy { fingers[it] to voicing.frets[it]!! }
                .filterValues { it.size >= 2 }
                .forEach { (finger, strings) ->
                    // A muted string between is allowed: the finger rests on it. One that sounds open or lower cannot be.
                    (strings.min()..strings.max()).forEach { string ->
                        val fret = voicing.frets[string]
                        assertTrue(fret == null || fret >= finger.second, "$line: finger ${finger.first} lies across string ${string + 1}")
                    }
                }
        }
    }
}
```

It fails on the eight lines before the fix and passes after.

## Manual check

On any platform, with the guitar in standard tuning, open the Chords tab, search for C69, C#mmaj11, Ebmmaj11 (D#mmaj11),
and Abm11 (G#m11), and open each chord's sheet: the shape named in the table above (the first one for the first three,
the third for Abm11) shows no bar crossing a lower dot, and its finger numbers match the table. Repeat for C69 in drop D.
