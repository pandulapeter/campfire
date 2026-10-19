<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/dialogs/sheet

The bottom sheet every sheet goes through (`CampfireBottomSheet`) and its header, height, and Save shortcut.

- Drawn edge to edge, background `sheetContainerColor()`; the date picker passes the same color to
  `DatePickerDefaults.colors(containerColor = …)`.
- `ModalBottomSheet`'s own bottom-inset padding is replaced: the sheet keeps the top inset, centers between the
  horizontal safe-drawing edges, and hands content a `contentPadding` of the remaining bottom inset + 16dp, which
  scrolling content applies inside its scroll so rows pass under the navigation bar. The keyboard pads the sheet's
  column outside the content's scroll, so bringing a focused field into view clears it. Insets are read during layout,
  so padding follows each keyboard animation frame.
- **In a short window** (`SHORT_WINDOW_HEIGHT`) the header and pinned controls scroll with the content in one column
  above the keyboard; the content is given the window's height inside that scroll (bounding lazy lists), and its bottom
  padding excludes insets the column already consumed.
- Every sheet has a close button in its `SheetHeader`, tall or not: a picker dragged to full height leaves no scrim to
  tap.
- A title is one line; a sheet with no text input may wrap it to two (`titleMaxLines`), since no keyboard budget counts
  its header.
- **Content height changes animate** (`animateSheetContentHeight`), but a change of the room offered (keyboard, window
  resize) is followed in the same frame so the sheet never trails the keyboard. Checklists shift rows by what the sheet
  still has to grow (`followSheetGrowth`) so a ticked row stays under the finger. Content that fills the sheet keeps
  filling the room at every drag offset (`uncoveredTopInset`); otherwise a grid overflowing by less than the status bar
  oscillates the anchor and snaps the sheet back under the finger.
- The close button runs `sheetState.hide()` and then clears the dialog itself: Material only calls `onDismissRequest`
  for a swipe, scrim, or back, and a hidden sheet whose state is still set leaves an invisible modal layer that swallows
  the next tap.
- From the moment a sheet starts closing, its header actions do nothing (`LocalIsSheetClosing`, which keyboard Done
  actions read too), so a Save during the slide never writes a cancelled draft.
- A close-button hide counts only if it ran to its end; a close after a header action or the content's own button
  (`BottomSheetContentScope.close`) is final — drag off, a cut-short hide finished from where it is, after Material's
  own hide — so a double tap never leaves an answered sheet up.
- Every `visibleDialog`-hosted sheet dismisses through `dismissSheet(itsOwnDialog)`, a no-op once another dialog took
  its place: a sheet replaced while hiding reports the cancelled animation as a dismissal.
