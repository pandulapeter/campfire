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
  the grid's end padding, so cards end before it while sticky headers run under it. The column stays whether or not the
  list scrolls, so rows never reflow.
- A press centers the thumb: at once for a mouse, but **for touch only after a long press or a mostly vertical move past
  slop** (`awaitThumbPress`), because the column is on Android's back-gesture edge. A tap jumps only on lift, and a lift
  the system consumed for back is ignored.
- Thumb and track fade by draw alpha, not a `graphicsLayer`: a layer would clip the bubble, and the web build doesn't
  redraw a `ModulateAlpha` layer whose alpha alone changed.
- Position is estimated from average item size, excluding the pinned sticky header (listed out of order).
- Haptics (touch only): a tick per row, a notch per section letter and at the ends (`FastScrollerDetent.kt`,
  `detentNotch`), the end decided by where the finger asked the thumb to be; read once a frame.

### Sticky section headers

- No background or divider; only the content takes touches, and **neither the header nor its menu button is focusable**:
  a lazy grid keeps a focused item placed after scrolling away, which stops it drawing later pinned headers.
- The pushed-away pinned header is a visual copy outside the grid, so the grid's clip can't cut it, keeping its pinned
  width. The row extends behind the fast scroller so pill and app bar reach measure from one edge.
- Clicking a header scrolls to its section start.

### Scrolling performance

- `ListTopFade` caches an inverse gradient and draws with `DstOut` in its existing layer, translating rather than
  rebuilding a shader per card.
- `FastScroller` consumes a conflated channel once per frame so pointer events can't cancel the frame wait or a scroll;
  animation values are read while drawing.
