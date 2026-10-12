# Twenty-seventh review — song layout and column centering

**Commit:** `21173fed6` on `master`, clean tree. **Angle:** a lean review of the song details layout: the scored
pages, the balanced column count, narrow columns, fitted text, the per-column centering, and the 900 ms
`ColumnCenteringHold` from `6fa66a96b`. It also covers the About card rework in `21173fed6`, and whether the layout part of
`songDetails/CLAUDE.md` still matches the code. Three read-only reviewers ran on Sonnet: the pure algorithm (one of
them ported `arrange` to Python and fuzzed it 200k times), the Compose side, and the About card with the docs. Every
finding was then re-read against HEAD. There was no live run.

## Headlines

- **01:** the fold hold may never hold. It is switched on from a `LaunchedEffect`, which can run after the frame that
  already measures and records the folded centering. Two reviewers found this independently.
- **03:** on one narrow input, `flowIntoPages` leaves the second half of a song with no page end. A block fits, but
  the padding at its cut pushes it over the screen. The rest of the song then collapses into the first page's column.
- Nothing breaks the rule that every line is reachable with Up and Down alone. The centering geometry fuzzed clean:
  there are no negative gaps, no overflow past a page, and no off-by-one at the 45/55 split.

## Index

| #  | Plan                                                                           | Kind        | Severity | Lane |
|----|--------------------------------------------------------------------------------|-------------|----------|------|
| 01 | Arm the centering hold in the frame the fold is drawn in                       | bug         | medium   | H    |
| 02 | Hold pages of several rows where they were after a fold                        | bug         | low      | H    |
| 03 | End a page whose block overflows by its cut padding                            | bug         | low      | C    |
| 04 | Count column gaps in the balanced column search (+ compute fewest wrapping once) | bug / perf | low      | H    |
| 05 | Skip spreading bookkeeping when nothing is centered                            | performance | low      | H    |
| 06 | Correct the About card and centering docs                                      | docs        | low      | H    |

## Lanes

| Lane | Area                         | Plans, in order       | Files owned |
|------|------------------------------|-----------------------|-------------|
| C    | Page cutting                 | 03                    | `SectionCutting.kt`, `SectionPagingTest.kt` |
| H    | Hold, arrange, search, docs  | 01, 02, 05, 04, 06    | `ColumnCenteringHold.kt`, `SectionGrid.kt`, `SongSectionsLayout.kt`, `SongInfoGroup.kt`, `songDetails/CLAUDE.md`, `ColumnCenteringHoldTest.kt` (new), `SectionGridTest.kt` |

**Merge order:** C first, since it is small and self-contained, then H. H is in that order because 02 builds on 01's
hold, 05 reshapes the `arrange` code that 02 extends, and 06 rewrites `CLAUDE.md` after 01 and 02 changed it. Lane C
touches no shared file; 03 adds no `CLAUDE.md` sentence (the paging paragraph next to the one 06 rewrites already
covers a page that runs over).

## Decisions

- **D1 (plan 04): count the gaps between columns in the width a single-row page fills?** *Recommended: yes.* The
  columns and their gaps are what the eye sees as filled. The column count chosen for some songs on wide windows may
  shift, so the manual check compares Bad Moon Rising at 80% and House of the Rising Sun. If **no**, plan 04 only
  hoists the fewest grid's wrapped-line count. — **answered 2026-10-12: count the gaps.**

## Challenge

- **01: amended.** The premise holds for certain on desktop, iOS, and the web, not only possibly. In CMP 1.12.1,
  `FrameRecomposer` queues effect coroutines on a trampoline that nothing flushes between `performFrame` and
  `measureAndLayout`. `SideEffect` runs inside `sendFrame`'s apply, before layout, a pager page's subcomposition
  included. The release is now a plain `delay` then `isHolding = false`. The plan's `if (!hold.isHolding) return`
  would keep the hold on forever if an effect body ever ran before the `SideEffect`, and the generation counter was
  redundant, since a cancelled `delay` never returns. The `List` equality of `foldKeys.toList()` (a `Set<String>` and
  a `Boolean?`) matches `LaunchedEffect`'s per-key `equals`. The spreads it holds come from the last lookahead pass
  before the fold, since only lookahead passes record, and none does while holding.
- **02: amended.** `joinsPrevious` is a `BooleanArray`, so it is kept by reference and compared with
  `contentEquals`. The plan now spells out why the clamp keeps every line reachable by Up and Down: the dividers and
  the resting offsets never shift, and `pageBottoms` shifts with `tops`.
