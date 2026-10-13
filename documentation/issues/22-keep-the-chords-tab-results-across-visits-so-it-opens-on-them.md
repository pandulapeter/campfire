# Keep the Chords tab's last results with its key and search, so coming back opens on them and on the scroll position

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — within a session the empty page and the lost scroll only follow once `ChordVoicings`' cache of
defaults has been emptied (it holds 256 and is dropped whole when full, so about seven keys or tunings browsed, or
songs read with as many chords, empty it), not on every return; the problem says so, and the fix is unchanged.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/chords/ChordLibraryState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/chords/ChordsScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/chords/ChordLibrary.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/chords/ChordLibraryTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/chords/CLAUDE.md`

## Problem

The tab's key, search, and scroll position are the session's (`ChordLibraryState`, held by the view model, "since the
tab leaves the back stack whenever another is picked"), but its results are not: `ChordsScreen.kt` at 72d48deb7 starts
every visit from

```kotlin
var results by remember {
    mutableStateOf(tuning?.let { chordLibraryResults(keys, keyIndex, query, notation, it, searchesShapes = false) })
}
```

which is null wherever one chord of the key needs the search (`ChordLibrary.kt`: "it answers null for anything the
tables cannot answer alone"). That is most keys in the open tunings, DADGAD, and drop D (a probe at 72d48deb7: key G is
not answered by the tables in Drop D, Open G, or DADGAD), and the mandolin's and banjo's cross tunings. Within a session the first visit's search fills `ChordVoicings`' cache of defaults, and `needsSearch` is false for a
chord already looked for, so a return is answered by the tables path at once while the cache still holds the key's
chords. But the cache holds 256 defaults and is emptied whole when it fills (`MAX_CACHED_DEFAULTS`; a key is 37 chords,
and every song read adds its own), so after browsing about seven keys or tunings, or reading songs with as many chords, a return to
the tab in such a tuning — from another Tools tab or from Songs — draws the controls over an empty page until the search
ends, as the first visit after the app was restored always does (live run: `107-chords-after-death.png`, then `108-…-later.png`), although
`screens/chords/CLAUDE.md` says "its first frame is already the page".

It also loses the scroll position: `rememberRetainedLazyGridState(state.scrollPosition)` restores the first visible
item's index into a grid that, in that first layout, holds only the controls, so the grid clamps to the top, and the
`DisposableEffect` then saves that top position over the old one. A player who scrolled to `m7b5` in DADGAD and comes
back from a song finds the page at its top.

## Fix

1. Hold the last results in `ChordLibraryState` (`var shown: ChordLibraryShown? = null`, a plain property, not a state:
   only the next visit's first composition reads it), tagged with everything they were found for. With plan 20's
   `ChordLibraryShown(tuning, results)`, add `keys`, `keyIndex`, `query`, and `notation` to it, or add a separate
   `inputs` value; keep it `@Immutable` and comparable.
2. `ChordsScreen.kt`: initialise the screen's results from `state.shown` where its inputs equal the current ones, then
   from the tables (`searchesShapes = false`), then from `state.shown` if it is for the same tuning (a held page, as
   within a visit), else null; write every new result into `state.shown` as it is set. Put the choice in a pure helper
   in `ChordLibrary.kt`, e.g. `initialChordLibraryShown(held: ChordLibraryShown?, inputs, answerFromTables: () -> ChordLibraryResults?)`.
   With the results there in the first layout, `rememberRetainedLazyGridState` restores the scroll position as it is
   meant to.
3. `screens/chords/CLAUDE.md`, "What it shows": the session also keeps the last results, so the tab opens on them; and
   "The cells and the sheet": its first frame is the page wherever the tab was visited in this session, not only where
   the tables answer.

Process death still starts from the tables (the state is the session's, by design), which is the case of
`107-chords-after-death.png`; that first visit after a restore keeps today's empty page until the search ends, and its
cells fade in as they arrive (`listItemAnimation`).

Depends on plan 20 (the tagged type) and touches `ChordsScreen.kt` after plan 21; land 20, 21, then this.

## Tests

`ChordLibraryTest.kt`: the helper returns the held results when their inputs equal the current ones, without calling
the tables; returns the tables' answer when the inputs differ and the tables answer; returns the held results for the
same tuning when the tables do not answer (key changed); and null when the held results are for another tuning and the
tables do not answer.

## Manual check

Chords tab, guitar in DADGAD, key G: wait for the chords, scroll down to the last row. Switch to the Tuner tab and back,
and to Songs and back: the chords are there in the first frame, at the same scroll position. Change the key: the old
chords stay until the new ones arrive (unchanged behaviour).
