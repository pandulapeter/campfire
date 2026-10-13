# Tag the Chords tab's results with the tuning they were found for, and never page a stale default into the shape sheet

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — the pager's new rule now also drops a `DEFAULT` shape when the tuning has no shapes at all (the
plan kept `listOfNotNull(selection.shape)` for an empty list, which still paged a stale default), and step 2 says
outright that an instrument without shapes shows its message whatever results are held.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/chords/ChordsScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/chords/ChordLibrary.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/chords/ChordLibraryCell.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ChordShapePager.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ChordShapePagerTest.kt` (new),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/chords/ChordLibraryTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/chords/CLAUDE.md`

## Problem

`ChordsScreen.kt` at 72d48deb7 keeps the results on screen until the next ones are found:

```kotlin
var results by remember {
    mutableStateOf(tuning?.let { chordLibraryResults(keys, keyIndex, query, notation, it, searchesShapes = false) })
}
LaunchedEffect(keys, keyIndex, query, notation, tuning) {
    results = tuning?.let {
        chordLibraryResults(keys, keyIndex, query, notation, it, searchesShapes = false)
            ?: withContext(Dispatchers.Default) { chordLibraryResults(keys, keyIndex, query, notation, it) }
    }
}
```

and draws them with the *current* tuning:

```kotlin
is ChordLibraryResults.Chords -> if (tuning != null) {
    items(items = shown.chords, …) { chord ->
        ChordLibraryCell(…, chord = chord, tuning = tuning, selection = selectShape(chord, tuning, storedShapes),
            onClick = { viewModel.showDialog(DialogType.ChordReference(chord)) })
```

