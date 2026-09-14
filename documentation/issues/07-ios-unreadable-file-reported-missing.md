# 07 · iOS reports a file that exists but cannot be read as missing, and sync deletes it everywhere

**Severity:** high (deletion propagates to every device) · **Area:** `:data:source:local:implementation` (iOS), `:data:repository:implementation`

## Symptom

A library file that is present but unreadable for a moment (data-protection before first unlock while a background
sync continues, a file being replaced by the Files app mid-read, a permissions hiccup) is treated as absent. The sync
engine then plans it as "deleted locally" and deletes it on Dropbox; the next run on every other device deletes it
there too.

## Cause

`IosFileStorage.readData` (`data/source/local/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.ios.kt:108–109`):

```kotlin
.let { if (fileManager.fileExistsAtPath(it)) NSData.dataWithContentsOfFile(it) else null }
```

`dataWithContentsOfFile` returns `nil` on any failure, and the caller cannot tell it from "not there". The JVM actual
only returns null for `!isFile` and throws otherwise. `SyncEngine.readLocalStates()` (`SyncEngine.kt:103–114`)
`filterNotNull()`s the nulls, and the planner (`SyncPlanner.kt:137–142`: remote present, index present, local absent)
issues `DeleteRemote`.

## Fix

1. In `FileStorage.ios.kt`, make the two cases distinct:

   ```kotlin
   private fun readData(directory: StorageDirectory, name: String): NSData? {
       val path = filePath(directory, name)
       if (!fileManager.fileExistsAtPath(path)) return null
       return NSData.dataWithContentsOfFile(path) ?: throw IllegalStateException("Could not read \"$name\".")
   }
   ```

   Use the `dataWithContentsOfFile:options:error:` overload to put the `NSError`'s `localizedDescription` into the
   message if it is reachable from Kotlin/Native without ceremony; otherwise the plain message is fine.

2. That makes `SongLocalSourceImpl.readSong` skip such a song with a log line (it already catches `Exception`), and
   makes `SyncEngine.readLocalStates` throw. A thrown read aborts the run, which is the safe outcome — but make it a
   *storage* failure rather than `UNKNOWN`: in `SyncRepositoryImpl.toFailureReason()` map `IllegalStateException`
   and `kotlinx.io`/platform IO exceptions to `SyncFailureReason.STORAGE`. Simplest: introduce
   `class LibraryStorageException(message, cause) : Exception` in `:data:source:local:api`, throw it from the three
   file storages where a read or write fails on an existing file, and map that one type.

3. Optional hardening in the engine: catch the exception per file in `readLocalStates`, log it, and **exclude that
   key from planning entirely** (drop it from the remote list and the index passed to `SyncPlanner.plan`) so the rest
   of the run proceeds and the file is neither uploaded nor deleted. Report it in the summary as a skipped file if
   the summary gains such a field; otherwise the log line is enough. Do this only if step 2's "abort the run" turns
   out too coarse in practice.

4. Check `readText`/`readBytes` on the web actual for the same collapse: `getFileHandle(..).catch(() => null)` maps
   every rejection to "missing". Narrow that catch to `NotFoundError` (see issue 33) so a `NotAllowedError` throws.

## Verification

iOS simulator: `chmod 000` a song in the app container (via `xcrun simctl get_app_container` + Finder), run a sync:
the run must fail with the storage message, and the file must still be on Dropbox afterwards.
