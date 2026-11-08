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

- Edge to edge on `sheetContainerColor()`, which the date picker's colors repeat.
- `ModalBottomSheet`'s bottom-inset padding is replaced: content gets a `contentPadding` of the remaining bottom inset
  + 16dp, applied inside its scroll so rows pass under the navigation bar. The keyboard pads the column outside the
  scroll, so bringing a focused field into view clears it. Insets are read during layout, following every keyboard
  frame.
- **In a short window** header and pinned controls scroll with the content in one column above the keyboard; the
  content gets the window's height inside that scroll (bounding lazy lists), its bottom padding excluding insets the
  column consumed.
- Every sheet has a close button, tall or not: a picker dragged to full height leaves no scrim to tap. Except a question
  asked until answered (`isDismissibleByTouch` off): no close button, swipe, or scrim, so a stray tap does not put it
  away; back and Escape still do, as for every sheet.
- A title is one line; a sheet with no text input may wrap it, since no keyboard budget counts its header.
- **Content height changes animate**, but a change of the room offered (keyboard, resize) is followed in the same frame
  so the sheet never trails the keyboard. Checklists shift rows by what the sheet still has to grow
  (`followSheetGrowth`) so a ticked row stays under the finger, and a row pinned under the content keeps the sheet's
  bottom edge (`stayOnSheetBottom`). Content that fills the sheet keeps filling the room at
  every drag offset (`uncoveredTopInset`), else a grid overflowing by less than the status bar oscillates the anchor and
  snaps the sheet back under the finger.
- The close button runs `sheetState.hide()` then clears the dialog itself: Material calls `onDismissRequest` only for a
  swipe, scrim, or back, and a hidden sheet whose state is still set leaves an invisible modal layer swallowing the next
  tap.
- From the moment a sheet starts closing its header actions (and keyboard Done) do nothing, so a Save during the slide
  never writes a cancelled draft. A close-button hide counts only if it ran to its end; a close after an action is
  final (drag off, a cut-short hide finished, Material's own hide), so a double tap never leaves an answered sheet up.
- Every `visibleDialog` sheet dismisses through `dismissSheet(itsOwnDialog)`, a no-op once another dialog took its
  place: a sheet replaced while hiding reports the cancelled animation as a dismissal.
