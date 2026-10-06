<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :domain:api

Use case interfaces and the `ScreenData` aggregate. This is what the presentation layer depends on — it never sees
repositories. It exposes `:chordpro` as an `api` dependency, so the UI can render a `ChordProSong` without depending on
the parser itself.

Conventions: one interface per use case, a single `operator fun invoke(...)`, named `Get*` (observe a flow or read one
value), `Load*` (trigger a read), `Save*` / `Create*` / `Update*` / `Rename*` / `Delete*` (change something), `Import*` / `Export*` for the file
paths, `Is*` for a question with a yes or no answer (`IsFirstRun`), or a verb for pure transforms (`NormalizeText`,
`NormalizeSearchText`, `ParseChordPro`, `TransposeChordPro`, `TransposeChordProText`, `ConvertChordProNotation`,
`ConvertChordProTextNotation`).

- `ScreenData` bundles what the song and setlist screens need in one object: the setlists (every one of them, archived
  included, in the order `UserPreferences.SetlistSortingMode` asks for), the songs filtered and sorted the way the
  preferences ask for and the same list cut into its sections (`songSections`, `SongSection`: an artist or an initial
  per header, the texts that start with no letter first) — cut where it is sorted, so that a header cannot come up
  twice, `tags` (every label the library uses with how many songs carry it, most used first),
  `languages` (the same for the languages its songs declare, with the ones that declare none last) and
  `unfilteredSongs` — the whole library, hidden songs included, which is what a setlist and the song details screen
  are read from: a setlist shows what somebody wrote down rather than a view of the library, so the song filters
  never reach into one, and an entry missing from there is a file that is really gone. Each of the two filter groups
  is counted after every other filter but before its own, so that selecting one value does not empty the list of the
  ones that could be selected next. `isWholeLibrary` is false for a value in which an unreadable part stands in empty;
  the demo library and the library counts ask it.
- `EditSetlistUseCase` / `RenameSongFileUseCase` are the two that move a file rather than write one. A setlist's
  file follows its title on its own, since nothing in the library points at a setlist by name — which is why editing
  one is a single use case: the title it is filed under and the description it carries are written together, and only
  the first of them decides where the file goes — and it is told which setlist by file name rather than handed one,
  so that the entries it writes back are the library's and not the ones a dialog was opened with; a song's moves only
  when the user asks (`Song.canUpdateFileName` is what offers it), and everything that named the old one — every
  setlist entry holding the song, its saved transposition — moves with it, which is the same walk
  `DeleteSongUseCase` makes to drop those references.
- `SongFilter` is the tags and languages the song list is narrowed to, and the one input of `GetScreenDataUseCase`
  that comes from above rather than from a repository: the presentation layer holds it and passes it in as a flow.
  It is deliberately not a preference and never written anywhere — a filter is a question asked of the library for
  the moment, and one that came back on the next launch would read as songs having gone missing. How several selected
  values of a group combine (`tagMatchMode`, `languageMatchMode`) is a standing choice, and stays a preference.
- `GetUserPreferencesUseCase` is separate from `ScreenData` on purpose: the theme and the language must reach the UI
  before the library has been scanned, and folding them into the aggregate would make the whole app wait for the songs.
- `PrettifyChordProUseCase` formats raw ChordPro for the editor overflow action, retaining unsupported directives,
  comments and literal environment interiors. Imports use the same formatter in `PrepareImportUseCaseImpl`.

