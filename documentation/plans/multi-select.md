<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Selecting several songs — implementation plan

Written 2026-10-06 against `3160dc997`. A selection mode on the Songs screen: songs are ticked, and what is ticked is
put into setlists, tagged, given languages or exported in one go. Nothing about it reaches the network, and nothing new
is stored: the selection lives for as long as the mode does.

## 1. What is being built

1. **A selection mode on the Songs screen**, started from a song's own menu and ended by a floating **Done** button
   that counts what is ticked, by Back, Escape or the web's Back, and by leaving the screen.
2. **Every card carries a box in the mode** in place of its star and its menu, and a tap ticks it instead of opening
   the song.
3. **What is ticked leads the list**, the way it does in the Choose songs sheet: a **Selection** section on top, the
   newest tick first, an unticked song staying there until the search, the sorting or a filter next changes.
4. **The search, the sorting and the filters keep working**, since "search, tick, search again, tick" is how a
   selection is put together. The selection is by file name and survives all three.
5. **A selection menu in the bar**, where New stands outside the mode: Select all, Deselect all, then **Choose
   setlists**, **Manage tags**, **Manage languages** and **Export songs**, which act on everything that is ticked.

### What is already there, and what is not

- **The setlist reorder mode is the model for the mode itself** (`CampfireViewModel.reorderingSetlistFileName`): a
  snapshot state in the view model, ended in `updateBackStack` when its screen stops being on top, answered first by
  `navigateBack` and by the desktop Escape handler, a history entry of its own in `BrowserRoutes.paths`, a
  `NavigationBackHandler` in the screen and an `ExtendedFloatingActionButton` at the list's bottom end.
- **`ChecklistOrder` is the ordering asked for**, pure and tested (`components/ChecklistOrder.kt`): ticked and recently
  unticked keys share a leading group, a new tick goes before all of it, and a refresh key reseeds the group in the
  list's own order. The Choose songs sheet's refresh key is the sorting, the query and its chips; the songs list
  already carries the same thing as one value, `SongGroups.filterKey`.
- **`ExportSongsUseCase` already takes a list**: one song leaves as its `.cho`, several as `campfire_songs.zip`.
- **`PrintLayout` already lays out any number of songs that are not a setlist**: no overview, unnumbered titles
  (`PrintSong.index` null), and `startSongsOnNewPage` honoured. Only the export screen assumes one song or a setlist.
- **One song's tags and languages are written by rebuilding its text** (`setSongTags`, `setSongLanguages` through
  `editSongText`), which reads the file's own tags from the text rather than from the list.
- **A setlist is changed one serialized step at a time** (`UpdateSetlistUseCase`, `updateEditableSetlist`), and
  `Setlist.withSongTicked` is the pure half of a tick.
- **Not there: a row that can be half ticked.** `CheckboxListItem` is two-state; the one `TriStateCheckbox` is the
  export screen's private `SelectAllListItem`.
- **Not there: a write of many song files that announces itself once.** `SongRepository.saveSong` updates the cached
  list and calls `libraryChanges.onLibraryChanged()` per file, so three hundred songs tagged through it would be three
  hundred library emissions, each one sorting and sectioning the whole library again.
- **`SongGroup.header` is the domain's `SongSection.Header`**, which has no place for a section the sort did not cut.

### Defaults this plan assumes — veto any of them before the work starts

1. **One way in: a "Select songs" entry heading a song row's menu**, which starts the mode with that song ticked. A
   long press keeps opening that menu, so on touch it is a long press and a tap. No button is added to the bar, where a
   fifth one would leave a pinned artist about 135dp on a 360dp phone for something that is rarely wanted. The
   alternative is the long press starting the mode directly, which is what most apps do and what the row's menu was
   given the gesture for before there was a mode.
2. **A ticked card moves to the Selection section at once, and the list does not follow it there.** The choosers
   leave a ticked row where it was tapped and never scroll on a tick, since a list being read through and ticked
   along the way would otherwise be thrown about by every tick; the card moving is this mode's own signal.
3. **Back closes an open search first, then ends the mode.** Ending the mode is the step that loses something.
4. **No action ends the mode.** The same selection can be tagged, then put into a setlist, then exported; Done is what
   lets go of it.
5. **Leaving the Songs screen ends the mode and drops the selection**, a tab tapped included, and nothing of it is
   restored after the process dies. This is the reorder mode's rule, and it is what keeps a song opened by an import,
   the import screen and the other tabs from ever standing over a mode nobody can see.
