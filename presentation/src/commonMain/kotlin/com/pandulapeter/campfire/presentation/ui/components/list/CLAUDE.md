<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/components/list

The list scaffolding every list screen shares: columns, placeholders, the top fade, the fast scroller, and sticky
headers.

### The fast scroller (`FastScroller.kt`)

- The only scrollbar, on both lists; the letter bubble only on the song list. The `FAST_SCROLLER_WIDTH` column overlays
  the grid's end padding, so cards end before it while sticky headers run under it. The column is there only while the
  list scrolls (`FastScrollerColumn.kt`), so a short list is balanced; it animates when the user's doing changes that,
  and is taken as is on first measure (kept in `ScrollPosition`) and while the library arrives, the thumb's fade with
  it. Decided as the grid is measured (`measuresFastScrollerColumn`), with the flag of the composition that gave it its
  items: a coroutine would hear of the last batch after the screen recomposed as loaded. "Loaded" is the list's own
  `SongGroups.isLibraryRead` / `ListedSetlists.isLibraryRead`, since songs are indexed off the main thread and land
  after `isLoading` turns off. The column count always leaves room for it, so only card widths change.
- **The column's width is read only while laying out** (`FastScrollerColumn.width` is a reader): the grid's end padding
  (`only(deferredExtraEnd = …)`), the headers' `endPadding`, and `LocalListItemTrailingKeylineAdjustment` (a `() -> Dp`
  provided once per column) all read it in measure or placement, as do the Done buttons' animated bottom rooms
  (`deferredExtraBottom`), so the spring re-lays out the cards and composes nothing. Readers are compared by identity
  and remembered, so the grid keeps its measure policy.
- A press centers the thumb: at once for a mouse, but **for touch only after a long press or a mostly vertical move past
  slop** (`awaitThumbPress`), because the column is on Android's back-gesture edge. A tap jumps only on lift, and a lift
  the system consumed for back is ignored.
- Thumb and track fade by draw alpha, not a `graphicsLayer`: a layer would clip the bubble, and the web build doesn't
  redraw a `ModulateAlpha` layer whose alpha alone changed.
- The letter bubble is a surface pill reaching out of the thumb, as tall as the app bar's actions pill (48dp), the
  letter in primary at its far end under the pill's last button (`CLOSED_PILL_LAST_ACTION_END_INSET`, which assumes
  New is detached), so the finger on the thumb doesn't cover it.
- Position is estimated from average item size, excluding the pinned sticky header (listed out of order).
- Haptics (touch only): a tick per row, a notch per section letter and at the ends (`FastScrollerDetent.kt`,
  `detentNotch`), the end decided by where the finger asked the thumb to be; read once a frame.

### Sticky section headers

- No background or divider; only the content takes touches, and **neither the header nor its menu button is focusable**:
  a lazy grid keeps a focused item placed after scrolling away, which stops it drawing later pinned headers.
- The pushed-away pinned header is a visual copy outside the grid, so the grid's clip can't cut it, keeping its pinned
  width. The row extends behind the fast scroller so pill and app bar reach measure from one edge.
- Clicking a header scrolls to its section start, where the list keeps `SECTION_START_ROOM` above it. Nothing of the
  section before shows in that room: the pushed header's fraction is measured to the content's start
  (`pushedHeaderVisibleFraction`), and `ListTopFade` thins its fade near a section start only for that section's cards.

### Scrolling to the top

- A grid with sticky headers goes to its top with `animateScrollToTop`, never `animateScrollToItem(0)`, whose guessed
  distance is corrected at every header, so the scroll slows down and starts again. It re-estimates the remaining
  distance each frame and covers the easing curve's share of it.

### Scrolling performance

- `ListTopFade` caches an inverse gradient and draws with `DstOut` in its existing layer, translating rather than
  rebuilding a shader per card.
- `FastScroller` consumes a conflated channel once per frame so pointer events can't cancel the frame wait or a scroll;
  animation values are read while drawing.
