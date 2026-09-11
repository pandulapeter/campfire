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
  the way the preferences ask for, and `songFileNames` — every song in the library including the ones a filter is
  hiding, so that a setlist entry can tell "hidden" from "the file is gone".
- `GetUserPreferencesUseCase` is separate from `ScreenData` on purpose: the theme and the language must reach the UI
  before the library has been scanned, and folding them into the aggregate would make the whole app wait for the songs.
- `TransposeChordProUseCase` transposes the parsed model (what the viewer shows), `TransposeChordProTextUseCase` the raw
  text (what the editor's transpose action rewrites). Both take `UserPreferences.Accidentals` as well as the semitones,
  because how a black key is spelled is the reader's preference and not a property of the move.
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
