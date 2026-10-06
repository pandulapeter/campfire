# Draw a barre for a shape that names no fingers only where the shape needs one or its lowest-fret strings are neighbours

**Challenged:** amended — the "neighbours" exception still barred the commonest open shapes written without fingers (Em `0 2 2 0 0 0`, A `x 0 2 2 2 0`, Asus2, Esus4, ukulele D `2 2 2 0`), which the app's own table fingers separately: 17 table shapes with their fingers dropped got a barre the table does not have. The short barre is now only the index finger flat across the treble strings under a higher fretted string (F `x x 3 2 1 1`, ukulele B♭ `3 2 1 1`), which gives no barre the table lacks; a table-wide test pins that.

**Kind:** bug (wrong diagram)  ·  **Severity:** low  ·  **Platforms:** all (screen and PDF)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/ChordDiagramGeometry.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/chords/ChordDiagramGeometryTest.kt`, `presentation/CLAUDE.md`

## Problem

For a shape without a fingering, `barresOf` (`ChordDiagramGeometry.kt`) lays a barre across the lowest stopped fret
wherever two strings or more are held there and nothing open or muted lies between them:

```kotlin
val stopped = frets.indices.filter { (frets[it] ?: 0) > 0 }
if (stopped.isEmpty()) return emptyList()
val lowest = stopped.minOf { frets[it]!! }
val atLowest = stopped.filter { frets[it] == lowest }
if (atLowest.size < 2) return emptyList()
val from = atLowest.first()
val to = atLowest.last()
if ((from..to).any { (frets[it] ?: 0) < lowest }) return emptyList()
return listOf(ChordDiagramGeometry.Fretted.Barre(row = lowest - baseFret, fromString = from, toString = to, finger = null))
```

A higher fret in between does not stop it. The table shapes carry fingers, so the app's own defaults are fine, but
the shapes that name none are drawn wrong: every `{define}` written without `fingers` (most definitions in the wild:
`{define: D base-fret 1 frets x x 0 2 3 2}`), every shape the search finds, and a stored player shape the tables do
not know. Traced:

- Guitar D `x x 0 2 3 2`: stopped strings 3, 4, 5 at 2, 3, 2; `atLowest = [3, 5]`, no fret below 2 between → a barre
  from the G string to the high E at fret 2, the B string's 3 drawn as a dot on top. D is three fingers.
- Ukulele G `0 2 3 2`: strings 1 and 3 at 2 with 3 between → a barre over three strings. Also three fingers.
- Guitar Em `0 2 2 0 0 0` and A `x 0 2 2 2 0` (how most files without `fingers` write them): a two- and a three-string
  barre, where the app's own table fingers them `0 2 3 0 0 0` and `0 0 1 2 3 0`. Checked over the whole table
  (`ChordVoicingTables`, 221 shapes, each drawn without its fingers): 42 come out with other barres than their
  fingering's today, 35 of them with a barre the fingering does not have (17 still with the rule first proposed here).

The same geometry is drawn in the PDF (`printChordsOf`).

## Fix

Without a fingering, lay the barre only where the shape cannot be held without one (more stopped strings than four
fingers), or where it is the short barre of the index finger laid flat across the treble strings while another finger
frets the string next to it higher — an F `x x 3 2 1 1`, a ukulele B♭ `3 2 1 1`: the strings held at the lowest fret
are neighbours with nothing else between them, the run ends on the last (highest sounding) string, and the string just
before the run is stopped at a higher fret:

```kotlin
val from = atLowest.first()
val to = atLowest.last()
// The index finger laid flat across the thin strings under a higher fretted one, as in a small F; anything else that
// four fingers can hold one string each is drawn as dots, as the table fingers an Em or an A.
val isShortBarre = to - from + 1 == atLowest.size && to == frets.lastIndex && from > 0 && (frets[from - 1] ?: 0) > lowest
if (stopped.size <= MAX_FINGERS && !isShortBarre) return emptyList()
```

placed after the `atLowest.size < 2` check (reusing its `from`/`to`), with `private const val MAX_FINGERS = 4` beside
`MIN_FRETS` (the same four fingers `ChordVoicings` counts). Traced: D `x x 0 2 3 2` → 3 stopped, `[3, 5]` not
neighbours → no barre (three dots); Em `0 2 2 0 0 0` → `[1, 2]` does not reach the last string → no barre; A
`x 0 2 2 2 0` → no barre; F `1 3 3 2 1 1` → 6 stopped → barre 0..5 (unchanged); B♭ `x 1 3 3 3 1` → 5 stopped → barre
1..5 (unchanged, the existing test); Bm `x 2 4 4 3 2` → 5 stopped → barre 1..5; F `x x 3 2 1 1` → `[4, 5]`, last
string, string 3 at 2 → barre 4..5 (unchanged); ukulele G `0 2 3 2` → no barre; ukulele B♭ `3 2 1 1` → barre 2..3;
ukulele D `2 2 2 0` → no barre (the table's `1 2 3 0`); Dmaj7 `x x 0 2 2 2` → string 2 open before the run → no barre
(the table's `0 0 0 1 2 3`). Over the whole table drawn without fingers this draws **no** barre the fingering lacks;
the shapes the rule misses (ukulele `1 1 1 4`, `2 2 2 3`, …) are drawn one dot per string, each a finger, which still
reads as a way to hold them. Update the KDoc of `chordDiagramGeometryOf` ("…or, for a shape that names no fingers, the
lowest stopped fret where the shape takes more than four fingers without one, or where the index finger lies across the
thin strings under a higher fretted one, with no open or muted string under it") and `presentation/CLAUDE.md`'s
"barres from the fingering or from the lowest fret" ("…or from the lowest fret where the shape needs one").

## Tests

`ChordDiagramGeometryTest`, extend `a barre is found from the fingering, or from the lowest fret where nothing says`:
`fretted(listOf(null, null, 0, 2, 3, 2)).barres` is empty and its dots are on strings 3, 4 and 5;
`fretted(listOf(0, 2, 2, 0, 0, 0)).barres` and `fretted(listOf(null, 0, 2, 2, 2, 0)).barres` are empty;
`chordDiagramGeometryOf(ChordVoicing.Fretted(listOf(0, 2, 3, 2)), UKULELE, root = 7)` has no barre and
`ChordVoicing.Fretted(listOf(3, 2, 1, 1))` on the ukulele has the one barre from 2 to 3;
`fretted(listOf(null, null, 3, 2, 1, 1)).barres` is the single barre from 4 to 5 (tighten the existing filtered
assertion); the existing F and B♭ assertions stay. New test `a shape without fingers gets no barre its table fingering
lacks`: over a sweep of roots × qualities (`""`, `m`, `7`, `m7`, `maj7`, `sus2`, `sus4`, `6`, `9`, `dim7`, `aug`,
`m7b5`, `add9`, `5`) on the guitar and the ukulele, for every shape of `ChordVoicings.all` that carries fingers (the
table's), every barre of `chordDiagramGeometryOf(shape.copy(fingers = null), …)` is one of the barres of
`chordDiagramGeometryOf(shape, …)` (same row and strings, the finger aside). It fails today on Em and A and with the
plan's first rule; it passes with this one (checked against `ChordVoicingTables` by script: 0 of 221).

## Manual check

Add `{define: D base-fret 1 frets x x 0 2 3 2}` and `{define: Em base-fret 1 frets 0 2 2 0 0 0}` to a song and open it
on the guitar: the D diagram has three dots and no bar, the Em two dots. `{define: F base-fret 1 frets x x 3 2 1 1}`
keeps its short bar. Export a PDF with chord diagrams: the same.
