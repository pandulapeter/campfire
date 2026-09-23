# 23 — Renaming or deleting a song misses setlists the cache has not seen, and a deletion stops at the first failure

**Severity:** dangling references, a success reported for a walk that could not be made (all platforms) · **Area:** `:domain:implementation` (`RenameSongFileUseCaseImpl.kt`, `DeleteSongUseCaseImpl.kt`), `:data:repository` (`SetlistRepository`), `:presentation` (`CampfireViewModel.kt`, one message)

**Read, not run.** This was found by reading the two use cases and the setlist repository at HEAD (2065e47f); it has
not been reproduced in a running build. The "Verification" section below is how to confirm it, and confirming it is
the first step of the work.

## What the user sees

1. **A setlist written since the last rescan keeps the old name.** Sync writes setlist files without going through
   the repository and only rescans at the end of a run (and every few seconds during it); an import writes its
   setlists and rescans in a `finally` at the end. A song renamed ("Update file name") or deleted in that window is
   followed only in the setlists the cache already held. A setlist that arrived in between keeps naming the old file
   and shows the song as missing, and the rename reports that every reference followed.
2. **A setlist scan that failed is taken for "no setlists".** If the setlist directory could not be read (the scan
   is `Failure(null)`), a rename walks nothing and reports `haveReferencesFollowed = true` — no "a setlist still
   points at the old name" message — and a deletion drops nothing.
3. **One setlist that cannot be written stops a deletion.** The song file is already gone; the first `updateSetlist`
   that throws ends the walk, so the remaining setlists and the saved transposition keep naming the deleted file,
   and the view model reports "Something went wrong" (`Message.OperationFailed`) and leaves the song details / editor
   screen open on a file that no longer exists (the pops after `deleteSong.invoke` never run).

`domain/implementation/CLAUDE.md` says the deletion is "the same walk" as the rename, and the rename's is written to
attempt every reference and report failures at the end.

## Cause

`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/RenameSongFileUseCaseImpl.kt:48-62`:

```kotlin
    private suspend fun updateReferences(song: Song, renamed: String): Boolean {
        val failures = mutableListOf<Exception>()
        setlistRepository.loadSetlistsIfNeeded().orEmpty()
            .filter { setlist -> setlist.entries.any { it.songFileName == song.fileName } }
            .forEach { setlist ->
                attempt(failures) {
                    setlistRepository.updateSetlist(setlist.fileName) { latest ->
```

`DeleteSongUseCaseImpl.kt:37-48`:

```kotlin
    private suspend fun removeReferences(fileName: String) {
        setlistRepository.loadSetlistsIfNeeded().orEmpty()
            .filter { setlist -> setlist.entries.any { it.songFileName == fileName } }
            .forEach { setlist ->
                setlistRepository.updateSetlist(setlist.fileName) { latest ->
                    latest.copy(entries = latest.entries.filterNot { it.songFileName == fileName })
                }
            }
        userPreferencesRepository.loadUserPreferencesIfNeeded()
            ?.takeIf { fileName in it.transpositions }
            ?.let { userPreferencesRepository.saveUserPreferences(it.copy(transpositions = it.transpositions - fileName)) }
    }
```

- `loadSetlistsIfNeeded()` is the cache (`BaseLocalDataRepository.loadDataIfNeeded`,
  `data/repository/implementation/.../base/BaseLocalDataRepository.kt:79-81`): it reads the directory only the first
  time, or after a failure. `SetlistRepositoryImpl.latest` (`SetlistRepositoryImpl.kt:110-120`) reads the *file* for
  each setlist it is asked to change — the repository's own KDoc (`:104-107`) says why: "Sync writes setlist files
  without going through this repository, and the cache only catches up at the next rescan" — but which setlists the
  walks ask about is still decided by the cache.
