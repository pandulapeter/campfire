# Keep the third of an augmented or diminished chord written with a `5` (`C+5`, `Caug5`, `Cdim5`, `C°5`), and read `C-5` as a flat five

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProChords.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProChordsTest.kt`, `chordpro/CLAUDE.md`

## Problem

`ChordProChords.Reader.number` turns a `5` after any quality but `sus`, `add` and `ø` into a power chord:

```kotlin
override fun number(number: String) {
    when (quality) {
        "sus" -> if (number == "2") suspension = MAJOR_SECOND else if (number != "4") extend(number)
        "add" -> add(number)
        "ø" -> if (number != "7") extend(number)
        else -> when (number) {
            "2" -> added += MAJOR_SECOND
            "4" -> suspension = PERFECT_FOURTH
            "5" -> third = null
            else -> extend(number)
        }
    }
}
```

`ChordProChordNames.read` hands `+`, `aug`, `dim`, `°`, `m`, `min`, `mi`, `-` over as the *quality* and the `5` after
them as the *number*, so a `5` that only repeats which fifth the quality already chose deletes the third. Proven at
dac1d9d59 with a probe test:

| name | parsed | id | should be |
|---|---|---|---|
| `C+5`, `Caug5` | `[0, 8]` (C G#) | `C:0.8` | C E G# (`C:0.4.8`, the id of `Caug`) |
| `Cdim5`, `C°5` | `[0, 6]` (C F#) | `C:0.6` | C Eb Gb (`C:0.3.6`) |
| `Cm5`, `Cmin5` | `[0, 7]` | `C:0.7` | C Eb G |
| `C-5` | `[0, 7]` | `C:0.7` | see below |
| `C5` | `[0, 7]` | `C:0.7` | C G (right) |

`C+5` is a common way of writing an augmented triad, and with the third gone the diagram, the search, the keyboard
keys, the Chord shapes sheet's note row and the chord's id (`C:0.8`, a different stored choice than `Caug`'s) are all
those of a two-note chord.

`C-5` has two readings, since `-` is both the minor quality (`C-7`) and the flat sign (`C7-5`, `Cm7-5`, read as a flat
five, see the `"Cm7-5" to "C D# F# A#"` case in `ChordProChordsTest`): a minor triad with a redundant `5`, or
`C(b5)`. Today it is neither.

## Fix

In `Reader.number`, only a bare `5` (no quality) is the power chord; after a quality the `5` names the fifth that
quality already has, and `-5` reads like the `-5` everywhere else in a name:

```kotlin
"5" -> when (quality) {
    null -> third = null
    // `-` is the flat sign wherever a `5` follows it (`C7-5`), so `C-5` is the major triad with a flat fifth rather
    // than a minor one with its fifth named twice.
    "-" -> {
        third = MAJOR_THIRD
        fifth = DIMINISHED_FIFTH
    }
    else -> Unit
}
```

Options for `C-5` (a decision, see the report): **(a, recommended)** `C E Gb`, consistent with `C7-5`/`Cm7-5`;
(b) `C Eb G`, the minor quality with a redundant five (drop the `"-"` branch); (c) leave it a power chord (no).

`Cmaj5` (today a power chord too) becomes a major triad through `else -> Unit`, which is what it says.

Add one bullet to the KDoc of `ChordProChords` next to "`5` alone is the power chord": "a `5` after a quality names
the fifth the quality has (`C+5` is `Caug`, `Cdim5` is `Cdim`), and `C-5` is the flat five `C7-5` writes". Mirror it
in the `ChordProChords` paragraph of `chordpro/CLAUDE.md` ("The readings a chart leaves open are made once here (…)").

A choice stored under the old ids (`C:0.8`, `C:0.6`, `C:0.7` for these spellings) is simply no longer matched; no
migration is needed, since those ids only ever drew a wrong chord.

## Tests

In `ChordProChordsTest`'s table of usual names (`every usual name is read as the notes chord charts agree on`) add:
`"C+5" to "C E G#"`, `"Caug5" to "C E G#"`, `"Cdim5" to "C D# F#"`, `"C°5" to "C D# F#"`, `"Cm5" to "C D# G"`,
`"C-5" to "C E F#"` (or the option chosen), keeping `"C5" to "C G"`. In `spellings of one chord share an id` add
`listOf("Caug", "C+", "C+5", "Caug5")` and `listOf("Cdim", "C°", "Cdim5")`. Run `./gradlew :chordpro:desktopTest`
(also `ChordVoicingTablesTest`, which re-reads every table name through `parse`).

## Manual check

Open a song with `[C+5]la [Cdim5]la` on the guitar: the Chords section shows an augmented and a diminished triad
diagram (three distinct notes), and the Chord shapes sheet's note row reads three notes for each.
