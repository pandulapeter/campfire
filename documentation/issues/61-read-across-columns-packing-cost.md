# 61 · Resizing a wide window with "Read across columns" on stalls on songs of many short sections, and every width passed through is remembered

**Severity:** minor (performance; desktop, web and wide tablets only, with the horizontal flow enabled; a song of a couple of hundred sections) · **Area:** `:presentation` (`SongLyrics.kt`: `flowIntoRows`, `SectionMeasurements`) — from commit `639f469c`

## Symptom
Turn on Settings → Songs → "Read across columns", open a song whose lyrics were pasted double-spaced (every line
ends up a paragraph of its own: 200 sections and more) in a window wide enough for three or four columns, and drag
the window's edge. In the web build, which has one thread, the window visibly stalls on every frame of the drag; on
the desktop it is a stutter. Memory grows for as long as the drag lasts and is only given back when the song, the
text size or the density changes.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt:943-961`.
The dynamic program tries every `(start, end, columnCount)` and stacks the candidate row from scratch for each:

```kotlin
for (start in sectionCount - 1 downTo 0) {
    …
    for (end in start + 1..sectionCount) {
        for (columnCount in 1..maxColumnCount) {
            tallest[columnCount - 1] = max(tallest[columnCount - 1], heights[columnCount - 1][end - 1])
            if (stack(start, end, columnCount, tallest[columnCount - 1]) != columnCount) continue   // O(end - start)
```

That is n³/6 · k steps: 200 sections and 4 columns is about 5.3 million per candidate count, up to three candidates
per search. The result is cached by `SectionGridKey`, whose `settledWidth` changes on **every frame** of a window
resize, so the search runs per frame.

The heights it works from are cached in `SectionMeasurements` (`:764-771`):

```kotlin
private val heights = HashMap<Long, Int>()
fun height(index: Int, width: Int, measure: (Int) -> Int) = heights.getOrPut((index.toLong() shl 32) or width.toLong()) { measure(width) }
```

A resize asks about new widths on every frame, none of which comes back, and each adds `sections × widths` boxed
entries that stay until the holder is rebuilt (`remember(sections, fontScale, density)`). `TabRows` was given a bound
for exactly this (`MAX_TAB_WIDTHS`, `af973f9b`); this map was not.

## Fix
Both in `SongLyrics.kt`. The packing has to come out **identical** — same rows, same column counts, same ties — so
the change only stops doing work whose answer is already known; it does not touch the cost function, the order the
candidates are tried in or the tie-break.

1. `flowIntoRows`: carry the first-fit stacking from one `end` to the next, and stop extending a row that can no
   longer become feasible. Two facts make that exact:
   - First-fit is a left to right scan under a fixed cap, so the stacking of `[start, end)` is the stacking of
     `[start, end - 1)` plus one step — unless the added section is the new tallest *and* that raises the cap
     (`minOf(tallest, maxStackHeight)`), in which case the row is stacked again from `start`.
   - In a feasible row of `k` cells every cell is no taller than the row's tallest section: a cell of several sections
     passed `… <= cap` when its last one was added, and a cell of one section is that section. So the heights of the
     row add up to at most `k ×` the tallest section from `start` onwards, whatever `end` turns out to be. The sum
     only grows with `end`, so once it is past that, no longer row with that many columns is feasible either.

   Replace the block from `// heights[k - 1][i] is …` down to the end of the `for (start …)` loop (`:916-961`) with:

   ```kotlin
   // heights[k - 1][i] is the height of section i in a row of k columns, heightSums[k - 1][i] the total height of
   // the sections before i at that width, and tallestFrom[k - 1][i] the height of the tallest section from i onwards.
   val heights = Array(maxColumnCount) { column -> IntArray(sectionCount) { heightAt(it, column + 1) } }
   val heightSums = Array(maxColumnCount) { column ->
       LongArray(sectionCount + 1).also { sums -> for (index in 0 until sectionCount) sums[index + 1] = sums[index] + heights[column][index] }
   }
   val tallestFrom = Array(maxColumnCount) { column ->
       IntArray(sectionCount + 1).also { tallest -> for (index in sectionCount - 1 downTo 0) tallest[index] = max(tallest[index + 1], heights[column][index]) }
   }
   // Calls onCell with the cell of every section in [start, end) when the sections are stacked first-fit into cells
   // as tall as the tallest section of the row, but never taller than the screen, and returns the number of cells.
   fun stack(start: Int, end: Int, columnCount: Int, tallest: Int, onCell: (index: Int, cell: Int) -> Unit): Int {
       …                                                          // body unchanged
   }
   // costs[i] is the smallest total height of the sections from i onwards, rowEnds[i] where their first row ends and
   // rowColumnCounts[i] how many columns that row has.
   val costs = LongArray(sectionCount + 1)
   val rowEnds = IntArray(sectionCount + 1)
   val rowColumnCounts = IntArray(sectionCount + 1)
   // The first-fit stacking of the candidate row, per column count, carried from one end of the row to the next:
   // stacking every candidate from its start again is what makes a song of many short sections cubic, and the
   // search runs on every frame of a window being resized.
   val tallest = IntArray(maxColumnCount)
   val caps = IntArray(maxColumnCount)
   val cells = IntArray(maxColumnCount)
   val cellHeights = IntArray(maxColumnCount)
   val isExhausted = BooleanArray(maxColumnCount)
   for (start in sectionCount - 1 downTo 0) {
       var best = Long.MAX_VALUE
       tallest.fill(0)
       isExhausted.fill(false)
       var exhaustedCount = 0
       var end = start + 1
       while (end <= sectionCount && exhaustedCount < maxColumnCount) {
           for (columnCount in 1..maxColumnCount) {
               val column = columnCount - 1
               if (isExhausted[column]) continue
               val sectionHeights = heights[column]
               // No cell of a feasible row is taller than the row's tallest section, so the sections of a row of
               // this many columns add up to no more than that many times the tallest one still to come. The sum
               // only grows with the row, so past this point no longer row of this many columns has to be tried.
               if (heightSums[column][end] - heightSums[column][start] > columnCount.toLong() * tallestFrom[column][start]) {
                   isExhausted[column] = true
                   exhaustedCount++
                   continue
               }
               val added = end - 1
               tallest[column] = max(tallest[column], sectionHeights[added])
               val cap = minOf(tallest[column], maxStackHeight)
               // A taller section raises the cap, and what was stacked under the lower one may fit into fewer
               // cells under the new one, so the row is stacked again from its start. Otherwise the stacking so
               // far stands, and only the section that was added is placed.
               val from = if (added == start || cap != caps[column]) {
                   caps[column] = cap
                   cells[column] = 0
                   cellHeights[column] = sectionHeights[start]
                   start + 1
               } else {
                   added
               }
               for (index in from until end) {
                   if (cellHeights[column] + sectionGap + sectionHeights[index] <= cap) {
                       cellHeights[column] += sectionGap + sectionHeights[index]
                   } else {
                       cells[column]++
                       cellHeights[column] = sectionHeights[index]
                   }
               }
               if (cells[column] + 1 != columnCount) continue
               val cost = tallest[column] + if (end < sectionCount) rowGap + costs[end] else 0L
               // Ties go to the longer row, so that the slack ends up at the bottom of the song rather than in its
               // middle, and then to the fewer, wider columns, which wrap less.
               if (cost < best || (cost == best && end > rowEnds[start])) {
                   best = cost
                   rowEnds[start] = end
                   rowColumnCounts[start] = columnCount
               }
           }
           end++
       }
       costs[start] = best
   }
   ```

   The reconstruction below it (`:962-978`) stays as it is and keeps using `stack(…) { index, cell -> … }`; since
   that is now the only caller, `onCell` loses its default value. A row of one section and one column is never
   exhausted (`h <= 1 × tallestFrom`), so `best` is always found, as before. Extend the function's KDoc with one
   sentence after "…which is why every length is tried.": "Every length that still can be feasible, that is: the
   stacking is carried from one length to the next rather than redone, and a column count is given up for a row
   once its sections add up to more than that many cells could ever hold."

   This exact loop was checked against the current one in a Python port on 4 000 random inputs (1–14 sections, 2–5
   columns, heights from one line to sixty, gaps 0–20, `maxStackHeight` from 50 to unbounded): identical `rowEnds`,
   `rowColumnCounts` and `costs` every time. On 200 equal sections × 4 columns the inner steps go from 5 333 200 to
   1 190; with a mix of heights from one line to twenty, to 9 071.

2. `SectionMeasurements`: keep the heights per width, unboxed, and let go of the widths of earlier searches once
   there are many of them. `SongLyrics` passes the section count:
   `remember(sections, fontScale, density) { SectionMeasurements(sectionCount = sections.size) }`.

   ```kotlin
   private class SectionMeasurements(private val sectionCount: Int) {

       private val heightsByWidth = HashMap<Int, IntArray>()
       private var lastGridKey: SectionGridKey? = null
       private var lastGrid = SectionGrid(rows = IntArray(0), columns = IntArray(0), columnCounts = IntArray(0))

       /** The intrinsic height of the section at [index] when it is [width] wide. */
       fun height(index: Int, width: Int, measure: (Int) -> Int): Int {
           val heights = heightsByWidth.getOrPut(width) { IntArray(sectionCount) { UNMEASURED_HEIGHT } }
           if (heights[index] == UNMEASURED_HEIGHT) heights[index] = measure(width)
           return heights[index]
       }

       /** The grid decided for [key], which is only searched for again once the key has changed. */
       fun grid(key: SectionGridKey, search: () -> SectionGrid): SectionGrid {
           if (key != lastGridKey) {
               // A window being resized searches at a new width on every frame and none of those comes back, so
               // the widths are only kept until there are more of them than a few searches ask about. They are let
               // go of between two searches and never during one, which asks about the same few over and over.
               if (heightsByWidth.size > MAX_SECTION_WIDTHS) heightsByWidth.clear()
               lastGrid = search()
               lastGridKey = key
           }
           return lastGrid
       }
   }
   ```

   with `private const val UNMEASURED_HEIGHT = -1` and `private const val MAX_SECTION_WIDTHS = 32` next to
   `MAX_TAB_WIDTHS`. One search asks about at most `maxColumnCount` widths (often fewer: every count whose column
   would be wider than `MAX_COLUMN_WIDTH` shares the clamped width), so 32 is several searches' worth. `height` is
   only ever called from inside `search` (`heightAt` and `SectionGrid.height()` are local to it), which is what makes
   clearing in `grid` safe. Update the class KDoc's "Neither of the two is state" paragraph with: "The heights are
   kept per width and only for the widths of the last few searches."

What must not change: `balanceIntoColumns`, `arrange`, the search loop in `SongSectionsLayout`, the tie-break, and
the rule that a cell is capped by `maxStackHeight` but a single taller section still gets a cell of its own.

What this leaves: a search at a new settled width still asks every section for its intrinsic height at every column
count up to the candidate — `sections × widths` real text layouts per resize frame, which is by now the larger part
of that frame. It is inherent in letting every row pick its own column count, and it is only paid while the window's
edge is being dragged.

## Tests
None (UI is untested; `flowIntoRows` is private to `SongLyrics.kt`). While implementing, keep the old loop next to
the new one under another name and compare the two `SectionGrid`s (`rows`, `columns`, `columnCounts` with
`contentEquals`) on a few real songs from a scratch `main` or a temporary `check(…)`; delete it before finishing.

## Verify
1. `./gradlew :app:desktop:run`, Settings → Songs → "Read across columns" on. Take screenshots of three songs (a
   short one, the demo songs, a long one with a tall chorus) at two window widths **before** the change and compare
   after: the rows, the column counts per row, the dividers and the centering must be identical.
2. Make a test song of 250 one-line paragraphs (a line, a blank line, …) plus one 20-line verse in the middle. Open
   it wide and drag the window edge back and forth: no stall; the layout at rest equals the one before the change.
3. Same song with the flow off: unchanged (that path does not call `flowIntoRows`).
4. Text size up to the maximum and back, then a transposition: the grid is rebuilt (new `SectionMeasurements`) and
   matches.
5. `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`,
   then the web build with the same 250-paragraph song and a browser window resize.

## Docs
None. `presentation/CLAUDE.md` describes what the horizontal flow does, not how the packing is searched; the KDoc
changes above carry the rest.

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`

## Depends on
Nothing. Shares `SongLyrics.kt` with plans 54, 56 and 57 — one after another, in any order (56 edits `gridFor` inside
the same `sectionMeasurements.grid(…) { }` block this plan leaves alone; 57 adds a parameter-free `remember` next to
the `SectionMeasurements` one this plan gives an argument to).