- `.orEmpty()` turns the `null` that means "the setlists could not be read" (`SetlistRepository.kt`, "or null if they
  could not be read") into "there are none", and the rename then returns `failures.isEmpty()` = true.
- `removeReferences` has no `attempt`: the first exception propagates out of `DeleteSongUseCaseImpl.invoke` (`:32-35`)
  to `CampfireViewModel.deleteSong` (`presentation/.../ui/CampfireViewModel.kt:1126-1136`) inside
  `launchLibraryChange` (`:1984-1993`), which reports `OperationFailed` and skips the screen clean-up that follows the
  call.

## The change

Invoke the **`code-style`** skill before the first edit.

Three parts: ask the files, not the cache, which setlists name the song; treat not being able to ask as a failed
reference; and make the deletion's walk the rename's walk, in one function both use.

### `SetlistRepository` — which setlists name a song, from the files

`data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SetlistRepository.kt`, after
`loadSetlistsIfNeeded`:

```kotlin
    /**
     * The file name of every setlist that names [songFileName], read from the setlist files rather than from the cache:
     * sync and an import write setlist files behind this repository's back, and the cache only catches up at the next
     * rescan. For the walks that follow a song's file name - a rename, a deletion - where a setlist the cache has not
     * seen yet would go on naming a file that is gone. A setlist the cache holds counts as well, since [updateSetlist]
     * changes one whose file cannot be decoded as the cache has it. Nothing is cached.
     *
     * Throws when the setlists cannot be listed: not knowing which setlists name the song is not knowing that none do.
     */
    suspend fun loadSetlistFileNamesNaming(songFileName: String): List<String>
```

`SetlistRepositoryImpl.kt`:

```kotlin
    override suspend fun loadSetlistFileNamesNaming(songFileName: String): List<String> {
        fun Setlist.names() = entries.any { it.songFileName == songFileName }
        val onDisk = setlistLocalSource.loadSetlists().filter { it.names() }.map { it.fileName }
        val cached = loadDataIfNeeded().orEmpty().filter { it.names() }.map { it.fileName }
        return (onDisk + cached).distinct()
    }
```

`SetlistLocalSource.loadSetlists` (`data/source/local/implementation/.../source/SetlistLocalSourceImpl.kt:37-59`)
throws when the directory cannot be listed and skips a single file it cannot read, which is exactly the contract
above (the skipped file is covered by the cache half, and a file that is neither readable nor cached cannot be
changed by `updateSetlist` either). Setlists are few and small; reading them all once per rename or deletion costs
far less than the song file the action itself writes. It is deliberately *not* done under `writeMutex` /
`LibraryFileLock`: `updateSetlist` reads each file again under both locks before it changes it, so the list is only
used to decide which files to ask, and taking `LibraryFileLock` here would deadlock with the `updateSetlist` calls
that follow (the lock is not reentrant, see `data/repository/implementation/CLAUDE.md`). The window that remains — a
setlist written by sync between this read and the walk — is the same one every other change to a setlist has, and
the next run brings the entry in as a missing song rather than losing anything.

### One walk for both use cases

New file
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/SongReferences.kt`
(MPL header per `code-style`):

```kotlin
/**
 * Everything in the library that refers to a song by its file name - the setlists holding it and its saved
 * transposition - moved to [newFileName], or dropped where that is null. The walk a rename and a deletion both make
 * once the file itself has moved or gone, which is why it is shared: neither can be undone at that point, so every
 * reference is attempted even after one fails, and whether all of them followed is the answer rather than an
 * exception. The caller runs it [kotlinx.coroutines.NonCancellable], for the same reason.
 *
 * Which setlists name the song is asked of the files ([SetlistRepository.loadSetlistFileNamesNaming]) rather than of
 * the cache, which does not know a setlist sync or an import wrote since the last rescan; not being able to ask counts
 * as a reference that did not follow.
 */
internal suspend fun followSongReferences(
    setlistRepository: SetlistRepository,
    userPreferencesRepository: UserPreferencesRepository,
    fileName: String,
    newFileName: String?,
): Boolean {
    val failures = mutableListOf<Exception>()
    suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        failures += exception
        null
    }
    attempt { setlistRepository.loadSetlistFileNamesNaming(fileName) }.orEmpty().forEach { setlistFileName ->
        attempt {
            setlistRepository.updateSetlist(setlistFileName) { latest ->
                latest.copy(
                    entries = if (newFileName == null) {
                        latest.entries.filterNot { it.songFileName == fileName }
                    } else {
                        latest.entries.map { entry -> if (entry.songFileName == fileName) entry.copy(songFileName = newFileName) else entry }
                    },
                )
            }
        }
    }
    attempt {
        userPreferencesRepository.loadUserPreferencesIfNeeded()
            ?.takeIf { fileName in it.transpositions }
            ?.let { preferences ->
                val transpositions = preferences.transpositions - fileName
                userPreferencesRepository.saveUserPreferences(
                    preferences.copy(
                        transpositions = if (newFileName == null) {
                            transpositions
                        } else {
                            transpositions + (newFileName to preferences.transpositions.getValue(fileName))
                        },
                    ),
                )
            }
    }
    failures.forEach { println("A reference to \"$fileName\" could not be updated: ${it.message}") }
    return failures.isEmpty()
}
```

(`updateSetlist` returning null — the setlist's file is gone — is not a failure: there is nothing left that names the
song. That matches today.)

`RenameSongFileUseCaseImpl.kt`: delete `updateReferences` and `attempt` (`:48-85`) and call

```kotlin
        val haveReferencesFollowed = withContext(NonCancellable) {
            followSongReferences(setlistRepository, userPreferencesRepository, fileName = song.fileName, newFileName = renamed)
        }
