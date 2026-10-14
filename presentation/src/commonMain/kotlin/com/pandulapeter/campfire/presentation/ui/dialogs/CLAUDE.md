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

Dialog and sheet content. The export screen is `ui/screens/export`.

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

### `CoverArtSearchSheet.kt`

Opened from the cover in the About the song sheet; absent in performance mode or with Cover art off.

- The search is asked by `setVisibleDialog` as the sheet is put up (so its first frame already says "searching") and
  afterwards **only from the search key or button, never per keystroke**: MusicBrainz allows one request a second.
- The Web address tab previews after a 500 ms typing pause; an address that does not load can still be saved, since a
  host may refuse only the web build.
- A tile whose thumbnail fails is dropped (that is how a cover-less release group is found out); a new search restores
  them, since the failure may have been momentary.
- **The sheet opens at full height**, since its content grows after opening and resizing while sliding stuttered.
- The search lives in the view model (`coverArtSearch`) to survive activity recreation, and is cancelled once the sheet
  is nowhere in the stack (the Remove question over it keeps it).

### `SongDetailsLookupSheet.kt`

**Writes nothing.** Put over the Edit song details form (which stays open under it) with the form's offered values and
draft (`DialogType.SongDetailsLookup`).

- Fill in (Ctrl / Cmd + S) hands a new draft to the form (`fillSongMetadata`) and closes the lookup. Each Fill in bumps
  `fillCount`, which keys the form's fields, so they reset even to an equal draft. A restored process reopens the
  form, not the lookup.
- Search runs as the sheet opens (`addOpenDialogsChange`), then only on the search key or button; its state is
  `SongDetailsLookupController`'s, cleared with the sheet.
- A row's review (`SongDetailsReview.kt`, pure and tested): one box per field it would change, ticked only where the
  form is empty and never for the title; identical values omitted, durations compared as durations.
- MusicBrainz songwriters are fetched when a row opens (one request; the controller does not re-ask for the row it
  already holds, so activity recreation asks nothing). iTunes names none.

### `OpenSourceLicensesSheet.kt`

- **A build lists only what it ships** (`OpenSourceComponent.builds` against `appBuild`, which follows the screenshot
  tool's impersonation); the README lists every build's.
- **The list is kept by hand**: see the root `CLAUDE.md`'s Build section. License texts live under
  `composeResources/files/licenses` of the source set of the builds that show them (Google's 220 KB Play list is the
  Android build's alone). `OpenSourceComponentsTest` checks every build has every text it lists, and the order.
- The list was compiled from every artifact on the Android, desktop, and web runtime classpaths (jars and AARs searched
  for NOTICE and license files) and from Skiko's native libraries (Skia `third_party`, found with `strings`): Windows
  and the web lack the raw image readers (piex, DNG SDK), and only Linux and the web carry FreeType.
- An open entry's texts are list items cut at blank lines (`licenseTextChunks`): some run to thousands of lines.

### `CampfireDialogs.kt` and the rest

- `ConfirmImportReplace` confirms an import's Replace (the review is `ui/screens/importReport/`). Imports run one at a
  time through `importQueue`; a batch from the OS that comes down to one song opens that song once it is in (over the
  editor only once everything there is saved).
- **What's new** (`WhatsNewDialog`; when it shows is `ui/firstRun/`): the version is recorded once the dialog has
  stayed up a few seconds (`onWhatsNewShown`) or as it closes, not on its first frame, since on Android Play's answer
  can arrive after it opened and the update-required screen then ends the process. Body: `• **Headline** Description`
  bullets separated by `\n`.
- `ImportProgressContent.kt`: `isCancellable` separates the read-only phases (download, unpack, read, compare), which
  offer Cancel, from the writing ones, which are never cancelled. There is no progress dialog: an import that cannot
  put its screen up yet is read meanwhile behind the list screens' progress bar; the demo import shows nothing.
