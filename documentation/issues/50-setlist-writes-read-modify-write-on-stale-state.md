# 50 · Setlist writes are read-modify-write on a snapshot that is stale for the length of a file write

**Severity:** medium (a lost tap on the transposition stepper, a swipe-removal undone) · **Area:** `:data:repository:*`, `:domain:*`, `:presentation` (`CampfireViewModel`)

## Cause

`SetlistRepositoryImpl.saveSetlist` (:39–42) persists first and updates `_dataState` afterwards; the ViewModel's
`setlists` is two `stateIn` hops behind that. `setTransposition` (setlist branch, `CampfireViewModel.kt:829–853`),
`addSongToSetlist` (:1061), `removeSongFromSetlist` (:1117), `reorderSetlist` (:1132) and `setSetlistArchived` all
read `setlists.value`, edit, and write the whole file, with no lock (only `setSetlistSongs` has one, and it needs no
stale value because the picker hands it the full list).

Song opened from a setlist → tap **+** twice quickly: tap 2 arrives while tap 1's write is in flight, reads the
still-stale setlist, writes +1 again. Two quick swipe-removals become one; a reorder can overwrite a removal.

## Fix

Move the read-modify-write into the repository, under one lock, reading the repository's own cache:

1. `SetlistRepository` gains

   ```kotlin
   /**
    * Changes one setlist as a single step: read the latest, transform, write. Every change to a setlist's entries
    * goes through here, so two of them made in quick succession build on each other instead of on the same
    * snapshot. Null when there is no such setlist.
    */
   suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist): Setlist?
   ```

   implemented in `SetlistRepositoryImpl` with a `private val writeMutex = Mutex()`: under the lock, read the current
   setlist from `dataState.value.data` (the cache `saveSetlist` just updated), apply `transform`, call `saveSetlist`.
   `saveSetlist` itself stays for the callers that hand in a whole setlist they own (creation, import, rename).
2. `UpdateSetlistUseCase(fileName, transform)` in `domain/api` + impl.
3. ViewModel: `setTransposition` (setlist branch), `addSongToSetlist`, `removeSongFromSetlist`, `reorderSetlist`,
   `setSetlistArchived` and `setSetlistSongs` call it with their transform instead of reading `setlists.value`.
   `setSetlistSongs` keeps its "fall back to the sheet's own setlist while the library has not caught up" case by
   trying `updateSetlist` first and falling back to `saveSetlist(setlist.copy(...))` on null; `setlistSongsMutex`
   can then go. The stepper's `transposition + 1` is computed by the *screen* from the value it shows; make the
   transform `{ it.copy(entries = …transposition = clamped) }` with `clamped` from the argument as today — the lost
   tap is fixed by the lock ordering the writes, not by re-deriving the delta.
4. `data/repository/api/CLAUDE.md` and `data/repository/implementation/CLAUDE.md`: the rule that setlist entries are
   changed through `updateSetlist`.

## Verification

Desktop: open a song from a setlist, hammer **+** five times: the setlist file says +5. Swipe two songs out quickly:
both gone.
