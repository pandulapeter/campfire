# Prefer chord shapes that keep the root, and replace the picked table rows that drop it needlessly

**Kind:** bug (content)  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/voicings/ChordVoicingSearch.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/voicings/UkuleleChordTable.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/voicings/MandolinChordTable.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/voicings/OpenTuningChordTables.kt` (`mandolinGdgd`),
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/voicings/BanjoChordTables.kt` (all five tunings),
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/voicings/ChordVoicingTables.kt` (KDoc),
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/voicings/ChordVoicingTablesTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/voicings/ChordVoicingsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/voicings/CLAUDE.md`
**Challenged:** amended — the search now orders its shapes (rooted first) instead of dropping the rootless ones, which
`ChordVoicings.all` hands to the shape picker (`dialogs/ChordShapeSearch.kt`) as the player's alternatives; the default
check of step 3 also covers the chords the tables reach by moving a shape from another root, since a replaced picked
row is such a source; the presentation tests and the UI goldens are run too; the review's `rootless.txt` is not in the
scratchpad, so the scratch script rebuilds the list.

## Problem

42982c6ee appended to every table the shapes of the chords a chart names that it had none for, "picked from the
search's shapes" by the rules in `ChordVoicingTables`' KDoc. No generator is committed (the commit touches only the
tables and the voicings `CLAUDE.md`), and none may be: review and table-making scripts stay in the scratchpad.

The search lets a shape leave out the root whenever the chord has more notes than the instrument has strings, and it
never prefers a shape that keeps it (`ChordVoicingSearch.kt`):

```kotlin
private fun omissions(chord: Chord, strings: Int): Set<Int> {
    val fifth = ((chord.root + 7) % 12).takeIf { 7 in chord.intervals && chord.intervals.size >= 4 && it != chord.bass }
    val root = chord.root.takeIf { 0 in chord.intervals && chord.pitchClasses.size > strings && it != chord.bass }
    return setOfNotNull(fifth, root)
}
```

and orders its shapes by position, then strings sounding, then stretch:

```kotlin
.sortedWith(
    compareBy<ChordVoicing.Fretted>({ position(it.frets) }, { -it.frets.count { fret -> fret != null } }, { stretch(it.frets) })
        .thenComparator { a, b -> compareFrets(a.frets, b.frets) },
)
```

