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

Dialog and sheet content. The export screen is `ui/screens/export` (see its notes).

### `ui/dialogs/CoverArtSearchSheet.kt`

`ui/dialogs/CoverArtSearchSheet.kt` — the cover search, opened from the song details editing menu's "Set cover art" or
"Change cover art" entry and titled with it; performance mode and a turned-off switch leave it out. Two tabs.
**Search**: artist, album and title fields prefilled from the file (`CampfireViewModel.coverArtQueryOf`, the held text
parsed, the library's entry where there is none), asked by `setVisibleDialog` as the sheet is put up — so its first
frame already says the search is running rather than crossfading from the hint while it slides — and afterwards only
from the keyboard's search key or the button, never per keystroke, since MusicBrainz allows the whole app one request a
second; and a grid of what MusicBrainz and iTunes found, each catalogue's records joining at the end as it answers,
closed by an indicator while one is still pending.

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
that it outlives an Android activity being recreated, and `setVisibleDialog` cancels and clears it whenever the sheet
stops being the dialog on screen. It says when MusicBrainz asked it to wait (only once nothing else is pending), when it
failed (with a retry) and when it found nothing, and credits both catalogues at the end of the search tab. **Only the
header and the tabs hold still on tall windows**: the search tab is one `LazyVerticalGrid` whose first item is the three
fields and the button — outlined, so that the header's Save is the sheet's one filled action — and whose last is the
credit, what stands in place of the records being an item between them, and Save and Remove (the bin, since it asks
first anyway) sit at the end of the header (`CampfireBottomSheet`'s `actions`, which are handed the sheet's close) — a
phone with the keyboard up otherwise left the covers no room at all. It goes through `CampfireBottomSheet` like every
sheet, wider than the others (840dp) for the grid.

### `ui/dialogs/CampfireDialogs.kt`

`ui/dialogs/CampfireDialogs.kt` — `CampfireDialogs` renders whatever `visibleDialog` asks for: the confirmation an
import's Replace takes over the import screen (`ConfirmImportReplace`; the question it confirms is the screen's, see
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
up), the **Song defaults** sheet (`SongPlayingDialog`, `DialogType.SongPlaying`, opened from the song details editing
menu's "Edit song defaults", after Edit song details (`songPlayingAction`), and from the About the song sheet's Song
defaults group: what the file declares for the key, typed in the reader's notation and written in the standard one — the
key the chords are written in, which the field says under it where the file opens with a `{transpose}` that moves them
before anything names it — the capo and the tempo, each an optional field with its range under it and its default as the
placeholder, and the time signature as the Metronome tab's own `TimeSignaturePicker`; Save writes the fields changed and
only those, through `setSongPlaying`, and so judges only those (`isValidSongPlayingDraft`): a value the file already
holds outside a range is drawn as an error but does not keep the other fields from being saved, and a field a feature
switch hides is never judged.

It is where the difference between the file and the steppers is explained, rather than on the page: its first line says
the steppers change these for this setlist only, or for the library (never "this device", since the library's overrides
are synced to every device on the account), and while the song is adjusted there a card names how — the transposition,
the capo, the tempo — in the color the steppers draw an override in, with a Reset that takes all of it back),  the song
language picker (every ISO code with a search over the names and the codes — including the codes a file may spell a
language with rather than the one the library files it under, so that `HUN` and `hu-HU` both find Hungarian, which is
`NormalizeLanguageCodeUseCase`'s job — the ones the song already declares held at the top for as long as the dialog is
open — a row that reordered itself under the finger that just ticked it would be worse than a list that has to be
scrolled — and one write when it is confirmed), delete song / delete setlist confirmations (the only way to delete
either), the editor's unsaved changes and revert questions, and the controls, setlist picker, song picker and welcome
`ModalBottomSheet`s.

**The welcome sheet** (`DialogType.Welcome`) is the first run's one screen of its own and is kept that short on purpose:
a line about the app, the theme and the color — the settings screen's own `UiModeChoice` and `ThemeColorChoice`
(`components/UiModeChoice.kt`, `ThemeColorChoice.kt`), over the app in its own theme so that every tap shows on the
songs behind — a line pointing at Settings that names Dropbox wherever `syncProviders` is not empty, since sync is the
one thing a new user cannot find by using the app, and "Open Settings" / "Get started".
`CampfireViewModel.showWelcomeOnFirstRun` puts it up once `hasShownApp` is set rather than as the library arrives, since
a sheet is a window of its own on Android and would slide up over the launch screen, and only onto a screen with no
other dialog on it and no import screen under it; Open Settings lands on the Library tab where the build has sync and on
General where it has none, and moves behind the sheet as it goes down. It is shown on that one run and never again,
since the first run's preferences are written before it is up. `WhatsNewDialog` is an alert titled with
`CAMPFIRE_VERSION_NAME`, shown once per version after the first installed one.

Its body is a list of `• **Headline** Description` bullets separated by `\n` in the localized resource, ordered by
importance to the user, each a bold one-line headline with a short description of the change and its practical benefit
under it (a bullet without the bold part is all description). The rows keep wrapped text aligned and 16dp between
changes, in a scrolling area capped at 420dp with the usual fades at both ends; the title and the **Get started** button
(the welcome sheet's string) stay visible. `showWhatsNewOnVersionChange` waits for the app and for every import queued,
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
it from the picker's own state rather than through `visibleDialog` (which holds one dialog at a time), because the song
goes into the new setlist as soon as it exists (`createSetlistWithSong`) and the picker is where that shows. **Automatic
keyboard behavior is specific to each form** (`rememberFirstFieldFocusRequester`): new song, new and duplicate setlist,
tag management, language management and library deletion focus their first field on opening. Editing an existing
setlist, song metadata, song links and cover art leave their fields unfocused. Both assignment sheets also leave search
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
before the editor opens. The bodies of the new song, setlist details and library deletion sheets scroll, so the field is
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
`campfireBottomSheetContainerColor` for its background (the app background in light themes, Material's lighter sheet
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
row ticked in a short sheet stays under the finger while the sheet rises around it. The close button hides the sheet
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

### `ui/dialogs/ImportProgressDialogHost.kt` / `ImportProgressContent.kt`

`ui/dialogs/ImportProgressDialogHost.kt` / `ImportProgressContent.kt` — an import the user asked for that lasts more
than 400 ms shows its phase, the current file name and the processed count in a progress dialog, and waits while another
dialog is up rather than covering whatever is typed into it. Its Cancel, offered during the three preparing phases only
(`isCancellable`) and fading out as the writing starts, cancels that preparation and not the queue
(`cancelImportPreparation`): nothing has been written yet, so nothing is announced and the next batch runs. The writing
phases stay uncancellable, and tapping outside or Back never cancels. Comparing and the final library update have no
count, so their bar is indeterminate. It is only the happy path's: the import screen shows the progress of an import it
reports on itself, with the same `ImportProgressContent`. The first-run demo import shows none of it.
