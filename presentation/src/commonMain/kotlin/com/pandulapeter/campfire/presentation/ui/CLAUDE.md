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
- `setVisibleDialog` drops work parked behind a dialog (the exit behind the unsaved changes or the stop the import
  question, `PendingExit.question`) once it leaves; a dropped exit is reported to `requestExit`'s `onCancelled`, because
  the macOS quit request must be answered. The song picker after a new setlist and What's new show only over no dialog (`DialogHost.showIfNoneIsShown`).
- The import is parked on the import screen and dropped however that screen leaves the back stack.
- **The import's work belongs to the app**: the queue, the preparation and its plan, and the Import write live in
  `state/importing/ImportWork` (a `@Single` on `ApplicationScope`, the one holder Koin builds); `ImportController` is its
  view. A cleared view model cancels none of it: the next one's `startQueue` takes a preparation left behind first and
  waits out a write. A review waiting for the user is not work and goes with its view model. What a batch staged (its
  received or downloaded archive and the files unpacked from it, `ImportRequest.stagedFiles`) is deleted when
  `ImportWork.settle` lets it go, never before, so Try again reads the same staged archive. An Import that finds a
  staged file gone (`StagedFileMissingException`, nothing written) turns into the failure stage, whose Try again reads
  what of the batch and of every batch that joined its review was staged (`ImportRequest.readAgainAfter`) again, the
  joined ones sent back into the queue to join the new review. An archive still being received
  (`ImportedFile.receiving`, see `ui/platform`) is awaited first, as `RECEIVING` with its bytes, and the batch's source
  becomes its staged copy; a Cancel then, or a batch settled before its preparation took it, discards it. One refused
  for space fails with no Try again (`ArchiveNotReceivedException`), there being nothing left to read again.
- Intent handlers are non-suspend; library writes go through `launchLibraryChange`, which turns a failure into a
  snackbar, not a crash; a write refused for space says so (`MessageSink.sendFailure`, `Message.StorageFull`). An import's writes and the selection's tag/language writes run `NonCancellable` once started
  (no half-imported archive, no tag on half the songs); planning stays cancellable.
- **Search** runs over `indexedSongs`, normalized once per library change, not per keystroke. It also holds the
  *unfiltered* library by file name (`songLookup`), since a setlist names its songs whatever the filters hide. A query
  that folds to nothing (punctuation, emoji) is no search. Each list screen's `SearchState` lives here so the desktop's
  Escape handler, outside the composition, can close it (field rules: `screens/CLAUDE.md`).
- **Renames** (`state/songs/SongFilesController.kt`; metronome and navigator follow themselves): back stack entries on
  the old name are rewritten, not popped (each song once, or two pages would share a key). The library drops the old
  name before the screens follow, so `songsBeingRenamed` carries the song under its old name meanwhile; the metronome holds a playing
  click's changes until its context names the new file (`metronomeRenames`). A state, not a wait: no emission says it is
  the last.
- The tag/language *filter* (`songFilter`) is neither a file nor a preference: view model and saved state only.
  `isSongFilterActive` is asked of the chips shown, not the raw selection (which may hold a tag the library lost).

## `CampfireApp`

- **App-wide shortcuts** (`AppKeyboardShortcuts.kt`, `AppShortcuts.kt`): Ctrl / Cmd + F, V, plus, minus, zero, N, A, S,
  1 to 7, and slash, and M, T, U, and `?`, are heard at the root after everything under it, on every platform with a
  keyboard that has them (`KeyShortcut.platforms`); the root takes the focus whenever nothing under it holds it (not
  under a dialog or menu), since keys travel only along the focus path. A held key acts once; the keys held are
  forgotten as a dialog, a sheet, or a menu comes or goes, since their release reaches its window instead. Each asks the
  view model whether the screen on top offers what it presses (New only outside read only mode and the selection,
  Ctrl / Cmd + 1 to 7 only over a main screen, the shortcuts sheet over no dialog, U only over song details with the Tuner
  feature on), and is left unconsumed where it does not. `?` and U are left alone while a field is being
  typed in (an open text input session, counted by
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
  while the songs are on; with the songs off it is off in effect, `isReadOnlyInEffect`, the preference kept). Song
  details' bar shows a mark while it is on, opening the Preferences on General.
- Hides every way to change the library: both "New" buttons, song menus (and their long press / right click), setlist
  header actions, reorder handles and row menus, "Add to setlist", "Choose songs", and Edit song file, which is every way into
  the editor, and the Playing and Tempo sheets the dock's chips open. Search, sorting, filters, text size, and the chords switch stay.
- Folds nothing: every section is shown whole and no fold toggle is offered, so nothing goes missing on stage; the
  saved folds are kept, and come back once the mode is off.
- Settings keeps every row; its two library actions are disabled, not hidden.
- Files opened with, shared to, or dropped on Campfire are still imported, and a shared or dropped one-song text still
  opens the editor (`PastedSongController.isNewSongDropAvailable`): each is the user bringing something in on purpose.
  Only paste is refused (`isNewSongPasteAvailable`), so a stray Ctrl / Cmd + V makes no song.

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

