<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Draw the song cards' two edge fades by painting the known color behind them, without an offscreen layer per card

**Challenged:** amended — the top fade's paint covers only the card's own rounded rectangle, not its cell: cells overlap while cards travel (a sort, the search's travel, a setlist row dragged under the top), and background painted over a cell's padding would cover the neighbour passing beneath, which the mask never touched; a test for it.

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all (GPU-bound; confirm on Windows)  ·  **Run on:** Windows (to confirm the gain), any machine to implement
**Files:**
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/labels/SongLabels.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/songCard/SongListItem.kt` (hands the card color to the labels)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/list/ListTopFade.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/CLAUDE.md` (Edge fades)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/labels/CLAUDE.md`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/list/CLAUDE.md`
- `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongCardFadesTest.kt` (new)

## Problem

Two per-card fades each take an offscreen layer, which on a GPU is a render target of the node's size allocated, drawn
into, and composited every frame the card is drawn:

- **The label row's sideways fade**, on every Songs card whose labels overflow (`SongLabels.kt:109-110`):

  ```kotlin
  private fun Modifier.horizontalFadingEdges(scrollState: ScrollState) = graphicsLayer {
      compositingStrategy = if (scrollState.maxValue > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
  }.drawWithContent { drawContent() … drawRect(brush = …, blendMode = BlendMode.DstIn) … }
  ```

  The row stands on the card, an opaque `Surface` of `containerColor` (`SongListItem.kt:137`, `surfaceContainerLow`
  by default; labels are shown on Songs cards only, `labels/CLAUDE.md`), so masking to transparent and then showing the
  card through is the same picture as painting the card's color over the edge.
- **The fade under the list's top**, on every card within the fade's reach while the list is scrolled
  (`ListTopFade.kt:206-208`):

  ```kotlin
  .graphicsLayer {
      compositingStrategy = if (position.top - fade.pullPx < fade.heightPx && fade.strengthAt(position.top) > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
  }
  ```

  then a `DstOut` gradient. A row of cards (up to one per column) is in it at any scroll position, and
  `strengthAt` (`:101-125`) maps `listState.layoutInfo.visibleItemsInfo` into a new list per faded card per frame (in
  the layer block and again in the draw). Unlike the labels, these cards fade to **the screen's background**, not the
  card's color: "the cards are gone entirely under the header's row, so its text is read against the screen's
  background" (`ListTopFade` KDoc). Behind every list item is the top level screen's `Surface` of
  `MaterialTheme.colorScheme.background` (`TopLevelScreenSurface.kt:60`), the background warmth included (it is applied
  to the scheme, `CampfireTheme.kt:75`).

`components/CLAUDE.md` already states the rule this follows: over a known opaque color, paint that color rather than
mask, "since a mask costs a viewport-sized offscreen buffer every frame"; it lists `ListTopFade` among the exceptions,
and `labels/CLAUDE.md` says the row masks with `DstIn` in an offscreen layer. The offscreen measurement cannot see the
GPU's share (it records draw commands only), so the gain is to be confirmed on the owner's Windows laptop.

## Fix

1. **Labels:** `SongLabels` gains `fadeColor: Color` (the card's container color, passed by `SongListItem`, which has
   it as `containerColor`). `horizontalFadingEdges` drops the `graphicsLayer` and draws, after `drawContent()`, a
   horizontal gradient from `fadeColor` to `fadeColor.copy(alpha = 0f)` over each edge, with the same widths as now
   (`HORIZONTAL_FADE_WIDTH`, as far as is left to scroll). Read the color through `rememberUpdatedState` inside the draw,
   so a theme cross-fade (the scheme is interpolated every frame) redraws without recomposing; cache the brushes in a
   `drawWithCache` keyed on size and color.
2. **Top fade:** `fadingUnderListTop` drops the layer and draws, after the content, the inverse of today's mask as
   paint: a vertical gradient from `background` (strength as alpha) at the covered height to transparent at the fade's
   end, in viewport coordinates as now (`translate(top = -top)`). **Not over the whole cell**: the mask only ever erased
   the cell's own pixels, so its transparent padding left whatever was beneath, but cells overlap while cards travel
   (`listItemAnimation`'s placement on a sort or the search's travel, a setlist row dragged towards the top), and paint
   over the padding would cover the card passing under it. So for the song and setlist cards the paint is the card's
   own shape, `MaterialTheme.shapes.medium` inset by the cell's `cardPadding` (`fadingUnderListTop` takes the inset
   and the shape; the position it records stays the cell's, so `strengthAt` and the section comparisons are
   unchanged). The other faded items (the divider, the add-songs hint, the random picks question, a setlist's summary,
   Choose songs, Show archived) have no surface; they paint their own bounds, and a card passing under one of them
   inside the fade band for a placement's few frames is accepted, or keep their layer if the test below shows it. `background` comes from `MaterialTheme.colorScheme.background` via `rememberUpdatedState`.
   Compute `strengthAt` once per frame for the list, not per card: a `derivedStateOf` in `ListTopFade` over the list's
   scroll, pull, and header positions, which the cards read in draw (the per-card part is only the
   `cardTop < sectionStart.top + beforeContentPadding` comparison).
