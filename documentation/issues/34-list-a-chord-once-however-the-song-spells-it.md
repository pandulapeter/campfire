# List a chord once in the Chords section, the Chord shapes sheet and the PDF however many ways the song spells it

**Challenged:** amended — the dedupe key is the chord with its definition's *shape* (voicing and move), not the whole `ChordDefinition`, whose `name` differs between the `{define: A# …}` and `{define: Bb …}` of one identical shape and would have kept two identical diagrams; noted the keys plan 33 still needs and plan 14's `spelling`.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/SongChords.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/chords/SongChordsTest.kt`, `presentation/CLAUDE.md`

## Problem

`songChordsOf` (`ui/chords/SongChords.kt`) makes one entry per distinct *written name*:

```kotlin
val shownNames = ChordProNotation.shownNames(song, notation)
val parsed = shownNames.keys.mapNotNull { name -> ChordProChords.parse(name)?.let { name to it } }
if (parsed.size > MAX_SONG_CHORDS) return emptyList()
...
return parsed.map { (name, chord) -> ... }
```

`ChordProChords.parse` reads a parenthesized chord, a lowercase minor and both accidentals as the same `Chord`, so a
song that writes `[G]` and `[(G)]` (an optional chord, common in charts), `[Am]` and `[a]`, or `[A#]` in one verse and
`[Bb]` in another gets two cells for one chord: two identical diagrams in the Chords section, two cells in the PDF,
and in the Chord shapes sheet two steppers that write the same entry (`setChordVoicing(..., chord.chord.id, …)` — the
id is the same) so stepping one silently changes the other. Each duplicate also counts towards `MAX_SONG_CHORDS` and
runs `ChordVoicings.default` again.

## Fix

Collapse entries that are the same chord drawn with the same definition, keeping the first one written (its name is
the one the cell shows, the same rule as "a chord played on both sides of a modulation is one diagram, named by the
step it is first played on"), **before** the cap and before any shape is looked for:

```kotlin
val definitions = song.metadata.definitions.filter { it.instrument == instrument }
// One chord written two ways - (G) and G, a and Am, A# and Bb - is one diagram, under the name it is first written with,
// unless the song defines the two spellings differently.
val entries = shownNames.keys
    .mapNotNull { name -> ChordProChords.parse(name)?.let { chord -> Triple(name, chord, definitionOf(name, chord)) } }
    .distinctBy { (_, chord, definition) -> chord to definition?.let { it.voicing to it.movedBy } }
if (entries.size > MAX_SONG_CHORDS) return emptyList()
return entries.map { (name, chord, definition) -> ... }
```

where `definitionOf(name, chord)` is the existing expression moved into a local function:

```kotlin
fun definitionOf(name: String, chord: Chord) =
    (definitions.lastOrNull { it.name == name } ?: definitions.lastOrNull { ChordProChords.parse(it.name) == chord })
        ?.takeIf { it.movedBy == 0 || (it.voicing as? ChordVoicing.Fretted)?.frets?.let(ChordVoicings::isHoldable) != false }
```

and the keyboard's `ChordProDefinitions.transposed(...)` step stays inside the `map`, applied to the kept
definition. The key compares the definition by what is drawn (its voicing and `movedBy`), not by its `name`: a song
that writes `{define: A# frets x 1 3 3 3 1}` and `{define: Bb frets x 1 3 3 3 1}` is one diagram. Two spellings defined
with *different* shapes stay two cells with one `chord.id`; the Chord shapes sheet hides the stepper of a defined chord
(`Source.DEFINED`), so they never write over each other, and plan 33's key keeps the name for them. With plan 14 the kept
entry's `spelling` is the first-written name's (`A#` spells its notes from A), consistent with the name the cell shows. Everything else in the `map` is unchanged. (If lane D's plan 30 has landed first, keep its
`isShapePending` lines in the `map`.) Update `songChordsOf`'s KDoc ("each chord once, under the name it is first
written with, however else the song spells it") and `presentation/CLAUDE.md`'s "every chord the page names once" to
"every chord once, however the page spells it, under the name it is first played with".

## Tests

`SongChordsTest`: `a chord written two ways is listed once` —
`names("[G]la [(G)]la [a]la [Am]la [A#]la [Bb]la").map { it.name }` is `["G", "a", "A#"]`; with
`{define: Bb base-fret 1 frets x 1 3 3 3 1}` added before it the A#/Bb pair stays one entry carrying that definition
(both spellings match it, by name or by notes); with `{define: A# base-fret 1 frets x 1 3 3 3 1}` and `{define: Bb
base-fret 1 frets x 1 3 3 3 1}` both, still one entry; with `{define: Bb base-fret 6 frets 1 3 3 2 1 1}` instead of
the A# one, two entries (`A#` and `Bb`) with the same `chord`. The existing `a songbook gets no section` test still holds (its 60
chords are all distinct).

## Manual check

Open a song with `[G]` in one line and `[(G)]` in another, and `[A#]`/`[Bb]`: the Chords section, the Chord shapes
sheet and an exported PDF each show one G and one A# diagram.
