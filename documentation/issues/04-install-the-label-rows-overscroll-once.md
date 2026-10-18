<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Compose a song card's label row once rather than twice as it scrolls in

**Challenged:** amended — premise confirmed (`ScrollState`'s `maxValue` starts at `Int.MAX_VALUE` in Compose 1.12.1, so the drop condition does not apply) and widened to the list grids, which flip the other way; the clip must stay conditional (read in a layer, not composition) so overflowing containers keep their cross-axis overflow; the dispose reset stays a `DisposableEffect`, since a connection has no dispose.

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ContentOverscroll.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/labels/SongLabels.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/CLAUDE.md`

## Problem

`SongLabels` uses `.bounceHorizontalScroll(scrollState)`, and `ContentOverscroll.kt` decides in composition:

```kotlin
if (effect == null || state.canScrollForward || state.canScrollBackward) return this
```

`canScrollForward` derives from `ScrollState.maxValue`, which is written during measure. On a card's first composition the
answer is the pre-measure one; after the first measure a row that fits flips it, and `SongLabels` recomposes the next frame
to add the clip, overscroll and nested scroll nodes. Every tagged card entering the viewport is composed twice.

Checked at Compose 1.12.1: `ScrollState._maxValueState` starts at `Int.MAX_VALUE`, so `canScrollForward` is true before the
first measure and every fitting row flips (the drop condition does not hold). The same decision costs more on the list
screens: a `LazyGridState` reads `canScrollForward == false` before its first measure, so `SongList` / `SetlistList` (which
call `bounceScrollableContent` while composing) take the fitting branch first and recompose their whole body once the
grid turns out to scroll, and again whenever typing or a filter moves the list between fitting and scrolling.

## Fix

Always install the nested scroll connection and the overscroll (keep the `effect == null` early return: desktop and web
have none, and it never flips), and let `ShortContentOverscroll.onPreScroll` / `onPreFling` decide at the gesture whether
the content fits (they already re-check, and return zero while it scrolls).

- **The clip stays conditional.** Today only fitting content gets `clipToBounds()`; a scrolling `horizontalScroll` /
  `verticalScroll` / lazy grid clips its main axis only (`clipScrollableContainer`), so label chips' ripples and focus
  rings and a sheet's sideways overflow are drawn past the cross axis. Replace `clipToBounds()` with
  `graphicsLayer { clip = !(state.canScrollForward || state.canScrollBackward) }`, read in the layer block.
- **The own-pull fade** (`fadingTopEdge { ownPull.towardsStart }`, vertical, no `pull` handed in) is then on scrolling
  sheets too: it reads 0 there and keeps `CompositingStrategy.Auto`, at the cost of one more layer; acceptable, or fold it
  into the conditional layer above.
- **The reset**: a `NestedScrollConnection` has no dispose, so keep `DisposableEffect(usedPull) { onDispose { … = 0f } }`,
  now unconditional. Leaving the fitting state mid-gesture no longer cancels anything (the scope lives on), and
  `onPreFling` springs a pull back to 0 as it does today.
- The second, idle overscroll effect on content that scrolls is never fed (its `onPreScroll` returns zero first), but it is
  drawn as a node: check on Android that an idle stretch draws through without a layer, and on iOS that the idle bounce
  offsets nothing.

There are ~90 callers through `bounceScrollableContent`, `bounceVerticalScroll` and `bounceHorizontalScroll` (grids, every
sheet, the label rows); check a sample of each still bounces only when it fits.

## Tests

None in `commonTest`. Check by hand that a fitting sheet still drags down, a fitting label row still bounces, an
overflowing one still scrolls with the platform overscroll and draws its chips' ripples uncut, and that the song list no
longer recomposes as it turns from fitting to scrolling while a search is typed.
