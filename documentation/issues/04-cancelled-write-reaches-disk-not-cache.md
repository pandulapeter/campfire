# 04 · A setlist change, song rename or deletion interrupted by leaving the app reaches the file but not the lists, and the next change writes the old version back

**Severity:** data loss (Android 9–11 in practice, where Back at the root finishes the activity and clears the view
model; rare — the leaving has to land during a write of a few to a few dozen milliseconds, or during the longer
reference walk of an "Update file name") · **Area:** `:data:repository:implementation` (`SetlistRepositoryImpl`,
`SongRepositoryImpl`), `:domain:implementation` (`RenameSongFileUseCaseImpl`, `DeleteSongUseCaseImpl`)

## Symptom
1. Android 11: open a setlist, drag a song to a new place (or tick a song in the picker, or archive it) and press Back
   to leave the app at once.
2. Reopen the app from the launcher (the process is still alive; only the activity and its view model went).
3. The setlist shows the old order although the file holds the new one. Move another song: the file is written from
   the old order plus the new move, and the first move is gone for good — also on every device it syncs to.

The same with "Update file name" (a longer window: the file moves, then every setlist holding it and the saved
transposition are rewritten one by one): cancelled half way, the song is under its new name while some setlists still
point at the old one — they show the song as missing and it has silently left them, with its per-setlist
transposition. A case-only rename cancelled between its steps leaves the only copy under `x_renaming.cho`.

## Cause
Every library write is started with `launchLibraryChange` in `viewModelScope`
(`presentation/.../CampfireViewModel.kt:1646`), which is cancelled when the view model is cleared. The repositories
write the file and then update their cache in two steps with a suspension in between, and nothing makes the pair
atomic with respect to cancellation:

`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImpl.kt:85-88`
```kotlin
private suspend fun write(setlist: Setlist) {
    setlistLocalSource.saveSetlist(setlist)
    updateData { current -> current.orEmpty().filterNot { it.fileName == setlist.fileName } + setlist }
}
```

`saveSetlist` ends in `withContext(Dispatchers.IO)` (`JvmFileStorage.writeText`, `:76-78`), and `withContext` has a
prompt cancellation guarantee: when the caller was cancelled while the block ran, the finished result is discarded and
`CancellationException` is thrown on return. The file is written, `updateData` is skipped. The repository is a Koin
`@Single`, so it outlives the view model with the stale cache, and the next `updateSetlist` builds on it (`latest()`,
`:82`) — which is the whole point of reading the cache there.

The same shape everywhere a write is followed by a cache update:
- `SetlistRepositoryImpl.createSetlist` (`:46-50`), `renameSetlist` (`:58-66`), `deleteSetlist` (`:76-79`).
- `SongRepositoryImpl.createSong` (`:65-72`), `renameSong` (`:80-87` — the moved file is gone from the list's view of
  it until a rescan, and its cached text is not invalidated), `deleteSong` (`:89-93`), and `saveSong` after its guard
  (`:58-62`; the editor and the header edits call it under `NonCancellable` already, which is why only the other paths
  are reachable today).
- `domain/implementation/.../RenameSongFileUseCaseImpl.kt:34-60`: its KDoc says "once the file has moved there is no
  going back", and attempts every reference even after one fails — but a cancellation between the move and the last
  reference ends the walk.
- `DeleteSongUseCaseImpl.kt:24-34`: the same walk after the deletion (dangling entries are only cosmetic there, since a
  missing song is shown as missing, but the saved transposition stays behind).
- `FileNames.kt:89-95`, the case-only move: four storage calls, each a cancellation point.

## Fix
Make "the file changed" and "the cache knows" one step, from the moment the first byte can reach the disk:

1. `SetlistRepositoryImpl`: inside each `writeMutex.withLock { … }`, wrap the body in `withContext(NonCancellable)`.
   The lock is still *acquired* cancellably (a change that has not started yet may be abandoned); once it is held, the
   write and the cache update finish together. For `updateSetlist` that includes the `latest()` read, which is fine:
   it is a cache read under the lock.
