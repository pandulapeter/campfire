<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui

The view model facade, the composition root, and the product rules every screen follows.

### `ui/CampfireViewModel.kt`

- State lives in plain `internal` holders it builds (not Koin definitions), each taking `viewModelScope`, its use
  cases and narrow lambdas for the holders it calls, so no cycles. Screens call holders through their `internal val`s;
  its own members serve other modules or span holders. **Construction order is behaviour**: holders are declared, and
  their `init` collectors started, in a fixed order.
- Cross-holder reactions are listeners registered in order (`Navigator.addOnBackStackChanged`,
  `DialogHost.addBeforeDialogChange` / `addAfterDialogChange`).
- **Every state is kept up to date from the moment the view model exists** (`stateIn(viewModelScope, Eagerly)`), never
  only while collected: a state started by its first collector shows its initial value for a frame and the screen
  animates the real one in as a change. Several states are also *acted on* (`syncState`, `userPreferences`,
  `setlists`, `hasUnsavedEditorChanges`, `isSyncing`) and must not go stale.
- Theme and language come from `GetUserPreferencesUseCase`, not `ScreenData`, so they don't wait for the library scan.
- `visibleDialog` is written through `setVisibleDialog`, which drops work parked behind a dialog (the exit behind the
  unsaved changes question) once that dialog leaves; a dropped exit is reported to `requestExit`'s `onCancelled`,
  because the macOS quit request must be answered. The song picker after a new setlist and What's new are shown only
  over no dialog (`DialogHost.showIfNoneIsShown`).
- The import is parked on the import screen and dropped however that screen leaves the back stack.
- Intent handlers are non-suspend; library writes go through `launchLibraryChange`, which turns a failure into a
  `Message.OperationFailed` snackbar instead of a crash. An import's writes and the selection's tag/language writes run
  `NonCancellable` once started (no half-imported archive, no tag on half the songs); planning is still cancellable.
- **Search**: runs over `indexedSongs` (`ui/search/SearchIndex.kt`), normalized once per library change, not per
  keystroke. It also holds the *unfiltered* library by file name, which setlists and the details screen
  (`songLookup`) read, since a setlist names its songs whatever the filters hide. A query that folds to nothing
  (punctuation, emoji) is no search. Each list screen's `SearchState` (`components/SearchState.kt`) lives here so the
  desktop's Escape handler, outside the composition, can close it; it holds a `TextFieldState` so text and caret
  survive navigation, takes focus once per opening (`takeFocusOnOpen`), and `close` keeps the text so the field reads
  right while animating away — so lists combine `activeQuery`, gated on `isOpen`, never the raw text.
- **Renames** (`state/songs/SongFilesController.kt`; metronome and navigator follow themselves, `followSongRename`): entries on
  the old name are rewritten, not popped (each song once, or two pages would share a key). The file moves before
  references follow, so `songsBeingRenamed` carries the song under its old name meanwhile; the metronome holds a
  playing click's changes until its context names the new file (`metronomeRenames`). A state, not a wait: no emission
  says it is the last.
- Tags and languages are written into the file (`SetChordProTagUseCase`). Save in the tag sheet writes the whole set in
  one rewrite, removing only tags it offered and left unticked. The tag/language *filter* (`songFilter`) is neither a
  file nor a preference: view model and saved state only. `isSongFilterActive` is asked of the chips shown, not the raw
  selection (which may hold a tag the library lost).
- **Interrupted transitions**: every entry reports `transition.isRunning` (`ReportNavigationTransition`); a back stack
  change during a running transition bumps `navigationGeneration` into the entry metadata so the new scene never equals
  the one it started from. Without it, Navigation 3 takes its "predictive back cancelled" path and gets stuck halfway.
- Top level screens keep their scroll here (`ScrollPosition`, read by `rememberRetainedLazyGridState` /
  `rememberRetainedScrollState`), since Navigation 3 drops an entry's remembered state and tab selection rebuilds the
  stack.

### `ui/CampfireApp.kt`

- Snackbars are queued in the view model with a running number: two identical messages are the same object, so the
  queue numbers them.
- **Rescans** happen only on `ON_START` (not `ON_RESUME`, which iOS sends after Control Center), on desktop window focus
  if the last rescan is ten seconds old (`refreshIfStale`), on launch, and on retry after a failed load — and only where
  the library can change outside the app (`isLibraryEditableOutsideApp`: iOS, desktop). **No pull to refresh or refresh
  button anywhere.** `isLoading` latches off after the first completed read.
- **Nothing is composed until preferences are read** (`arePreferencesLoaded`; read, not read successfully; only ever
  turns on), else the first frame shows the wrong palette and language.

### Performance mode

- `UserPreferences.isPerformanceModeEnabled` ("Read only" in the UI, first row of Settings → Features).
- Hides every way to change the library: both "New" buttons, song menus (and their long press / right click), setlist
  header actions, reorder handles and row menus, "Add to setlist", "Choose songs", Edit song file, and the four playing
  controls — which is every way into the editor. Search, sorting, filters, text size, and the chords switch stay.
- Settings keeps every row (the mode is switched off there); its two library actions are disabled, not hidden.
- Files opened with or shared to Campfire are still imported, and a shared one-song text still opens the editor (see
  `presentation/CLAUDE.md`); paste and every drop are refused (`PastedSongController.isNewSongPasteAvailable`,
  `state/importing/DropAvailability.kt`).

