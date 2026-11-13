<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->


# :presentation — ui/dialogs

- A new form sheet with typed or picked input gets a `SavedDialog` variant, so a process Android restores reopens it.
  A new confirmation deliberately does not: an unanswered question is not one to put back.
- **A dialog or sheet is titled with the label of the entry that opened it**; one about one song or setlist names it in
  the `SheetHeader` subtitle.
- A dialog about one song is taken down by the view model when that song leaves the library. Setlist dialogs are not,
  since the song picker opens on a setlist the library has not caught up with yet.

### The dialog stack

**A dialog opened from another one is put over it rather than in its place**, so going back slides only the top one
away and uncovers the one under it as it was (sheet, draft, scroll), never re-opened.

- Slots are keyed by `slotKey`, built from names, never a class: `rememberSaveable` saves a sheet's typed values under
  its hash, which must outlive the process.
- What stacks over what is `stacksOver` (`DialogStack.kt`, tested), each only over the same target, one hop each, never
  over one of its own kind. `setVisibleDialog` derives the stack from the dialog alone: one already in the stack goes
  back to it, one that stacks over the top goes over it, anything else replaces the stack.
- **The stack never takes a sheet out from under the user**: a sheet closes itself and is removed once its hide ended
  (`dismissSheet`); an underlying dialog that must change is updated in place (`updateUnderlyingDialog`); a question
  whose answer leaves the sheet under it nothing to do asks that sheet to close itself (`dismissDialogAndSheetUnder`).
- What belongs to a sheet lives as long as it is anywhere in the stack (`DialogHost.addOpenDialogsChange`).
- Nested setlist naming and calendar sheets are local state of the sheet under them, not stack entries.

### The rest

- Imports run one at a time (`importQueue`); a batch from the OS that comes down to one song opens it once it is in
  (over the editor only once everything there is saved).
- An import's read-only phases offer Cancel; the writing ones are never cancelled. There is no progress dialog: an
  import that cannot put its screen up yet is read under the window's progress bar (`ui/progress`); the demo import
  shows nothing.
- The delete confirmations say when the item, or anything, is shared as a link, which the deletion stops, read from
  `ShareLinkController.shares` (this device's copy, never a request). A song only inside a shared setlist gets no word.
