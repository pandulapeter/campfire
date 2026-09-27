<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 17 — Give a song row's tag strip an offscreen layer only when it overflows

| | |
|---|---|
| Lane | C |
| Impact | medium (GPU memory and fill; higher on web, desktop and iOS) |
| Confidence | medium–high |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/Tags.kt |
| Depends on / conflicts with | — |
| Commit message | `Composite a song row's labels offscreen only when they overflow the row.` |

## Problem
`Tags.kt:126-128`:

```kotlin
private fun Modifier.horizontalFadingEdges(scrollState: ScrollState) = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent { ... }
```

This modifier is on `SongLabels`, which is drawn in every song row that has a tag, a language or the "Add tag" pill. Outside performance mode `onAddTag` is non-null on the songs screen (`SongsScreen.kt:399`), so that means every row.

- The offscreen compositing is unconditional.
- On Android it becomes a hardware texture per row: about 1080 × 84 px × 4 B ≈ 350 KB, so around 5 MB for a screenful. A texture is allocated as each row scrolls in.
- On the Skia targets (desktop, iOS, web) it is a `saveLayer` per row, every time the scene is drawn.
- The layer is only needed for the `BlendMode.DstIn` fades, and those are drawn only when `scrollState.maxValue > 0`. The typical row (a tag or two plus "Add tag") fits, and draws no fade at all.

## Fix
Read the overflow in the layer block. That is a draw-phase read, so it costs nothing in composition:

```kotlin
private fun Modifier.horizontalFadingEdges(scrollState: ScrollState) = graphicsLayer {
    // Only a row that overflows draws a fade, and only a fade needs the offscreen layer.
    compositingStrategy = if (scrollState.maxValue > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
}.drawWithContent { ... unchanged ... }
```

`drawWithContent` already draws nothing when both fades are 0, so a row that fits looks exactly as it does now. Update the KDoc's sentence about needing the offscreen layer ("which needs the offscreen layer") to say it is only taken while the row overflows.

What must not change: the fade must still go to the card's color, not to a hole, whenever the row does overflow.

## Verification
- `./gradlew :presentation:desktopTest`, `:app:desktop:run`, and the Android debug build.
- Manual check:
  1. Find a song with many tags, so its row overflows, and scroll the row sideways. The fade must still go to the card color, in light and dark theme.
  2. A row with one tag must look unchanged.
- Measurement: turn on Android "Debug GPU overdraw" / "Show hardware layers updates", or use Layout Inspector's layer view. Rows that fit should no longer show a hardware layer. Alternatively, compare a GPU profile of a fling on the web build.