```

The KDoc of `invoke` (`:30-41`) stays true; add "Which setlists hold the song is asked of the files rather than the
cache (see `followSongReferences`)."

### `DeleteSongUseCase` reports whether the references followed

`domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/DeleteSongUseCase.kt`:

```kotlin
    /**
     * Deletes the file and removes the song from every setlist and from the saved transpositions. Throws when the file
     * could not be deleted, and nothing else has been touched then. Once it is gone the rest is attempted whatever
     * fails, and the answer is whether every reference could be removed: false leaves a setlist or the saved
     * transposition naming a file that is gone - a setlist then shows the song as missing.
     */
    suspend operator fun invoke(fileName: String): Boolean
```

`DeleteSongUseCaseImpl.kt`:

```kotlin
    override suspend operator fun invoke(fileName: String): Boolean {
        songRepository.deleteSong(fileName)
        return withContext(NonCancellable) {
            followSongReferences(setlistRepository, userPreferencesRepository, fileName = fileName, newFileName = null)
        }
    }
```

and delete `removeReferences`.

### `CampfireViewModel.kt` — say it after the screens have closed

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:1126-1136`:

```kotlin
    fun deleteSong(fileName: String) = launchLibraryChange {
        val haveReferencesBeenRemoved = deleteSong.invoke(fileName)
        ... (unchanged: the draft, the screens, the texts)
        _songTexts.update { it - fileName }
        // Said once the screens have let go of the file, which is gone whatever else could not be rewritten.
        if (!haveReferencesBeenRemoved) sendMessage(Message.SongDeletedPartly)
    }
```