6. **The button says "Done" and carries the count as a small pill after the label**, absent while nothing is ticked.
   The alternative is the count as the label ("3 selected") with a cross for an icon.
7. **Tags and languages are saved from a sheet, as they are for one song**, with a third state for what only some of
   the songs carry. A row left in that state is left alone in every file.
8. **Choose setlists only adds, and takes back only what it added.** A setlist that already held some of the songs
   keeps those whatever is tapped: taking a song out of a setlist loses its place and its key there, and that stays
   something done in the setlist, one song at a time and after a question.
9. **An export is in the order the screen sorts by**, not the order of the ticks, and in the keys the library plays
   the songs in, as one song exported from the library is.
10. **There is no bulk delete.** See §9.

## 2. The mode (`CampfireViewModel`)

- `internal var selectedSongFileNames by mutableStateOf<Set<String>?>(null)`: null while the mode is off, a set, empty
  included, while it is on. Snapshot state like `reorderingSetlistFileName`, so `BrowserRoutes.paths` observes it.
  `isSongSelecting` is that and the Songs screen being on top.
- `startSongSelection(fileName)`, `toggleSongSelection(fileName)`, `selectSongs(fileNames)`, `deselectAllSongs()`,
  `endSongSelection()`. A `songSelectionSession` counter goes up with every start, which the list keys its order by
  (§3.1).
- **Ended** in `updateBackStack` when the top is no longer `CampfireDestination.Songs`, and when performance mode comes
  on. **Not** ended by `setVisibleDialog`: the export screen is a dialog, and it opens over the mode.
- **Pruned** as the library changes: a song that left it leaves the selection. Only a library that was read whole
  prunes (`ScreenData.isWholeLibrary`, and not while it is loading), or a rescan publishing in batches would untick
  what it has not reached yet.
- `navigateBack`: `isSongSelecting && !songsSearch.isOpen.value -> endSongSelection()`, next to the reorder mode's
  line. The desktop Escape handler in `CampfireDesktopApp.kt` gets the same branch **after** the search's, where the
  reorder mode's is before it — the reorder mode has no search to close.
- `BrowserRoutes.paths`: for the songs, `ROOT`, then `ROOT` again while selecting, then `SEARCH` while the search is
  open, which is the order Back takes them in. `synchronize()` works by depth, so a mode started from a search result
  is one `replaceState` and one `pushState` in answer to that tap. `entryCount` is unchanged: like the reorder mode,
  the selection is never part of a `NavigationState`, and Forward to its entry is refused. No new first segment, so
  `app/web`'s route lists stay as they are.
- `isEditingSongs: StateFlow<Boolean>`, true while a bulk write runs (§4.2).

## 3. The list (`ui/screens/songs/`)

### 3.1 The Selection section — `SongSelectionGroups.kt`, pure and tested

`fun List<SongGroup>.withSelectionLeading(order: ChecklistOrder): List<SongGroup>`: the songs whose keys the order
holds are taken out of their sections and put into one group in front, in `ChecklistOrder.ordered`'s order; a section
left empty goes, header and all. With nothing held the list is the one it was given, the same instance.

`SongList` calls `rememberChecklistOrder(checkedKeys = selection, refreshKey = songGroups.filterKey)` inside
`key(songSelectionSession)` and only while the mode is on, and builds its groups from the result. Outside the mode none
of this runs and the list is `songGroups.groups` exactly as today. The session key matters: an order remembered across
two sessions would open the second with the first one's unticked songs still leading.

The refresh key is the one the list was built for rather than one read off the preferences, for the reason
`ScrollToTopWhenChanged` uses it: the reseeded order and the list it reorders arrive in one composition.

**Everything in `SongList` that counts items reads the derived groups**, not `songGroups.groups`: `SongSectionIndex`,
`firstCardIndex`, `itemIndexOf`, `isHeaderless` and `areHeadersCollapsing`, and the group lookup inside
`searchScroll.update`. The anchor's `contents` has to be a value that changes with a tick as well, so that a tick
releases the position a closed search would otherwise restore to an index that now holds another card.

### 3.2 The section's header

`SongGroup.header` becomes a presentation type, `SongGroupHeader`: `Section(val header: SongSection.Header)` or
`Selection`, each with the `key` a lazy item wants. The domain's sections stay what the sort cut. The places that read
a header follow: `displayText()` ("Selection"), `fastScrollerLabel` ("✓"), `SongSectionIndex` and its test, the pushed
header and the `header_…` item keys.

