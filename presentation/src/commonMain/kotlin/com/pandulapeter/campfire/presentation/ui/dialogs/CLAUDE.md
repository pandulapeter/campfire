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
  (see `ui/navigation`). A new confirmation deliberately does not: an unanswered question is not one to put back.
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
- What belongs to a sheet for as long as it is anywhere in the stack (the cover search, the lookup search, the dialog a
  restored process reopens — the topmost with a `SavedDialog`) is kept by `DialogHost.addOpenDialogsChange`.
- Nested setlist naming and calendar sheets are local state of the sheet under them, not stack entries.

### `CampfireBottomSheet`

Every sheet goes through it. Rules a change must keep:

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
- **Content height changes animate** (`animateSheetContentHeight`), but a change of the room offered (keyboard, window
  resize) is followed in the same frame so the sheet never trails the keyboard. Checklists shift rows by what the sheet
  still has to grow (`followSheetGrowth`) so a ticked row stays under the finger. Content that fills the sheet keeps
  filling the room at every drag offset (`uncoveredTopInset`); otherwise a grid overflowing by less than the status bar
  oscillates the anchor and snaps the sheet back under the finger.
- The close button runs `sheetState.hide()` and then clears the dialog itself: Material only calls `onDismissRequest`
  for a swipe, scrim, or back, and a hidden sheet whose state is still set leaves an invisible modal layer that
  swallows the next tap.
- From the moment a sheet starts closing, its header actions do nothing (`LocalIsSheetClosing`, which keyboard Done
  actions read too), so a Save during the slide never writes a cancelled draft.
- A close-button hide counts only if it ran to its end; a close after a header action or the content's own button
  (`BottomSheetContentScope.close`) is final — drag off, a cut-short hide finished from where it is, after Material's
  own hide — so a double tap never leaves an answered sheet up.
- Every `visibleDialog`-hosted sheet dismisses through `dismissSheet(itsOwnDialog)`, a no-op once another dialog took
  its place: a sheet replaced while hiding reports the cancelled animation as a dismissal.

### Forms

Root `CLAUDE.md` owns the rules that every typed modal is a `TextFieldBottomSheet` with its actions in the header.

- **First-field focus is per form** (`rememberFirstFieldFocusRequester`): new song, new and duplicate setlist, tag
  management, language management, and library deletion focus their first field; duplicate song, edit setlist, song
  metadata, links, and cover art do not. Both assignment pickers leave search unfocused at every height.
- Every typed field has a clear button (`rememberClearTextButton`), and every keyboard action does something (at
  least hiding the keyboard).
- **Every list that shares a sheet with a text field hides the keyboard as it starts scrolling down**
  (`HideKeyboardWhenScrolledDown`). The list screens' own instance stands down while a dialog covers them: in Firefox
  the keyboard shortens the page, the list scrolls to keep the opening card in view, and would put away the keyboard
  the dialog just raised.
- **New song** reuses Edit song details' fields and order (`SongMetadataField`); `CreateSongUseCase` writes nonblank
  optional directives into the initial template.
- **Duplicate song** (Songs screen cards only, `SongActions.isDuplicable`): the same sheet prefilled from the file,
  unfocused. `DuplicateSongUseCase` gets only changed fields, written over the file's current text, so the copy keeps
  every other directive; it is named like any new song (unchanged → original name `_2`) and opens in the editor.
- **Setlist details** (`SetlistDetailsDialog`: new, edit, duplicate, and the picker's naming sheet are one sheet with
  different labels): title, description, date (today unless the setlist has one; a copy starts today), and Countdown.
  A copy is named before it is made, since two setlists may share a title. The title is a `TextFieldValue` so the whole
  title is selected the first time it takes focus (and not before: a selection without a caret reads as a glitch); the
  description puts the caret at the end.

### Song defaults sheet

`SongPlayingDialog`: key (typed in the reader's notation, written in standard), capo, tempo, and time signature as the
file declares them. Save writes and validates only changed fields (`isValidSongPlayingDraft`): an out-of-range value
already in the file shows as an error without blocking the others, and a field a feature switch hides is never judged.
Its first line never says "this device", since library overrides are synced.

### Pickers

The setlist picker (every setlist, one song) and the song picker (every song, one setlist) share parts:

- The header's sort button is the list screen's own preference, so the sheet orders as the screen does. A new sort or
  tag/language order sends the list to its first row (`ScrollToStartWhenChanged`, held until the reordered list
  arrives).
- `PickerList` takes the height the sheet has left and **never gets shorter while open**, so narrowing by search does
  not shrink the sheet and move the field being typed into. Its gap under the search field is content padding.
- The song picker lists the setlist's own songs first (held while open), then the rest in the songs screen's order.
  Its tag and language chips (`SortableChipRow`) are a plain `Row` animated by `animateBounds`, since a lazy row sent to
  its start drops its placement animation; their selection is the sheet's own, empty on every opening. Rows and chips
  are built off the main thread once per library (`SongPickerIndex.kt`).
- **One write per tick** (`CampfireViewModel.setSetlistSong`, serialized by a `Mutex`, each applied to the setlist as
  the library has it then): ticks come faster than a write round-trips, and boxes following `setlists` would flick
  back. A gone setlist is not recreated from the sheet's copy. A ticked song goes to the end; an entry whose file is
  missing is kept.
- The setlist picker's "New setlist" renders the naming sheet over the picker from its own state, not via the stack,
  since the song goes into the setlist as soon as it exists (`createSetlistWithSongs`). It opens named after a search
  that found nothing. A song with no listable setlist (`hasListableSetlist`) skips straight to the naming sheet.
- Language picker: search over names and codes (`HUN` and `hu-HU` both find Hungarian, via
  `NormalizeLanguageCodeUseCase`); the song's own languages stay at the top while open, since a row reordering under
  the finger that ticked it is worse than scrolling.

### The selection mode's sheets

For the songs screen's selection mode (see `ui/screens/songs`). Each holds the selection as it was when opened; none
has a `SavedDialog`, since the mode is not restored either.

- **Choose setlists** (`SelectionSetlistsSheet`): a box per setlist, ticked / empty / half ticked by how many of the
  songs it holds (`selectionBoxState`, following the library until the sheet is first tapped on it, then the sheet's
  own record, since the library lags its writes). Tapping an empty or half box appends the missing songs in one write
  (`Setlist.withSongsAdded`); tapping a box the sheet filled removes exactly those (`withSongsTakenOut`). **A song the
  setlist already held is never removed from here**, since that loses its place and key. A library with nothing to
  list skips to the naming sheet (`createSetlistWithSongs`).
- **Manage tags / Manage languages** (`TagsSheet`, `LanguagesSheet`, shared with the one-song dialogs) run on
  `BulkLabelDraft`: a half-ticked row cycles on → off → half; Save writes only rows turned on or off, leaving half rows
  alone. Writes go through `EditSongsUseCase` on the file as on disk, run `NonCancellable`, and end in one snackbar.
- A tag's color (`TagColorButton`, a popup rather than a second sheet) is written at once (`setTagColor`), not on Save,
  and survives cancelling: the color belongs to the tag library-wide.

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