A `data object SongDeletedPartly : Message` next to `SongFileRenamedPartly` (`:2120`), mapped in
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt:557` to
`stringResource(Res.string.songs_delete_song_partly)`, and the string in both files, next to
`songs_update_file_name_partly` (line 93 of each):

- `values/strings.xml`: `<string name="songs_delete_song_partly">The song was deleted, but a setlist or the saved transposition still names it</string>`
- `values-hu/strings.xml`: `<string name="songs_delete_song_partly">A dal törlődött, de egy lista vagy a mentett transzponálás még hivatkozik rá</string>`

## Tests

`:data:repository:implementation` — `SetlistRepositoryImplTest.kt` (its `FakeSetlistLocalSource` keeps a `files` map):

1. `the setlists naming a song are read from the files` — load the repository with `[s1 = [a.cho]]`, then put
   `s2 = [a.cho, b.cho]` into `localSource.files` behind its back: `loadSetlistFileNamesNaming("a.cho")` is
   `[s1, s2]` (order-insensitive), `loadSetlistFileNamesNaming("c.cho")` is empty, and the cache is unchanged (still
   `[s1]`).
2. `a setlist only the cache knows still counts` — a cached `s1` naming `a.cho` whose file the fake's `loadSetlists`
   skips (make the fake drop it from the listing): still in the answer.
3. `a listing that fails throws` — the fake's `loadSetlists` throws: `assertFailsWith` on the call.

`FakeSetlistLocalSource` may need a `loadSetlists` override hook for 2 and 3; add one if it lacks it.

`:domain:implementation` — new `SongReferencesTest.kt` in `commonTest/.../useCases/`, with fakes of
`SetlistRepository` (a map of setlists; `loadSetlistFileNamesNaming` answers from the map or throws on demand;
`updateSetlist` applies the transform or throws for a named file) and `UserPreferencesRepository`:

4. A rename moves every entry and the transposition, answers true.
5. A deletion drops every entry and the transposition, answers true.
6. The listing throws → answers false, and the transposition is still moved (rename) / dropped (deletion).
7. The first of two setlists throws on `updateSetlist` → the second is still changed, the transposition still
   followed, answers false. (Today's deletion stops at the first.)
8. `updateSetlist` returning null for a setlist that went away → answers true.

And one per use case, to pin the wiring: `DeleteSongUseCaseImpl` answers what the walk answered and does not walk at
all when `songRepository.deleteSong` throws (the exception propagates).

Every fake implementing `SetlistRepository` needs the new method: `GetScreenDataUseCaseImplTest.kt`,
`ExportLibraryUseCaseImplTest.kt` (`throw UnsupportedOperationException()`), and `RecordingSetlistRepository` in
`data/repository/implementation/src/commonTest/.../sync/FakeSyncCollaborators.kt`.

Run the root unit test command, and compile `:presentation` for one target (`./gradlew :presentation:compileKotlinDesktop`)
for the view model change.

## Verification

1. **Stale cache, desktop.** Start the app with a song `foo.cho` in the library. With the app running, write
   `library/setlists/new.setlist.json` by hand (copy an exported one) naming `foo.cho` — the running app's cache does
   not know it. Change `foo.cho`'s title in the editor so that "Update file name" is offered, take it. Refresh the
   library (the setlists screen's refresh, or restart).
   - **Before:** `new.setlist.json` still names `foo.cho` and shows the song as missing; no message was shown.
   - **After:** it names the new file.
   Repeat with Delete instead: before, the setlist keeps a missing entry; after, the entry is gone.
2. **Failed setlist write, desktop.** Make one setlist file read-only (`chmod 444`), with two setlists naming the song,
   and a saved transposition for it. Delete the song.
   - **Before:** "Something went wrong", the details screen stays on a deleted song, the second setlist and the
     transposition still name it.
   - **After:** the screen closes, the second setlist and the transposition are cleaned up, and the new "…still names
     it" message is shown. Undo the `chmod`.
3. Hungarian UI: the message reads in Hungarian.

## Docs

- `domain/implementation/CLAUDE.md`, the `RenameSongFileUseCaseImpl` bullet (lines ~95-101): replace "Both walks change
  each setlist through `updateSetlist`, so they build on the latest version of it and wait for a change that is being
  written." with "Both are one walk (`useCases/SongReferences.kt`): which setlists name the song is asked of the files
  (`SetlistRepository.loadSetlistFileNamesNaming`) rather than of the cache, which does not know what sync or an import
  wrote since the last rescan; each one is changed through `updateSetlist`, so it builds on the latest version and waits
  for a change that is being written; and every reference is attempted even after one fails, the deletion answering
  like the rename whether all of them followed."
- `domain/api/CLAUDE.md` needs nothing (it names the use cases, not their return values).
- `data/repository/implementation/CLAUDE.md`, the `SetlistRepositoryImpl` bullet: add "`loadSetlistFileNamesNaming`
  reads every setlist file afresh for the reference walks, outside the locks — `updateSetlist` reads each one again
  under them." (That file has uncommitted edits by another agent at the time of writing; merge.)
- `presentation/CLAUDE.md`: if it lists the messages, add `SongDeletedPartly` next to `SongFileRenamedPartly`
  (grep before writing).

## Files touched

- `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SetlistRepository.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImplTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncCollaborators.kt`
- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/DeleteSongUseCase.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/SongReferences.kt` (new)
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/RenameSongFileUseCaseImpl.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/DeleteSongUseCaseImpl.kt`
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/SongReferencesTest.kt` (new)
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImplTest.kt`, `ExportLibraryUseCaseImplTest.kt` (fakes)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`, `values-hu/strings.xml`
- `domain/implementation/CLAUDE.md`, `data/repository/implementation/CLAUDE.md`

## Depends on

- **27** changes what `songRepository.renameSong` answers after the move; independent lines of
  `RenameSongFileUseCaseImpl.kt` (`:43` there, the walk here). Either order.
- `CampfireViewModel.kt`, `CampfireApp.kt` and the two `strings.xml` are also edited by lane D plans (and plan 17 in
  lane B edits `CampfireViewModel.kt`); the edits here are local (`deleteSong`, the `Message` list, one `when` arm,
  one string each), so land in any order and merge.
- `FakeSyncCollaborators.kt` is shared with the sync tests of lane B; the addition is one method on
  `RecordingSetlistRepository`.
- Plan 26 edits `ExportLibraryUseCaseImplTest.kt` too (its fakes); merge.
