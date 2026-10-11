# Keep every other section's measured sizes when a section or a run is folded

**Challenged:** sound

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongUnits.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionFolding.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionSizesPoolTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`

## Problem

`SongLyrics.kt` keys the pool of measured sizes on the folds:

```kotlin
// Everything the height of a section depends on apart from its own content and the width it is measured at, the
// folded runs included. Within one of these the sizes of a section are reused by content, so an edit or a
// transposition only measures the sections it changed.
val sectionSizesPool = remember(fontScale, density, foldedSections, lyricsStyle, headerStyle, defaultLabels) { SectionSizesPool<UnitContent>() }
```

Folding or unfolding anything (a section's pill, a chorus card's title row, a tab or grid run's toggle) goes through
`CampfireViewModel.toggleSectionFold` into `UserPreferences.foldedSections`, so `foldedSections` is a new set and the
whole pool is thrown away. The next layout pass starts from empty `SectionSizes` for every unit of the song and
re-measures every unit at every width the column search asks about (and at the fitted widths too, where the song is
fitted), on the main thread, in the frame that also starts the fold's fade and `animateBounds` spring. On a long song on
a tablet or a desktop window, one tap on a fold re-lays-out the whole song, when only the toggled section changed.

The key is there for a reason: the pool reuses sizes by `UnitContent`, which is only

```kotlin
internal data class UnitContent(
    val section: RenderSection,
    val firstItem: Int,
    val lastItem: Int,
)
```

and a `RenderSection.Lines` does not change when it is folded (the fold lives in `FoldedRuns`, read by
`SongSectionContent`). Without the key, a folded section that is one unit either way (an uncut section, or one with a
single item: `SongUnits.of` makes `0 until itemCount` one range both folded and unfolded) would reuse its unfolded
height, and a section whose tab or grid run is folded would reuse the heights of the run unfolded.

## Fix

Make the folds that can change a unit's size part of that unit's `UnitContent`, and drop `foldedSections` from the
pool's key, so a fold re-measures only the section it belongs to.

1. In `SongUnits.kt`, add a field to `UnitContent`:

   ```kotlin
   internal data class UnitContent(
       val section: RenderSection,
       val firstItem: Int,
       val lastItem: Int,
       /** The keys of [foldedSections][FoldedRuns] that fold this section or a run inside it, see [foldedKeysOf]. */
       val foldedKeys: Set<String>,
   )
   ```

   and say in its KDoc that the folds are part of what a chunk holds, since they change its height.

2. In `SectionFolding.kt`, add a pure helper:

   ```kotlin
   /**
    * The keys of [folded] that fold [section] or one of its runs (`intro#1`, `intro#1/tab#1`), which is all of the
    * folds that can change its height. A key under it that names no run of it (the rest of a chorus recall, keyed
    * `chorus#1/1`) only costs it a measurement it did not need.
    */
   internal fun foldedKeysOf(section: RenderSection, folded: Set<String>): Set<String> = when (section) {
       is RenderSection.Lines -> folded.filterTo(HashSet()) { it == section.foldKey || it.startsWith("${section.foldKey}/") }
       else -> emptySet()
   }
   ```

   The run keys are `"$sectionFold/${nextFoldKey(runName)}"` (`nextRunFoldKey`) and a section's key is its own
   `foldKey`, so the prefix catches every key that can affect the section. Over-inclusion is harmless (one more
   measurement of a section); under-inclusion would reuse a wrong height, so prefer the prefix to anything narrower.
   The Chords section's fold is already part of its content (`RenderSection.Chords.isFolded`), and no other section
   folds, hence `emptySet()`. Do not try to narrow the set to the run keys of one unit's own items: per section is
   simple, and a fold already re-lays-out that section's other chunks.

3. In `SongLyrics.kt`:

   ```kotlin
   val sectionSizesPool = remember(fontScale, density, lyricsStyle, headerStyle, defaultLabels) { SectionSizesPool<UnitContent>() }
   ...
   val sectionMeasurements = remember(units, sectionSizesPool, foldedSections) {
       val foldedKeys = sections.map { foldedKeysOf(it, foldedSections) }
       SectionMeasurements(
           sectionSizesPool.sizesFor(
               units.itemRanges.mapIndexed { unit, items ->
                   val section = units.unitSections[unit]
                   UnitContent(sections[section], items.first, items.last, foldedKeys[section])
               },
           ),
       )
   }
   ```

   (`units` is already re-remembered on `foldedSections`, so the explicit key only documents the dependency.) Rewrite
   the comment above the pool: the folds are no longer one of its keys, they are part of each chunk's content.

`SectionMeasurements` is still rebuilt on a fold (new `units`), so the grid is searched again, which it has to be; the
difference is that every unchanged unit answers from its kept heights. Nothing else changes: the fold's fade, the
spring, and which units exist are as before.

## Tests

In `SectionSizesPoolTest.kt` (pure, `commonTest`), with two real `RenderSection.Lines` built in the test (a header, a
`foldKey` such as `"verse#1"` / `"chorus#1"`, one `SectionPart.Lines` of a lyrics line or two):

- `folding one section keeps the sizes of the others`: `sizesFor` a list of `UnitContent` for both sections with
  `foldedKeys = emptySet()`, write a height into the second one's sizes, then `sizesFor` the list again with the first
  section's `foldedKeys = setOf("verse#1")`: the second one's `SectionSizes` is `assertSame`, and its height is still there.
- `a folded and an unfolded copy of an uncut section do not share sizes`: the same section as one unit
  (`0..itemCount-1`) with `foldedKeys` empty, then with its own key: `assertNotSame`.

And a test of `foldedKeysOf` (in `RunFoldKeyTest.kt`, next to `nextRunFoldKey`'s): for `foldKey = "intro#1"` and
`folded = setOf("intro#1", "intro#1/tab#1", "verse#1", "intro#10")`, the result is `{"intro#1", "intro#1/tab#1"}`
(`intro#10` must not match); for a `RenderSection.Comment` it is empty.

## Manual check

On a desktop window wide enough for three or four columns, open a long song with several sections and a tab run. Fold
and unfold a verse, then the tab run's own toggle, several times quickly: the fold should animate without the hitch
on the first frame that a long song showed before, and the layout after each fold must be exactly what it was before
the change (compare a screenshot of the same fold state taken on master). Fold the song's only section where it is one
unit (a one-line section, or a chorus recall with nothing to show) and check its card shrinks to its title and grows
back when unfolded.

**CLAUDE.md:** in `screens/songDetails/CLAUDE.md`, replace "The folded runs key `SectionMeasurements`, since they change
the sections' heights and with them the columns;" with a sentence saying the folds of each section are part of what
its chunks' measured sizes are kept by (`UnitContent.foldedKeys`), so a fold measures only its own section again, while
the grid is still searched again since the columns change.