3. **Check each place the modifier is used** (`SongListRow.kt:100`, `SongList.kt:347`, `:366`, `:384`,
   `SetlistSongRow.kt:155`, `SetlistList.kt:276`, `:338`, `:355`): every one must have only the screen's background
   behind it. Cases to look at: a setlist row being dragged (raised, its shadow outside its bounds: the paint covers
   only the cell, as the mask did), list items fading in (`listItemAnimation`'s alpha applies to the paint too, over the
   same background: same result), the Android overscroll stretch (draws the cards, and the paint, higher: the existing
   `pullPx` handling stays), and the two screens of a tab cross fade (each paints its own background under its cards).
4. **Docs:** `components/CLAUDE.md`'s Edge fades: take `ListTopFade` out of the masks' list and say it paints the
   screen's background; `labels/CLAUDE.md`: the row's edges paint the card's color, no layer; `list/CLAUDE.md:52`
   ("caches an inverse gradient and draws with `DstOut` in its existing layer"): paints the background, no layer.

Alternative kept for the record: leave the mask but share one layer for the whole list (an overlay over the grid that
spares the sticky headers). Rejected in the `ListTopFade` KDoc already, since the headers are items of the same list.

## Tests

New `SongCardFadesTest.kt` (pixels, `screens/songs`): `` `a card's label row that runs past its end fades into the card's color` ``
(a song with many tags; the pixel at the row's end edge is the card color, a pixel 30 dp in is a pill's outline) and
`` `a card scrolled under the list's top fades into the screen's background` `` (scroll by a card's height; the pixel at
the top of the first visible card under the header row is the background color), each in light and dark and with a
background warmth set. `` `a card travelling under another in the fade keeps its own pixels` `` (scroll so a row sits in the fade band, change
the sort, pause mid-travel; a pixel of a card under another card's padding is that card's color, not the background).
Existing: `SongCardFitTest.kt`, `SongListAccessibilityTest.kt` (the press guard under the fade
is unchanged), and `commonTest`'s `listTopFadeStrength` / `sectionStart` tests.

**Measure:** plan 01's scenarios will not move much (draw is recording only there; expect the `pop:*` and `tab:*` draw
part to drop slightly, since no layer is set up). The real measure is on Windows (plan 02 step 2's frame counter): scroll
the Songs list of the committed library at full speed with the fast scroller for 10 s and compare long frames before and
after.

## Manual check

On the owner's Windows laptop, the scroll above with skiko's long-frame log, and a look at the fades in light, dark, and
with warmth, mid theme change too (switch the theme while the list is scrolled).
