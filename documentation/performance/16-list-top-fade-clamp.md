<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 16 — Stop the top-of-list fade from redrawing every card on every scroll frame

| | |
|---|---|
| Lane | C |
| Impact | medium |
| Confidence | medium–high |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/EdgeFade.kt |
| Depends on / conflicts with | — |
| Commit message | `Redraw only the cards under the top of a list as it scrolls.` |

## Problem
`EdgeFade.kt:93-113` (`Modifier.fadingUnderListTop`) is put on every card of both list screens. Its call sites are `SongsScreen.kt:387`, and `SetlistsScreen.kt:336, 420, 442, 462, 478`.

```kotlin
.onPlaced { position.top = it.positionInWindow().y - fade.viewportTop }
.graphicsLayer { compositingStrategy = if (fade.strength > 0f && position.top < fade.heightPx) ... }
.drawWithContent {
    drawContent()
    val strength = fade.strength
    if (strength > 0f && position.top < fade.heightPx) { ... }
}
```

Here is what happens on every scroll frame:

- The lazy grid places every visible item again, so `onPlaced` runs for every card. That is one `positionInWindow()` walk each, plus a write of a new `position.top`, which is a `mutableFloatStateOf`.
- Once the list has been scrolled at all (`strength > 0`), the draw block reads `position.top` for every card, not only for the one or two actually under the fade.
- So the draw of every visible card is invalidated on every frame. Each card's layer is re-recorded: the Surface background, the clip, the title and artist text, and the painters. A lazy grid normally avoids that, because a scroll only moves layers.
- Estimated cost: 15–20 visible cards times tens of microseconds, roughly 0.5–1.5 ms of main-thread time per frame on a mid-range Android phone. The Skia targets re-record pictures the same way.

## Fix
1. Clamp the value that is written, so that a card below the fade writes the same number every frame. Writing an equal value to a structural-equality state invalidates nothing:
   ```kotlin
   .onPlaced { position.top = minOf(it.positionInWindow().y - fade.viewportTop, fade.heightPx) }
   ```
2. Read the per-card condition first in both blocks, so `fade.strength` (and through it the scroll offset) is only read for cards under the fade:
   ```kotlin
   .graphicsLayer {
       compositingStrategy = if (position.top < fade.heightPx && fade.strength > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
   }
   .drawWithContent {
       drawContent()
       if (position.top < fade.heightPx) {
           val strength = fade.strength
           if (strength > 0f) drawRect(/* unchanged */)
       }
   }
   ```
3. `pointerInput` compares `position.top + down.position.y < fade.heightPx`. With the clamp, a card below the fade stores `heightPx`, so `heightPx + y < heightPx` is false for any `y >= 0`, which is the same answer as before. Say so in a `//` comment. The gradient's `startY`/`endY` only matter while `top < heightPx`, where the value is unclamped.

What must not change:

- The look of the fade.
- The press-swallowing under the header.
- The `@Stable` `ListTopFade` API.

## Verification
- `./gradlew :presentation:desktopTest`, then run `:app:desktop:run` and the Android debug build.
- Manual check:
  - Scroll a long list slowly past a pinned header. Cards must still fade out under the header row and back in below it.
  - Taps on a faded card near the header must still be swallowed.
  - A card that crosses into the fade from below must start fading at the same point as before.
- Measurement: while flinging, compare Android GPU rendering or Perfetto `Record View#draw` / `RenderNode` recording time before and after. The number of re-recorded card layers per frame should drop from every visible card to the one or two under the fade. On the desktop, a Skiko frame time comparison works as well.
- Update the `fadingUnderListTop` KDoc if it mentions reading the position, adding one line on why the value is clamped.
