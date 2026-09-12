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
value), `Load*` (trigger a read), `Save*` / `Create*` / `Rename*` / `Delete*` (change something), `Import*` / `Export*` for the file
paths, `Is*` for a question with a yes or no answer (`IsFirstRun`), or a verb for pure transforms (`NormalizeText`,
`ParseChordPro`, `TransposeChordPro`, `TransposeChordProText`, `ConvertChordProNotation`).

- `ScreenData` bundles what the song and setlist screens need in one object: the setlists (every one of them, archived
  included, in the order `UserPreferences.SetlistSortingMode` asks for), the songs filtered and sorted the way the
  preferences ask for, `tags` (every label the library uses with how many songs carry it, most used first),
  `languages` (the same for the languages its songs declare, with the ones that declare none last) and
  `unfilteredSongs` — the whole library, hidden songs included, which is what a setlist and the song details screen
  are read from: a setlist shows what somebody wrote down rather than a view of the library, so the song filters
  never reach into one, and an entry missing from there is a file that is really gone. Each of the two filter groups
  is counted after every other filter but before its own, so that selecting one value does not empty the list of the
  ones that could be selected next.
- `RenameSetlistUseCase` / `RenameSongFileUseCase` are the two that move a file rather than write one. A setlist's
  file follows its title on its own, since nothing in the library points at a setlist by name; a song's moves only
  when the user asks (`Song.canUpdateFileName` is what offers it), and everything that named the old one — every
  setlist entry holding the song, its saved transposition — moves with it, which is the same walk
  `DeleteSongUseCase` makes to drop those references.
- `GetUserPreferencesUseCase` is separate from `ScreenData` on purpose: the theme and the language must reach the UI
  before the library has been scanned, and folding them into the aggregate would make the whole app wait for the songs.
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
- `IsFirstRunUseCase` is the only one that asks about the installation rather than about the library: whether
  Campfire has ever written its preferences, which is the first thing it writes about itself and therefore the one
  trace a device that has been used has. It exists for the demo library alone, and has to be asked before anything is
  saved, so it is read once as the app starts.
- `NormalizeLanguageCodeUseCase` answers what language a piece of text names, under the code the library files it by:
  the same normalization `SetChordProLanguagesUseCase` puts a code through on its way into a file, offered to the UI so
  that a search field can be typed into with a code rather than a name.
- `ConvertChordProNotationUseCase` is the last step of rendering: it writes the transposed model in German notation
  when `UserPreferences.ChordSpelling` asks for it, and hands the song back untouched when it does not. There is no
  text-level counterpart on purpose — that one rewrites the file, and a file is always written in the app's own
  notation, which is why the two halves of `ChordSpelling` do not travel together everywhere.

Sync adds `GetSyncStateUseCase`, `GetSyncProvidersUseCase`, `ConnectSyncProviderUseCase`,
`DisconnectSyncProviderUseCase`, `RestoreSyncUseCase`, `SynchronizeLibraryUseCase` and
`CancelSynchronizationUseCase` — the only ones that share a file with each other (two of them), since they are one
feature and each is a single line over `SyncRepository`. `GetSyncStateUseCase` is separate from
`GetScreenDataUseCase` for the same reason the preferences are: a settings screen must not wait for a scan of the
whole library to say whether an account is connected. `ConnectSyncProviderUseCase` takes an
`AuthorizationCompletionPage` along with the provider, because the page the desktop's browser lands on after consent
is the one piece of Campfire's text rendered outside the app and the data layer can see neither the translations nor
the chosen language — so the words travel down from the UI like any other string the user reads.
