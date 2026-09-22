# 02 · A setlist touched while a sync run is bringing a new version of it down puts the old one back, and the next run uploads it over the other device's edit

**Severity:** data loss (all platforms; plausible — a shared setlist changed on one device, and the other device's
user opens the app and taps a transposition, reorders, archives or adds a song within the first seconds, while the
launch-time run is still going; silent, and on every device after the next run) · **Area:** `:data:repository:implementation` (`SetlistRepositoryImpl.updateSetlist` / `renameSetlist`), `:data:source:local:*` (`SetlistLocalSource`)

## Symptom
1. Two devices share a Dropbox folder. On the tablet, reorder `gig.setlist.json` and add two songs; sync.
2. On the phone, open Campfire. The launch-time run (`RestoreSyncUseCase`) starts and downloads `gig.setlist.json`.
3. Within the next few seconds (on a large library, the next ten or more), open the setlist and tap **+** on a song's
   transposition (or drag a song, archive the setlist, add a song to it, edit its title, rename or delete a song in it).
4. The phone's file is now the setlist as it was *before* the run, plus the tap. The run finishes; the next run sees
   the file changed locally and not remotely and uploads it. The tablet's reorder and its two songs are gone from
   every device. No conflict copy is made, because as far as sync can tell nothing conflicted.

## Cause
Every change to an existing setlist is built on the repository's cache
(`SetlistRepositoryImpl.kt:82`):

```kotlin
private suspend fun latest(fileName: String) = loadDataIfNeeded()?.firstOrNull { it.fileName == fileName }
```

used by `updateSetlist` (`:54-56`) and `renameSetlist` (`:58-66`) — i.e. by `setTransposition`'s setlist branch
(`CampfireViewModel.kt:1111`), `:1372`, `:1398`, `:1422`, `:1431`, `:1444`, `RenameSongFileUseCaseImpl.kt:43`,
`DeleteSongUseCaseImpl.kt:34` and `EditSetlistUseCaseImpl.kt:23`.

A sync run writes library files behind the repository's back (`SyncEngine.download`, `SyncEngine.kt:304`), and the
cache only learns about them from a rescan. During a run that is the live rescan, which waits at least a second and
five times what the previous rescan took (`SyncRepositoryImpl.kt:405-413`, `liveRescanPauseAfter` at `:647`) and is
only scheduled from a progress event; the complete one comes after the run. Review 2 plan 08 (`2847fddf`) added the
rescans on every way out of a run, but in its own words the stale cache is what "writes the old version back over the
one the run brought in" — and inside a run that window is still open.

Once written, nothing can tell: the engine recorded the downloaded bytes in the index (`SyncEngine.kt:305-308`), so
the next plan sees *changed locally, unchanged remotely* and makes it an `Upload` with the remote's current revision
(`SyncPlanner.kt`, `hasChangedLocally && !hasChangedRemotely`), which Dropbox accepts.

Songs are not affected the same way: every write built on a song's text passes the text it was built on as
`expectedText` and is refused when the file differs (`SongRepositoryImpl.saveSong`), and `renameSong` reads the text
from the file (`SongLocalSourceImpl.kt:95`). The editor's explicit save is deliberately unguarded (documented in
`data/repository/implementation/CLAUDE.md`) and is left alone here.

## Fix
Build every change to an existing setlist on the file, not on the cache. The lock is already there
(`writeMutex`), and sync's writes are atomic replacements on every platform, so the file read under the lock is the
current setlist.

1. `SetlistLocalSource` (`data/source/local/api/.../SetlistLocalSource.kt`) gains
   ```kotlin
   /**
    * The setlist stored under [fileName], read from the file rather than from anything cached, so that a change built
    * on it builds on what is really there - which a sync run may have replaced a moment ago. Null if there is no such
    * file; throws if it is there and cannot be read or decoded.
    */
   suspend fun loadSetlist(fileName: String): Setlist?
   ```
   implemented in `SetlistLocalSourceImpl` with `withContext(Dispatchers.Default)`, `fileStorage.readText(
   StorageDirectory.SETLISTS, fileName)?.let { json.decodeFromString<SetlistDocument>(it).toModel(fileName) }` —
   exactly what one element of `loadSetlists` (`:36-51`) does, minus the swallowing.
