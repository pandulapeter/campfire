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

Implements `:domain:api` over `:data:repository:api`. Every use case is a `@Factory`; impl classes are public with an
`internal constructor`. A failure a use case notes but does not throw goes to the injected `Logger` (`:data:model`).

## Screen data (`GetScreenDataUseCaseImpl`)

- Built on `Dispatchers.Default` (the view model collects on main), in two halves (songs, setlists) combined at the
  end, so a setlist write does not re-filter and re-sort the library.
- The whole library is sorted once per change of library or sorting mode (`sortMemo`); filters only narrow that order
  (`ScreenData.sortedSongs` is it, for the song picker). Sort key through `NormalizeTextUseCase` (accents ignored); one
  key decides both order and sections; text starting with no letter comes first.
- `cache` lets `Loading` / `Failure` still carry the last good data. With none yet, a part whose first read failed
  stands in empty (songs, setlists) or at defaults (preferences), flagged by `ScreenData.isWholeLibrary = false`, and is
  never cached. A part merely still loading is never filled in.
- Setlists: by date (latest day first, one day's by title) or by title, archived last; never narrowed by the song
  filters. **Every order ends in the file name**: repository lists are unordered (a write moves its item to the end).
- `ScreenData` carries the filter and the three preferences it was built for, so two equal lists built for different
  filters are distinct values to the final `distinctUntilChanged`.
- Tags and languages (`SongLanguage.UNKNOWN` = declares none) are each counted over the songs the *other* group
  leaves. Neither can empty the other: what counts as a tag/language is decided before either filter runs; a selected
  value no song carries any more is ignored (not emptying the list); a value the other group narrowed to nothing stays
  with count zero, sorted last, drawn disabled, so chips hold still. "Any" vs "all" per group (`tagMatchMode`,
  `languageMatchMode`).
- `LoadScreenDataUseCaseImpl` loads all repositories in parallel and waits for all, failures included.

## Import

`PrepareImportUseCaseImpl` + `ImportPlanner` work out what would happen; `ImportFilesUseCaseImpl` carries it out. The
split exists so a whole archive can be put to the user as one review before anything is written.

### Review (product rule)

- **Every import the user starts is reviewed before it writes** on its own screen (`CampfireDestination.ImportReport`,
  `:presentation`'s `ui/screens/importReport/`): every entry listed and ticked; names taken by something *different*
  each get keep-both or replace, plus one choice for all (`ImportChoices`); unticking leaves an entry out; leaving the
  screen cancels. The demo library and a single song file handed over by the system are not reviewed unless a name
  they want is taken.
- **Replacing is the only overwrite in the app**: a choice on that screen plus a confirmation, and only
  `ImportConflictResolution.REPLACE` overwrites. `ImportFilesUseCaseImpl` re-checks the rule at the write.
- A song already in the library under its name, a numbered sibling (`x_2.cho`), or the exact name it arrived under is
  disregarded, compared after `ChordProPrettifier`, notation normalization, and one spelling per directive
  (`{t:X}` = `{title: X}`). Both sides are prettified, so files imported before formatting was automatic still match.
- Two different files of one batch wanting one name are never a question: the second is numbered. A library file the
  batch brings back unchanged is never offered for replacement (found in a first pass over the whole batch, since the
  unchanged copy may come after the song wanting its name); a different one wanting that name is numbered.

### Preparing

- Unpacks archives recursively (paths stripped, the archiver's hidden files skipped). An archive is known by name
  (`LibraryFiles.isArchiveFileName`) or, for an unknown name, by bytes (`isZipArchive`) — never a `.docx`. An archive
  holding nothing importable is reported as unsupported under its own name.
- Splits multi-song files at `{new_song}`; each song is named by `SongRepository.importFileName` from its own header.
  The arrival name is only a fallback *title*, and is used to recognise a library file of exactly that name holding
  the same text (exports carry library names, possibly from an older naming rule).
- Planner compares a song with its whole name family, records batch repeats by entry index, raises one conflict per
  library name, and leaves numbering to the write. Recorded names are the library's own spellings (case / Unicode
  form) as listed (`ImportPlan.SongEntry.replacesFileName`), so no setlist points at a name the song list lacks.
- Songs compare by text, setlists by fields, never by stored document. An undated setlist file
  (`ImportPlan.SetlistEntry.isDated` false) is compared without date or countdown (parsing gives it today; a
  replacement keeps the library's) — so re-importing the demo setlist finds it unchanged. Setlist entries compare as
  they will be written (pointing where each song lands), resolved first among songs of the same picked file
  (`SongEntry.origin`), since names are unique only within an archive.
- Runs on `Dispatchers.Default`, yielding between songs (web painting, cancellation). Reads the library files of every
  touched family up front, 64 at a time in parallel. Owns the size budget: archives share one budget; an oversized
  file goes to `ImportPlan.oversizedFileNames`.
- Documents go through `DocumentRepository`, `mapper/DocumentMappers.kt`, and `ChordSheetConverter`; plain text uses
  the converter too, but ChordPro extensions are untouched. Caps: 16 MiB document input, 8 MiB converted text.
  Extracted prose is a valid import; no readable text is `unreadableDocumentFileNames`, not unsupported. All incoming
  songs are prettified after conversion, splitting, and notation normalization. No origin info survives in the
  library.

### Applying

- A plan is held against the library as prepared (the review may stay open indefinitely). Only two things are
  re-checked at write time: setlists are re-planned on the names songs actually got (`ImportPlanner.replanSetlists`),
  and an identical song whose library file sync deleted meanwhile is written after all. A replacement is not
  re-checked: the user confirmed it by name.
- Songs before setlists. A disregarded duplicate maps to the library copy; a renamed collision (`x_2.cho`) makes the
  setlist naming it a different setlist, numbered rather than disregarded. A setlist that becomes a conflict only at
  re-plan is written numbered (no choice was offered), unless unticked.
- An unticked entry is left alone with its repeats; a new song left out is still pointed at by its batch's setlists
  under its arrival name (shown as missing). Identical entries are disregarded whatever the choices say.
- A write failure stops before any dependent setlist and returns written names, the failed source, and the remainder;
  cancellation propagates. What was written is adopted in one change (`adoptImported`, also after failure or
  cancellation), from the models built, not by rescanning.
- Result lists: duplicates name each library file once, and a repeat of a song this import wrote is not "already in
  the library"; `leftOutFileNames` one per unticked row; `convertedSongFileNames` counts only actual writes;
  `convertedSongToOpen` only for a lone converted song with no other entries or errors.
- `ImportProgress`: unpacking and conversion count input files; applying counts all entries (duplicates and left-out
  included); comparison and adoption are indeterminate.
- `DownloadImportFileUseCaseImpl` feeds a download through preparation as one picked file.

## Export

- A single song exported alone is the `.cho` as on disk; anything else (several songs, a setlist, the library) is a
  zip. Transposition is never baked in.
- `ExportFileNames.kt` names what leaves: a single song by the import's rule (`SongRepository.importFileName`), so it
  comes back under the same name; a setlist by its title through `LibraryFiles.normalizedName`. Inside an archive,
  library names are kept, since setlists point at songs by file name.
- A setlist exported with some songs (`songFileNames`) packs and names only those (the rest of the document kept); a
  ticked song missing from the library is still named. Exported whole, the document is the stored file unchanged.
- A library export whose song or setlist scan failed returns null, never a partial archive. Files are taken from the
  folder (`loadSongFileSizes`, `loadSetlistFileSizes`), not the scan, so new files are included and undecodable
  setlist JSON is backed up as stored. Unreadable or oversized files are named beside the archive.

## Renames and references

- **A file is only renamed by the app when the user asks, or when nothing is lost.** A setlist's file follows its
  title (`EditSetlistUseCase`, which also writes the description), since nothing points at a setlist by name. A
  song's does not: its name may title it, setlists point at it, and the user may have chosen it. **Update file name**
  is offered where name and metadata drift apart beyond case or Unicode form (`Song.canUpdateFileName`). Old names
  stay until one of those happens.
- `RenameSongFileUseCaseImpl` and `DeleteSongUseCaseImpl` share one walk (`useCases/SongReferences.kt`) over what
  names a song by file: setlists, saved transposition and tempo, folded sections. Rename moves the file first, then
  follows every reference; delete drops them. Every reference is attempted even after one fails; failure is returned,
  not thrown; not cancellable once the file has moved / is gone. Setlists naming the song are found from the files
  (`loadSetlistFileNamesNaming`), not the cache, and changed through `updateSetlist`. A setlist already naming the new
  name keeps one entry.

## Others with logic

- `CreateSongUseCaseImpl`: template with `{title}`, `{artist}`, empty `{key}`, `{capo: 0}`, `{tempo: 120}`, `{time: 4/4}`
  (the values a song declaring none plays by), and an empty verse; nonblank optional fields via
  `ChordProMetadataFields`.
- `DuplicateSongUseCaseImpl`: the original's current file text with only the changed fields set, named by the copy's
  own title and artist (unchanged copy gets numbered). Null if the original is unreadable.
- `EditSongsUseCaseImpl`: `SongRepository.editSongs`, many files, one announcement.
- `:chordpro` wrappers (`Transpose*`, `Parse*`, `Convert*Notation`, `Prettify*`, `SetChordPro*`) exist so the
  presentation never calls the parser. `:chordpro` knows no preferences: `mapper/AccidentalsMappers.kt` (nullable
  `preferFlats`) and `mapper/NotationMappers.kt` (`ChordNotation`) are the whole translation.
- `NormalizeTextUseCaseImpl` uses `:data:model`'s `withoutAccent` table (shared with file names) and drops combining
  marks. `NormalizeSearchTextUseCaseImpl` also strips spaces, punctuation, and symbols (`acdc` finds `AC/DC`); it is
  not used for sorting, which it would break (`a b` after `ab c`).
- `SyncUseCaseImpls.kt`: connecting, and restoring at startup when credentials come back connected, start a sync run
  at once (not debounced) and do not wait for it.
