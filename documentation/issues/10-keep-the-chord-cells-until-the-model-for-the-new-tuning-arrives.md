# Keep the Chords section's cells as they were until the song's model has been collected for the new tuning

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ChordCells.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ChordCellsTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/CLAUDE.md`

## Problem

The song details page builds a `SongLyricsModel` for `SongLyricsInputs.chordTuning` (`SongDetailsPage.kt`:
`chordTuning = chordDiagrams?.tuning`). When the player picks another tuning or instrument, the inputs change and
`rememberSongLyricsModel` keeps the *old* model on screen while the new one is built on `Dispatchers.Default` (its KDoc:
"Every later model is built on Dispatchers.Default whole, with the one before it staying on screen until it is ready").
But the cells are not built from the model alone. `SongLyrics.kt` at 72d48deb7:

```kotlin
val cells = remember(model, chordDiagrams?.tuning, chordDiagrams?.storedShapes, chordDiagrams?.showsDefinitionsOnly) {
    when {
        chordDiagrams == null -> emptyList()
        chordDiagrams.showsDefinitionsOnly -> definitionCellsOf(model.song, chordDiagrams.notation)
        else -> chordDiagrams.tuning?.let { chordCellsOf(model.chords, it, chordDiagrams.storedShapes) }.orEmpty()
    }
}
```

`chordDiagrams.tuning` and `storedShapes` are already the new tuning's, while `model.chords` still carry the old
tuning's `defaultShape` and `definition`. `chordCellsOf` → `selectShape` returns that old `defaultShape` as
`Source.DEFAULT` and the cell draws it against the new tuning's strings. A probe at 72d48deb7 confirmed it: the chords of
`[G]la [C]la` collected for `ChordTuning.GUITAR` and drawn with `chordCellsOf(chords, ChordTuning.GUITAR_DROP_D, …)` give
G as `3 2 0 0 0 3` (standard tuning's shape, which sounds F‑something on drop D strings) where drop D's own G is
`5 2 0 0 0 3`; drawn with `ChordTuning.UKULELE` the cell holds the six-string `3 2 0 0 0 3`. Guitar → keyboard puts a
fretted shape into a keyboard cell.

What the player sees, for the moment the background build takes (longer on a slow phone and in a tuning that needs the
search): Standard → Drop D, every cell crossfades (`changedShapeCells`, same name, same instrument, another tuning) into
a wrong shape, then a second crossfade interrupts it towards the right one; Guitar → Ukulele draws six-string geometry
in four-string cells for a frame or more. A wrong diagram that is visibly animated into breaks "a state that starts on a
wrong initial value is fixed at its source" (root `CLAUDE.md`).

`SongLyricsModel` already says which tuning its chords were collected for (`val chordTuning: ChordTuning?`, "The tuning
[chords] were collected for").

## Fix

1. In `ChordCells.kt`, add a pure helper next to `chordCellsOf`:

   ```kotlin
   /**
    * The cells of [chords] in [tuning], or null while they were collected for another one ([collectedFor]): a model is
    * rebuilt away from the main thread when the tuning changes, and its chords carry the shapes of the tuning they were
    * collected for, which drawn on another tuning's strings are other chords.
    */
   internal fun chordCellsOf(chords: List<SongChord>, collectedFor: ChordTuning?, tuning: ChordTuning, storedShapes: Map<String, String>) =
       if (collectedFor != tuning) null else chordCellsOf(chords, tuning, storedShapes)
   ```

   (or name it `chordCellsOfModel(model, tuning, storedShapes)` taking the `SongLyricsModel`; either is fine, keep it
   pure).

2. In `SongLyrics.kt`, keep the cells last built (a plain holder, not a state, the way `InstrumentChips` keeps
   `LastTuning`) and draw those while the helper answers null:

   ```kotlin
   val lastCells = remember { LastChordCells() }
   val cells = remember(model, chordDiagrams?.tuning, chordDiagrams?.storedShapes, chordDiagrams?.showsDefinitionsOnly) {
       when {
           chordDiagrams == null -> emptyList()
           chordDiagrams.showsDefinitionsOnly -> definitionCellsOf(model.song, chordDiagrams.notation)
           else -> chordDiagrams.tuning?.let { tuning ->
               chordCellsOf(model.chords, model.chordTuning, tuning, chordDiagrams.storedShapes) ?: lastCells.value
           }.orEmpty()
       }
   }
   lastCells.value = cells
   ```

   A tuning of null (an instrument without shapes) still empties the section at once, as today: there is nothing to
   draw and no wrong shape to show. While the old cells are held they keep their own `tuning`, so they are drawn exactly
   as before; when the matching model arrives the cells change once, and `changedShapeCells` crossfades the cells of the
   same instrument once (Standard → Drop D) and swaps the others (Guitar → Ukulele, which `changedShapeCells` already
   leaves out by instrument).

3. `ui/chords/CLAUDE.md`, the paragraph "Everything that drew for an instrument draws for a tuning": add one clause that
   the page keeps the cells it has until the song's chords have been collected for the new tuning, so a tuning picked
   crossfades once, from the old shapes to the new.

Plan 11 also edits `SongLyrics.kt` and `ChordCells.kt` (the `withChordsSection` call); land this one first, the two
changes are next to each other but do not overlap.

## Tests

New `ChordCellsTest.kt` in `commonTest/.../ui/screens/songDetails/`:

- chords collected with `songChordsOf(ChordProParser.parse("[G]la [C]la"), ChordNotation.STANDARD, ChordTuning.GUITAR)`:
  `chordCellsOf(chords, ChordTuning.GUITAR, ChordTuning.GUITAR_DROP_D, emptyMap())` is null;
  `chordCellsOf(chords, ChordTuning.GUITAR, ChordTuning.GUITAR, emptyMap())` equals the three-argument `chordCellsOf`.
- `collectedFor = ChordTuning.GUITAR`, `tuning = ChordTuning.UKULELE` is null.
- `collectedFor = null` (a model built without a tuning) with any tuning is null.

## Manual check

On a phone, open a song with several chords (G, C, D, Em), the Chords section unfolded. Tap the tuning chip and pick
Drop D, then Open G, then back to Standard: each pick crossfades every changed cell once, straight into the right shape
(drop D's G is `5 2 0 0 0 3`, never `3 2 0 0 0 3` in between). Pick Ukulele from the instrument chip, then Keyboard:
the cells change once, with no six-string grid in a ukulele cell and no fretted grid where the keyboard goes.
