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

## `CampfireViewModel`

- State lives in plain `internal` holders it builds (not Koin definitions), each taking `viewModelScope`, its use cases,
  and narrow lambdas for the holders it calls, so no cycles. Holders start their own collectors in one `init` at the end
  of their class body and are built so that nothing they start reaches a holder built later; a listener that only calls
  its own holder is registered by it. The cross-holder lifetime rules are policies registered in the view model in one
  place, in the dialog host's and the navigator's `apply`: `SoundLifetime` (sound and listening end with their screen)
  and `ModeEndings` (a mode ends with its screen). The starts still in the view model's `init` need a holder built after
  theirs, wait on its arguments, or are what its tests start by hand (`EditorSession.startRecovery`).
- A value saved once it holds still exists only through `PendingWrites` (`preference`, or `register` for the tempo and
  capo overrides), which the exit, `onCleared`, and the app going to the background (`onAppPaused`) all write: a
  process iOS ends in the background does not wait for a debounce.
- **Every state is `stateIn(viewModelScope, Eagerly)`**, never started by its first collector, which would show its
  initial value for a frame and animate the real one in. Several (`syncState`, `userPreferences`, `setlists`,
  `hasUnsavedEditorChanges`, `isSyncing`) are also acted on and must not go stale.
- Theme and language come from `GetUserPreferencesUseCase`, not `ScreenData`, so they don't wait for the library scan.
- `setVisibleDialog` drops work parked behind a dialog (the exit behind the unsaved changes question) once it leaves; a
  dropped exit is reported to `requestExit`'s `onCancelled`, because the macOS quit request must be answered. The song
  picker after a new setlist and What's new show only over no dialog (`DialogHost.showIfNoneIsShown`).
- The import is parked on the import screen and dropped however that screen leaves the back stack.
- Intent handlers are non-suspend; library writes go through `launchLibraryChange`, which turns a failure into a
  snackbar, not a crash. An import's writes and the selection's tag/language writes run `NonCancellable` once started
  (no half-imported archive, no tag on half the songs); planning stays cancellable.
- **Search** runs over `indexedSongs`, normalized once per library change, not per keystroke. It also holds the
  *unfiltered* library by file name (`songLookup`), since a setlist names its songs whatever the filters hide. A query
  that folds to nothing (punctuation, emoji) is no search. Each list screen's `SearchState` lives here so the desktop's
  Escape handler, outside the composition, can close it (field rules: `screens/CLAUDE.md`).
- **Renames** (`state/songs/SongFilesController.kt`; metronome and navigator follow themselves): back stack entries on
  the old name are rewritten, not popped (each song once, or two pages would share a key). The file moves before
  references follow, so `songsBeingRenamed` carries the song under its old name meanwhile; the metronome holds a playing
  click's changes until its context names the new file (`metronomeRenames`). A state, not a wait: no emission says it is
  the last.
- The tag/language *filter* (`songFilter`) is neither a file nor a preference: view model and saved state only.
  `isSongFilterActive` is asked of the chips shown, not the raw selection (which may hold a tag the library lost).

## `CampfireApp`

- **App-wide shortcuts** (`AppKeyboardShortcuts.kt`, `AppShortcuts.kt`): Ctrl / Cmd + F, V, plus, minus, zero, N, A, S,
  1 to 7, and slash, and M, T, and `?`, are heard at the root after everything under it, on every platform with a
  keyboard that has them (`KeyShortcut.platforms`); the root takes the focus whenever nothing under it holds it (not
  under a dialog or menu), since keys travel only along the focus path. A held key acts once; the keys held are
  forgotten as a dialog, a sheet, or a menu comes or goes, since their release reaches its window instead. Each asks the
  view model whether the screen on top offers what it presses (New only outside read only mode and the selection,
  Ctrl / Cmd + 1 to 7 only over a main screen, the shortcuts sheet over no dialog), and is left unconsumed where it does
  not. `?` is left alone while a field is being typed in (an open text input session, counted by
  `InterceptPlatformTextInput` in `CampfireApp`): the desktop and iOS type a character from an event after its key-down,
  which reaches the root.
