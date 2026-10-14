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

Implements `:domain:api` over `:data:repository:api`. Every use case is an `internal` `@Factory` class, as the root's
conventions have it. A failure a use case notes but does not throw goes to the injected `Logger` (`:data:model`).

## Screen data (`GetScreenDataUseCaseImpl`)

- Built on `Dispatchers.Default` in two halves (songs, setlists) combined at the end, so a setlist write does not
  re-filter and re-sort the library.
- The library is sorted once per change of library or sorting mode (`sortMemo`); filters only narrow that order. One
  key (`NormalizeTextUseCase`, accents ignored) decides both order and sections.
- `cache` lets `Loading` / `Failure` still carry the last good data. With none yet, a part whose first read failed
  stands in empty (songs, setlists) or at defaults (preferences), flagged by `ScreenData.isWholeLibrary = false`, and is
  never cached. A part merely still loading is never filled in.
- Setlists are never narrowed by the song filters. **Every order ends in the file name**: repository lists are
  unordered (a write moves its item to the end).
- `ScreenData` carries the filter and the three preferences it was built for, so two equal lists built for different
  filters are distinct values to the final `distinctUntilChanged`.
- Tags and languages (`SongLanguage.UNKNOWN` = declares none) are each counted over the songs the *other* group
  leaves. Neither can empty the other: what counts as a tag/language is decided before either filter runs; a selected
  value no song carries any more is ignored (not emptying the list); a value the other group narrowed to nothing stays
  with count zero, sorted last, drawn disabled, so chips hold still.

## Import

`PrepareImportUseCaseImpl` + `ImportPlanner` work out what would happen; `ImportFilesUseCaseImpl` carries it out.

### Review (product rule)

- **Every import the user starts is reviewed before it writes** on its own screen (`CampfireDestination.ImportReport`,
  `:presentation`'s `ui/screens/importReport/`): every entry listed and ticked; names taken by something *different*
  each get keep-both or replace, plus one choice for all (`ImportChoices`); unticking leaves an entry out.
- **Replacing is the only overwrite in the app**: a choice on that screen plus a confirmation, and only
  `ImportConflictResolution.REPLACE` overwrites. `ImportFilesUseCaseImpl` re-checks the rule at the write.
- A song already in the library under its name, a numbered sibling (`x_2.cho`), or the exact name it arrived under is
  disregarded, compared after `ChordProPrettifier`, notation normalization, and one spelling per directive
  (`{t:X}` = `{title: X}`). Both sides are prettified, so files imported before formatting was automatic still match.
- Two different files of one batch wanting one name are never a question: the second is numbered. A library file the
  batch brings back unchanged is never offered for replacement (found in a first pass over the whole batch, since the
  unchanged copy may come after the song wanting its name); a different one wanting that name is numbered.

### Preparing

- Unpacks archives recursively. An archive is known by name or, for an unknown name, by bytes (`isZipArchive`) —
  never a `.docx`.
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
- Runs on `Dispatchers.Default`, yielding between songs (web painting, cancellation). Owns the size budget: archives
  share one.
- Documents and plain text go through `ChordSheetConverter`; ChordPro extensions never do. No readable text is
  `unreadableDocumentFileNames`, not unsupported. All incoming songs are prettified after conversion, splitting, and
  notation normalization. No origin info survives in the library.

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
  the library"; `convertedSongToOpen` only for a lone converted song with no other entries or errors.

## Export

- A single song exported alone is the `.cho` as on disk; anything else (several songs, a setlist, the library) is a
  zip. Transposition is never baked in.
- `ExportFileNames.kt` names a single song by the import's rule (`SongRepository.importFileName`), so it comes back
  under the same name. Inside an archive, library names are kept, since setlists point at songs by file name.
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
  names a song by file: setlists and the per-song preference maps (`UserPreferences.withSongRenamed`). Rename moves
  the file first, then follows every reference; delete drops them. Every reference is attempted even after one fails;
  failure is returned, not thrown; not cancellable once the file has moved / is gone. Setlists naming the song are
  found from the files, not the cache, and changed through `updateSetlist`.

## Others with logic

- `CreateSongUseCaseImpl`'s template carries empty `{key}`, `{capo: 0}`, `{tempo: 120}`, and `{time: 4/4}`: the values
  a song declaring none plays by.
- `DuplicateSongUseCaseImpl`: the original's current file text with only the changed fields set, named by the copy's
  own title and artist (unchanged copy gets numbered). Null if the original is unreadable.
- `DeleteLibraryUseCase` (Settings → Library, after typing `DELETE`) deletes every song and setlist: it stops any
  running sync, then runs one with deletions allowed (the typed word answering the guard), emptying the cloud folder and
  every synced device too.
- `:chordpro` use cases (`Transpose*`, `Parse*`, `Convert*Notation`, `Prettify*`, `SetChordPro*`) are kept where a
  preference is translated for `:chordpro` (which knows none): `mapper/AccidentalsMappers.kt` (nullable `preferFlats`)
  and `mapper/NotationMappers.kt` (`ChordNotation`) are the whole translation.
- `NormalizeTextUseCaseImpl` uses `:data:model`'s `withoutAccent` table (shared with file names) and drops combining
  marks. `NormalizeSearchTextUseCaseImpl` also strips spaces, punctuation, and symbols; it would break sorting
  (`a b` after `ab c`).
- Connecting (`ConnectSyncProviderUseCaseImpl`), and restoring at startup when credentials come back connected
  (`RestoreSyncUseCaseImpl`), start a sync run at once (not debounced) and do not wait for it.
