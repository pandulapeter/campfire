<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Dropped findings (app side)

Findings of the 59th review that did not hold once the code was read; each is one sentence why.

- **`animateSheetContentHeight` as a cost of its own (39 % of the sheet's layout):** that share is inclusive of the
  whole sheet content measured inside its `layout` block (`SheetHeightAnimation.kt:57-59`); its own work is a few
  comparisons, so plan 11 targets what it measures (the filter chips' intrinsics and bounds), not the modifier.
- **The cards' top fade drawn "in the card colour" (plan 12 as first worded):** under the list's top the cards fade to
  the screen's background, not to their own color (`ListTopFade`'s KDoc), so plan 12 paints `colorScheme.background`
  there and the card color only on the label row.
