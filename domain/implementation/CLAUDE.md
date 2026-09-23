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

Implements `:domain:api` on top of `:data:repository:api`. Koin wiring: `Module.kt` holds the
`@Module @ComponentScan object DomainModule`, and every use case is a `@Factory`. Impl classes are public with an
`internal constructor`.

The ones that carry real logic:

- `GetScreenDataUseCaseImpl` — combines the setlist, song and preference flows, and the `SongFilter` flow the caller
  passes in, into one `Flow<DataState<ScreenData>>` — in two halves, songs and setlists, combined at the end, so that
  a setlist being written does not filter and sort the whole library again — built on `Dispatchers.Default` since
  the view model collects it on the main thread. Applies the "songs without chords" filter, the tag filter and
  the sorting and the sections it is listed under (by title or artist, through
  `NormalizeTextUseCase`, so accents are ignored; one key decides both, and whatever starts with no letter comes
  first), and keeps a `cache` so that a `Loading` or `Failure` state can still
  carry the last good data — and where there is none yet, a part whose first read failed stands in empty (songs,
  setlists) or at the defaults (preferences), so the part that was read is still shown; such a value says so
  (`ScreenData.isWholeLibrary`) and is never cached. A part that is merely still loading is never filled in. It also
  orders the setlists (newest first or by title, the archived ones after the rest either
  way) — every order it produces ends in the file name, because the repositories' lists are in no particular order (a
  written item moves to the end) and a tie would otherwise be decided by it — without ever narrowing them: the song filters are about the song list, and the setlists screen decides for
  itself whether it is showing the archived ones. The whole library travels alongside the filtered list — see
  `ScreenData.unfilteredSongs`. There are two filter groups over the same library, the tags and the languages
  (`SongLanguage`, with `UNKNOWN` standing for the songs that declare none), and each is counted over the songs the
  *other* one leaves, so the numbers on a chip say what picking it would actually do. Neither can empty the other:
  what still counts as a tag and what still counts as a language are both decided from the chord-filtered library
  before either filter runs, a selected value no song carries any more is ignored rather than emptying the list
  (which is what lets the filter keep it, see `SongFilter.selectedTags`), and one the *other* group has
  narrowed down to nothing stays on the list with a count of zero, sorted last — the controls draw it disabled rather
  than dropping it, so the chips hold still as the other group changes and a selected one can still be turned off. Several selected values of a group mean
  "any of them" or "all of them" as the preferences say, each group on its own (`tagMatchMode`, `languageMatchMode`):
  a song can carry several languages as much as several tags, so "every language" is the question of a bilingual
  song book.
- `LoadScreenDataUseCaseImpl` — fans the initial load (or a rescan) out across the repositories in parallel and waits
  for all of them, failures included: one unreadable part of the screen must not keep the rest empty.
