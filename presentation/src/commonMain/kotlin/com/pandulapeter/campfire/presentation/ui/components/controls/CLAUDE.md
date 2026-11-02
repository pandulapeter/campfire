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

- `SegmentedChoice` gives a selected label only the width its check mark leaves and drops the mark where labels
  wouldn't fit beside it (judged at full size); a label shrinks to 75% before it is cut. "Keyboard", "Sharps (#)", and
  "Előnézet" must be whole at 360dp.
- **Every segmented button takes `segmentShape`**, never Material's `itemShape`: a border keeps its outline until its
  shape changes and hears of no new layout direction, so a language switch to or from right to left would round the end
  segments on the wrong side.
- `ColorChoice`: discs wrap rather than scroll; the selection is a ring in its own color, since an icon in the middle
  would read as the color's identity.
- **A stepper's pill never changes length while used**, so − and + stay under the finger: the value keeps the room of
  its widest `widestValues` plus the reset icon, which grows in only while the value can be reset.
- **Only the tempo and the reference pitch are typed into** (a wide range, a number people arrive with); other values
  are tried a step at a time while their effect is watched.
- `StepperField` sets every keystroke that leaves a number in range, with no confirm key; one outside is drawn in the
  error color and sets nothing, never clamped (415..466 is typed through 4 and 44). A tap on its value starts typing, so
  only the icon resets it. A unit (` Hz`) is drawn beside the field, so only digits are selected.
- A focused field counts in `OverlayState.isValueBeingTyped`, so the song's keys leave it alone; it is no overlay to the
  back stack or the web's history. **A press anywhere outside its pill ends typing** (`CampfireApp`'s root, initial
  pass, never consumed), since nothing else takes the focus from it; inside the pill typing continues on touch.
- `LabeledControlRow`: the label sits beside a wide control while its longest word fits, else above. The form never
  changes with the value (`decidingLabel` measures the widest label), and a row that stacked at a width stays stacked
  (tested), so nothing moves under a dragging finger. Not animated.
- `PagerIndicator` follows the pager's position, read during layout/draw so swipes don't recompose, and never animates
  on its own.
