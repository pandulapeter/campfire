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

Use case interfaces and `ScreenData`: all the presentation layer sees (never repositories). `:presentation` calls
`:libs:chordpro`'s pure functions directly: no use case only forwards to one.

Naming: `Get*` (observe or read), `Load*` (trigger a read), `Save*` / `Create*` / `Update*` / `Rename*` / `Delete*`,
`Import*` / `Export*`, `Is*` for yes/no, a plain verb for pure transforms.

## Rules a change must keep

- `ScreenData.unfilteredSongs` (hidden songs included) is what setlists and song details read: song filters never
  reach into a setlist, and an entry missing there is a file really gone.
- `SongFilter` is deliberately not a preference and never persisted: a filter restored on next launch reads as songs
  gone missing. The match modes (`tagMatchMode`, `languageMatchMode`) are preferences.
- `GetUserPreferencesUseCase` and `GetSyncStateUseCase` are separate from `ScreenData` so theme, language, and sync
  status never wait for a library scan.
- `EditSetlistUseCase` takes the setlist's file name, not a setlist, so the entries written back are the library's,
  not a dialog's stale copy.
- `IsFirstRunUseCase` (has Campfire ever written its preferences) must be read once at start, before anything is saved.
- The sharing use cases ask the connected account's folder (`GetSharedLinksUseCase` only observes this device's copy of
  its list); none waits for a sync run, and none throws but cancellation.
- The editor draft use cases keep a copy against process death, never a write of the song.
- `ForgetSyncConnectionUseCase` tells the service nothing: a first launch runs it before restoring, since the iOS
  Keychain survives an uninstall. `ConnectSyncProviderUseCase` takes an `AuthorizationCompletionPage` because the
  desktop browser's landing page is rendered outside the app and the data layer has no translations.
  `ApplyImportUseCase` records a planted demo, which lets sync take another version's untouched demo instead of keeping
  both.
- Import application stops on a write failure and returns a partial `ImportResult` rather than losing what was
  written; cancellation still throws. The `ImportProgress` observer may run on the worker dispatcher.