- `PrepareImportUseCaseImpl` / `ImportPlanner` / `ImportFilesUseCaseImpl` — the import policy, split the way sync's
  is: one works out what would happen, the other carries it out. Preparing unpacks archives (recursively, path stripped, the archiving
  tool's own hidden files left where they were, and one that was picked directly counted as skipped), sorts each file into song / setlist / skipped by its extension, splits
  a file holding several songs at `{new_song}` with `:chordpro`, asks `SongRepository.importFileName` what each song's
  own header names it — the name the file arrived under is
  passed as a fallback *title*, for the songs that declare none, and is otherwise only used to recognise a library
  file of exactly that name holding the same text — an export hands songs out under their library names, and one
  named before today's rule is still the same song. `ImportPlanner`
  compares a song with the whole family of its name (the unnumbered file and its numbered siblings), records a repeat
  in the batch by entry index, asks one conflict question per library name — and none about a library file the batch
  itself brings back unchanged, which the planner finds in a first pass over the whole batch before it plans in
  arriving order, since the unchanged copy may come after the song that wants its name — and leaves numbering to the
  write that can see the directory. `ImportFilesUseCaseImpl` holds the same rule once more at the one place a file is
  overwritten. The names it records are the library's own spellings: where the file system answers the derived name
  with a file listed under another spelling of it (another case on macOS and Windows, the other Unicode form on APFS),
  the listed one is what an identical song maps to and what a replacement writes over
  (`ImportPlan.SongEntry.replacesFileName`), so that no setlist of the batch is pointed at a name the song list does
  not hold. Only a decision made before anything is written can be put to the user as one question about a
  whole archive, which is the reason for the split — three hundred questions is not a choice. A song is compared by
  Preparing runs on `Dispatchers.Default`, because decoding, splitting, header parsing and comparisons would otherwise
  occupy the view model's main thread, and yields between songs so the web can paint and cancellation can stop it.
  its text and a setlist by its title and entries, never by the stored document, which carries a priority the import
  assigns itself — and the entries as they will be written, each pointing where its song lands, so a setlist that
  names an incoming song is only the library's one when the song ends up where the library's points. `ImportPlanner` is
  covered by `commonTest`. Applying turns each entry plus the
  `ImportConflictResolution` into write / replace / disregard / leave alone, and only `REPLACE` ever overwrites.
  Songs are still written before setlists and the names they actually got are remembered, so that a setlist arriving in
  the same archive still points at its songs after a collision renamed one — a disregarded duplicate maps to the copy
  the library already had. The setlists are planned again on those names (`ImportPlanner.replanSetlists`), since the
  plan could only expect each song in place: an edited song kept next to the library's is `x_2.cho`, and the unchanged
  setlist naming it is then a different setlist under a taken name, which goes in numbered and points at `x_2.cho`
  rather than being disregarded as the library's copy. A setlist that becomes a conflict only there was not part of
  the question, so it is written numbered whatever the answer was. Preparing owns the import's size budget: every archive unpacks into what the ones before it
  left, and a file over its limit goes to `ImportPlan.oversizedFileNames`, which the UI reports on its own line.
- `ExportLibraryUseCaseImpl` / `ExportSongsUseCaseImpl` / `ExportSetlistUseCaseImpl` — decide what leaves as what: a
  single song is the `.cho` file as it is on disk, everything else is a zip. The user's transposition is never baked in.
  The name the file leaves under goes through `ExportFileNames.kt` (`campfire_library.zip`, `campfire_songs.zip`, the
  song's or setlist's own title otherwise); what is *inside* an archive keeps its library names, since a
  setlist points at its songs by file name and the import follows those names. A library export whose song or setlist
  scan failed is a failed export (null), never an archive of what happened to be read; the songs are taken from the folder
  (`SongRepository.loadSongFileSizes`) rather than from the scan, so that a song written since the scan is in the
  archive and one the scan skipped is read again; what cannot be read, or is larger than a song can be (and so is
  never opened), is named beside the archive. The view model says so after the save.
- `CreateSongUseCaseImpl` — writes the new-song template (`{title}`, `{artist}`, `{key}` and an empty verse).
- `TransposeChordProUseCaseImpl` / `TransposeChordProTextUseCaseImpl` / `ParseChordProUseCaseImpl` /
  `ConvertChordProNotationUseCaseImpl` — thin wrappers over `:chordpro`, so the presentation layer never calls the
  parser directly. `mapper/AccidentalsMappers.kt` is the whole of the translation: `:chordpro` depends on nothing and
  so knows no preferences, and takes the spelling as the nullable `preferFlats` the preference maps onto. The notation
  one needs no mapper — the preference is a flag, and `ChordProNotation` either runs or does not.
- `NormalizeTextUseCaseImpl` — accent-insensitive, case-insensitive text for sorting and searching, over
  `:data:model`'s `withoutAccent` table, which the normalized file names share, with the combining marks of a
  decomposed accent dropped (`isCombiningMark`). `NormalizeSearchTextUseCaseImpl` is the search's key on top of it:
  the same text with the spaces, punctuation and symbols taken out, so `ymca` finds `Y.M.C.A.` and `acdc` finds
  `AC/DC`. It stays apart because a sort by it would file `a b` after `ab c`.
- `ExportFileNames.kt` — what a file is called on the way out: a single song the name its own header gives it, by the
  import's rule (`SongRepository.importFileName`, the file name standing in as the title only where the song declares
  none) with its stored extension, so that it comes back under the name it left under; a setlist its title through
  `LibraryFiles.normalizedName`.
- `RenameSongFileUseCaseImpl` — the mirror image of `DeleteSongUseCaseImpl`: the same two places refer to a song by
  its file name (the setlists holding it, the saved transposition), and where a deletion drops those references a
  rename follows them. The file moves first, so nothing is ever pointed at a name that does not exist yet, and once it
  has moved every reference is attempted even after one fails; whether any failed is returned at the end rather than
  thrown, since the move has happened and the caller has to follow it either way, and the walk is not cancellable
  once the file has moved (nor is the deletion's once the file is gone). Both are one walk (`useCases/SongReferences.kt`):
  which setlists name the song is asked of the files (`SetlistRepository.loadSetlistFileNamesNaming`) rather than of
  the cache, which does not know what sync or an import wrote since the last rescan; each one is changed through
  `updateSetlist`, so it builds on the latest version and waits for a change that is being written; and every
  reference is attempted even after one fails, the deletion answering like the rename whether all of them followed.
- `EditorDraftUseCaseImpls.kt` — the two editor draft use cases in one file, each a line over
  `EditorDraftRepository`.
- `SyncUseCaseImpls.kt` — all eight sync use cases in one file, since each is a line over `SyncRepository` and they
  are one feature. The two that are not: connecting runs a first sync straight away (an account connected onto a
  library that then stays empty leaves the user to work out that something else is expected of them), and restoring
  at startup does the same, but only when the stored credentials actually came back connected. Neither waits for the
  run — `synchronize()` returns immediately and the repository rescans the library when it is done.
