<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Draw the song's page-break dividers from its layout instead of composing one per line

**Challenged:** amended — the holder must be snapshot state written by the main (approach) pass only, `dividerHeight`
must keep feeding `placeSections`/`songRowsOf` the divider's rounded height, the draw must reproduce
`HorizontalDivider`'s line exactly, and the draw order and the unscaled full width are spelled out.

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongLyrics.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongSectionsLayout.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/CLAUDE.md`

## Problem

`SongLyrics.kt`:

```kotlin
repeat((sections.size - 1 + units.isCuttableBefore.count { it }).coerceAtLeast(0)) { HorizontalDivider() }
```

composes one Material `HorizontalDivider` (a `Canvas` layout node) for every place a page could start, about one per line of
a cuttable song, on every composed pager page, though nearly none is ever measured.

Do plan 06 first; this plan edits the code it moves.

## Fix

Draw the dividers from a `drawWithContent` on `SongSectionsLayout`'s own modifier, at the tops its measure already
computes (`arrangement.dividerTops`), and drop the `repeat(...) { HorizontalDivider() }` children and
`dividerMeasurables`:

- **Geometry is load-bearing**, not only for pixels: `dividerHeight` feeds `placeSections` (the last row's room,
  `SectionPlacement.kt`) and `songRowsOf` (where a scroll rests: each divider's bottom edge). Keep it
  `if (grid.pageCount > 1) DividerDefaults.Thickness.roundToPx() else 0`, which is what measuring the first
  `HorizontalDivider` (`fillMaxWidth().height(thickness)`) gives today.
- Reproduce `HorizontalDivider`'s draw: for each of the first `pageCount - 1` tops, a line of stroke
  `Thickness.toPx()` from x = 0 to the layout's width, at y = `top - dividerHeight / 2 + Thickness.toPx() / 2` (its
  box was placed at `top - height / 2` and draws its line through the middle of the thickness). The full width, not the
  rows' (see the comment on `dividerConstraints`), and unscaled: only the sections take `placeWithLayer`'s fit scale.
- **The holder is snapshot state** (`mutableStateOf<List<Int>>`, structural equality), not a plain field: a re-measure
  that moves the tops without changing the layout's size (a centering hold released, a fold elsewhere in the row)
  does not otherwise invalidate this node's draw. Write it in the placement block of the main pass only
  (`!isLookingAhead`): the approach pass measures animated sections at their animated sizes, so its tops are the ones
  on screen, while the lookahead's are where they settle.
- **Draw order**: `drawContent()` first, then the lines, so they stay over the cards and the sections (layers
  included), as they are now by being placed last. The modifier goes inside the `semantics` one; the caller's
  `fadingTopEdge` stays outside, so the fade still covers them.
- The color is `DividerDefaults.color` read in composition and captured by the draw lambda (a theme cross-fade then
  redraws, as the composed divider did).
- Update `SongSectionsLayout`'s KDoc ("its children (the song sections, followed by the dividers …)") and the content
  comment in `SongLyrics.kt`.

## Tests

The golden renders must not change (`./gradlew :tools:screenshots:test`); a pixel change there means the color or the
thickness differs.
