# 56 · On a phone every line of a song is laid out twice whenever the song is opened, transposed or resized

**Severity:** performance (all platforms, every window narrower than two columns — every phone in portrait; on every open, every transposition tap and every frame of a pinch) · **Area:** `:presentation` (`SongLyrics.kt`, `SongSectionsLayout`)

## Symptom
Open a long song (a few hundred lines) on a phone, or in the editor's preview on a phone. The page appears late; a
swipe to the next song of a setlist hitches as the neighbour pages are composed (`beyondViewportPageCount = 1` in
`SongDetailsScreen.kt:325`, so three pages at a time); every tap on the transposition stepper and every frame of a
text size pinch does the same work again. It is worst on low-end Android, old iPhones and the web build's single
thread. Nothing is wrong on screen — the cost is a whole second text layout of the song that nobody reads.

## Cause
`SongSectionsLayout` decides a `SectionGrid` before it measures anything. With one column the grid needs no heights
at all — every section is row 0, column 0 — but `gridFor(1)` still asks every section for its intrinsic height,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt:690-701`:

```kotlin
fun gridFor(columnCount: Int) = if (isHorizontalFlow && columnCount > 1) {
    flowIntoRows(…)
} else {
    List(measurables.size) { heightAt(it, columnCount) }.balanceIntoColumns(columnCount, sectionGapPx)
}
```

On a window under two minimum column widths (`MIN_COLUMN_WIDTH` 384dp × `fontScale`, plus the gap and the padding:
roughly 800dp) `maxColumnCount` is 1 (`:674`), and both branches of the search reach `gridFor(1)`:

```kotlin
if (availableHeightPx > 0) {
    var candidate = 1
    var candidateGrid = gridFor(candidate)                       // heights of every section, thrown away
    while (candidate < maxColumnCount && candidateGrid.height() > availableHeightPx) { … }   // never entered
    candidateGrid
} else {
    gridFor(maxColumnCount)                                      // the same
}
```

`heightAt` is `Measurable.maxIntrinsicHeight`, which for a `Text` is `ParagraphLayoutCache.intrinsicHeight` (and
the multi paragraph twin of it for the chorded lines, which pass `onTextLayout`). In Compose 1.12.0 that runs a full
`layoutText(…)` and keeps only the resulting `Int` (`cachedIntrinsicHeight`), not the paragraph, so the real
`measure()` at `:728-731` lays every paragraph out again at the very same width. `SectionMeasurements` does not help:
it is rebuilt for every new `sections` list (a transposition), `fontScale` (every frame of a pinch) and `density`,
and its grid is searched again whenever the width or the available height changes (the header's height arriving a
frame after the first measurement is one such change on every open).

The heights are genuinely needed once two columns are possible: `candidateGrid.height()` has to know whether the song
fits one column before trying two. That case is not what this plan is about and stays as it is.

## Fix
One change, in `SongLyrics.kt`, inside the `sectionMeasurements.grid(gridKey) { … }` block of `SongSectionsLayout`.

1. Make `gridFor` answer a single column without asking for any height. Replace the function at `:690-701` with:

   ```kotlin
   fun gridFor(columnCount: Int) = when {
       // A single column is every section stacked in its order, however tall each of them is, so it is the one
       // grid that is known without an intrinsic measurement. Asking for the heights anyway lays every line of
       // the song out once for a number nobody reads and then once more to be drawn, and on a window too narrow
       // for a second column - which is every phone - this is the only grid there is.
       columnCount == 1 -> singleColumnGrid(measurables.size)
       isHorizontalFlow -> flowIntoRows(
           sectionCount = measurables.size,
           maxColumnCount = columnCount,
           heightAt = ::heightAt,
           sectionGap = sectionGapPx,
           rowGap = rowGapPx,
           maxStackHeight = if (availableHeightPx > 0) availableHeightPx else Int.MAX_VALUE,
       )
       else -> List(measurables.size) { heightAt(it, columnCount) }.balanceIntoColumns(columnCount, sectionGapPx)
   }
   ```

   The order of the branches matters: `columnCount == 1` has to come before `isHorizontalFlow`, which is what the
   old `isHorizontalFlow && columnCount > 1` said.

2. Add the helper next to `balanceIntoColumns` (after the `SectionGrid` class is fine too), so that the result is
   exactly what `balanceIntoColumns(1, …)` returns today — including for a song with no sections, where all three
   arrays are empty (`maxColumnCount` is coerced to 1 for an empty song, so that case always comes this way):

   ```kotlin
   /** The grid of a layout one column wide: every section in the one cell of the one row, in their order. */
   private fun singleColumnGrid(sectionCount: Int) = SectionGrid(
       rows = IntArray(sectionCount),
       columns = IntArray(sectionCount),
       columnCounts = if (sectionCount == 0) IntArray(0) else intArrayOf(1),
   )
   ```

3. Leave the search loop alone. With `maxColumnCount == 1` the `while` condition fails on `candidate < maxColumnCount`
   before `candidateGrid.height()` is evaluated, so no height is asked for. With `maxColumnCount > 1` the first
   thing the loop does is `candidateGrid.height()`, which asks for exactly the heights `gridFor(1)` used to — they
   are in `SectionMeasurements.heights` either way, so the wide-window path does the same work as before.

What must not change:
- Do not replace the intrinsic pass of the wide-window path with a trial `measure()`: a `Measurable` may be measured
  once per pass, and the winning width is only known after the search.
- Do not skip `sectionMeasurements.grid(…)` for the single-column case; it is what keeps `lastGrid` and `lastGridKey`
  coherent when the window later grows to two columns.
- `balanceIntoColumns` keeps handling `columnCount == 1` correctly on its own; it just is not called with it any more.

## Tests
None (UI is untested). `singleColumnGrid` and `balanceIntoColumns` are private to `SongLyrics.kt` in `:presentation`.

## Verify
1. `./gradlew :app:desktop:run`, make the window narrow (under ~800dp), open a long song. The page must look exactly
   as before: one column, centered, the same gaps; resize slowly across the point where a second column appears and
   back — the sections must still animate into two columns and back into one.
2. Turn "Read across columns" on in Settings → Songs and repeat: one column narrow, rows when wide.
3. Open the editor on the same narrow window, switch to Preview: same single column.
4. Open a song that has no sections at all (a file holding only `{title: x}`): no crash, empty page under the header.
5. To see the saving, temporarily wrap the `measure = measurables[index]::maxIntrinsicHeight` argument of `heightAt`
   in a counter (or put a breakpoint on it) and confirm it is not hit at all on a narrow window while it still is on
   a wide one. Remove the counter again.
6. `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`

## Docs
`presentation/CLAUDE.md`, the `screens/songDetails/SongLyrics.kt` bullet says "the candidates are evaluated from the
sections' intrinsic heights (the winner is the only width they are measured at, …)". Add after that parenthesis:
"and a window with room for a single column asks for none of them, since one column is the same grid whatever the
heights are". The KDoc of `SongSectionsLayout` gets the same clause at the end of its "The candidate column counts
are evaluated with the sections' intrinsic heights…" paragraph.

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. Plans 54, 57 and 61 edit the same file (`SongLyrics.kt`) and must be scheduled one after another with this
one; 61 rewrites `flowIntoRows` and the `SectionMeasurements` cache, not `gridFor`, so the order between them is free.