It is a `SectionHeader` like the others, so it pins, stands in the bar's place and collapses with the rest when the
search opens. It says "Selection" and not "Selected", since a song unticked a moment ago is still under it.

### 3.3 The divider while there are no headers

A search's results are one ranked group with no header, and an open search collapses every header. There the leading
group is set off as the sheets set it off: one full-span `HorizontalDivider` item after it, present only while both
parts hold something, drawn only while the headers are collapsed and fading on their spring.

### 3.4 The cards

`SongListItem` already takes everything needed: in the mode its `actions` are a `Checkbox` with no click of its own,
crossfaded with the star and the menu; `onClick` is `toggleSongSelection`; `onLongClick` is null; and the caller's
modifier makes the card one `toggleable` of `Role.Checkbox` for a screen reader. A tag or a language tapped under a
song still filters the list, which in the mode is the quickest way to "everything tagged Christmas", then Select all.

The entry itself is `SongActions`' `leadingItems`, which is where entries that belong to the row rather than to the
song already go: "Select songs", `isAlwaysInMenu`, handed in by the songs screen alone.

### 3.5 Holding the list still under a tick

A lazy grid keeps its first visible item by key, which holds the viewport through most ticks without help. Two cases
need it: the ticked card being that first visible item (the grid would follow it to the top), and the first tick of a
session, which adds a header above everything. In the composition the new order arrives in, the grid is asked to keep
the first visible card **that is not the ticked one** where it is: `requestScrollToItem` with that card's index in the
new groups (`itemIndexOf`) and its current offset. The rows that move slide through `listItemAnimation` as they do for
any change of the list's contents.

This is the part of the plan most likely to need a second attempt. It shares a frame with `SongSearchScrollAnchor` and
`ListAnchor`, and has to be watched in one, two and three columns, with the search open and closed.

### 3.6 The Done button

`ExtendedFloatingActionButton` in `SongList`'s `Box`, where the setlists screen has its own: `Alignment.BottomEnd`, the
same paddings (`extraEnd = FAST_SCROLLER_WIDTH`, 16dp), `fadeIn() + scaleIn()`. A check for an icon, `done` for the
label, and after it the count on a small pill in the button's content color, scaling in with the first tick and out
with the last, its number rolling up or down as it changes. Its content description is a plural: "Done, 3 songs
selected". The list's bottom padding grows by the button's height and its margin while the mode is on, since a card's
box is at its end and the last row's would otherwise sit under the button.

### 3.7 The bar

- The search, the sort and the filter stay, enabled.
- The slot behind the divider (`closedSearchActions`) holds `NewItemMenu` outside the mode and the selection menu in
  it, swapped with a crossfade. `SearchableTopAppBar` learns to keep that slot while the search is open
  (`areClosedSearchActionsKeptInSearch`), since the search is where most ticking happens; New keeps leaving for the
  field as it does now.