2. `SongRepositoryImpl`: the same for `createSong`, `renameSong` and `deleteSong`, and in `saveSong` for the part after
   the `expectedText` guard (`saveSongContent` → `invalidate` → `loadSong` → `updateData`). The guard's read may stay
   cancellable.
3. `RenameSongFileUseCaseImpl.invoke`: everything after `songRepository.renameSong(song)` returned a name —
   the setlists walk and the transposition — runs in `withContext(NonCancellable)`. `DeleteSongUseCaseImpl.invoke`:
   the walk after `deleteSong` likewise.
4. Leave `moveFile` alone: with 2 it is only ever called inside a `NonCancellable` block.

Do **not**:
- wrap the whole use cases or `launchLibraryChange` in `NonCancellable` — a change that has not begun should still go
  away with its screen, and `launchLibraryChange` also hosts reads;
- use `GlobalScope` or a repository scope to "finish in the background": the caller is waiting for the result and for
  the exception, and `NonCancellable` gives both;
- catch `CancellationException` to update the cache and rethrow: every one of the suspension points in between would
  need it.

## Tests
`:data:repository:implementation` (`commonTest`, run with `./gradlew :data:repository:implementation:desktopTest`):

`SetlistRepositoryImplTest`, with a second gate in `FakeSetlistLocalSource.saveSetlist` that is awaited *after*
`files[setlist.fileName] = setlist` (`var afterSaveGate: CompletableDeferred<Unit>?`):
- `a change whose caller is cancelled after the file was written still reaches the cache`: `val job = launch {
  repository.updateSetlist(FILE_NAME) { it.copy(entries = it.entries + Setlist.Entry("b.cho")) } }`, `runCurrent()`,
  `job.cancel()`, complete the gate, `advanceUntilIdle()`; then `repository.loadSetlistsIfNeeded()` holds the entry
  `b.cho`. Fails today (the cache still has only `a.cho`).
- `a change cancelled before it took the lock writes nothing`: hold the lock with a gated first change, launch a second
  one, cancel the second before releasing the first; the file ends with only the first change.

`SongRepositoryImplTest`, with an equivalent gate after the write in `FakeSongLocalSource.saveSongContent` and in
`deleteSong`:
- `a deletion whose caller is cancelled after the file went is gone from the list too`.
- `a guarded save cancelled after it wrote updates the list` (the list entry for that file is the re-read one).

`:domain:implementation` has no fakes for these use cases; the `NonCancellable` in them is covered by review.

## Verify
1. `./gradlew :data:repository:implementation:desktopTest`.
2. Android 11 emulator (`./gradlew :app:android:assembleDebug`): in a debug build, temporarily add `delay(2000)` after
   `setlistLocalSource.saveSetlist(setlist)` in `write` (scratch change, not committed), reorder a setlist and press
   Back at once; reopen from the launcher. Before: old order shown, next reorder drops the first. After: the new order
   is shown.
3. Same with a `delay` inside the setlists walk of `RenameSongFileUseCaseImpl`: "Update file name" on a song that is in
   two setlists, Back at once, reopen: both setlists hold the song under its new name.

## Docs
`data/repository/implementation/CLAUDE.md`, the `SetlistRepositoryImpl` bullet: add "The write and the cache update
under that lock run as one `NonCancellable` step, so a change whose screen goes away while it is being written is
still in the cache the next change reads; the lock itself is waited for cancellably." Add the same half-sentence to the
`SongRepositoryImpl` bullet. `domain/implementation/CLAUDE.md`, the `RenameSongFileUseCaseImpl` bullet: "…the failures
are thrown together at the end, and the walk is not cancellable once the file has moved."

## Touches
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImpl.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongRepositoryImpl.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/RenameSongFileUseCaseImpl.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/DeleteSongUseCaseImpl.kt`
- the two repository tests, `data/repository/implementation/CLAUDE.md`, `domain/implementation/CLAUDE.md`

## Depends on
Nothing. 02 changes `SetlistRepositoryImpl.latest` inside the same locked blocks, and 16 touches the picker's call
of `updateSetlist`; schedule 04 and 02 one after the other, either first.
