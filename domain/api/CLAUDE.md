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
value), `Load*` (trigger a read), `Save*` / `Create*` / `Delete*` (change something), `Import*` / `Export*` for the file
paths, or a verb for pure transforms (`NormalizeText`, `ParseChordPro`, `TransposeChordPro`, `TransposeChordProText`,
`ConvertChordProNotation`).

- `ScreenData` bundles what the song and setlist screens need in one object: the setlists, the songs filtered and sorted
  the way the preferences ask for, `tags` (every label the library uses with how many songs carry it, most used first),
  `languages` (the same for the languages its songs declare, with the ones that declare none last) and
  `songFileNames` — every song in the library including the ones a filter is hiding, so that a setlist entry can
  tell "hidden" from "the file is gone". Each of the two filter groups is counted after every other filter but before
  its own, so that selecting one value does not empty the list of the ones that could be selected next.
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