- Song details' dock under the song (`screens/songDetails/dock`) starts with the key chip: the key of the page's chords
  and, wherever it is not zero, the capo. It opens the **Playing sheet** (`dialogs/playing`: the instrument
  and tuning, and the transposition and capo steppers, `songView/firstSection`'s), read only mode's opening nothing; the
  tuner is the dock's own. A dock that would hold only the key chip leaves it, and the tuner, to the app bar. The **tempo chip** ends it: the page's tempo, the bar lit while the click plays, and play and stop; the
  chip opens the **Tempo sheet** (`dialogs/playing`: the tempo stepper ending in Tap, the song's accents, and play and
  stop), read only mode's opening nothing. With the chords off the dock is the metronome panel itself. The dock and the sheets are drawn
  at the interface's text size, never the song's; the editor's preview shows the four values in its About the song
  tiles.
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
  editor screen, and paste (a dropped text too); disables the Setlists, Attachments, Online song details, Cover art,
  Pictures from links, and Pages from links rows. Imports still happen, always through the review and under a warning
  atop it (as do setlists with Setlists off, `ImportFeatureWarning`), but no preview or editor starts
  (`ImportController`'s `areSongsEnabled`).
- **Random picks** (`isRandomPicksEnabled`, disabled with the songs off): also answers the question under them.
- **Metronome**: its tab, a song's tempo chip and Tempo sheet, the M and T keys, and tempo and time wherever a song is read.
  The Song page's Dock section takes the song's part alone (`isDockMetronomeEnabled`), as it does the dock's way to the
  tuner (`isDockTunerEnabled`, the song's menu then holding it); both are device-local.
- **Tuner**: its tab and the sheet's entry, and with them every way to ask for the microphone.
- **Chords** (`isChordLibraryEnabled`): the Chords tab and the scale sheet's chord rows, whatever songs show.
- **Scales** (`areScalesEnabled`): the Scales tab and the scale sheet, and with them the scale suggestions.
- **Setlists**: their tab and every way into one; files still travel through import, export, and sync. Off with the
  songs too (`FeatureSwitches.areSetlistsEnabled`).
- **Attachments** (`isAttachmentsEnabled`, after Setlists, disabled with the songs off): About the song's Attachments
  group (the editor card's too) and the editor's Attachment button; the files songs name still sync, export, and import.
- **Online song details** (`isSongDetailsLookupEnabled`): the lookup button. **Cover art** (`isCoverArtEnabled`):
  fetching, drawing, and search. **Pictures from links** (`isPictureLinkDownloadEnabled`): an import's downloads of the
  pictures its songs name by address; From a link and a missing picture's Download stay. **Pages from links**
  (`isPageLinkLoadingEnabled`, only where the platform can read a page, `CanLoadPagesUseCase`): a shared or pasted
  page's address read once on the device (`/presentation/CLAUDE.md`); off, the links-only snackbar.

Song preferences (the Preferences' Song and Chords pages, `ui/preferences`), each disabled rather than hidden while
another takes what it adds:

- **Chords** (`areChordsEnabled`): a song's chords, key, transposition, and capo; the chord spelling rows are disabled
  only once neither songs nor the Chords and Scales tabs name a chord.
- **Chord diagrams** (`areChordDiagramsEnabled`, in the Song page's Dock section with scale suggestions): disabled
  while chords are off; takes a song's diagrams (the dock's, the preview's and the PDF's Chords section); chosen shapes
  kept. The instrument pill stays (the tuner, the keyboard's
  sounding names).
- **Chord degrees** (`areChordDegreesEnabled`): off by default, since numerals a reader never asked for look like the
  notation gone wrong; disabled while no diagram it labels is drawn (a song's in neither numbering, or a scale sheet's,
  which needs the Scales and Chords tabs); takes the degree over each diagram's name, in the PDF too, and the scale
  sheet's numerals.
- **Scale suggestions** (`areScaleSuggestionsEnabled`): disabled while scales or chords are off (read off the chords);
  takes the dock's scale chips (the preview's Scales section) and the chord sheets' scales.
- **Chord names with a capo** (`chordNames`, after the accidentals): shapes fingered above the capo (default) or what
  sounds (`ui/playing`); disabled with songs or chords off and on the keyboard, which always reads what sounds, as its
  description says. Asked once, over the first song a capo matters on, never with the layout question.
- **Capo keeps the key** (`capoKeepsKey`, after it): a capo put on changes only the shapes, the file's `{capo}` alone
  being pitch. Enabled on the keyboard too, which then shows no capo; the question's third answer.
- **Layout** (`songLayout`), heading the Song page section as a segmented control: pages, or one column scrolling at the
  song's pace, asked once over the first song (`screens/songDetails/autoScroll`). The one choice that rearranges rather
  than hides. Under it while pages are picked, **Turn by** (`pageTurn`, asked under the layout question's cards while
  pages are picked there): a page, or a column at a time, the song a strip sliding sideways, stepped by a row of
  buttons along the bottom (`songView/grid`, `screens/songDetails/stepping`).
