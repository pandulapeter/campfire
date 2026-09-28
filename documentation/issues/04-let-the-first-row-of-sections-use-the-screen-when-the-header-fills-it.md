# Let the first row of sections use the whole screen when the header leaves too little of it

**Challenged:** amended — the `SongLyrics.kt` KDoc (line ~1329) states the first-row rule too and is updated with it; the pure helper lives in `SectionGrid.kt` (not just a KDoc edit); a boundary test is added. The rule itself holds: `SectionGridKey` only carries the derived number, `maxFirstRowHeight` is read only by `flowIntoRows` (horizontal flow), and `snappedScrollTarget` lets a fling rest at `divider1 - viewport`, where row 0 (at most one screen tall) is whole on screen, so nothing becomes unreadable.

**Kind:** bug (layout)  ·  **Severity:** low  ·  **Platforms:** all; seen on phones in landscape and short desktop windows
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionGrid.kt` (the new helper and the KDoc),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionGridTest.kt`,
`presentation/CLAUDE.md` (the section grid paragraph, if it states the first-row rule)

## Problem

Since 42c970dd the song's header carries the transposition and text-size steppers, stacked, on top of the chip rows,
and the first row of sections is held to what the header leaves of the screen:

```kotlin
// SongLyrics.kt
val maxFirstRowHeightPx = if (maxRowHeightPx == Int.MAX_VALUE) maxRowHeightPx else (maxRowHeightPx - headerHeight()).coerceAtLeast(0)
```

```kotlin
// SectionGrid.kt, flowIntoRows
val maxHeight = if (start == 0) maxFirstRowHeight else maxRowHeight
```

A single section is always allowed a row of its own, so when the header takes most of the viewport — a phone in
landscape has about 330 dp under the app bar, and the header with its chips and steppers takes well over half of that —
no two sections fit side by side under the cap, and **the first section sits alone in a one-column row**, at
`maxColumnWidth`, with empty width beside it, while every later row has columns. It is the first thing the reader sees
of the song. (This may be what `documentation/TO_DO.md`'s "Pixel 10 Pro XL Landscape: Román lány layout could be
optimized" is about; check that song after the fix.)

The cap exists so that the first row can be read with the header above it at the scroll position the song opens at.
When the header leaves less than half the screen, that is not going to happen anyway: the reader scrolls the header
away first, and `snappedScrollTarget` already lets a fling rest anywhere between the top of the song and the point
where row 1's divider meets the bottom of the viewport, which is where the whole first row is on screen.

## Fix

In `SongLyrics.kt`, hold the first row to what the header leaves only while that is at least half the screen;
otherwise give it the same cap as every other row:

```kotlin
val maxFirstRowHeightPx = when {
    maxRowHeightPx == Int.MAX_VALUE -> maxRowHeightPx
    // A header that takes more than half the screen is scrolled away before the first row is read, so that row is
    // held to the screen like the others rather than to the sliver the header leaves, which only one section fits.
    maxRowHeightPx - headerHeight() < maxRowHeightPx / 2 -> maxRowHeightPx
    else -> maxRowHeightPx - headerHeight()
}
```

Name the half as a private constant if the file has a place for such constants. Also update the `SongLyrics` composable's KDoc sentence "the first row is never taller than what the [headerHeight] above it leaves of the screen, since the scroll comes to rest on the top of the song rather than on the top of that row" (line ~1329) to say that holds only while the header leaves at least half the screen. Update `flowIntoRows`' KDoc sentence
"The first row is held to [maxFirstRowHeight] instead, what the song's header leaves of the screen" to say the caller
passes the full height where the header leaves less than half. `flowIntoRows` itself does not change, so its tests
stay as they are; the rule lives in the caller, which is not pure. Drop this plan if the challenger or the executor
finds the first row's cap is relied on elsewhere (the snapping's divider offsets, `SectionGridKey`) in a way this breaks.

## Tests

The rule is in a composable's measure block, so factor the `when` above into a small internal pure function next to
`flowIntoRows` (`firstRowHeightCap(maxRowHeight: Int, headerHeight: Int): Int`) and test it in `SectionGridTest`:
unbounded stays unbounded; header 300 of 900 → 600; header 450 of 900 (leftover exactly half) → 450; header 500 of 900 → 900; header taller than the screen → 900.
Plus one `flowIntoRows` case: nine sections of 280, three columns, `maxRowHeight = 900`, `maxFirstRowHeight = 900`
→ the first row has more than one column.

## Manual check

A phone in landscape (or the desktop window at about 1000 × 450), a song of eight short sections with chords: the
first row has as many columns as the rows after it. Portrait on a phone and a tall desktop window: unchanged (the
first row still fits under the header when the song opens).
