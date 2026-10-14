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

Dialog and sheet content; the root holds the dialog stack and the confirmations, and `sheet`, `forms`, `pickers`,
`labels`, `lookup`, and `about` have a `CLAUDE.md` of their own. The export screen is `ui/screens/export`.

- A new form sheet with typed or picked input gets a `SavedDialog` variant, so a process Android restores reopens it
  (`SavedDialogRestorer`). A new confirmation deliberately does not: an unanswered question is not one to put back.
- **A dialog or sheet is titled with the label of the entry that opened it**; one about one song or setlist names it in
  the `SheetHeader` subtitle.
- A dialog about one song (setlist picker, tag and language dialogs, delete confirmation) is taken down by the view
  model when that song leaves the library. Setlist dialogs are not, since the song picker opens on a setlist the library
  has not caught up with yet.

### The dialog stack

**A dialog opened from another one is put over it rather than in its place**, so going back slides only the top one
away and uncovers the one under it as it was (its sheet, draft and scroll position), never re-opened.

- `DialogHost.openDialogs` is the stack (`visibleDialog` last); `CampfireDialogs` draws all of it, slotted by `slotKey`.
- Which dialog stacks over which is `stacksOver` (`DialogStack.kt`, pure and tested): the About the song sheet's
  editors over it, the Remove cover question over the cover sheet, the lookup over the metadata form — each only over
  the same target.
- Every change goes through `setVisibleDialog`, which derives the stack from the dialog alone
  (`nextUnderlyingDialogs`): one already in the stack goes back to it, one that stacks over the top goes over it,
  anything else replaces the stack. `dismissDialog` uncovers the one under the top.
- **The stack never takes a sheet out from under the user**: a sheet closes itself and is removed once its hide ended
  (`dismissSheet`); an underlying dialog that must change is updated in place (`updateUnderlyingDialog`); a question
  whose answer leaves the sheet under it nothing to do asks that sheet to close itself (`dismissDialogAndSheetUnder`,
  `closeRequest`, read by `CampfireBottomSheet` through `LocalIsSheetCloseRequested`).
- What belongs to a sheet for as long as it is anywhere in the stack (the cover search, the lookup search, the sheet
  `SavedDialogRestorer` saves) is kept by `DialogHost.addOpenDialogsChange`.
- Nested setlist naming and calendar sheets are local state of the sheet under them, not stack entries.

### `CampfireDialogs.kt` and the rest

- `ConfirmImportReplace` confirms an import's Replace (the review is `ui/screens/importReport/`). Imports run one at a
  time through `importQueue`; a batch from the OS that comes down to one song opens that song once it is in (over the
  editor only once everything there is saved).
- `ImportProgressContent.kt`: `isCancellable` separates the read-only phases (download, unpack, read, compare), which
  offer Cancel, from the writing ones, which are never cancelled. There is no progress dialog: an import that cannot
  put its screen up yet is read meanwhile behind the list screens' progress bar; the demo import shows nothing.
