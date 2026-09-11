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
  Applies the "songs without chords" filter, the tag filter and the sorting (by title or artist, through
  `NormalizeTextUseCase`, so accents are ignored), and keeps a `cache` so that a `Loading` or `Failure` state can still
  carry the last good data. The unfiltered file names travel alongside the filtered list — see
  `ScreenData.songFileNames`. There are two filter groups over the same library, the tags and the languages
  (`SongLanguage`, with `UNKNOWN` standing for the songs that declare none), and each is counted over the songs the
  *other* one leaves, so the numbers on a chip say what picking it would actually do. Neither can empty the other:
  what still counts as a tag and what still counts as a language are both decided from the chord-filtered library
  before either filter runs, a selected value no song carries any more is ignored rather than emptying the list
  (which is what lets the preference keep it, see `UserPreferences.selectedTags`), and one the *other* group has
  narrowed down to nothing stays on the list with a count of zero, since a filter that is on has to be visible to be
  turned off. Several selected languages always mean "any of them" — a song is sung in one language or another, never
  in all of them at once, which is why there is no counterpart to `TagMatchMode` here.
- `LoadScreenDataUseCaseImpl` — fans the initial load (or a rescan) out across the repositories in parallel and waits
  for all of them, failures included: one unreadable part of the screen must not keep the rest empty.
- `PrepareImportUseCaseImpl` / `ImportFilesUseCaseImpl` — the import policy, split the way sync's is: one works out
  what would happen, the other carries it out. Preparing unpacks archives (recursively, path stripped), sorts each file
  into song / setlist / skipped by its extension, splits a file holding several songs at `{new_song}` with `:chordpro`,
  and then holds every result against the name it wants: free, taken by exactly this, or taken by something else
  (`ImportPlan.Status`). Only a decision made before anything is written can be put to the user as one question about a
  whole archive, which is the reason for the split — three hundred questions is not a choice. A song is compared by its
  text and a setlist by its title and entries, never by the stored document, which carries a priority the import
  assigns itself; names claimed earlier in the same batch count as taken too, so an archive holding the same song twice
  answers for the second copy the way the library answers for the first. Applying turns each entry plus the
  `ImportConflictResolution` into write / replace / disregard / leave alone, and only `REPLACE` ever overwrites.
  Songs are still written before setlists and the names they actually got are remembered, so that a setlist arriving in
  the same archive still points at its songs after a collision renamed one — a disregarded duplicate maps to the copy
  the library already had.
- `ExportLibraryUseCaseImpl` / `ExportSongsUseCaseImpl` / `ExportSetlistUseCaseImpl` — decide what leaves as what: a
  single song is the `.cho` file as it is on disk, everything else is a zip. The user's transposition is never baked in.
  The name the file leaves under goes through `ExportFileNames.kt` (`campfire_library.zip`, `campfire_songs.zip`, the
  song's or setlist's own title otherwise), where a song's two halves keep the dash of
  `LibraryFiles.ARTIST_TITLE_SEPARATOR` between them; what is *inside* an archive keeps its library names, since a
  setlist points at its songs by file name and the import follows those names.
- `CreateSongUseCaseImpl` — writes the new-song template (`{title}`, `{artist}`, `{key}` and an empty verse).
- `TransposeChordProUseCaseImpl` / `TransposeChordProTextUseCaseImpl` / `ParseChordProUseCaseImpl` /
  `ConvertChordProNotationUseCaseImpl` — thin wrappers over `:chordpro`, so the presentation layer never calls the
  parser directly. `mapper/AccidentalsMappers.kt` is the whole of the translation: `:chordpro` depends on nothing and
  so knows no preferences, and takes the spelling as the nullable `preferFlats` the preference maps onto. The notation
  one needs no mapper — the preference is a flag, and `ChordProNotation` either runs or does not.
- `NormalizeTextUseCaseImpl` — accent-insensitive, case-insensitive text for sorting and searching, over the accent
  table in `Accents.kt` that the export names share.
- `SyncUseCaseImpls.kt` — all seven sync use cases in one file, since each is a line over `SyncRepository` and they
  are one feature. The two that are not: connecting runs a first sync straight away (an account connected onto a
  library that then stays empty leaves the user to work out that something else is expected of them), and restoring
  at startup does the same, but only when the stored credentials actually came back connected. Neither waits for the
  run — `synchronize()` returns immediately and the repository rescans the library when it is done.