- The selection menu is an `ActionsMenu`: **Select all** (every song listed now, so the search's results or the
  filter's; absent once they are all ticked) and **Deselect all** (absent while nothing is), both `isAlwaysInMenu`;
  then Choose setlists (absent with the Setlists feature off), Manage tags, Manage languages and Export songs, which
  come out as buttons where the pill has room and are disabled while nothing is ticked or `isEditingSongs`.
- `ImportProgress(isImporting = isImporting || isEditingSongs)`.
- `NavigationBackHandler`, as on the setlists screen: enabled while selecting with the search closed, no dialog and
  no menu open.

## 4. The actions

Each opens with a snapshot of the selection **in the order the screen sorts by** (`pickerSongs` has it), titled with
its entry's label and subtitled with a count ("12 songs"). `DialogType.SelectionSetlists`, `SelectionTags` and
`SelectionLanguages` carry file names; a song that left the library since is skipped when they write.

### 4.1 Choose setlists

The `SetlistPicker`'s sheet with a three-state box per setlist: none of the songs, some, all.

- A tap on *none* or *some* adds the songs the setlist lacks to its end, in the selection's order, as one write
  (`Setlist.withSongsAdded(fileNames)` in `SetlistSongToggle.kt`, through `updateEditableSetlist`). The sheet remembers
  what it added to which setlist (`rememberSaveable`).
- A tap on a setlist the sheet filled takes out exactly what it added, back to *none* or *some*.
- A setlist that held all of them when the sheet opened is ticked and disabled, as the setlist a song is being read
  through is in the one-song sheet. Archived setlists are listed only where they hold one of the songs, disabled.
- The boxes follow the sheet's own record rather than the library, for the reason the Choose songs sheet's do.
- "New setlist" names a setlist and creates it holding the whole selection (`createSetlistWithSong` generalized to a
  list); with no setlist to list, the sheet is skipped for the naming sheet, as today.

Pure and tested: `withSongsAdded`, the taking back, and the three-state answer for a setlist.

### 4.2 Manage tags, Manage languages

**The sheets.** The bodies of `SongTagsDialog` and `SongLanguagesDialog` are lifted into composables that take a
`ToggleableState` per row, and both the one-song dialogs and the new ones use them; one song is the case where no row
is ever mixed. `CheckboxListItem` gets a `state: ToggleableState` form. The draft is pure, `BulkLabelDraft`: built from
the songs' own tags (folded by case) or codes, a row starts *on* where every song has it, *off* where none does and
*mixed* otherwise, and a tap goes mixed → on → off → mixed for a row that started mixed and on ↔ off for the rest. It
answers `added` (rows now on that did not start on) and `removed` (rows now off that did not start off), and Save is
enabled while either holds anything. A tag typed and created starts off and is ticked by being entered, as today.
Mixed rows lead the list with the ticked ones.

**The write.** A new use case, since the existing path announces every file:

```kotlin
interface EditSongsUseCase {
    /** Applies [edit] to each file as it is on disk, writes the ones it changed and announces them together. */
    suspend operator fun invoke(fileNames: List<String>, edit: (text: String) -> String): EditedSongs
}
data class EditedSongs(val changedFileNames: Set<String>, val failedFileNames: List<String>)
```

`SongRepository.editSongs` behind it: per file, under `libraryFileLock`, read the file (not the cache), apply, write if
the text changed — the read and the write share the lock, so there is no `expectedText` to compare — each file's write
`NonCancellable`; then one `invalidate` of their texts and one update of the cached list with the files read again,
which is what `adoptImported` does for an import, and one `libraryChanges.onLibraryChanged()`. A file that cannot be
read or written is named in the result and the rest carry on. One sync run follows, ten seconds later, like after any
burst.

The view model's `setSelectionTags(fileNames, added, removed)` and `setSelectionLanguages(…)` build `edit` from the
use cases `setSongTags` and `setSongLanguages` already use: tags are removed and added by `SetChordProTagUseCase`
against the tags parsed from that file's text; languages are that file's own codes less `removed` plus `added`, written
with `SetChordProLanguagesUseCase`. `isEditingSongs` is true meanwhile. It ends in one snackbar: "12 songs updated", or
"3 songs could not be updated" where some failed.

### 4.3 Export songs

One song ticked opens `DialogType.Export(song = …)`, exactly the row's own Export. Several open the same screen on a
third kind of source:

- `DialogType.Export` gains `songs: List<Song>`; `preparePrintSource` makes its entries from them, with the library's
  transposition and the file's own tempo and capo, as for one song exported from the library.
- `PrintSource` says what it is rather than only whether it is a setlist (`kind`: song, setlist, songs), and the export
  screen's `isSetlist` checks are split into the two things they ask: *is there a running order* (the setlist content
  choice, the overview, the manifest in the zip) and *is there more than one song* (the Songs ticks with Select all,
  "Start songs on a new page", the zip format and its preview).
- The ticks list the songs unnumbered; today's row is `"${entry.index}. ${entry.title}"`, which would print `null`.
- The files format is **Zip**, described as the songs as ChordPro files with no setlist; `exportFiles` hands the
  ticked names to `ExportSongsUseCase`, whose archive is already `campfire_songs.zip`. The PDF is `campfire_songs.pdf`
  (`pdfFileName`, and `PrintFileNameTest`), titled with the count.
- Saved, it says "Songs exported". The screen closes onto the mode, the selection as it was.

## 5. Strings

Added to both files: Select songs; Selection; Deselect all (Select all is `print_select_all`, to be renamed for its
second use); the button's description (a plural); the sheets' subtitle, "%d songs" (a plural); Export songs; the zip's
description for songs; Songs exported; "%d songs updated" and "%d songs could not be updated" (plurals).

## 6. Corner cases, and what each one does

