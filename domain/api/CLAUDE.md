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

Use case interfaces and `ScreenData`: all the presentation layer sees (never repositories). Exposes `:chordpro` as
`api` because `CreateSongUseCase` and `DuplicateSongUseCase` take `:chordpro` types; `:presentation` calls `:chordpro`'s
pure functions directly (no use case only forwards to one).

Naming: `Get*` (observe or read), `Load*` (trigger a read), `Save*` / `Create*` / `Update*` / `Rename*` / `Delete*`,
`Import*` / `Export*`, `Is*` for yes/no, a plain verb for pure transforms.

## Rules a change must keep

- `ScreenData.unfilteredSongs` (hidden songs included) is what setlists and song details read: song filters never
  reach into a setlist, and an entry missing there is a file really gone. Each filter group is counted after every
  other filter but before its own. Sections are cut where the list is sorted, so a header cannot repeat.
- `SongFilter` is deliberately not a preference and never persisted: a filter restored on next launch reads as songs
  gone missing. The match modes (`tagMatchMode`, `languageMatchMode`) are preferences.
- `GetUserPreferencesUseCase` and `GetSyncStateUseCase` are separate from `ScreenData` so theme, language, and sync
  status never wait for a library scan.
- `EditSetlistUseCase` takes the setlist's file name, not a setlist, so the entries written back are the library's,
  not a dialog's stale copy; title and description are written together and the title decides the file name.
- `SetChordProTagUseCase` is the one `:chordpro` wrapper: tag matching ignores case and folds NFC.
- `IsFirstRunUseCase` (has Campfire ever written its preferences) exists for the demo library and must be read once at
  start, before anything is saved.
- Network: only sync, `GetCoverArtUseCase` / `SearchCoverArtUseCase`, the song details lookup, and
  `DownloadImportFileUseCase` (unused yet; for shared links) reach it, all through `:data:source:remote`.
- Sharing (`sharing/`) is sync's network too: `CreateShareLinkUseCase`, `StopSharingUseCase`,
  `StopSharingEverythingUseCase`, and `LoadSharedLinksUseCase` ask the connected account's folder;
  `GetSharedLinksUseCase` only observes this device's copy of its list. None waits for a run, and none throws but
  cancellation.
- The editor draft use cases keep a copy against process death, never a write of the song.
- `ForgetSyncConnectionUseCase` tells the service nothing: a first launch runs it before restoring, since the iOS
  Keychain survives an uninstall. `ConnectSyncProviderUseCase` takes an `AuthorizationCompletionPage` because the
  desktop browser's landing page is rendered outside the app and the data layer has no translations.
  `RememberDemoLibraryFilesUseCase` lets sync take another version's untouched demo instead of keeping both.
- Import application takes `ImportChoices`, stops on a write failure, and returns a partial `ImportResult` rather than
  losing what was written; cancellation still throws. The `ImportProgress` observer may run on the worker dispatcher.