2. `SetlistRepositoryImpl.latest` (`:82`):
   ```kotlin
   /**
    * The setlist as its file holds it. Sync writes setlist files without going through this repository, and the cache
    * only catches up at the next rescan: a change built on the cache in between would put the version from before the
    * run back, and the next run would upload it over the edit it had just brought in. A file that cannot be decoded
    * (edited by hand into invalid JSON) falls back on the cache, which is what every change was built on before.
    */
   private suspend fun latest(fileName: String): Setlist? {
       val cached = loadDataIfNeeded()?.firstOrNull { it.fileName == fileName }
       return try {
           setlistLocalSource.loadSetlist(fileName).also { if (it == null && cached != null) forget(fileName) }
       } catch (exception: CancellationException) {
           throw exception
       } catch (exception: Exception) {
           println("Could not read the setlist \"$fileName\": ${exception.message}")
           cached
       }
   }
   ```
   with `forget` being `updateData { current -> current.orEmpty().filterNot { it.fileName == fileName } }`. A setlist
   whose file is gone (deleted by sync, or on the desktop by hand) is then `null`, which every caller already takes
   as "nothing to change" — except the song picker's `setSetlistSongs` (`CampfireViewModel.kt:1398`), which falls back
   on `saveSetlist` with the picker's own copy and would recreate the deleted setlist from it; 16 removes that
   fallback.
3. The successful write already puts the result into the cache (`write`, `:85-88`), so the list catches up with the
   incoming version as a side effect.
4. Do **not**: take the sync run's lock around setlist edits (a first sync can take minutes, and every tap would wait
   for it); rescan the whole library on every edit; or rely on a per-file invalidation from the engine alone — any
   write the repository does not make (the desktop user editing the folder, an iOS Files-app copy) has the same shape.

## Tests
`SetlistRepositoryImplTest` (its `FakeSetlistLocalSource` keeps a `files` map; implement `loadSetlist` as
`files[fileName]`):
- `a change is built on the file rather than on the list read before it`: repository loaded with
  `setlist(entries = [a, b])`; then `files["gig.setlist.json"] = setlist(entries = [b, a, c])` directly (sync);
  `updateSetlist("gig.setlist.json") { it.copy(isArchived = true) }` → the file holds `[b, a, c]` archived.
- `a rename is built on the file as well`: same shape with `renameSetlist`, asserting the entries of the renamed file.
- `a change to a setlist whose file is gone writes nothing and drops it from the list`: `files.remove(...)` after the
  load; `updateSetlist` returns `null`, `files` stays empty, and `setlists.value` no longer holds it.
- `a setlist whose file cannot be decoded is changed as the list has it`: `loadSetlist` throws → the cached version
  plus the change is written (today's behaviour).
A `SetlistLocalSourceImpl` test is not needed; it is a four-line read of the existing decoder.

## Verify
1. Desktop app (`./gradlew :app:desktop:run`) and the web app (`:app:web:wasmJsBrowserDevelopmentRun`) connected to one
   Dropbox test account, both synced, both holding a setlist with five songs.
2. On the web: reverse the order, sync. On the desktop: **Sync now**, and while the progress row is still showing (put
   a few hundred songs in the folder first so the run lasts), open the setlist and tap a transposition.
3. After the run and one more: both devices show the reversed order with the transposition. Before the fix: the
   original order, on both.
4. `./gradlew :data:repository:implementation:desktopTest`.

## Docs
`data/repository/implementation/CLAUDE.md`, the `SetlistRepositoryImpl` bullet: "held from reading the setlist out of
its own cache to having the write back in that cache" becomes "held from reading the setlist out of its **file** to
having the write back in the cache. The file and not the cache, because sync writes setlist files behind this
repository's back and the cache only catches up at the next rescan: a change built on it in between would put the
version from before the run back, and the next run would upload that over the other device's edit."
`data/repository/api` `SetlistRepository.updateSetlist` KDoc: "read the latest" → "read the latest from its file".
`data/source/local/api/CLAUDE.md` if it lists `SetlistLocalSource`'s members: add `loadSetlist`.

## Touches
- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/SetlistLocalSource.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SetlistLocalSourceImpl.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImpl.kt`
- `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SetlistRepository.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImplTest.kt`
- `data/repository/implementation/CLAUDE.md` (and `data/source/local/api/CLAUDE.md` if it lists the members)

## Depends on
04 wraps the bodies of the same `writeMutex.withLock { … }` blocks in `NonCancellable` (different lines from
`latest`); do the two one after the other, either first. 16 should land with or after this plan (it removes the
picker's recreate-on-null, which this plan makes reachable immediately after a sync deletion rather than only after
the next rescan). Independent of 03, which closes the other half of the same race (an edit landing while a download
is in flight) inside the engine.