- Snackbars are queued with a running number: two identical messages are the same object.
- **Rescans** only on `ON_START` (not `ON_RESUME`, which iOS sends after Control Center), on desktop window focus if the
  last is ten seconds old (`refreshIfStale`), on launch, and on retry after a failed load — and only where the library
  can change outside the app (`isLibraryEditableOutsideApp`: iOS, desktop). **No pull to refresh or refresh button
  anywhere.** `isLoading` latches off after the first completed read.
- **Nothing is composed until preferences are read** (`arePreferencesLoaded`; read, not read successfully; only ever
  turns on), else the first frame shows the wrong palette and language.

## Read only mode

- `UserPreferences.isPerformanceModeEnabled` (first row of the Preferences' General page, `ui/preferences`, shown only
  while the songs are on; with the songs off it is off in effect, `PreferencesController.isPerformanceModeEnabled`, the
  preference kept). Song details' bar shows a mark while it is on, opening the Preferences on General.
- Hides every way to change the library: both "New" buttons, song menus (and their long press / right click), setlist
  header actions, reorder handles and row menus, "Add to setlist", "Choose songs", Edit song file, and the four playing
  controls, which is every way into the editor. Search, sorting, filters, text size, and the chords switch stay.
- Settings keeps every row; its two library actions are disabled, not hidden.
- Files opened with or shared to Campfire are still imported, and a shared one-song text still opens the editor; paste
  and every drop are refused (`PastedSongController.isNewSongPasteAvailable`, `state/importing/DropAvailability.kt`).

## What a song carries in its file

Tags, languages, cover, and links are directives in the song file, so they travel through export, import, and sync.

- **Tags**: `{tag}`; the library's tags are whatever the songs carry. Save in the tag sheet writes the whole set in one
  rewrite, removing only tags it offered and left unticked. Tag and language sorting are shared by filters and sheets.
- **Languages**, a category of their own: `{meta: language en}` each, lowercase ISO codes, 639-2 folded to 639-1 where
  one exists (`eng` → `en`, `rom` kept). The filter group appears only with more than one language in the library, with
  "Unknown" for songs declaring none.
- A song shows its first `MAX_TAGS` tags and `MAX_LANGUAGES` languages; the file keeps the rest, and no edit drops them
  (a sheet takes off only what it offered).
- **A tag's color is the library's** (`UserPreferences.tagColors`, keyed by `tagKey`: case and Unicode form share one),
  written at once from the tag sheet's popup, not on Save.
- **Cover**: the first `http(s)` `{meta: cover …}`. Any address is kept; only public `https` ones are fetched; the
  search only recommends.
- **Links**: `{meta: link https://… Optional name}` each; nothing is ever fetched from them. Shown in their saved order
  (tags and languages always alphabetical), edited with order in Manage links, written on Save.

## How a song is played

- Transposition, capo, tempo, and time signature are the first section of the song details grid, after the instrument
  pill (layout: `songView/firstSection`). The transposition stepper names the key it takes the page's chords to; the
  tempo stepper ends in Tap, followed only by a time signature other than 4/4. They scale with the text, hence
  `UserPreferences.MIN_FONT_SCALE`.
- Transposition, capo, and tempo are overridden per setlist or per device (`ui/playing`); **only the time signature is
  written into the file** (`{time}`), and the click counts by it. The **Song defaults sheet** (only from About the song)
  edits the file's four values; empty means the default.
- Chords off removes key and capo; metronome off removes tempo and time, everywhere but the editor's preview.
- Card details after the artist are fixed per list (`SongCardOptions`): key and tempo on Songs, plus the duration on
  Setlists; the key goes with the chords switch and the tempo with the metronome's, nothing written.

## Haptics

- Only what the eye cannot easily follow, through `LocalHapticFeedback` (no app switch; nothing on desktop). Felt: the
  fast scroller, setlist reorder lift/pass/drop, each repeat of a held stepper (not a tap), a `SwitchListItem` flip, and
  a song row's long press (a missing song's too).
