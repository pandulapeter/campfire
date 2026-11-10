<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Measure a unit once for every width wider than its widest line, for the kinds whose height cannot change there

**Challenged:** amended — the probe now also covers a right-to-left chorded line, a section heading pill with its fold
chevron, a card's title row, a folded section, and the Timing line's wrapping values; the clamp reads the maximum
width cached once per `SectionSizes`, so only kinds whose widest line is fixed by their content may be flagged (no
animation or state inside them changes it); plan 20's new minimum width for a tune changes nothing here, since every
chunk drawing a tune stays unflagged. Lane N, last.

**Kind:** performance  ·  **Severity:** high  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/grid/SectionMeasurements.kt`,
`…/ui/songView/SongSectionsLayout.kt` (one line, no growth), the file that builds the `UnitContent` list (after plan 02:
the helper it moved out of `SongLyrics.kt`; before it, `…/ui/songView/SongLyrics.kt`), `…/ui/songView/grid/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/grid/SectionMeasurementsTest.kt`,
a new `presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/UnitHeightPastWidestTest.kt`

Lane N, last: after plan 02 (both build the per-unit list in the same place) and after plans 20 and 21, whose layout
changes this cache must reproduce exactly.

## Problem

`SectionMeasurements` keeps every unit's intrinsic height per exact width:

```kotlin
fun height(index: Int, width: Int, measure: (Int) -> Int): Int = sizes[index].heightsByWidth.getOrPut(width) { measure(width) }
```

and `SongGridSearch` asks for a unit's height at its column's width, clamping only the units on a card:

```kotlin
fun unitWidthFor(unit: Int, columnWidth: Int): Int {
    val section = units.unitSections[unit]
    if (units.cardStarts[section] < 0) return columnWidth
    return minOf(columnWidth, unitsOf(section).maxOf(::maxWidthOf))
}
```

Every candidate column count is a different width, and every frame of a window being resized is a new set of them, so
almost every line of a song — nearly all of them narrower than any column — runs its `maxIntrinsicHeight` again for
each candidate width, though its height is the same at all of them. Measured on song C (6 chorded sections, 3 tunes, a
40-system tab, desktop window): **first open 11,353 intrinsic measurements of which 3,907 distinct**, open again 3,723,
going back with every engraving cached still 11,175, a width change from 3 to 2 columns 8,829; the 16 measure passes of
the first open total 189 ms with a worst pass of 109 ms. A plain song opens with 171.

## Fix

Key the height of a unit by `minOf(width, maxWidth(index))` where its kind is **proven** to measure the same at every
width at or past its maximum intrinsic width, and by the exact width otherwise.

1. `SectionMeasurements` takes, beside the sizes, a `BooleanArray` saying for each unit whether it is flat past its
   widest line (name it for that, e.g. `isFlatPastWidest`). `height` takes the unit's `IntrinsicMeasurable` (or both
   measure functions) instead of one lambda, so it can read the unit's `maxWidth` (already cached in `SectionSizes`)
   and clamp the key for flagged units. `SongSectionsLayout`'s metrics object changes in place, one line for one line —
   the function is 190 lines, past the 120 limit, and may not grow:

   ```kotlin
   override fun height(index: Int, width: Int) = sectionMeasurements.height(index = index, width = width, measure = measurables[index]::maxIntrinsicHeight)
   ```

2. The flags are computed where the `UnitContent` list is built (from the unit's section and item range), in the
   helper plan 02 moves out of `SongLyrics` (the `SongLyrics` function is ~300 lines, past its limit: nothing may be
   added to it). Flag a unit when it is:
   - a chunk of a `RenderSection.Lines` none of whose items is a `SectionItem.RunSlot` of `FoldableKind.VERBATIM` —
     lyric lines (chorded or not), `SectionItem.Inline` comments and key changes, `TAB` and `GRID` run slots, and the
     section's heading. A tab slot answers `rows.naturalWidth` as its intrinsic width and `TabRows.at(width)` treats
     `width >= naturalWidth` as the unwrapped rows, so its height is constant there; text and grid lines no longer wrap
     past their widest line.
   - a `RenderSection.Comment`, `RenderSection.KeyChange`, or `RenderSection.Timing`.

   The clamp reads `SectionSizes.maxWidth`, measured once for as long as the chunk's content is equal, so a flagged kind
   must have a widest line fixed by that content: nothing animated or read from state inside it may change it (the
   part picker's `AnimatedVisibility` does, which is one more reason a tune's chunk stays unflagged).

   **Not flagged**: any chunk holding a `VERBATIM` slot (an ABC tune), since `SongNotationBlock` scales by
   `minOf(unitSize, width / pageWidth)` and a leaving or stand-in engraving made for a wider page than `run.maxWidth`
   (a window just narrowed) makes its height depend on the width past it; `RenderSection.Chords`, `Scales`, and
   `Metadata`, whose custom layouts nobody has proven flat. A kind may be added later with its own probe.

3. Leave the 32-widths eviction in `grid()` as it is: flagged units now hold one entry for every width past their
   widest line.

Must not break: heights identical to today's for every unit (the grid, the pages, and the pedal steps unchanged — this
is a cache key, not a layout change); a tab slot's intrinsic width is still the whole run's; card units keep their
section-wide clamp; "a resized window moves nothing abruptly"; the first frame of a song is laid out as before.

Not in scope: `SectionCutting.kt`'s `flowAt(k)`, which builds `IntArray(unitCount) { heightAt(it, k) }` for the whole
song per column count; with this fix each entry is a map lookup.

## Tests

- `SectionMeasurementsTest`: a flagged unit whose max width is 100 asked for heights at 120, 150, and 300 calls its
  measure once (with 100); an unflagged one calls it once per width; widths under the max width are measured each.
- **Write this test first; for any kind whose probe fails, leave that kind unflagged** (the plan stands for the rest):
  `UnitHeightPastWidestTest` (desktopTest, which has Skia through `compose.desktop.currentOs`) composes, in an
  `ImageComposeScene`, one unit of each flagged kind the way `SongSectionChildren` emits it — a chorded lyric line (and
  one in a right-to-left song, Hebrew lyrics under `LayoutDirection.Rtl`), a line long enough to wrap, a comment, a key
  change, a tab slot (first and last slot of a multi-row run), a grid line, a section heading pill with its fold
  chevron, a chorus card's title row with its first line, a folded section, a `Timing` line with tempo and time (its
  values may wrap as a group) — inside a probe `Layout` that reads `maxIntrinsicWidth(∞)` and asserts that
  `maxIntrinsicHeight(w)` equals `maxIntrinsicHeight(maxIntrinsicWidth)` for `w` from it up to it plus 600 px in steps.
- The existing grid tests (`SongGridSearchTest`, `SectionCuttingTest`, …) and the song details UI tests
  (`SongDetailsFitTest`, `PedalCoverageTest`, `ColumnTurnTest`) pass unchanged.
- Confirm the gain with the sweep's measurement method (an untracked `:tools:screenshots` test with temporary log
  lines counting intrinsic callbacks on song C, deleted afterwards): the first open's intrinsic count should fall from
  11,353 toward its 3,907 distinct ones, and a width change's from 8,829. Report the numbers in the lane report only.
