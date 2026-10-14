<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :data:model

Pure Kotlin domain models, no dependencies; the layer-crossing currency (stored documents and bytes never leak past
their own module). The one platform difference is `normalizedToNfc` (`library/Normalization.kt`, expect/actual).
`DataState` and `Logger` are at the root, everything else in a feature package; `library` and `preferences` have a
`CLAUDE.md` of their own.
`DataState<T>` (`Idle` with its data; `Loading` / `Failure` with the last data, if any) wraps everything out of a
repository.

## Songs and setlists

- `Song` is metadata only, read at scan time: its first `{tempo}`, `{time}`, and readable `{capo}` are there so a
  metronome paged to the next setlist song has them before its text loads. `canUpdateFileName` is computed once per scan
  because every row asks.
- **`Setlist.unknownFields`** (and each entry's) keep members this version does not know, as opaque JSON, so a later
  version's field survives this one's save. Per-entry transposition, tempo, and capo, `isArchived`, and `description`
  travel with the file.
- Tags, languages, cover, and links live in the song text as directives (see `:chordpro`); `Tag` / `SongLanguage` are
  counted views built per scan. `SongLanguage.UNKNOWN` (`und`, which `:chordpro` reads as no language, so no song
  carries it) groups songs that declare none. Language display names are the platform's (`:presentation`).

## Import

`ImportBudget` is the one class every platform reads incoming files through: a file the import would not look inside
is never kept (an unknown type is read only to check for a zip signature), and an oversized one is reported rather than
loaded. Limits are in `ImportLimits` (sized for an Android heap). `ImportPlan` is computed before anything is written;
`ImportChoices` answers it (unticked `ImportEntryKey`s, Keep both or Replace for all and per row; changing the for-all
choice leaves per-row ones alone). `ExtractedDocument` is transient; no original file is stored with a song.

## Other

- `AuthorizationCompletionPage` and `SystemBrowser` live here, not in `:data:source:remote:api`, so nothing above the
  repositories sees the remote contracts to connect an account.
- Cover search and song details lookup types (`CoverArtQuery`, `SongDetailsQuery`, …): only addresses are ever stored,
  never images, and nothing of a lookup is kept.
- `SyncState.Connected` carries the last run's outcome so a failure stays on screen until replaced. A run with
  `SyncSummary.failed` files or `havePreferencesFailed` is not `isComplete`.