| Case | Behaviour |
| --- | --- |
| Performance mode | No row menu, so no way in; switching it on ends a mode that is running. |
| Setlists switched off | Choose setlists is not in the menu. |
| A filter or a search hides ticked songs | They stay ticked and counted; the Selection section holds only the ones listed. |
| A bulk tag takes songs out of the active filter | They leave the list and stay ticked. |
| Select all with a search open | Ticks the results, added to what was ticked before. |
| The search finds nothing | The usual placeholder; the button and the menu stay. |
| A sync run deletes a ticked song | It leaves the selection, and the count follows. |
| A sync run brings a song in | It is listed unticked. |
| Every ticked song is gone while a sheet is open | Save writes nothing and says so as a failed operation. |
| Files dropped or opened with the app | An import that needs its screen ends the mode; one that ends in a snackbar does not. |
| A tab tapped, Settings opened | The mode ends and the selection is dropped. |
| Rotation, a theme change | The selection is the view model's and stays. |
| The process dies in the background | The app comes back to the songs without the mode. |
| A bulk write while a sync run is going | Both take the library's file lock per file; the run that follows carries the changes. |
| A file fails halfway through a bulk write | The rest are written, and the snackbar counts the ones that were not. |
| Back with a sheet over the mode | The sheet closes; the next Back closes the search or ends the mode. |
| Web: Forward to the mode's entry after it ended | Refused, as for the reorder mode. |
| The flexible update's Restart, a required update | Unaffected: nothing here is unsaved text. |
| A song with no chords, a song with no artist | Nothing special: a card is a card. |
| One song ticked | Every action works, Export being the one-song export. |

## 7. Order of work

Each step builds and tests green on its own, one commit each.

1. The pure pieces and their tests: `SongSelectionGroups`, `BulkLabelDraft`, `withSongsAdded` and the setlist's
   three-state answer.
2. The mode: the view model's state and its endings, the cards, the Selection section and its header type, the
   divider, the viewport hold, the Done button, Back, Escape and the web's history, and a bar whose menu holds Select
   all and Deselect all only. Verified on the desktop first, then on the 360 × 640 dp emulator.
3. Choose setlists.
4. `EditSongsUseCase` through the domain and the repository, the two sheets made three-state, Manage tags and Manage
   languages.
5. Export songs.
6. Docs: the root `CLAUDE.md` (a Conventions entry for the mode, the Printing section's third source, the test list),
   `presentation/CLAUDE.md` (the songs screen, the bar's slot, the dialogs, `BrowserRoutes`), the `domain` and
   `data/repository` ones for the new use case, `app/web` for the history entry, the README's feature list.

## 8. Checks owed by hand

- **The viewport under a tick**, in one, two and three columns: the first card on screen ticked, the first tick of a
  session, the last song of a section, a tick in search results, a tick while a fling is still running.
- The search opened and closed inside the mode, with and without ticks in between: no card jumps, and closing an
  unchanged search still returns to where it was opened.
- 360 × 640 dp, in Hungarian: the Done button with a three-digit count beside the fast scroller, the bar with the
  search open and the selection menu kept, the last card's box clear of the button.
- A phone on its side, with the keyboard up in each of the three sheets.
- A wide window with the filter panel: the menu's entries out as buttons, the button over the list and not the panel.
- Back on Android, a predictive one included, Escape on the desktop, and Back and Forward in Chrome, Firefox and
  Safari: search first, then the mode, and never off the page.
- TalkBack and VoiceOver: a card read as a checkbox with its state, the button's count.
- Three hundred songs tagged at once on the web build: the progress bar, one list update at the end, one sync run.
- An export of a selection in both formats, Save and Share on a phone, the zip imported back into an empty library.
- A sync run deleting a ticked song from another device while a sheet is open.

## 9. Not in this plan

- **Deleting what is ticked.** It was not asked for and it is the one bulk action that cannot be taken back: it needs
  a confirmation that counts and names, `DeleteSongUseCase` per file with its partial failures, and a decision about
  the sync guard, which a large deletion made on this device would trip on the next run. Worth its own plan.
- A selection on the Setlists screen, or of setlists.
- Bulk metadata (artist, album), bulk cover art, bulk transposition, bulk Update file name.
- Taking songs out of setlists in bulk.
- Ctrl / Cmd + A, Shift + click ranges and Ctrl / Cmd + click on the desktop and the web. Ctrl + A belongs to the
  search field whenever it has the caret, which makes it a decision rather than a shortcut.
- Dragging across cards to tick them.
- Keeping a selection across the tabs or across a restart.
