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

Dialog and sheet content. The export screen is `ui/screens/export` (see its notes). A new form sheet with typed or
picked input gets a `SavedDialog` variant, so that a process Android restores reopens it (see `ui/navigation`), and a new
confirmation deliberately does not: a question nobody answered is not one to put back.

### `ui/dialogs/CoverArtSearchSheet.kt`

`ui/dialogs/CoverArtSearchSheet.kt` — the cover search, opened by tapping the cover in the About the song sheet (or the
empty place for one) and titled "Set cover art" or "Change cover art" accordingly; performance mode and a turned-off
switch leave it out. Two tabs. **Search**: artist, album, and title fields prefilled from the file
(`CampfireViewModel.coverArtQueryOf`, the held text parsed, the library's entry where there is none), asked by
`setVisibleDialog` as the sheet is put up — so its first frame already says the search is running rather than
crossfading from the hint while it slides — and afterwards only from the keyboard's search key or the button, never per
keystroke, since MusicBrainz allows the whole app one request a second; and a grid of what MusicBrainz and iTunes found,
each catalogue's records joining at the end as it answers, closed by an indicator while one is still pending.

**Web address**: a field for any `http`/`https` address, typed with or without its `https://`
(`ChordProCoverArt.usableUrl` decides what counts) and a preview of it, asked for once the typing has paused for 500 ms;
one that does not load can still be saved, since a host may refuse only the web build. Save writes the pick or the
address into the song (`setSongCoverArt`, through `SetChordProCoverArtUseCase`, the path a tag takes); Remove, here or
in the overflow menu, asks the same confirmation before taking a cover off. A tile whose thumbnail does not load is
dropped from the grid, which is how a release group without a cover is found out, and takes the selection with it if it
had it; a new search puts every dropped tile back, since a thumbnail may have failed only for the moment. **The sheet
opens at its full height** whatever it holds, since what it holds grows after it has opened and a sheet resizing while
it slid up was what made it stutter.

The search is the view model's (`coverArtSearch`, one `Active` state per query carrying the `CoverArtSearchResults`), so
that it outlives an Android activity being recreated, and it is cancelled and cleared once the sheet is open nowhere
in the dialog stack (a Remove question over it keeps it). It says when MusicBrainz asked it to wait (only once nothing else is pending), when it
failed (with a retry) and when it found nothing, and credits both catalogues at the end of the search tab. **Only the
header and the tabs hold still on tall windows**: the search tab is one `LazyVerticalGrid` whose first item is the three
fields and the button — outlined, so that the header's Save is the sheet's one filled action — and whose last is the
credit, what stands in place of the records being an item between them, and Save and Remove (the bin, since it asks
first anyway) sit at the end of the header (`CampfireBottomSheet`'s `actions`, which are handed the sheet's close) — a
phone with the keyboard up otherwise left the covers no room at all. It goes through `CampfireBottomSheet` like every
sheet, wider than the others (840dp) for the grid.

### `ui/dialogs/SongDetailsLookupSheet.kt`

`ui/dialogs/SongDetailsLookupSheet.kt` — "Find song details online", put up by the search button in the Edit song
details form's header (`SongMetadataDialog`'s `startButton`, absent while the "Online song details" switch is off). It
**writes nothing**: it is put over the form, which stays open under it (the dialog stack, below), handed the form's
offered values and its draft (`DialogType.SongDetailsLookup`). Fill in, the header's one action, which Ctrl / Cmd + S
presses, puts the ticked suggestions into the form where it is (`CampfireViewModel.fillSongMetadata`, a new
`DialogType.SongMetadata.draft` the form resets its fields to) and slides the lookup away; every Fill in is a hand-over
of its own (`fillCount`, which the form's fields are keyed by), so they are reset to it even where it equals the one
before. Closing it leaves the form as it was. The form still compares its draft with the offered values on Save, so only what changed is written. A
restored process reopens the form rather than the lookup, from the file.

The search (title, narrowed by artist and album, through `CatalogueQueryFields`, the cover search's fields) is asked as the
sheet is put up (`addOpenDialogsChange`), from the draft, and afterwards only from the keyboard's search key or the
button; its state and the songwriters of the open row are `SongDetailsLookupController`'s, cleared and cancelled with
the sheet. Everything but the header is one `LazyColumn`: the fields, what stands in for results (the cover search's
loading, busy and failed lines), the recordings as each catalogue answers (`SongDetailsCandidateRow`), and the credit,
which also says that neither catalogue knows a key or a tempo. A tapped row opens in place into its review
(`SongDetailsReview.kt`, pure and tested): one box per field it would change, the form's current value under the
suggestion, ticked where the form holds nothing and never for the title, an identical value left out and durations
compared as durations. On MusicBrainz the songwriters are asked for as the row opens — one request, `LaunchedEffect`
keyed by the open row, which an Android activity recreated with the row open finds already answered
(`SongDetailsLookupController` asks nothing again for the row it already holds; opening another row replaces it, so
coming back to the first asks again) — and join the rows when they arrive; iTunes names none, and its rows say nothing
about it.

### `ui/dialogs/OpenSourceLicensesSheet.kt`

`ui/dialogs/OpenSourceLicensesSheet.kt` — Settings → About → Open-source licenses (`DialogType.OpenSourceLicenses`):
Campfire's own license first, then everything this build ships that somebody else wrote, by name
(`OpenSourceComponents.kt`: the libraries, the transitive ones included, the web build's fonts, the desktop builds'
runtime, and the chord shapes the tables were filled from), each naming its holder and its license. **A build lists
only what it ships** (`OpenSourceComponent.builds` against `ui/platform`'s `appBuild`, which on the desktop follows the
screenshot tool's impersonation); the README lists every build's. A tap opens one entry (one at a time), with its
project page and its license's page, then its NOTICE where the project has one (Apache 2.0's section 4 d) and the
license's full text, read from the app's own files as the sheet opens rather than fetched. An open entry's texts are
items of the list, cut at blank lines (`licenseTextChunks`), since Google's list of what its Play libraries bundle runs
to thousands of lines; so opening and closing one is the list's own item animation, the texts fading while the entries
below move, vertically only.

**The list is kept by hand**: a dependency that ships in a build gets an entry, its license text and NOTICE under
`composeResources/files/licenses` of the source set of the builds that show it (`commonMain` where several do, so
Google's 220 KB list is the Android build's alone), and a row of the README's Third-party software table, in the same
change; `OpenSourceComponentsTest` checks that every build has every text it lists, what a few builds list, and the
order. The list was taken from every artifact on the Android, desktop, and web runtime classpaths, whose jars and AARs
were searched for NOTICE and license files, and from the projects' repositories, and from the native libraries Skiko
ships for the desktop, iOS, and the web, whose bundled third-party code (Skia's `third_party`) was found with `strings`:
Windows and the web are built without the raw image readers (piex, the DNG SDK), and only Linux and the web carry
FreeType.

### The dialog stack

**A dialog opened from another one is put over it rather than in its place**, so that going back slides only the top
one away and uncovers the one under it as it was — never puts it up again, sliding in as if it had just been opened.
`DialogHost.openDialogs` is the whole stack, the one on screen (`visibleDialog`) last, and `CampfireDialogs` draws every
dialog in it, each in a slot keyed by its place (`slotKey`: a song sheet's kind and `SongEditTarget`, any other
dialog's kind), so one under another keeps its sheet, its draft and its scroll position. Which dialog stacks over
which is `stacksOver` (`DialogStack.kt`, pure and tested): the About the song sheet's editors over it, the Remove cover
question over the cover sheet, the song details lookup over the metadata form, each only over the same text. Every
change goes through `setVisibleDialog`, which works the stack out from the dialog alone (`nextUnderlyingDialogs`): one
in the place of an open dialog goes back to it, closing everything above it; one that stacks over an open dialog goes
over it; anything else replaces the stack. `dismissDialog` uncovers the one under the top.

**A sheet taken off the stack is gone in one frame, and one that closes itself slides away first**, so the stack never
takes a sheet off from under the user: a sheet closes itself and is taken off once its hide has ended
(`dismissSheet`, as every sheet does); an open dialog that has to change does so where it is
(`updateUnderlyingDialog`, the form the lookup fills); and a question whose answer leaves nothing for the sheet under it
to do asks that sheet to close itself (`dismissDialogAndSheetUnder`, `closeRequest`, which `CampfireBottomSheet` reads
through `LocalIsSheetCloseRequested` and answers with its own final close). What belongs to a sheet for as long as it is
open anywhere in the stack — the cover search, the lookup's search, the dialog a restored process reopens (the topmost
one with a `SavedDialog`) — is kept by `DialogHost.addOpenDialogsChange`, which is handed the stack before and after.
Each sheet is a modal window of its own, so back, Escape, a tap on the scrim and Ctrl / Cmd + S reach only the top one.

### `ui/dialogs/CampfireDialogs.kt`

`ui/dialogs/CampfireDialogs.kt` — `CampfireDialogs` renders every dialog of the stack (above): the confirmation an
import's Replace takes over the import screen (`ConfirmImportReplace`, naming how many ticked taken names the review's
choices write over, and confirming with `resolveImport`; the review is the screen's, see
`ui/screens/importReport/` — imports run one at a time through the view model's `importQueue`, so files dropped, opened
with the app or planted as the demo while another import is running or reported on wait their turn rather than being
dropped; a batch the operating system handed over that comes down to one song, written or already in the library, opens
that song once it is in — in place of a song already open, and over the editor only once everything in it is saved), new
song and the three setlist bottom sheets (`NewSongDialog` and `SetlistDetailsDialog` keep their composable names — new
(from the setlists screen or from inside the setlist picker), edit and duplicate are the same sheet with different
labels, since all three ask the same three questions: the required title, the optional description that goes under the
setlist's header and that its screen's search reads, and the date, which starts as today unless the setlist already has
one (a copy starts as today too), with the Countdown checkbox next to it, centered on the field's border rather than on
the room above it for the label (under it where the sheet is too narrow for both, `MIN_DATE_ROW_WIDTH`). A copy is named
before it is made rather than after, since two setlists may carry the same title and one nobody named would sit under
the original's).

**A dialog or a sheet is titled with the label of the entry that opened it** — "Edit setlist", "Duplicate setlist",
"Manage languages", "Set cover art", "Change cover art" or "Edit song defaults" — so that what was tapped is what comes
up), the **Song defaults** sheet (`SongPlayingDialog`, `DialogType.SongPlaying`, opened from the About the song sheet's
Song defaults tile and titled "Edit song defaults": what the file declares for the key, typed in the reader's notation
and written in the standard one — the key the chords are written in, which the field says under it where the file opens
with a `{transpose}` that moves them before anything names it — the capo and the tempo, each an optional field with its
range under it and its default as the placeholder, and the time signature as the Metronome tab's own
`TimeSignaturePicker`; Save writes the fields changed and only those, through `setSongPlaying`, and so judges only those
(`isValidSongPlayingDraft`): a value the file already holds outside a range is drawn as an error but does not keep the
other fields from being saved, and a field a feature switch hides is never judged.

It is where the difference between the file and the steppers is explained, rather than on the page: its first line says
the steppers change these for this setlist only, or for the library (never "this device", since the library's overrides
are synced to every device on the account), and while the song is adjusted there a card names how — the transposition,
the capo, the tempo — in the color the steppers draw an override in, with a Reset that takes all of it back),  the song
language picker (every ISO code with a search over the names and the codes — including the codes a file may spell a
language with rather than the one the library files it under, so that `HUN` and `hu-HU` both find Hungarian, which is
`NormalizeLanguageCodeUseCase`'s job — the ones the song already declares held at the top for as long as the dialog is
open — a row that reordered itself under the finger that just ticked it would be worse than a list that has to be
scrolled — and one write when it is confirmed), delete song / delete setlist confirmations (the only way to delete
either), the editor's unsaved changes and revert questions, and the controls, setlist picker and song picker
`ModalBottomSheet`s.

`AppLanguageSheet` (`DialogType.AppLanguage`) is Settings' language list in a sheet, put up by the onboarding's first
page; a pick is written at once and closes it, and it has no `SavedDialog`, since nothing is typed into it.
`NotationSheet` (`DialogType.Notation`), opened from the notation chip in Settings, works the same way: the five
notations with their examples, a pick written at once and closing it.

`WhatsNewDialog` is an alert titled with
`CAMPFIRE_VERSION_NAME`, shown once per version after the first installed one.

Its body is a list of `• **Headline** Description` bullets separated by `\n` in the localized resource, ordered by
importance to the user, each a bold one-line headline with a short description of the change and its practical benefit
under it (a bullet without the bold part is all description). The rows keep wrapped text aligned and 16dp between
changes, in a scrolling area capped at 420dp with the usual fades at both ends; the title and the **Get started** button
(the onboarding's last button's string) stay visible. `showWhatsNewOnVersionChange` waits for the app and for every import queued,
running or reported on (`canShowWhatsNew` in `ui/firstRun/WhatsNewGate.kt`: no dialog, no import running, no import
screen, nothing in `importQueue`), and the version is recorded once the dialog has stayed composed for a few seconds
(`onWhatsNewShown`) or as it closes, rather than when it is asked for or on its first frame, since on Android Play's
answer can arrive after the dialog opened and the update required screen then covers it and its flow ends the process;
ending the process with the dialog up after that does not repeat it.

`UserPreferences.seenWhatsNewVersions` keeps all handled versions, including the first installed version and versions
with an empty or whitespace-only `whats_new_message`, which show nothing. The prepare-release skill replaces or clears
that resource in both languages; only the current release is shown. Content that closes its sheet from a button of its
own does so through `BottomSheetContentScope.close`, the final close the header actions are handed.

The two pickers are the same thing from either side — every setlist with a box for one song, every song of the library
with a box for one setlist — and share their parts: a header titled with what the sheet does ("Choose setlists", "Choose
songs") and naming what the boxes are about under it (`Artist - Title`, or the setlist's title), with the list screen's
own sort button (`SetlistSortMenu`, `SongSortMenu` in `components/SetlistSortMenu.kt` and `SongSortMenu.kt`) at its end
across from the close button — the same preference, so a sheet lists the setlists or the songs in the order the screen
does; a new order sends the list back to its first row (`ScrollToStartWhenChanged`, which keeps it there until the
reordered list arrives from the view model), and so does a new tag or language order the picker's chip rows and the
lists of the tag and language dialogs, the rows of all four lists sliding to their new places (`listItemAnimation`) —
the songs screen's filters need none of it, since their chips wrap under the very toggle that was tapped — as every
dialog about one song or setlist names it (`SheetHeader`: the tag, language, metadata and link sheets, and the edit and
duplicate setlist sheets), wherever each was opened from, the song details screen included — a search field — left
unfocused in both assignment sheets, so their lists stay visible until search is tapped — and `PickerList`, a lazy list
that takes what height the sheet has left — its gap under the search field is content padding rather than padding around
it, so a scrolled row goes under the field itself — and never gets shorter while the sheet is open, so that narrowing it
by a search does not shrink the sheet and move the field being typed into down the screen.

**Every list that shares a sheet or a dialog with a text field puts the keyboard away as it starts scrolling down**
(`HideKeyboardWhenScrolledDown`, the list screens' own): the pickers, the language list and the tag suggestions. The
list screens' own stands down while a dialog or a sheet covers them, since in Firefox the keyboard shortens the page and
a list shrinking past the card that opened the dialog scrolls down to keep it in view, which would put away the keyboard
the dialog has just brought up. The setlist picker's "New setlist" opens named after a search that found nothing, and a
setlist ticked there gets the song at its end. A song with no setlist it could be put in — none at all, or only archived
ones it is not in (`hasListableSetlist`) — skips the setlist picker and opens the naming sheet straight away.

The song picker lists the setlist's own songs first, in its order and held there while the sheet is open, and the rest
in the songs screen's order, and as the first item of its list, under the search field and scrolling away with the rows
(only the header and the field hold still on tall windows, and a typed search scrolls the chips out of the way,
`PickerList`'s `revealRowsKey`), it has the library's tags (marked with the label icon) and, on a second row, its
languages (marked with the language icon, and only where the whole library holds more than one) as sideways-scrolling
rows of `CountedFilterChip`s (`SortableChipRow`, each starting with a pinned `LabelSortingToggle` the chips scroll
behind and fade out before, `fadingUnderStartOverlay`, ordered as the songs screen's filters are; a plain scrolling
`Row` rather than a lazy one, so that a new order is animated by `animateBounds` — a lazy row sent back to its start
drops its items' placement animation after a frame; neither row where there is nothing to filter by) — its own
selection, empty on every opening rather than the songs screen's filter, any value within a group and both groups
together; its rows, in the order the domain layer sorted the library in (`ScreenData.sortedSongs`) and folded for the
search (`pickerSongs`), and its chips, counted (`songPickerFilters`, `SongPickerIndex.kt`), are built by the view model
away from the main thread once per library, so the sheet's first frame only orders what is already there; a search ranks
its results the way the songs screen's does, title and artist matches before a song found by a tag alone
(`songPickerMatches`, sharing `searchRank` with `rankSongs`), the ticked songs still leading; it keeps what is ticked
itself and writes one song in or out per tick (`CampfireViewModel.setSetlistSong`, one write at a time behind a `Mutex`,
each applied to the setlist as the library has it then, so neither the tick before it nor an entry a sync run brought in
while the sheet was open is lost; a setlist that is gone by then is not brought back from the sheet's copy), because
ticks into one file come faster than a write round trips through the library and boxes that followed `setlists` would
flick back.

A song ticked there goes to the end of the setlist, and an entry whose file is missing is kept, since it is not listed
to be unticked. The setlist picker stays where it is while its "New setlist" action renders the naming sheet on top of
it from the picker's own state rather than through the dialog stack, because the song
goes into the new setlist as soon as it exists (`createSetlistWithSong`) and the picker is where that shows. **Automatic
keyboard behavior is specific to each form** (`rememberFirstFieldFocusRequester`): new song, new and duplicate setlist,
tag management, language management, and library deletion focus their first field on opening. Duplicating a song,
editing an existing setlist, song metadata, song links, and cover art leave their fields unfocused. Both assignment sheets also leave search
unfocused at every window height; the keyboard opens when search is tapped. The calendar sheet shows only the calendar,
with no way to switch to typed date entry.

**Every field that is typed into has a clear button** (`rememberClearTextButton` in `components/ClearTextButton.kt`),
scaling in with the first character and out with the last, and every keyboard action does something: Next moves on,
Search and Done put the keyboard away where they have nothing else to do (the tag dialog's Done enters the typed tag and
puts it away too, so the list it narrowed is in view), and Done on a confirmation that is not ready yet (no title,
`DELETE` not typed) puts the keyboard away rather than doing nothing. **Every dialog that is typed into is a bottom
sheet** (`TextFieldBottomSheet` in `ui/dialogs/TextFieldBottomSheet.kt`: the new song, setlist details, tag, language,
metadata, link and library deletion forms). They use `CampfireBottomSheet`, with the confirming action and any list or
add action in its header; the header's close button cancels the draft. The date picker uses `CampfireBottomSheet` like
the forms, with Save in its header. Existing first-field focus behavior is preserved for each form.

**New song** includes the required title and optional subtitle, artist, album, composer, lyricist, year and duration,
using the same field order and side-by-side year/duration row as Edit song details. It reuses `SongMetadataField` and
`songMetadataSaver`; optional labels use the localized `optional_field_label` pattern (`(optional)` / `(nem kötelező)`),
consistent with setlist description and link name. The title retains autofocus, title and artist retain their
60-character limits, and the last field's Done creates the song when the title is valid. `CampfireViewModel.createSong`
passes the metadata to `CreateSongUseCase`, which writes nonblank optional directives into the initial song template
before the editor opens.

**Duplicate song**, a song card's menu entry on the Songs screen alone (`SongActions.isDuplicable`), is the same sheet
filled in with what the song says, read from its file (`showDuplicateSongDialog`, `DialogType.DuplicateSong`), titled
Duplicate song with the song under it and confirmed with Duplicate. It opens with no field focused, as Edit song details
does, since it is already filled in. `duplicateSong` hands `DuplicateSongUseCase` only the fields that were changed,
which are written over the file's text as it is when the copy is made, so the copy keeps the original's lyrics, chords, and
every other directive; it is named by its own title, subtitle, and artist like any new song (a copy nobody changed gets the
original's name with `_2`), and opens in the editor. The bodies of the new song, setlist details, and library deletion sheets scroll, so the field is
brought into view however little is left of them, and like every sheet's content they fade at the top only: a sheet's
content ends at the bottom of the window or at the keyboard, which is the bottom of what the window has left, and
nothing in the app fades towards that edge.

The setlist picker's naming sheet uses `SetlistDetailsDialog` too, description included, so a setlist made there is the
same as one made from its own screen. That sheet holds the title as a `TextFieldValue` rather than a `String` so that
all of it is selected the first time the title field takes the focus (the name of a copy, a search that found nothing,
or a title tapped to edit), and not before: a sheet opened to be looked over has nothing focused, and a selection drawn
without a caret reads as a glitch: that text is there to be replaced, so the first key typed writes the new name instead
of appending to the old one. The description opens with the caret at the end of it instead, since it is opened on to be
edited rather than replaced. Every sheet goes through `CampfireBottomSheet`, which draws it **edge to edge** and uses
`sheetContainerColor()` for its background (the app background in light themes, Material's lighter sheet
container in dark themes).

The date picker passes this same color to `DatePickerDefaults.colors(containerColor = …)` so its calendar and
typed-input body match the sheet header: `ModalBottomSheet` pads its whole content by the bottom safe drawing inset by
default, which left a scrolling list ending on a band of the sheet above the navigation bar, so the sheet keeps the top
inset (which it pads by once dragged against the status bar), centers its entire surface between the horizontal safe
drawing edges up to its maximum width (without adding inset padding inside the sheet), and hands its content a
`contentPadding` of the remaining bottom inset (the navigation bar; the keyboard's height pads the sheet's column
outside the content's scroll, on every window, so that bringing a focused field into view clears the keyboard) plus 16dp
instead. Bottom insets are read during layout, including the assignment lists, cover grid and calendar, so padding
follows the current keyboard animation frame.

Scrolling content applies it inside its scroll (the pickers' `LazyColumn`, the two controls' `verticalScroll`) so the
rows pass under the bar, and fixed content leaves it under its last row; the side panel adds the same 16dp to the
padding it hands the controls, since they no longer add it themselves. **In a short window** (`SHORT_WINDOW_HEIGHT`,
480dp) the header and everything the sheet pins under it scroll with the content instead, in one column padded above the
keyboard, so that bringing the caret into view moves them out of its way: a landscape keyboard leaves less than a header
and a field take together. The content is given the window's height inside that scroll, which bounds the lazy lists in
it, and its bottom padding excludes the insets the column has already consumed, including the navigation bar covered by
the keyboard, since the column is already above it. `CampfireBottomSheet` also draws every sheet's `SheetHeader`: its
title (and, for a sheet about one song or setlist, a subtitle naming it) and a **close button**, on every sheet rather
than only the tall ones, since a picker that grows past the screen fills it once dragged up and leaves no scrim to tap
and no edge that looks draggable.

It also **animates every change of its content's height** (`animateSheetContentHeight`): rows added to a list or taken
away, an error appearing, content swapped — the sheet's top edge follows with the default spatial spring, while a change
of the room offered (the keyboard sliding, the window resizing) is followed in the same frame, so the sheet never trails
the keyboard. The checklists' lists shift their rows up by what the sheet still has to grow (`followSheetGrowth`), so a
row ticked in a short sheet stays under the finger while the sheet rises around it. Content that fills the sheet at its
tallest keeps filling the room offered at every offset (`uncoveredTopInset`): Material anchors the expanded sheet at
the window's height less the sheet's, which includes the top inset still padded, so a grid that overflows by less than
the status bar's height would come to fit as the sheet is dragged off it, shrink the sheet and move the anchor on every
frame, putting the sheet back at the top under the finger. The close button hides the sheet
(`sheetState.hide()`) and then clears `visibleDialog` itself: Material's `ModalBottomSheet` only calls
`onDismissRequest` for a swipe, a tap on the scrim or a back press, and a hidden sheet whose state is still set keeps
its invisible modal layer over the screen, swallowing the next tap. From the moment a sheet starts closing — its close
button, a swipe or the scrim — its header actions do nothing (`LocalIsSheetClosing`, which New song's and Delete
library's keyboard Done read too), so a Save tapped during the slide never writes a cancelled draft.

The close button's hide only counts when it ran to its end, while a close that follows a header action or the content's
own button is final — the sheet's drag is off for that slide, and a hide cut short is finished from where the sheet is,
after any hide of Material's own (the scrim, back) has run — so a second tap of a double tap never leaves an answered
sheet up; and every sheet hosted by `visibleDialog` dismisses through `dismissSheet(itsOwnDialog)`, which does nothing
once another dialog has taken the sheet's place: a sheet replaced while it is hiding reports the cancelled animation as
a dismissal, Material's scrim and back handlers included. Nested setlist naming and calendar sheets dismiss through
their own local state so that the sheet underneath stays open. A dialog or sheet about one song (the setlist picker, the
tag and language dialogs, the delete confirmation) is taken down by the view model when that song leaves the library,
whichever screen it was opened from; the setlist dialogs are not, since the song picker opens on a setlist the library
has not caught up with yet.

### `ui/dialogs/ImportProgressContent.kt`

`ui/dialogs/ImportProgressContent.kt` — an import's phase, the current file name and the processed count, which the
import screen shows while the files are downloaded and read and while they are written; comparing and the final library
update have no count, so their bar is indeterminate. `isCancellable` is the line between the phases that only read
(downloading, unpacking, reading, comparing), under which the screen offers Cancel, and the ones that write, which are
never cancelled. There is no progress dialog: an import that cannot put its screen up yet (a dialog, an editor with
unsaved text, the onboarding) is read unseen meanwhile, with the list screens' progress bar, and the demo library's
import shows nothing at all.

### The selection mode's sheets

The songs ticked in the songs screen's selection mode (see `ui/screens/songs`) get three sheets, each subtitled with
how many songs it is about and holding the selection as it was when it opened, in the order the screen sorts by; none
of them has a `SavedDialog`, since the mode is not restored either. **Choose setlists** (`SelectionSetlistsSheet`) is
the setlist picker with a box per setlist that is ticked where it holds all of the songs, empty where it holds none and
half ticked in between (`selectionBoxState`, following what each setlist holds until the sheet is first tapped on it,
and from then on the sheet's own record of what it held then and what the sheet added since rather than the library,
which lags behind the sheet's writes): a tap on an empty or half ticked box adds the songs it lacks to its end as one
write (`Setlist.withSongsAdded`), and a tap on a box the sheet filled takes out exactly those (`withSongsTakenOut`) — a
song the setlist held already is never taken out from here, since that loses its place and its key in it. A setlist
that holds them all, untouched by the sheet, is ticked and disabled, an archived one is listed only where it holds one
of them, and a library with nothing to list skips to the naming sheet, whose setlist is created holding the whole
selection (`createSetlistWithSongs`, which the one-song picker goes through too).

**Manage tags** and **Manage languages** are the one-song sheets' bodies (`TagsSheet`, `LanguagesSheet`), which both the
one-song dialogs and the selection's use, driven by `BulkLabelDraft`: a row starts on where every song carries the label,
off where none does and half ticked otherwise, a half ticked row goes on, off, and back to half ticked, the rest only on
and off, and Save writes only what was turned on (onto every song) and what was turned off (off every song), leaving a
half ticked row alone in every file — for one song no row is ever half ticked, which is why the two share a body. The
selection's are written by `CampfireViewModel.setSelectionTags` / `setSelectionLanguages` (`SelectionEditing`), each
file edited as it is on disk through `EditSongsUseCase`, which announces the change once, and the whole of it ends in
one snackbar: how many songs were updated, or how many could not be. Once started, the write finishes even if the
screen, or the view model with it, goes.

Every row of Manage tags ends in the tag's mark (`TagColorButton`), drawn in its color, which opens a popup of the
colors as `ColorChoice` discs, each disc's check black or white, whichever contrasts with it more
(`isDarkerMoreLegible`) — no color first, then the palette, in rows of five, which fit a 360dp phone — rather than a
second sheet over a sheet that may have the keyboard up. The choice is written at once
(`CampfireViewModel.setTagColor`), not with Save, and stays when the sheet is closed without saving, since the color is
the tag's in the whole library rather than part of the songs being edited.