- **03: amended.** A probe run at HEAD confirmed it: the plan's inputs give `rows = [0, 0, 0]`. The "simpler"
  variant (end and `return` at the first end that fails by its padding) would cut off a later end that fits, and so
  change normal songs. With `[80, 95, 3, 40]` it turns three pages into four. Only the deferred report at the
  `return` points is kept, the tests move to `SectionPagingTest` with that second case, and `ColumnFlow` is left
  alone.
- **04: amended.** The gap is `columnGapPx`. `totalWidth` includes the gaps, so counting them in `filledWidth` is
  consistent.
- **05: amended.** It now also skips the `pageShifts` loop and `shiftedPageBottoms` when nothing is centered. Both are
  provably identity there.
- **06: sound.** Every quoted phrase exists at HEAD, and the spreading paragraph is above the paging sentence, so
  "(below)" is wrong as claimed.

## Checked and found solid

- The centering geometry in `arrange`. `freeSpace` and `spaceLeft` never go negative, and the floor division puts the
  remainder below, so nothing overflows by a pixel. A column never ends past `pageTop + max(pageH, centered)`, and
  sections in a cell never overlap. This held over 200k fuzzed cases under the `minRowPitch` the layout actually uses.
- The degenerate cases: an empty song, one section, zero gaps, `centeredRowHeight == 0`, a page exactly full, and content taller
  than the page.
- `pageCount` and `score` are unchanged by spreading. Stepping, through `lineTops` and `SongRows.bottoms`, lands where
  the spread columns are drawn, and `settledScrollTarget` never jumps back to a row start.
- `balancedAmong` cannot pick a grid that is not read whole, and never one that wraps more.
- The scoring cannot produce a NaN, ties are decided deterministically, and the binary search over `evenOut` caps is
  monotone.
- Timing stretches (`concatenated`) keep `columnKey`s unique and start a page each. The fitted text's `couldSaveAPage`
  is conservative on every axis.
- The hold:
  - It is per `SongLyrics` and the pager keys pages by file name, so no song inherits another's hold.
  - Held spreads are clamped, so a held column never overflows.
  - A grid change drops the hold.
  - The editor's preview, the PDF, and the screenshot tool never hold.
- The first frame is centered, because the viewport is known on the first measure. Accessibility traversal still
  follows the song's order.
- The About card:
  - The tile minimum height matches the one-link tile, so the first link does not grow it.
  - The info section in the song layout is the compact chip card, measured by its real composable.
  - RTL, long link names, empty states, and the fades all hold up.
- Every function and constant the layout part of `CLAUDE.md` names exists, with the numbers it states.

## Dropped after verification

- **The link buttons lose 6dp of touch target above the first row and below the last.** Compose's hit test checks
  bounds only on coordinators that carry pointer input. The slack `layout` modifier and the `Row` / `FlowRow` carry
  none, and the button's own minimum touch target extends its hit area. This is the same pattern as an `IconButton`
  inside a clickable card.
- **At a 200% system font, the link button's slack assumes a 36dp button and overflows its reported height by 2dp.**
  Nothing clips it, and the tile's 12dp padding absorbs it, so nothing visible changes.
- **The held geometry is not invalidated by a resize, pinch, or panel change inside the 900 ms.** `arrange` clamps it,
  so it only sits off-center until the release. This is cosmetic.
- **The held path ignores the "at least as much space above and below as a gap" reservation.** It lasts at most 900 ms
  and is clamped, and the recorded gap is never above `maxExtraSectionGap`.
- **Toggling the shared Chords fold starts a hold on composed neighbour pages too, and so does a late or synced
  `foldedSections`.** These pages are off screen, and the old spreads are clamped or empty.
- **`stepSections`'s `indexOfFirst` per page is O(rows × units).** It only matters at the 200-section ceiling, and it
  was not measured to matter.

## Manual checks owed

- **01:** on the desktop, the web, and an iPhone, fold a section in a two- or three-column song with room to spare. The
  headers above stay put for about a second, and two quick folds both land.
- **02:** on a phone held upright, fold a section on a page with two narrow sections side by side. The page stays put,
  then glides.
- **04:** compare column counts against the previous build: Bad Moon Rising at 80% on the laptop, House of the Rising
  Sun at the default window, and a short song maximized.
- **Not a plan:** `tools/screenshots` `Promo.kt`'s `laptop-song-light` still uses `fontScale 1.05`, chosen for the
  old 1.5× laptop density (now 1.32×). Re-check at the next screenshot retake that page one still ends on a section
  boundary.