- Not felt: plain buttons, menus, sheets, checkboxes, and **Tap tempo** (a buzz would compete with the beat).

## Accessibility

- A `Stepper`'s buttons both carry its value as state description (`valueDescription` for a spoken form).
- Section titles use `heading()` on their own `Text` so it merges into a clickable header.
- A notice that appears on its own is a polite live region (`SettingsMessage(isAnnounced = true)`); one present from the
  start is not.
- A row of `Role.RadioButton` chips is a `selectableGroup()`.

## Features and song preferences

Every switch is a `UserPreferences` field, never exported or synced, and **only hides**: nothing is written or deleted.
What a switch takes is gone everywhere, Song defaults and About the song included; an override nobody is shown is
neither named nor reset. How a disabled feature leaves the chrome and the back stack: `navigation/`.

Features (Settings → Features):

- **Songs** (`areSongsEnabled`, Settings only, never the onboarding): takes the Songs and Setlists tabs, every song and
  editor screen, and paste (a dropped text too); disables the Setlists, Online song details, and Cover art rows. Imports
  still happen, always through the review and under a warning atop it (as do setlists with Setlists off,
  `ImportFeatureWarning`), but no preview or editor starts (`ImportController`'s `areSongsEnabled`).
- **Random picks** (`isRandomPicksEnabled`, disabled with the songs off): also answers the question under them.
- **Metronome**: its tab, the click button and panel, the M key, and tempo and time wherever a song is read.
- **Tuner**: its tab and the sheet's entry, and with them every way to ask for the microphone.
- **Chords** (`isChordLibraryEnabled`): the Chords tab and the scale sheet's chord rows, whatever songs show.
- **Scales** (`areScalesEnabled`): the Scales tab and the scale sheet, and with them the scale suggestions.
- **Setlists**: their tab and every way into one; files still travel through import, export, and sync. Off with the
  songs too (`FeatureSwitches.areSetlistsEnabled`).
- **Online song details** (`isSongDetailsLookupEnabled`): the lookup button. **Cover art** (`isCoverArtEnabled`):
  fetching, drawing, and search.

Song preferences (the Preferences' Song and Chords pages, `ui/preferences`), each disabled rather than hidden while
another takes what it adds:

- **Chords** (`areChordsEnabled`): a song's chords, key, transposition, and capo; the chord spelling rows are disabled
  only once neither songs nor the Chords and Scales tabs name a chord.
- **Chord diagrams** (`areChordDiagramsEnabled`): disabled while chords are off; takes a song's Chords section, in the
  PDF too; chosen shapes kept. The instrument pill stays (the tuner, the keyboard's sounding names).
- **Chord degrees** (`areChordDegreesEnabled`): off by default, since numerals a reader never asked for look like the
  notation gone wrong; disabled while no diagram it labels is drawn (a song's in neither numbering, or a scale sheet's,
  which needs the Scales and Chords tabs); takes the degree over each diagram's name, in the PDF too, and the scale
  sheet's numerals.
- **Scale suggestions** (`areScaleSuggestionsEnabled`): disabled while scales or chords are off (read off the chords);
  takes the song's Scales section and the chord sheets' scales.
- **Chord names with a capo** (`chordNames`, after the accidentals): shapes fingered above the capo (default) or what
  sounds (`ui/playing`); disabled with songs or chords off and on the keyboard, which always reads what sounds, as its
  description says. Asked once, over the first song a capo matters on, never with the layout question.
- **Capo keeps the key** (`capoKeepsKey`, after it): a capo put on changes only the shapes, the file's `{capo}` alone
  being pitch. Enabled on the keyboard too, which then shows no capo; the question's third answer.
- **Layout** (`songLayout`), heading the Song page section as a segmented control: pages, or one column scrolling at the
  song's pace, asked once over the first song (`screens/songDetails/autoScroll`). The one choice that rearranges rather
  than hides. Under it while pages are picked, **Turn by** (`pageTurn`, never asked): a page, or a column at a time,
  the song a strip sliding sideways, stepped by a row of buttons along the bottom (`songView/grid`,
  `screens/songDetails/stepping`).
