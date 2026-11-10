<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Give a strip column wider than a slot a whole number of slots, so the columns in view still fill it and the next one starts past its edge

**Challenged:** sound

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/grid/StripPlacement.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/grid/StripPlacementTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/grid/CLAUDE.md` (the strip
bullet on sections that do not wrap)

Lane N, after plan 20 (both edit `grid/CLAUDE.md`'s strip bullet; the code is independent). Plan 20 removes the case
for engraved tunes; this one is for what still widens a strip column (a
tab staff, a grid line, a preformatted run, or a tune left as text).

## Problem

In the owner's screenshot (desktop, 135%, turned by column) a third column starts at about x = 1,327 dp and is cut by
the window's right edge, fading out only over its last gap: 2.3 columns are in view. It is not the next column peeking
by design. `grid/CLAUDE.md` promises "each column is centered in a slot of the view and a gap (`placeStrip`), so
neighbours start past its edge", and `placeStrip`'s KDoc "the columns in view fill it exactly, a gap apart, and the ones
beside them start past its edge rather than in its margins". That holds only while every column fits a slot. A wider
one gets a slot of its own width (`StripPlacement.kt:54–55`):

```kotlin
val slotWidth = (viewWidth + columnGapPx) / grid.stripColumns.coerceAtLeast(1)
val slots = IntArray(columnWidths.size) { maxOf(slotWidth, columnWidths[it] + columnGapPx) }
```

so every column after it is off the slot grid. Reproduced offscreen (pixels of a 1512 × 938 dp window at
`interfaceScale` 0.85): the strip has `stripColumns = 4`, `slot = 374`, the view 1,484 px; the tune's columns are 642 px
wide (plan 20's cause 1), so `slots = [374, 374, 374, 656, 656, …]`; two 656 px slots fill 1,312 px and 172 px of a third
column is in view, unfaded except at the very edge (`before-at135-3.png` in
`/private/tmp/claude-501/-Users-pandulapeter-Projects-Campfire/abb50f13-ea2c-47e6-bbd1-16b6d4895b8d/scratchpad/layout-bug/`).
Nothing is lost (the stops still reach every column, and a step moves by the leaving column's slot), but part of a
column sits in view that no stop centres, and the next step jumps it by a different amount than the eye expects.
After plan 20 a tune no longer does this; a tab staff wider than the strip's column (a long tab line at a large text
size, `stripColumnWidthOf`) still does.

## Fix

In `placeStrip`, round a slot up to whole slots, the column centered in them:

```kotlin
val slots = IntArray(columnWidths.size) { column ->
    val needed = columnWidths[column] + columnGapPx
    slotWidth * ((needed + slotWidth - 1) / slotWidth).coerceAtLeast(1)
}
```

`offsets` already centre a column in its slot less the gap. A column is at most the view less a gap
(`stripColumnWidthOf` caps it at `totalWidth - columnGapPx`, with `totalWidth` the width less the inset and a gap, the
same as `viewWidth`), so a column takes at most `stripColumns` slots, and the columns in view always fill whole slots:
the next column starts at or past the view's edge, where the fade brings it in. Guard `slotWidth == 0` (a zero-width
view) by keeping today's `maxOf` there. Stops stay the slot starts; the "strip ends at the first stop that shows its
last column" rule is unchanged.

Trade-off: a column a little wider than one slot gets two, with up to nearly a slot of space around it (centered, so
it is even on both sides), and the strip is that much longer; the alternative, re-deciding how many slots are in view
around a wide column (two slots of `(view + gap) / 2` while it is on screen), moves every other column by a different
step and breaks "a step moves by the leaving column's slot". Rounding up is the smallest change that keeps the promise.

Update the KDoc ("A column wider than a slot takes as much as it needs, and the gap" → "takes as many whole slots as it
needs, centered in them") and the `grid/CLAUDE.md` strip bullet ("widens its column up to the view less a gap, taking
whole slots").

## Tests

**Write this test first; drop the plan if it passes without the fix.** In `StripPlacementTest`, a strip of four
one-unit columns with `stripColumns = 3` at the file's fixture (width 1,000, inset 50, gap 20: view 930, slot 316),
the second column 500 wide and the others 280: assert the second column is centered in two slots (x = 316 + (632 − 20 −
500) / 2 = 372), the third starts past the view (x = 948 + (316 − 20 − 280) / 2 = 956 > 930), and the stops are
`[0, 316, 948]` (the third shows the last column, which ends at 1,552). Today it is placed at 836 + 8 = 844, in view. The existing three tests must pass unchanged (their
columns fit a slot). Run `./gradlew desktopTest` and `./gradlew :tools:screenshots:test --tests "*ColumnTurnTest*"`.