- `TransposeChordProUseCase` transposes the parsed model (what the viewer shows), `TransposeChordProTextUseCase` the raw
  text (what the editor's transpose action rewrites). Both take `UserPreferences.Accidentals` as well as the semitones,
  because how a black key is spelled is the reader's preference and not a property of the move.
- `SetChordProTagUseCase` puts one tag into a document or takes it out of it, leaving the rest of the text byte for
  byte — the text-level half of tagging, like `TransposeChordProTextUseCase`, because the result is written back to
  the user's own file. Which songs a tag then matches is decided in `GetScreenDataUseCase`, without regard to case.
- `SetChordProLanguagesUseCase` is the same for the languages of a song, except that it takes the whole set rather
  than one value at a time: the picker asks about every language before it is closed, and the file is better rewritten
  once than once per checkbox. The codes are normalized by `:chordpro` on the way in, so `en-US`, `EN` and `eng` all
  name the language `en` does.
- `SetChordProLinksUseCase` is the same for every link of a song at once, each a `ChordProLink` with an address and
  optional name: unchanged lines stay as written, renamed ones change in place, and new links follow the others.
- `SetChordProMetadataUseCase` is the same for the single-valued header fields that say what a song is (title, artist, album, year, …),
  given as a map of the fields to change: each is rewritten in place, written into the header, or removed when blank.
- `SetChordProCoverArtUseCase` is the same for the cover, a song having one: it rewrites the cover line in place,
  writes one after the album, or takes it off for null.
- `GetCoverArtUseCase` and `SearchCoverArtUseCase` are the covers themselves: the bytes of one, from the device's copy
  or downloaded, or nothing to show; and a flow of where the search of every catalogue is — the records found so far,
  the catalogues still pending, waiting or failed — which is what the sheet builds its grid, its indicators and its
  retry from. Both reach the network through `:data:source:remote`, the only
  things besides sync that do.
- `IsFirstRunUseCase` is the only one that asks about the installation rather than about the library: whether
  Campfire has ever written its preferences, which is the first thing it writes about itself and therefore the one
  trace a device that has been used has. It exists for the demo library alone, and has to be asked before anything is
  saved, so it is read once as the app starts.
- `NormalizeLanguageCodeUseCase` answers what language a piece of text names, under the code the library files it by:
  the same normalization `SetChordProLanguagesUseCase` puts a code through on its way into a file, offered to the UI so
  that a search field can be typed into with a code rather than a name.
- `ConvertChordProNotationUseCase` is the last step of rendering: it writes the transposed model in the notation of
  `UserPreferences.ChordSpelling`, and hands the song back untouched for the standard one.
  `ConvertChordProTextNotationUseCase` is its text-level counterpart, the editor's two boundaries: a file is shown in
  the reader's notation and what is typed is written back in `UserPreferences.Notation.STANDARD`, the only notation a
  file is ever in. `ParseChordProUseCase` takes the notation its text is written in, the standard one for a file and
  the reader's for the editor's field. The two numberings (`Notation.isNumbering`, Nashville and Roman numerals) are
  only ever the first use case's: no text is converted into them or read as written in them, which is why the editor's
  field is in `Notation.forTyping`. See `:chordpro`'s `ChordProNotation` for how each is read.

`GetEditorDraftUseCase` and `SaveEditorDraftUseCase` read and replace the editor's unsaved text kept outside the
library, which is what reopens an editor the system ended the app under; the stored draft is a copy against the process
ending, never a write of the song.

Sync adds `GetSyncStateUseCase`, `GetSyncProvidersUseCase`, `ConnectSyncProviderUseCase`,
`DisconnectSyncProviderUseCase`, `ForgetSyncConnectionUseCase`, `CancelSyncConnectionUseCase`, `RestoreSyncUseCase`,
`SynchronizeLibraryUseCase` and `CancelSynchronizationUseCase` — the only ones that share a file with each other (two
of them), since they are one feature and each is a single line over `SyncRepository`. `ForgetSyncConnectionUseCase`
differs from the disconnect in telling the service nothing: it is what a first launch runs before restoring, so a
reinstalled app does not pick up credentials the iOS Keychain kept across the uninstall. `GetSyncStateUseCase` is separate from
`GetScreenDataUseCase` for the same reason the preferences are: a settings screen must not wait for a scan of the
whole library to say whether an account is connected. `ConnectSyncProviderUseCase` takes an
`AuthorizationCompletionPage` along with the provider, because the page the desktop's browser lands on after consent
is the one piece of Campfire's text rendered outside the app and the data layer can see neither the translations nor
the chosen language — so the words travel down from the UI like any other string the user reads.

Import preparation and application accept an optional `ImportProgress` observer, which may run on the worker dispatcher.
Application counts duplicates and skipped conflicts as processed entries, stops on a write failure, and returns the
partial `ImportResult` with failed and unprocessed names rather than losing the successful ones; cancellation still throws.
