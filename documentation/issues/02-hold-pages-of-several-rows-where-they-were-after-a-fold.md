# Hold a page of several rows where it was after a fold, as the single-row pages are

**Challenged:** amended — `joinsPrevious` is a `BooleanArray` on `SectionGrid`, so the hold keeps a reference to it and compares it with `contentEquals` (as it already does `columnCounts`) rather than a `List<Boolean>` copy; and the clamp is spelled out as keeping a held page inside the room it is centered in, which is what keeps every line reachable by Up and Down.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all; mostly a phone held upright and narrow windows
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionGrid.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ColumnCenteringHold.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongSectionsLayout.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionGridTest.kt`
**Depends on:** 01 (same files; run after it)

## Problem

`SectionGrid.arrange` centers a page holding joined rows (`joinsPrevious`, a pair of narrow sections set beside each
other under or over another row) by moving it down whole, and the hold does not cover it:

```kotlin
val pageShifts = IntArray(pageTops.size) { page ->
    if (isSingleRowPage[page]) 0 else ((centeredRowHeight - (pageBottoms[page] - pageTops[page])) * SPACE_ABOVE_PERCENT / 100).coerceAtLeast(0)
}
```

Folding a section (or a tab/grid run) on such a page shrinks the page by d and moves everything on it, the tapped header
included, down by 0.45·d at once — the miss on a second tap that `ColumnCenteringHold` was written to prevent. The
KDoc of `arrange` and `CLAUDE.md` say a fold "leaves the columns where they were" without this exception.

## Fix

- `SongArrangement` gains `pageShifts: Map<Int, Int>` — the shift of every page of several rows, keyed by the row
  that starts it (stable while the rows are), recorded only where `centeredRowHeight > 0`.
- `arrange` gains `heldPageShifts: Map<Int, Int>? = null`. For a page of several rows with a held value, the shift is
  `held.coerceIn(0, max(0, centeredRowHeight - pageHeight))` (as far as it still fits, like the held column top);
  otherwise as today. The clamp is what keeps the rule that every line is reachable with Up and Down: the page then
  still ends within `centeredRowHeight` of its top, which is no more than the readable height of a row, and
  `pageBottoms` (the steps' `SongRows.bottoms`) and `tops` are shifted by the same held value, while the dividers and
  the resting offsets are not shifted at all, as today.
- `ColumnCenteringHold` records `pageShifts` beside `spreads` and also the grid's `joinsPrevious` beside
  `columnCounts`, both by reference (a grid's arrays are never written after it is built, and `columnCounts` is
  already kept that way); its held lookup returns both only when `columnCounts` *and* `joinsPrevious` are
  `contentEquals` to the current grid's (a different pairing means different pages, so the first row of a page names
  another page). Simplest shape: `heldSpreads(columnCounts, joinsPrevious)`
  and `heldPageShifts(columnCounts, joinsPrevious)`, or one small `HeldArrangement` class holding both — implementer's
  choice, keep it in `ColumnCenteringHold.kt`.
- `SongSectionsLayout.kt` passes both into `arrange` and records both where it records `spreads` today.
- Update `arrange`'s KDoc ("A page of several rows is moved down whole … instead") to say a held one keeps its shift,
  and the hold paragraph of `CLAUDE.md`.

## Tests

`SectionGridTest`: a grid with a page of two rows (one row joined to the one before), `centeredRowHeight` larger than
the page; arranging with `heldPageShifts` of that page's first row keeps the recorded shift (tops equal the unheld
tops of a taller version of the page); a held shift larger than what the page now leaves is clamped to it; without a
hold the shift is 45% as before.

## Manual check

On a phone held upright, open a song whose page shows two narrow sections side by side above or below another section
with room to spare; fold one of them. The page stays put for about a second, then glides to its new center.
