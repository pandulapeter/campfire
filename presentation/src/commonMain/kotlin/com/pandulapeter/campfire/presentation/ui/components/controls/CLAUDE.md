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
  `widestValues` plus, if resettable, its reset icon (`StepperValueRoom`; tabular digits, so a range's ends are its
  widest). The reset icon (`StepperResetMark`) grows in after the value only while it can be reset, and the value with
  its icon stay centered in that room. A tap on the value or the icon resets it. Only the tempo and the reference pitch take a typed number too (`StepperEntry`, a wide range and a number people come with): everywhere else values are tried a step at
  a time while their effect is watched, and a short list of valid values is no use for a numeric keyboard.
- `StepperField`: always a field, drawn as the plain value until focused (tap, click, Tab), which selects the number
  (after the focusing press is released, since the field places its caret then). A unit (` Hz`) is drawn beside the
  field, not in it, so only the digits are ever selected. **Every keystroke leaving a
  number of the range sets it**, with no key to confirm; a number outside it (415..466 is reached through 4 and 44) is
  drawn in the error color and sets nothing, never refused or clamped. Leaving writes back the value last set; Escape
  puts back the one at focus; Up / Down step. A typed number that is the value is never reselected (30 on the way to
  300). A tap on the value starts typing, so only its reset icon resets it; the icon cannot take the focus, so a click
  on it does not end typing on the press.
- While a field is focused it counts in `OverlayState.isValueBeingTyped`, so the song's arrows and Space / M leave its
  keys alone; it is no overlay to the back stack or the web's history. **A press anywhere outside its pill ends typing**
  (`CampfireApp`'s root, initial pass, never consumed), since nothing else takes the focus from it. A press inside the
  pill keeps typing on a touch screen; a desktop click on −/+ takes the focus itself.

### Labeled rows (`LabeledControlRow.kt`)

The label sits beside a wide control while its longest word fits, else above it. The form never changes with the value:
`decidingLabel` names the widest label (measured, never drawn), and a row that stacked at a width stays stacked
(`isLabelBesideControls`, tested), so nothing moves under a dragging finger. Not animated.

### The pager indicator (`PagerIndicator.kt`)

An indicator follows the pager's position (page plus offset), read during layout/draw so swipes don't recompose, and
never animates on its own.