Holding results is right across a key or a search change (`screens/chords/CLAUDE.md`: "a key that needs the search keeps
the chords on screen until it is answered"), but not across a tuning or instrument change: each held `SongChord` carries
the old tuning's `defaultShape`, which `selectShape` returns as `Source.DEFAULT` and the cell draws on the new tuning's
strings. A probe at 72d48deb7: the G of key G in standard tuning is `3 2 0 0 0 3`; drop D's own first G is
`5 2 0 0 0 3`, open G's `0 0 0 0 0 0`, DADGAD's `5 x 0 0 2 0`, the standard shape is in none of their shape lists, and
for key G none of the three is answered by the tables alone (`searchesShapes = false` gives null), so the stale cells
stay for as long as the search takes. Guitar → ukulele or keyboard puts six-string shapes in a four-string grid or a
keyboard cell meanwhile (the keyboard is answered at once, so for one frame).

Worse, a tap in that window opens `ChordReferenceSheet` with the stale chord, whose `selectShape` again yields the old
default as `Source.DEFAULT`, and `ChordShapePager.kt` puts any shape it does not list in front:

```kotlin
when {
    selection.source == SelectedShape.Source.DEFINED || all.isEmpty() -> listOfNotNull(current)
    current == null || all.any { it.sameShapeAs(current) } -> all
    else -> listOf(current) + all
}
```

Page 1 is then standard tuning's G drawn on drop D strings (it sounds an F-based chord, not G). Paging away and back to
it settles on it, and since it is not `shapes.first()`, `setChordVoicing` stores it under `guitar_drop_d` — a wrong shape
that every song then draws and that sync carries to the player's other devices.

## Fix

1. **Tag the results** (`ChordLibrary.kt`): `@Immutable internal data class ChordLibraryShown(val tuning: ChordTuning, val results: ChordLibraryResults?)`.
   In `ChordsScreen`, hold a `ChordLibraryShown?` instead of `ChordLibraryResults?`, built with the tuning it was
   computed for in both the initial `remember` and the `LaunchedEffect`.
2. **Draw held results with their own tuning, and do not open a sheet from them.** In the `Chords` branch, draw
   `ChordLibraryCell(tuning = shown.tuning, selection = selectShape(chord, shown.tuning, storedShapesOf(shown.tuning)))`
   where `storedShapesOf` reads `userPreferences.chordVoicings[shown.tuning.voicingKey]`, so the old cells stay exactly
   as they were (right for the strings they name) until the new ones arrive, which then crossfade in (step 4). While
   `shown.tuning != tuning`, make `onClick` do nothing (the cells are on their way out). Size the columns by the held
   tuning too (`chordCellMinWidth(shown?.tuning?.instrument ?: tuning?.instrument)`), so held cells keep their grid.
   An instrument without shapes (`tuning == null`) shows its message whatever `shown` still holds: check `tuning ==
   null` before looking at `shown` (today the `Chords` branch's `if (tuning != null)` draws nothing for that frame, and
   the message comes with the null results), so held cells are never drawn under an instrument without diagrams.
   (The alternative, showing no cells until the new ones arrive, empties the page for the length of a search, which the
   tab is built to avoid; not recommended.)
3. **Never page a stale default into the sheet** (`ChordShapePager.kt`): extract the `options` decision into a pure,
   tested `internal fun shapePagerOptions(selection: SelectedShape, all: List<ChordVoicing>): List<ChordVoicing>` and
   prepend `selection.shape` only where `selection.source == SelectedShape.Source.PLAYER` (a stored shape the app does
   not list, which is what the comment above it describes); a `DEFAULT` the list lacks is dropped, so the pager opens on
   the app's real first shape:
   ```kotlin
   val current = selection.shape
   when {
       selection.source == SelectedShape.Source.DEFINED -> listOfNotNull(current)
       selection.source == SelectedShape.Source.PLAYER && current != null && all.none { it.sameShapeAs(current) } -> listOf(current) + all
       else -> all
   }
   ```
   The empty list is no longer a case of its own: a `DEFAULT` with no shapes in the tuning gives no pages (the
   placeholder), where today's `all.isEmpty() -> listOfNotNull(current)` would page a stale default too. That changes
   nothing outside the stale case: `ChordVoicings.default` is `all(...).first()` for the same chord and tuning (tables,
   then search), so a current `DEFAULT` is always `all.first()` or null, on the song page and in the editor's sheet as
   in the tab; `DEFINED` (a song's `{define}`, also the undrawn definition `ChordShapeSheet` shows on request) and
   `PLAYER` (read for the sheet's own tuning, whatever plan 03 changes in `ChordVoicings.read`) page as today.
   `sameShapeAs` stays private beside it. This makes the store safe whatever reaches the sheet.
4. **Crossfade the cell between shapes on their own tuning** (`ChordLibraryCell.kt`): the `Crossfade` targets
   `selection.shape` but draws it with the `tuning` parameter, so the outgoing shape is drawn on the new strings for the
   length of the fade. Target `selection.shape to tuning` and draw each pair with its own tuning.
5. `screens/chords/CLAUDE.md`, "The cells and the sheet": after "a key that needs the search keeps the chords on screen
   until it is answered", add "drawn for the tuning they were found for, and not opened, after the tuning or the
   instrument changes".

Plans 21 and 22 also edit `ChordsScreen.kt` (and 22 `ChordLibraryState.kt`); land this one first. Plan 25 edits the KDoc
of `ChordReferenceSheet.kt`, which this plan does not touch.

## Tests

- New `ChordShapePagerTest.kt` (`commonTest/.../ui/dialogs/`): with `all = ChordVoicings.all(G, ChordTuning.GUITAR_DROP_D)`,
  `shapePagerOptions(SelectedShape(standardG, Source.DEFAULT), all) == all` (the probe's case, `standardG` being
  `ChordVoicings.default(G, ChordTuning.GUITAR)`); the same shape as `Source.PLAYER` comes first, then `all`; a `PLAYER`
  shape already in `all` gives `all`; `Source.DEFINED` gives the defined shape alone, with or without `all`; an empty
  `all` gives the `PLAYER` shape alone, and nothing for a `DEFAULT` shape; a `DEFAULT` of `all.first()` gives `all`.
- `ChordLibraryTest.kt`: for key G, `chordLibraryResults(…, ChordTuning.GUITAR)`'s G default is not among
  `ChordVoicings.all(G, ChordTuning.GUITAR_DROP_D)` — pins the reason the tag is needed (optional).

## Manual check

On a phone, Chords tab, key G, guitar in standard tuning. Pick Open G from the tuning chip: the cells stay as they were
(standard shapes) and then crossfade to open G's, never showing a standard shape in between; tapping a cell during the
wait does nothing. Then, in Drop D, tap G: the sheet's first page is `5 2 0 0 0 3`. Page to the second shape and back to
the first: the first song with a G (Songs → a song) still draws drop D's first G. Switch Guitar → Ukulele → Keyboard
from the instrument chip: no six-string grid ever appears in a ukulele or keyboard cell.
