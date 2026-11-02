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

Pure Kotlin domain models, no dependencies; stored documents and bytes never leak past their own module. The one
platform difference is `normalizedToNfc` (expect/actual).

## Songs and setlists

- `Song` is metadata read at scan time: its first `{tempo}`, `{time}`, and readable `{capo}` are there so a metronome
  paged to the next setlist song has them before its text loads; `capoChartKey` (`chordsKey`) lets lists name the
  sounding key without the text. `canUpdateFileName` is computed once per scan because every row asks.
- **`Setlist.unknownFields`** (and each entry's) keep members this version does not know, so a later version's field
  survives this one's save.
- `SongLanguage.UNKNOWN` (`und`, which `:chordpro` reads as no language, so no song carries it) groups songs declaring
  none. Language display names are the platform's (`:presentation`).

## Import

`ImportBudget` is the one class every platform reads incoming files through: a file the import would not look inside is
never kept (an unknown type is read only to check for a zip signature or text, flagged `isPlainTextByContent`), and an
oversized one is reported, not loaded (a text name over its limit is read only to be kept as a zip,
`ImportLimits.isReadAsText`). `ImportLimits` is sized for an Android heap. `ImportPlan` is computed before anything is
written; in `ImportChoices`, changing the for-all choice leaves per-row ones alone. No original file is stored with a
song.

## Other

- `AuthorizationCompletionPage` and `SystemBrowser` live here, not in `:data:source:remote:api`, so nothing above the
  repositories sees the remote contracts.
- Cover search and details lookup: only addresses are ever stored, never images; nothing of a lookup is kept.
- `share/ShareLinks` is the one grammar of shared links (the website's landing page copies its regex): it reads every
  form a platform hands over, tells a newer format (`SharedLink.FromNewerVersion`) from no link, and builds the download
  address from the validated parts, never from the link. A title's control characters read as spaces; its format
  characters, the joiners aside, are dropped.
- `JsonBounds` is the one check (depth, tokens outside strings) of outside JSON before it is parsed.
- `SyncState.Connected` carries the last run's outcome so a failure stays on screen until replaced. `SyncSummary.failed`
  files or `havePreferencesFailed` make a run not `isComplete`; `haveSharedCopiesFailed` never does.
