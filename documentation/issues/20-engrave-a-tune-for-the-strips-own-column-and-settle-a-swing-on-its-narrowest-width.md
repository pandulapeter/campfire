<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Give a tune the strip's own column, never ask a page wider than the widest column, and settle a swing on its narrowest width

**Challenged:** amended — a settled width now ratchets down to any narrower column the tune is placed in later (a fold,
an edit, or chords switched off after settling would otherwise leave it scaled below the text size until the window or
the text size changed); the clamp applies to every answer, a remembered width included; `minWidth` joins the
`SongNotations` instance's `remember` keys; the UI test's fill check skips each tune's last system; placed in lane N
after 12, 03, and 05 and before 02, which adds `minWidth` to its measure token.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongNotationBlock.kt`,
`…/ui/notation/SongNotations.kt` (`NotationRun`, `SongNotations`, `rememberSongNotations`),
`…/ui/notation/NotationPlacement.kt`, `…/ui/notation/CLAUDE.md`, `…/ui/songView/grid/CLAUDE.md` (the strip bullet on
sections that do not wrap), `…/ui/songView/CLAUDE.md` (only if a line about a tune's intrinsic width needs it),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationPlacementTest.kt`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SheetMusicColumnTest.kt` (new),
`tools/screenshots/src/test/resources/songs/campfire_tests-a_long_tune.cho` (new)

(`…` = `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation`.) Lane N, after plans 12, 03, and 05,
before 21, 14, 02, and 01. Plan 05 has already removed the write from composition and hoisted `base`, so the call this
plan changes reads, before it,
`val width = placement.widthFor(tunes, narrowestWidth, availableWidth, unitSize, rememberedWidth = cache?.placedWidthFor(base))`;
plan 12 has changed `SongNotations.of` to take the tune's text. Plan 02, after this one, adds `NotationRun.minWidth` to
the token it measures a tune's chunks by. `rememberSongNotations` (about 100 lines after 12, 03, and 05) must stay
under the 120-line limit: move the effect's body into a private suspend function if it would pass it.

## Problem

The owner's screenshot (a 1512 × 938 dp macOS window, text size 135%, turned by column, a guitar score of a notation
staff over tablature): the engraved systems are ~308 dp wide in columns placed ~655 dp apart, so half of every column
is empty, the tune takes about twice the columns and steps it should (ten dots), and a third column is cut by the
window's edge. Reproduced offscreen (`runAppTest`, `ImpersonatedPlatform.WINDOWS`, so the app's dp are the window's
over the desktop's `interfaceScale` 0.85; numbers below are pixels of that test, a dp of the window each). Three causes
chain together.

**1. A tune claims the widest column as its least width, so a strip widens its column to that.**
`SongNotationBlock` (`songView/SongNotationBlock.kt:79–90`) has no intrinsics of its own, so asked for its minimum
intrinsic width (infinite width) it answers `naturalWidth`:

```kotlin
// Asked how wide it would be, it answers the widest column a tune can have, not the page it was engraved for, so
// its column is never narrowed to an engraving that is about to follow the column.
val naturalWidth = ceil(run.maxWidth).toInt()
```

`run.maxWidth` is `MAX_COLUMN_WIDTH × fontScale` less the card padding (`SongNotations.kt:306`). That is right for the
*maximum* intrinsic width (a column narrows to its widest content), but the strip reads the *minimum* one as "a section
that does not wrap" and widens the tune's column to it (`grid/SongGridSearch.kt:165–168`):

```kotlin
private fun stripColumnWidthOf(unit: Int, totalWidth: Int, columnCount: Int) = maxOf(
    columnWidthFor(totalWidth, columnCount),
    unitsOf(units.unitSections[unit]).maxOf { metrics.minWidth(it) }.coerceAtMost(totalWidth - columnGapPx),
)
```

A tune reflows (it is engraved again for any width); it is not a tab staff. Logged at 135%: the strip's own columns are
485 px (3 readable) or 360 px (4 narrow, the one taken), the tune's column 642 px (560 dp × 1.35 × 0.85, logged as the
section's `minWidth`), its slot `maxOf(374, 642 + 14) = 656` (the owner's 655). The slots no longer tile the view
(1,484 px): two of them and 172 px of a third are in view — the cut-off third column (plan 21 covers the same
non-tiling for a real wide staff).

**2. The text before the first engraving asks for a page as wide as the screen.** The ABC text is a preformatted run
whose longest line is 4,906 px, so the strip widens its column to the view less a gap (1,470 px), the text reports
1,450 px (`SongSectionContent.kt:389`, `placeText`), and `NotationPlacement.widthFor` engraves for it:
`placed=1263` units at 135% (`1705` at 100%), wider than any column a tune can have. That engraving, scaled down into
a 360 px column, is a quarter of its height, so the next decide finds the whole song fits one page (`pagedCounts=[4]
pages=1`), it is not a strip, the tune lands in a 344 px paged column and is engraved again — a whole-song layout jump and
an engine run for nothing.

**3. The swing guard falls back to the narrowest column, then the strip puts it in the widest.**
`NotationPlacement.widthFor` (`notation/NotationPlacement.kt:62–72`):

```kotlin
val width = when {
    isSettled -> narrowestWidth
    placed == null -> lastWidth ?: narrowestWidth
    placed != lastWidth && placed in engravedWidths -> {
        isSettled = true
        narrowestWidth
    }
    else -> placed
}
```

Fresh open at 100% in the same window (deterministic; logged):

| engraved for (units) | the layout then                                                   | placed at (px → units) |
|----------------------|-------------------------------------------------------------------|------------------------|
| 264 (guess)          | text in a strip column widened to the view                        | 1450 → 1705            |
| 1705                 | scaled to nothing: the song is read whole, 3 paged columns        | 456 → 536              |
| 536                  | still scaled: read whole in 5 narrow columns                      | 268 → 315              |
| 315                  | a strip; the tune column widened to the widest (cause 1)          | 456 → **536, seen**    |
| **264** (settled)    | the same strip: 224 px of systems in 456 px columns               | —                      |

The revisit is not a two-width oscillation: it is a monotone settling that passes 536 twice because two transient
layouts were decided on scaled-down heights. Settled, nothing follows the column again. At 135% a fresh open happens to
converge (1263 → 299 → 542, the systems 622 px in 642 px columns, 6 dots, the third column still cut off by cause 1);
the owner's 270-unit staff (310 px) in 642 px columns is that same settled state, reached by a path through the text
size or the window this reproduction did not replay. The paged layout (turned by page) at 100% trips the guard too
(305 → 391 → 315 → 391 seen → 264 in 333 px columns, 67% full).

Before-pictures, offscreen:
`/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/abb50f13-ea2c-47e6-bbd1-16b6d4895b8d/scratchpad/layout-bug/before-at100-3.png`
(100%, settled on the narrowest) and `…/before-at135-3.png` (135%, converged, third column cut).

## Fix

All three, in this order; each is small. A prototype of exactly these (minimum intrinsic width 0, the clamp, the
narrowest of the swing) was run on the owner's song: at 135% the tune takes the strip's 360 px columns, engraved for
296 units (340 px of systems in each), four whole columns on screen, nothing cut off, 8 stops instead of the owner's 10,
two engine runs instead of three; at 100% five 285 px columns filled (311 units), 3 stops instead of 6, three runs
instead of four; turned by page at 100% settles on 315 units (86% of a 333 px column) rather than 264 (67%).

**1. A tune's least width is the narrowest page** (`SongNotationBlock.kt`, `SongNotations.kt`). Give `NotationRun` a
`minWidth: Float`, the narrowest page in pixels (`narrowestWidth * unitSize` in `rememberSongNotations`, carried by
`SongNotations` beside `maxWidth`; `NONE` passes 0f), and add it to the keys of the `remember` that builds the
`SongNotations` instance (`remember(tunes.isEmpty(), rendered, standIns, unitSize, maxWidth, minWidth, …)`): the
narrowest page follows the window wherever it is narrower than the narrowest column, which neither `unitSize` nor
`maxWidth` always does. Pass `SongNotationBlock`'s `Layout` an explicit `MeasurePolicy`
that keeps today's `measure` and overrides only `minIntrinsicWidth` to `ceil(run.minWidth).toInt()`; the default
`maxIntrinsicWidth` (measure at infinite width → `naturalWidth`) and the intrinsic heights (measure at a width) stay. Say
in the KDoc why: the tune re-engraves for any column, so no column should widen for it, while asking the widest as its
natural width still keeps a column from narrowing to it. The strip then gives a tune `columnWidthFor(totalWidth,
count)`, the same as the lyrics, and `stripFor` chooses the count by steps as it does for everything else. The paged
search is unaffected (`wideWidthFor` only uses a minimum width wider than `maxColumnWidth`, which a tune never was).

**2. Never engrave for more than the widest column** (`NotationPlacement.kt`, `SongNotations.kt`). Add
`widestWidth: Int` to `widthFor` (passed `widestWidth ?: narrowestWidth` from `rememberSongNotations`, and part of the
`context` key, now four values, or the reset misses a new widest) and clamp **every** answer, not only `placed`: the
`when`'s result is `.coerceIn(narrowestWidth, maxOf(narrowestWidth, widestWidth))`, so a remembered width
(`rememberedWidth`, `lastWidth`) is clamped too. `maxOf` keeps the range valid where the window is narrower than the
narrowest column (a 360 dp phone at 200%: both widths are then the window's page, and the answer is that). The text widened by its own unwrapped lines then asks
for the widest column's page (542 at 135%), which scales into the strip's column by 0.66, not 0.27, and stays a strip.

**3. A swing settles on the narrowest width it swung through, not the narrowest column** (`NotationPlacement.kt`).
Keep the widths engraved for in order since the context reset (a `MutableList<Int>`, cleared with `engravedWidths`; a
repeat of the last one is not appended). When `placed` is a width engraved before and not the last, settle on the
minimum of the list from `placed`'s first index to the end, and keep answering that while settled:

```kotlin
placed != lastWidth && placed in engravedWidths -> {
    settledWidth = engravedOrder.subList(engravedOrder.indexOf(placed), engravedOrder.size).min()
    isSettled = true
    settledWidth
}
```

Why it cannot scale a staff down where the swing came from renderings alone: each width of that run of the list was
placed by the layout the one before it made, so the narrowest one's layout placed it at its successor's width (or at
`placed`), which is no narrower.

**But the layout can change after settling within the same context** — a section folded or unfolded, an edit in the
editor's preview, chords switched off, a transposition rewrapping a chorded line — none of which resets the context
(narrowest, widest, page width, unit). Today's guard settles on the narrowest column, which no later column is
narrower than; a settled width wider than that can then sit in a narrower column, scaled down below the text size
until the window or the text size changes (what `d7c707bb1` forbids). So **while settled, ratchet down**:

```kotlin
isSettled -> minOf(settledWidth, placed ?: settledWidth).also { settledWidth = it }
```

A settled width only ever falls, never below the narrowest column, so it ends after finitely many engravings and
cannot oscillate; a wider column later leaves the narrower engraving in it unscaled (as today's narrowest does). Each
step down is placed at once (`isOnlyPlaced`, data arriving), as every placement is. Update the class KDoc's
second paragraph accordingly ("…settles on the narrowest width of the swing, which every layout of it holds unscaled,
and follows a column narrower than that down, never back up").

**Considered, not recommended here.** (a) Engraving every candidate width the search tries: an engine run per width
(4–30 ms each) on every decide, against one or two runs now. (b) Answering a tune's height at a width narrower than its
engraving as reflowed (height × engraved width / width) rather than scaled down, so the search never decides "read whole"
on a shrunken tune (the transient layouts in the table): it removes the remaining extra run at 100%, but the slot would be
measured taller than it is drawn during the 300 ms a resize or a pinch waits, leaving space under the staff that the
"systems scaled meanwhile" rule exists to avoid; worth its own plan only if the transients still show after this one.
(c) Keeping the guard's narrowest column but letting the strip narrow a tune's column to its engraving: the column then
follows the engraving, the exact feedback the guard was built to stop.

Must not break: notation on its first frame for a song placed before (`NotationCache`, `SheetMusicReopenTest`); a
tune's first rendering is data arriving (placed at once, `SongNotations.arrival`); a column never narrows to an
engraving (the maximum intrinsic width is unchanged); a tab staff still widens its strip column (only the notation
block's minimum changes); plan 05's write of the placed width.

Docs: `ui/notation/CLAUDE.md`'s placement bullet ("a layout swinging back to a width engraved before settles on the
narrowest" → on the narrowest width of the swing, and follows a narrower column down after; "A tune asks its column for the widest page, so no column narrows to
it" + "and for the narrowest as its least width, so a strip gives it its own column"; "never wider than the widest
column"); `ui/songView/grid/CLAUDE.md`'s strip bullet "A section that does not wrap (a staff)" — say an engraved tune is
not one: it is engraved for the strip's column.

## Tests

**Write the UI test first; drop the plan if it passes on master** (it fails at 100% as above).

- `NotationPlacementTest` (commonTest): rename and change `a layout swinging back … settles on the narrowest column` —
  after 300 → 500 → 400 and a placement back at 1,000 px (500 units, engraved before) it answers **400** (the narrowest
  of the swing), and still 400 after a placement at 800 px; `a new page width follows the placement again` expects 400
  where it now expects 300. Add `a placement wider than the widest column is engraved for the widest`:
  `place(first, widthPx = 5_000)` with `widestWidth = 600` answers 600. Add `a swing that passes a width twice settles
  on the narrowest it passed since`: widths 1,705 → 536 → 315 → 536 (the table, scaled to the test's unit) answers 315.
  Add `a settled width follows a narrower column down and never back up`: settled at 400 (as above), a placement at
  700 px (350 units) answers 350, then one at 1,000 px still 350; and a placement under the narrowest column answers the
  narrowest.
- `SheetMusicColumnTest` (`tools/screenshots`, new): `runAppTest(window = TestWindow(1512.dp, 938.dp), platform =
  ImpersonatedPlatform.WINDOWS, pageTurn = PageTurn.COLUMN, songs = listOf("campfire_tests-a_long_tune.cho"))`, open the
  song, wait for idle, then for each tune piece in view (the node with the run's content description, slot 0, and the
  pieces after it): its width is at most the widest line column's in view plus a card's padding (no column wider than
  the strip's), and in `captureWindow()` the rightmost non-background pixel inside its bounds is past 90% of its width
  (the engraving fills its column) — for every piece but the one holding the tune's last system, which abc2svg may
  leave short. Run it at 100% and at `fontScale = 1.35f`. The song file: a short grid section
  (`{start_of_grid: Outro}`) and one `{start_of_abc}` tune of about 60 bars in 4/4 with dense chords, `%%score (1 2)`,
  `V:1 treble-8 nm="Guitar"` and `V:2 tab stafflines=6 strings=E2,A2,D3,G3,B3,E4 nostems nm="Guitar"` (the tab voice
  syntax is in `libs/abc/implementation`'s tests), `I:linebreak $` so abc2svg breaks systems by width. Never the owner's
  song.
- Run `./gradlew desktopTest` and `./gradlew :tools:screenshots:test`; `StripPlacementTest`, `SongGridSearchTest`, and
  `ColumnTurnTest` must pass unchanged.
