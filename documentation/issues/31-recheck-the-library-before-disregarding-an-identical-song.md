# Write an "identical" song after all when its library file is gone by the time the question is answered

**Challenged:** amended — the song is written under the name its own header gives it (recomputed with `importFileName`), not under `entry.fileName`, which for an IDENTICAL entry is the *library's* file (a numbered sibling, or an "arrived as" name of an older rule); and the library is checked against the same repository list the planner used (`loadSongsIfNeeded()`, which a sync run refreshes), folded by NFC and case, instead of a `loadSongFileSizes()` listing.

**Kind:** robustness  ·  **Severity:** low  ·  **Platforms:** all with sync
**Files:** `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImpl.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImplTest.kt`,
`domain/implementation/CLAUDE.md`

## Problem
`PrepareImportUseCaseImpl` decides NEW / IDENTICAL / CONFLICTING against the library at prepare time, and an
`ImportReport.Review` question stays open for as long as the user takes. Setlists are planned again at write time
(`ImportPlanner.replanSetlists`), songs are not. A sync run (or a hand edit of the folder) during that time can delete
the library file an IDENTICAL song matched: `Action.DISREGARD` then reports a "duplicate" of a file that is no longer
there and the song the user imported is never written. A REPLACE answer can likewise overwrite a library file that
sync has changed since the plan. Only the first is cheaply fixable and silently loses data; the second was confirmed by
the user in a dialog naming the file.

Note what an IDENTICAL entry's `fileName` is (`ImportPlan.SongEntry` KDoc, `ImportPlanner.planSongs`): "the name of the
library file that already is this song, which may be a numbered sibling of the name it would have wanted" — or the
library name the file arrived under (`arrivedAsNames`, an export of a file named by an older rule). The name the song
itself wants is not in the plan.

## Fix
In `invoke`, before the song loop, and only if the plan has IDENTICAL songs with `repeatedEntryIndex == null`:
```kotlin
// A plan is held against the library as it was when the question was asked; a sync run may have deleted, since then,
// the file an identical song was going to be left as. The names come from the same list the planner used.
val libraryNames = songRepository.loadSongsIfNeeded()?.mapTo(hashSetOf()) { it.fileName.normalizedToNfc().lowercase() }
```
(`normalizedToNfc` from `:data:model`, which `:domain:implementation` already uses; the fold matches `FileNames.kt`'s
`isSameFileNameAs`, so a file another device re-spelt in case only still counts as there). A null list (the folder
could not be read) changes nothing. For such an entry whose folded `fileName` is not in the set, take `Action.WRITE`
instead of `DISREGARD`, writing under the name the song's own header gives it, derived exactly as
`PrepareImportUseCaseImpl` derived it:
```kotlin
songRepository.importFileName(fallbackTitle = entry.sourceFileName?.substringBeforeLast('.').orEmpty(), text = entry.text)
```
(`sourceFileName` is non-null exactly where the prepare step used the file's name as the fallback title: a file that
held one song). It goes through `songRepository.importSong(..., shouldReplace = false)` like any NEW song, so a name taken
meanwhile is numbered, it lands in `importedSongs` (and `convertedSongFileNames` if converted), `storedNames[index]`
and `storedSongFileNames` get the written name (so the batch's setlists and repeats follow it), and it is reported as
imported. `keptSongFileNames` keeps its entry: harmless, it only protects a name from REPLACE. Do not try to re-verify
REPLACE targets (would need the compared text kept in the plan); document in `domain/implementation/CLAUDE.md` (the
`ImportFilesUseCaseImpl` bullet) that a plan is held against the library at prepare time, with only the identical-song
case and setlists re-checked at write time. Drop this plan if the owner prefers the simplicity of "the plan is the
plan": the window needs a sync run that deletes a file during an open question.

## Tests
In `ImportFilesUseCaseImplTest`, with the existing `FakeSongRepository` (its `loadSongsIfNeeded()` lists `files.keys`,
its `importFileName` names by the `{title}`):
- plan an IDENTICAL entry with `fileName = "song_2.cho"`, `sourceFileName = "song_2.cho"`, text titled `Song`, against
  a library that does not hold `song_2.cho`: `importCalls` writes `song.cho`, the result's `importedSongFileNames` holds
  it and `duplicateFileNames` is empty (fails before: nothing written, one duplicate);
- the same with `song_2.cho` in the library: still disregarded, nothing written;
- library holds `Song_2.cho` (case only): still disregarded.

## Manual check
Import a song that is already in the library, but wait at an unrelated conflicts question in the same batch; delete the
library copy from another device and let sync run; answer the question: the song comes back, under its own name.
