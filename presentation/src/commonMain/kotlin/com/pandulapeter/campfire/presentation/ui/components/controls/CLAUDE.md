<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/components/controls

Steppers, choices, chips, and the other small controls the screens and sheets share.

### Choices

- `SegmentedChoice` gives a selected label only the width its check mark leaves (Material cuts it off), and drops the
  mark where labels wouldn't fit beside it; `isInline` for the editor's panes. Constraint: "Keyboard", "Sharps (#)", and
  "Előnézet" must be whole at 360dp.
- `ColorChoice`: discs that wrap rather than scroll; app's own first, then system's, then hues, gray last. The selection
  is a ring in its own color, since an icon in the middle would read as the color's identity.

### Labeled rows (`LabeledControlRow.kt`)

The label sits beside a wide control while its longest word fits, else above it. The form never changes with the value:
`decidingLabel` names the widest form (measured, never drawn), and a row that stacked at a width stays stacked
(`isLabelBesideControls`, tested), so nothing moves under a dragging finger. Not animated.

### The pager indicator (`PagerIndicator.kt`)

An indicator follows the pager's position (page plus offset), read during layout/draw so swipes don't recompose, and
never animates on its own.
