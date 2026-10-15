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

### Steppers (`Stepper.kt`)

- **A pill never changes length while used**, so − and + stay under the finger: the value keeps the room of its widest
  `widestValues` plus, if resettable, the reset icon (`StepperValueRoom`), which grows in only while the value can be
  reset. A tap on a plain value, or on the icon, resets it.
- **Only the tempo and the reference pitch are typed into** (`StepperEntry`, `StepperField`): a wide range and a number
  people arrive with. Elsewhere values are tried a step at a time while their effect is watched.
- `StepperField` sets every keystroke that leaves a number in range, with no key to confirm; one outside it is drawn in
  the error color and sets nothing, never clamped (415..466 is typed through 4 and 44). A tap on its value starts
  typing, so only the icon resets it. A unit (` Hz`) is drawn beside the field, not in it, so only the digits are ever
  selected.
- While a field is focused it counts in `OverlayState.isValueBeingTyped`, so the song's keys leave it alone; it is no
  overlay to the back stack or the web's history. **A press anywhere outside its pill ends typing** (`CampfireApp`'s
  root, initial pass, never consumed), since nothing else takes the focus from it. A press inside the pill keeps typing
  on a touch screen; a desktop click on −/+ takes the focus itself.

### Labeled rows (`LabeledControlRow.kt`)

The label sits beside a wide control while its longest word fits, else above it. The form never changes with the value:
`decidingLabel` names the widest label (measured, never drawn), and a row that stacked at a width stays stacked
(`isLabelBesideControls`, tested), so nothing moves under a dragging finger. Not animated.

### The pager indicator (`PagerIndicator.kt`)

An indicator follows the pager's position (page plus offset), read during layout/draw so swipes don't recompose, and
never animates on its own.