So the "search's first" is often an open-position shape without the root, and `ChordVoicingTablesTest` accepts it
(`it == played.root && played.pitchClasses.size > instrument.tuning.size`). Measured over the tables at 286971fe4
(every row's sounding notes; rows present at `42982c6ee^` counted as typed):

- **92 picked rows leave out the root while sounding another note twice** on non-drone strings, where a string could
  have carried the root: ukulele 11 (E11 `2 2 2 0` = A D F# A; Fmaj13 `2 2 0 0` = A D E A), mandolin 25
  (C13 `2 2 1 0`; E11 `2 0 0 2`), mandolin GDGD 41, banjos 15 (open D Em11 `0 0 1 0 0`). **No typed row does this (0).**
- **About 90 more picked rows leave out the root but keep the fifth** although the chord minus its fifth fits the
  strings, with a rooted shape at the same reach: mandolin C69 `0 0 0 0` (G D A E; `2 0 3 0` keeps C), Cmaj9
  `0 0 2 0` (`5 0 2 0`), ukulele D11 `0 0 0 0` (`0 2 0 3`). The typed ukulele table (chords-db) voices its ninths
  and 6/9s this way on purpose — 384 typed rows, C9 `0 2 0 1`, C69 `0 2 0 0` — and those stay; so this class is a
  preference of the search, not a rule a table test can hold every row to.

Every picked row is shown as its chord's first shape where no typed row comes before it. A reader with a mandolin
sees C6/9 drawn as four open strings, with no C anywhere.

## Fix

1. **The search prefers the root.** In `ChordVoicingSearch.search`, after the "played more quietly" filter, order the
   shapes in three bands, each keeping today's order inside it: the shapes that sound the chord's root (the bass of a
   slash chord already must sound), then the rootless ones that sound no pitch class on two non-drone strings, then the
   rest. Order, do not drop: `ChordVoicings.all` (`table + search(...)`) is the list the shape picker offers
   (`dialogs/ChordShapeSearch.kt`, the Chords tab), and a rootless voicing is a real choice on a four-string instrument
   (the typed ukulele table holds 384 of them); dropping them would take those alternatives away wherever a rooted
   shape exists. `search(...).isEmpty()`, which `ChordVoicingTablesTest`'s five-fret rule asks, is unchanged either way.
   A rooted shape now comes before a rootless open one even when it sits higher up the neck (never past the search's
   twelfth position); that is the point of the change. Leave `omissions` as it is: tightening it as first proposed ("the root may go
   only when the chord minus its fifth has more notes than strings") would leave some chords with no shape at all
   (the reviewer's script found about 80 picked rootless rows with no rooted shape within four frets) and would make
   the search contradict 384 typed ukulele rows. Update `omissions`' KDoc and the search's to say the root is
   preferred, not merely allowed to go. This also changes, at runtime, the alternatives `ChordVoicings.all` appends
   for a chord, and the default of a chord no table holds — both only towards shapes with the root.
2. **Replace the picked rows the search no longer offers first.** For each tuning, the picked rows are the ones not
   in `git show 42982c6ee^:<file>` (and not added by later commits' typed additions — the guitar tables' maj11 and
   11 rows of 7bb603365 and 1d6ef3f4f are typed); rows of earlier sweeps' appended scale chords are picked too, but
   were added before 42982c6ee and are left alone unless the new test fails them. For each picked row of 42982c6ee
   that leaves out its root: replace it **in place** (same line, same chord name, no fingering, as picked rows have
   none) with the new search's first shape for that chord on that tuning, picked by the KDoc's rules (an open-position
   shape where the search's first takes three fingers or fewer below the fourth fret, else the search's first), never
   past the twelfth fret. Where the only shapes are rootless, or the rooted one is past the twelfth fret, keep the
   row unless it doubles a note and a rootless one that does not exists. Write a throwaway scratch test or script in
   the lane's scratchpad folder to list and rebuild the rows (untracked, deleted before the commit; never committed).
   The review's list is not kept (no `rootless.txt` in its scratchpad at the challenge), so the scratch script
   builds the list itself: for each picked row, its sounding pitch classes against the chord's root (the banjo's drone
   not counted), and the new search's first shape for the chord. A corrected shape past fret 12 counts as no fix.
   The script needs nothing committed: a throwaway `commonTest` file that reads the tables through
   `ChordVoicingSearch` (internal, visible to the module's tests) and `git show 42982c6ee^:<file>` saved in the
   scratchpad for the typed rows is enough, run with `./gradlew :chordpro:desktopTest --tests <it>`.
3. **No default of a typed row changes.** For every chord the tables can answer in every tuning, compare
   `ChordVoicings.default(chord, tuning)` before and after (a scratch test run on `HEAD` and on the change, with
   `ChordVoicings.resetCache()` between): every chord name filed in the table, **and** each of those qualities on all
   twelve roots, since `ChordVoicingSearch.tableShapes` also answers a chord by moving a filed shape of the same quality
   from another root (`table.movable`), and a replaced picked row is such a source. A default may change only for
   chords whose shape at `42982c6ee^` came from no typed row (the search's first, or a picked row moved). Stop the plan and
   report if any other default moves. Keep a picked row that 42982c6ee filed to keep a displaced default ("a chord
   whose shape one of them would have displaced has that shape filed under it") even if it is rootless.
4. **Docs.** `ChordVoicingTables`' KDoc, in the paragraph on the chords a chart names: "…then the search's first,
   which keeps the root wherever a shape of the chord does…". The voicings `CLAUDE.md`, the tables bullet: a table
   shape that leaves out the root never sounds another note twice where its chord has a shape that does not
   (`ChordVoicingTablesTest`), and the search offers rootless shapes only where none keeps the root.

## Tests

- `ChordVoicingTablesTest`, a new test over every table: a shape that leaves out its chord's root (bass-less chords;
  the banjo drone not counted) sounds no pitch class on two strings, unless `ChordVoicingSearch.search` finds no shape
  of the chord at or below the twelfth fret that keeps the root or doubles nothing (the same pattern as the five-fret
  test's `search(...).isEmpty()`). It fails the 92 doubling rows today and passes every typed row.
- `ChordVoicingsTest` (or a new `ChordVoicingSearchTest` beside it): the search's first shape sounds the root for
  mandolin C69, mandolin Cmaj9, ukulele D11, ukulele E11, and banjo open D Em11; and for a chord with no rooted shape
  within the search's reach (find one with the scratch script, e.g. among mandolin GDGD's Ab13), the search still
  returns shapes; and `ChordVoicings.all` for mandolin C6/9 still holds the rootless `0 0 0 0`, after the rooted ones.
- `ScaleChordsTest` and the rest of `:chordpro:desktopTest` pass unchanged.
- `:presentation:desktopTest` (`ChordLibraryTest`, `ChordShapeGridTest`, `SongChordsTest`, `ChordSelectionTest`, and
  `ChordDiagramDrawingTest` read `ChordVoicings.all` and `default`) and `:tools:screenshots:test`: a changed
  expectation or golden is accepted only where its chord's default came from the search or a replaced picked row
  (say which in the lane report); any other is a stop.
