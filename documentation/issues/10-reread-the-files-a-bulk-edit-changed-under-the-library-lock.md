# End `SongRepositoryImpl.editSongs` with a re-read of the changed files under the library lock, as `refresh` does, instead of putting back the per-file snapshots

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (widest window on the web, where OPFS writes are slowest)
**Files:** `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongRepositoryImpl.kt`, `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongRepositoryImplTest.kt`, `data/repository/implementation/CLAUDE.md`, `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SongRepository.kt` (KDoc of `editSongs` only, if its wording changes)

## Problem

`editSongs` (the selection mode's Manage tags and Manage languages Save) reads, edits, writes, and re-reads each file
under `libraryFileLock`, one file at a time, collecting the re-read `Song`s, and only at the very end, outside the lock,
puts all of them into the cached list (`SongRepositoryImpl.kt` L114–154 at d650f3cb0):

```kotlin
fileNames.distinct().forEach { fileName ->
    val isEdited = libraryFileLock.withLock {
        ...
                withContext(NonCancellable) {
                    songLocalSource.saveSongContent(SongContent(fileName = fileName, text = edited))
                    changed += fileName
                    loadSongOrNull(fileName)?.let(reloaded::add)
                }
        ...
    }
    if (!isEdited) failed += fileName
}
} finally {
    if (changed.isNotEmpty()) {
        withContext(NonCancellable) {
            songContentRepository.invalidate(changed)
            replaceInCache(reloaded.mapTo(hashSetOf()) { it.fileName }, reloaded)
            libraryChanges.onLibraryChanged()
        }
    }
}
```

The lock is let go between files, so any other write (`saveSong`, `deleteSong`, `renameSong`, a sync run) can land on a
file that was already processed before the final `replaceInCache`, which then puts the snapshot taken before that write
back over it. That is exactly what `refresh` guards against, and says so (L68–70):

```kotlin
// Under the lock every write holds from its write to its cache update, so that a save or a deletion lands either
// before these reads, which then see it, or after the list has been updated with them - never between, where the
// version read here would be put back over it.
```

Scenario: tick 300 songs, Manage tags, tick a tag, Save (slow on the web). While it runs, open an already-processed
song's card menu and delete it, or edit it, or a sync run downloads a newer version of it. When the bulk edit finishes,
the deleted song reappears as a ghost card (its file is gone; opening it fails), or the list shows the old title and
tags, until the next rescan. The selection's actions are disabled while `isEditingSongs`, but the card menus of other
screens, the song details screen, and sync are not.

`adoptImported` (L172–179) has the same shape — the import's `Song`s, read as each file was written, are put into the
list outside the lock at the end — but its window is far narrower in practice (an import holds the import screen or its
progress, and an import never re-reads the directory on purpose, since a thousand-file archive would pay for it). It is
left alone here; mention it in the commit message only if the executor changes it, which this plan does not ask for.

## Fix

1. Move the body of `refresh`'s lock into a private function and call it from both places:

   ```kotlin
   /** Reads [fileNames] again and puts them in the list under [libraryFileLock], see [refresh]. */
   private suspend fun reloadIntoCache(fileNames: Set<String>) = libraryFileLock.withLock {
       val reloaded = fileNames.chunked(REFRESH_BATCH_SIZE).flatMap { batch ->
           coroutineScope { batch.map { fileName -> async { loadSongOrNull(fileName) } }.awaitAll() }
       }.filterNotNull()
       songContentRepository.invalidate(fileNames)
       replaceInCache(fileNames, reloaded)
   }
   ```

   `refresh` keeps its two early returns and ends with `reloadIntoCache(fileNames)`.
2. In `editSongs`, drop the `reloaded` list and the per-file `loadSongOrNull(fileName)?.let(reloaded::add)`; the
   `NonCancellable` block per file keeps only the write and `changed += fileName`. The `finally` becomes:

   ```kotlin
   if (changed.isNotEmpty()) {
       withContext(NonCancellable) {
           reloadIntoCache(changed)
           libraryChanges.onLibraryChanged()
       }
   }
   ```

   The number of `loadSong` reads is unchanged (one per changed file, now at the end, batched 64 at a time in parallel
   as `refresh` does), and it is still one emission of the list. Keying the replacement by `changed` rather than by the
   names that re-read also drops a changed file that no longer reads (deleted or renamed meanwhile), which the old
   `reloaded.mapTo(...)` key set silently kept in the list.
3. Update the `editSongs` sentence in `data/repository/implementation/CLAUDE.md` (the `SongRepositoryImpl` bullet, "…and
   then puts the files it changed into the list, drops their cached texts and announces the change once…"): it now
   reads the files it changed again under `LibraryFileLock` and puts them into the list as `refresh` does, so that a
   save, deletion, or rename that landed on one of them after it was written is not undone. Adjust the `editSongs` KDoc
   in `SongRepository.kt` ("The list and the cached texts are then updated once…") only if it no longer holds; it does.

## Tests

Add to `SongRepositoryImplTest` (commonTest), using `FakeSongLocalSource.readGate`, which `loadSongContent` awaits
after taking the text, while `editSongs` holds the lock:

```kotlin
@Test
fun `a deletion made while an edit of several songs runs is not undone by it`() = runTest {
    val localSource = FakeSongLocalSource(mapOf("a.cho" to "a", "b.cho" to "b", "c.cho" to "c"))
    val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), LibraryFileLock(), LibraryChanges(), Logger.Standard)
    repository.loadSongsIfNeeded()
    val first = CompletableDeferred<Unit>().also { localSource.readGate = it }
    val edit = launch { repository.editSongs(listOf("a.cho", "b.cho", "c.cho")) { "$it, tagged" } }
    runCurrent()                                   // holding the lock on a.cho's read
    val second = CompletableDeferred<Unit>().also { localSource.readGate = it }
    first.complete(Unit)
    runCurrent()                                   // a.cho written; holding the lock on b.cho's read
    val deletion = launch { repository.deleteSong("a.cho") }
    runCurrent()                                   // waiting for the lock, which a fair Mutex hands it next
    localSource.readGate = null
    second.complete(Unit)
    edit.join()
    deletion.join()

    assertEquals(listOf("b.cho", "c.cho"), repository.loadSongsIfNeeded()?.map { it.fileName }?.sorted())
}
```

It fails at d650f3cb0 (`a.cho` is put back with the title "a, tagged") and passes after the fix. A second test of the
same shape with `repository.saveSong(SongContent("a.cho", "retitled"))` in place of the deletion asserts the list's
`a.cho` is titled "retitled". The three existing `editSongs` tests must still pass unchanged.

## Manual check

On the web build with a few hundred songs: tick them all, Manage tags, tick a new tag, Save; while the snackbar has not
appeared yet, delete one song near the top of the list (already processed) from its card's menu on another tab of the
same screen, for example through the song details screen. When Save finishes, the deleted song must not come back, and
a song edited the same way must show its new title.
