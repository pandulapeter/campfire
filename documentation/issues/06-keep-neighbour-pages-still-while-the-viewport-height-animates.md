# Keep the pages beside the one being read at the settled viewport height while the height animates

**Challenged:** amended — as written the neighbours would still recompose `SongLyrics` and re-run
`SongSectionsLayout`'s measure on every frame, since the `onRowsPlaced` lambda reads `maxWidth` through the
`BoxWithConstraints` scope, whose constraints change with every frame of the height; the plan now hoists the viewport
size into a local the lambda captures, so strong skipping keeps the lambda and `SongLyrics` skips. Also notes that the
catch-up at rest is a `SPRING` change (placement-only `animateBounds` off screen) and adds it to the manual check.

**Kind:** performance  ·  **Severity:** low (medium in a short window, where every scroll collapses the title row)  ·  **Platforms:** all
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongPages.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsPage.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SettledViewportTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`

Independent of plans 01–05 (plan 05 also concerns the neighbour pages, but touches only `FontScaleController`).

## Problem

The pager composes and measures the pages on either side of the one being read (`beyondViewportPageCount = 1` in
`SongPages.kt`), all with the pager's constraints. When the viewport height animates (the metronome panel opening or
closing above the pager, or, in a short window, the title row collapsing over the frames of a scroll), every composed
page's `BoxWithConstraints` in `SongDetailsPage.kt` gets a new `maxHeight` on every frame, and the page uses it live:

```kotlin
val isChangingContinuously = rememberContinuousChange(maxWidth, maxHeight, currentFontScale)
...
sectionMotion = if (isChangingContinuously) SectionMotion.GLIDE else SectionMotion.SPRING,
...
onRowsPlaced = { rows ->
    flingBehavior.onRowsPlaced(
        placedRows = rows.belowPadding(topPaddingPx),
        viewport = with(density) { IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()) },
    )
},
rowViewportHeight = maxHeight,
```

The grid is already protected (`availableHeight = settled.height - …`), but for the two neighbours, on each frame:

- `rowViewportHeight` changes, so `SongLyrics` recomposes and `SongSectionsLayout` is measured and placed again, its
  rows padded to the new height and a new `SongRows` handed to `onRowsPlaced`, which re-anchors the neighbour's scroll;
- on the second frame `rememberContinuousChange` turns `GLIDE`, which recomposes every unit of both songs to take
  `animateBounds` off, and 200 ms after the animation ends it turns `SPRING` again and recomposes every unit to put it
  back on.

None of this is visible: the neighbours are off screen. On a phone in landscape, where the title row collapses with
every scroll, this doubles or triples the per-frame layout work of scrolling.

## Fix

Let only the page being read, and the page a swipe is heading to, follow the live height; the others use the height
the viewport last rested at, which `SongDetailsPage` already keeps (`settled.height`).

1. `SongPages.kt`: pass `isFollowingGesture` (already computed there as
   `page == pagerState.currentPage || page == pagerState.targetPage`) to `SongDetailsPage` as a new parameter, e.g.
   `followsViewport: Boolean`, documented as "Whether the page follows the viewport's height frame by frame: the page
   being read and the one a swipe is heading to; the others keep the height it last rested at". Update the comment above
   `isFollowingGesture` to say it covers the viewport height too.

2. `SongDetailsPage.kt`: after `settled.height` is updated, take

   ```kotlin
   val viewportHeight = pageViewportHeight(live = maxHeight, settled = settled.height, followsViewport = followsViewport)
   ```

   with a pure helper next to `settledHeight`:

   ```kotlin
   /** The height a page pads its rows to: the [live] one where it [followsViewport], the [settled] one otherwise. */
   internal fun pageViewportHeight(live: Dp, settled: Dp, followsViewport: Boolean) = if (followsViewport) live else settled
   ```

   and use `viewportHeight` instead of `maxHeight` in all three places above: `rememberContinuousChange(maxWidth,
   viewportHeight, currentFontScale)`, the `viewport` handed to `flingBehavior.onRowsPlaced`, and `rowViewportHeight`.
   The three must agree, since `onRowsPlaced` compares the viewport against the rows it was padded for. Leave every
   other use of `maxHeight` / `maxWidth` alone.

3. **Make the neighbour's `SongLyrics` actually skip.** Today the `onRowsPlaced` lambda computes
   `IntSize(maxWidth.roundToPx(), maxHeight.roundToPx())` inside itself, so it captures the `BoxWithConstraints` scope,
   whose constraints change on every frame of the height; strong skipping then makes a new lambda each frame, `SongLyrics`
   recomposes, and `SongSectionsLayout`'s measure block (a new measure policy, through its own `onRowsPlaced` wrapper)
   runs again - most of the per-frame work this plan is meant to remove. Compute the size outside it:

   ```kotlin
   val viewport = with(density) { IntSize(maxWidth.roundToPx(), viewportHeight.roundToPx()) }
   ...
   onRowsPlaced = { rows -> flingBehavior.onRowsPlaced(placedRows = rows.belowPadding(topPaddingPx), viewport = viewport) },
   ```

   so the lambda captures only `flingBehavior`, `topPaddingPx` and `viewport`, which are equal across the frames of a
   height animation for a page that does not follow it. Check that nothing else handed to `SongLyrics` reads the scope
   (`extraWidth` and `availableHeight` only change with the width and the settled height). The lyrics sit in a vertical
   scroll, so the layout's own constraints do not change with the height either, and nothing below it is measured
   again.

When the viewport comes to rest, `isViewportSettled()` turns true, `settled.height` takes the live height, and each
neighbour sees one change (a `SPRING`, off screen) and re-anchors once through `onRowsPlaced`'s height-only path. When a
swipe makes a neighbour the target page mid-animation, it switches to the live height in one step, which is the same
single change. The page being read behaves exactly as today.

That one change is discrete, so the neighbour is `SPRING` for it and its rows below the first move to the new padding
with `animateBounds`, off screen. Only placements change (no section changes size), so this is far cheaper than the
per-frame relayouts it replaces; do not try to make it a `GLIDE` (a neighbour held in `GLIDE` would flip to `SPRING`,
recomposing every unit, at the start of every swipe to it).

## Tests

In `SettledViewportTest.kt` (pure, `commonTest`):

- `a page that follows the viewport takes the live height`: `pageViewportHeight(live = 372.dp, settled = 400.dp,
  followsViewport = true) == 372.dp`.
- `a page beside it keeps the settled height`: the same with `followsViewport = false` is `400.dp`.

## Manual check

In a setlist on a phone in landscape (a short window), scroll a song so the title row collapses and expands
repeatedly: scrolling should be at least as smooth as on master. Then swipe to the next and previous songs: each must
be laid out for the current viewport (rows filling the screen to the bottom, the reading position at the top of a row,
no row peeking in under the one read) as soon as it is on screen. Open and close the metronome panel, then swipe both
ways: the same. Open and close the panel while a swipe is in progress: the incoming song must follow the panel as it
moves. With a layout inspector or recomposition counts (Android Studio), check that a neighbour's `SongLyrics`
is not recomposed on the frames of the panel animation.

**CLAUDE.md:** in `screens/songDetails/CLAUDE.md`, in the paragraph beginning "A viewport that only changes height —
the metronome panel opening or closing, the short window's title row collapsing —", add that only the page being read
(and the one a swipe is heading to) follows it frame by frame, the pages composed beside it keeping the height the
viewport last rested at, as they keep the settled text size during a pinch.