## What a song carries in its file

### Tags are part of the song file

- ChordPro `{tag}` directives, read into `Song.tags`, so a tag travels through export, import, and sync. The library's
  tags are whatever the songs carry. Sorting (most used / alphabetical) is `UserPreferences.tagSortingMode` and
  `languageSortingMode`, shared by the filters and the sheets.

### A tag's color is the library's, not the song's

- `UserPreferences.tagColors`, keyed by `tagKey` (case and Unicode form share one). Never pruned when the last song
  loses the tag (see `data/model/preferences/CLAUDE.md`). Picked from the tag sheet's popup
  (`dialogs/labels/TagColorButton.kt`) and written at once, not on Save. Drawn by `TagIcon` (see
  `components/labels/CLAUDE.md`).

### The language of a song is carried the same way, and is its own category rather than one more tag

- `{meta: language en}` per language, read into `Song.languages` as lowercase ISO codes, 639-2 folded to 639-1 where one
  exists (`eng` → `en`, `rom` kept). Its filter group appears only once the library holds more than one language, with
  "Unknown" for songs declaring none.

### The cover of a song is carried the same way too

- `{meta: cover https://…}`, the first `http(s)` one, into `Song.coverArtUrl`. Any address is accepted; the search only
  recommends. See Cover art in the root `CLAUDE.md`.

### Links about a song are carried the same way

- `{meta: link https://… Optional name}` per link, into `ChordProMetadata.links`; nothing is ever fetched from them. Shown
  in their saved order (tags and languages are always alphabetical), edited with order in Manage links, written on Save.

## How a song is played

### How a song is played is set in the song itself

- Transposition, capo, tempo, and time signature are the first section of the song details grid, each next to its
  control (the Transposition stepper names the written key it takes the chords to; the tempo stepper ends in Tap). They
  scale with the text, hence the floor `UserPreferences.MIN_FONT_SCALE`.
- Transposition, capo, and tempo are overridden per setlist or per device (see `ui/playing/CLAUDE.md`); **only the time
  signature is written into the file** (`{time}`), and the click counts by it.
- **Song defaults sheet** (from About the song's Song defaults tile only) edits the file's four values; empty means the
  default.
- Chords off removes key and capo; metronome off removes tempo and time — everywhere but the editor's preview.
- Card details after the artist are chosen per list (`songCardDetails`, `setlistCardDetails`); tempo follows the metronome
  switch (`CardDetails.isTempoShown`) without writing anything.

## Haptics

- Only what the eye cannot easily follow, through `LocalHapticFeedback` (platform feedback and its setting; nothing on
  desktop; no app switch). Felt: the fast scroller, setlist reorder lift/pass/drop, each repeat of a held stepper (not a
  tap), a `SwitchListItem` flip, and a song row's long press (`combinedClickable`; a missing song's row performs it too).
- Not felt: plain buttons, menus, sheets, checkboxes, and **Tap tempo** (a buzz would compete with the beat). The
  metronome's Vibrate is a feature, see `ui/platform/`.

## Accessibility

- A `Stepper`'s buttons both carry its value as state description (`valueDescription` for a spoken form).
- Section titles use `heading()` on their own `Text` so it merges into a clickable header.
- A notice that appears on its own is a polite live region (`SettingsMessage(isAnnounced = true)`); one present from the
  start is not.
- A row of `Role.RadioButton` chips is a `selectableGroup()`.

## Features

### Features are switched on and off as a whole

- Settings → Features. Every switch is a `UserPreferences` field, never exported or synced, and **only hides**: nothing
  is written or deleted. What each takes:
  - **Read only** (`isPerformanceModeEnabled`): see Performance mode.
  - **Chords** (`areChordsEnabled`): takes chords, key, transposition, and capo; disables the chord spelling rows.
  - **Chord diagrams** (`areChordDiagramsEnabled`): disabled while chords are off; takes the Chords section and the
    Chords tab (`FeatureSwitches.isChordLibraryEnabled`); chosen shapes kept. The Instrument row stays enabled (keyboard
    capo rule, tuner).
  - **Scales** (`areScalesEnabled`): takes the Scales tab and the scale sheet; independent of both chord switches. The
    scale sheet's chord rows follow Chord diagrams.
  - **Scale suggestions** (`areScaleSuggestionsEnabled`): disabled while scales or chords are off, as Chord diagrams is
    with chords, since a suggestion is read off the chords; takes the song's Scales section and the chord sheets' scales.
  - **Metronome**: takes its tab, the click button and panel, the M key, and tempo and time wherever a song is read.
  - **Tuner**: takes its tab and the sheet's entry, and with them every way to ask for the microphone.
  - **Setlists**: takes their tab and every way into one; setlist files still travel through import, export, and sync.
  - **Online song details** (`isSongDetailsLookupEnabled`): takes the lookup button.
  - **Cover art** (`isCoverArtEnabled`): cover fetching, drawing, and search.
- What a switch takes is gone everywhere, Song defaults and About the song included; an override nobody is shown is
  neither named nor reset.
- A disabled tab leaves the chrome (`topLevelDestinations`, animated by `chrome/NavigationItemPresence.kt`); a restored
  stack or opened address is cut at the first screen belonging to a disabled feature
  (`NavigationState.withoutDisabledFeatures`), so `/metronome` with the metronome off opens the songs and rewrites the
  address.
