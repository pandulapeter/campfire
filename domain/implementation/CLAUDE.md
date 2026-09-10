<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :domain:implementation

Implements `:domain:api` on top of `:data:repository:api`. Koin wiring in `Module.kt` (`domainModule`), all use cases as
`factory`. Impl classes are public with an `internal constructor`.

The ones that carry real logic:

- `GetScreenDataUseCaseImpl` — combines the setlist, song and preference flows into one `Flow<DataState<ScreenData>>`.
  Applies the "songs without chords" filter and the sorting (by title or artist, through `NormalizeTextUseCase`, so
  accents are ignored), and keeps a `cache` so that a `Loading` or `Failure` state can still carry the last good data.
  The unfiltered file names travel alongside the filtered list — see `ScreenData.songFileNames`.
- `LoadScreenDataUseCaseImpl` — fans the initial load (or a rescan) out across the repositories in parallel and waits
  for all of them, failures included: one unreadable part of the screen must not keep the rest empty.
- `ImportFilesUseCaseImpl` — the whole import policy in one place. Archives are unpacked (recursively, path stripped),
  each file is sorted into song / setlist / skipped by its extension, a file holding several songs is split at
  `{new_song}` by `:chordpro`, and the result is an `ImportResult` naming what was written and what was not. Songs are
  written before setlists and the names they actually got are remembered, so that a setlist arriving in the same archive
  still points at its songs after a collision renamed one.
- `ExportLibraryUseCaseImpl` / `ExportSongsUseCaseImpl` / `ExportSetlistUseCaseImpl` — decide what leaves as what: a
  single song is the `.cho` file as it is on disk, everything else is a zip. The user's transposition is never baked in.
- `CreateSongUseCaseImpl` — writes the new-song template (`{title}`, `{artist}`, `{key}` and an empty verse).
- `TransposeChordProUseCaseImpl` / `TransposeChordProTextUseCaseImpl` / `ParseChordProUseCaseImpl` — thin wrappers over
  `:chordpro`, so the presentation layer never calls the parser directly.
- `NormalizeTextUseCaseImpl` — accent-insensitive, case-insensitive text for sorting and searching.
