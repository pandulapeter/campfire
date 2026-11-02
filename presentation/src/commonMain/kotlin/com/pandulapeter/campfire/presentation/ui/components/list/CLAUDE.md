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

### The fast scroller (`FastScroller.kt`)

- The only scrollbar, on both lists; the letter bubble only on songs. Its column overlays the grid's end padding, so
  cards end before it while sticky headers run under it; the column count always leaves room for it.
- The column is there only while the list scrolls. It animates when the user's doing changes that, and is taken as is
  on first measure and while the library arrives. Decided as the grid is measured, with the flag of the composition
  that gave it its items (a coroutine would hear of the last batch after the screen recomposed as loaded); "loaded" is
  the list's own `isLibraryRead`, since songs are indexed off the main thread and land after `isLoading` turns off.
- **The column's width is read only while laying out** (`FastScrollerColumn.width` is a reader, as are the Done
  buttons' bottom rooms), so the spring re-lays out the cards and composes nothing. Readers are remembered and compared
  by identity, so the grid keeps its measure policy.
- A press centers the thumb: at once for a mouse, but **for touch only after a long press or a mostly vertical move past
  slop**, because the column is on Android's back-gesture edge. A tap jumps only on lift; a lift the system consumed for
  back is ignored.
- Thumb and track fade by draw alpha, not a `graphicsLayer`: a layer would clip the bubble, and the web build doesn't
  redraw a layer whose alpha alone changed.
- The bubble is as tall as the app bar's pill (48dp), the letter at its far end under the pill's last button (assuming
  New is detached), so the finger on the thumb doesn't cover it.
- Position is estimated from average item size, excluding the pinned header. The estimate shifts as rows of other
  heights scroll in, so a drag scrolls by the thumb's step plus a bounded share of the error, never against the thumb;
  only a jump over a screen and the two ends are placed absolutely.
- Haptics (touch only): a tick per row, a notch per section letter and at the ends, the end decided by where the finger
  asked the thumb to be. Pointer events go through a conflated channel read once per frame, so they can't cancel the
  frame wait or a scroll.

### Sticky section headers

- No background or divider; only the content takes touches, and **neither the header nor its menu button is
  focusable**: a lazy grid keeps a focused item placed after scrolling away, which stops it drawing later pinned
  headers.
- The pushed-away pinned header is a visual copy outside the grid, so the grid's clip can't cut it. The row extends
  behind the fast scroller so pill and app bar reach measure from one edge.
- Clicking a header scrolls to its section start, keeping `SECTION_START_ROOM` above it, where nothing of the section
  before shows: the pushed header's fraction is measured to the content's start, and `ListTopFade` thins its fade near
  a section start only for that section's cards.
- A grid with sticky headers goes to its top with `animateScrollToTop`, never `animateScrollToItem(0)`, whose guessed
  distance is corrected at every header, so the scroll stalls and restarts.
- `ListTopFade` caches an inverse gradient and draws with `DstOut` in its existing layer, never a shader per card.
