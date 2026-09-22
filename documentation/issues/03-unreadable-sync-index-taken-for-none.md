# 03 · A sync index that fails to read once is taken for no index: that run brings back deleted files, makes conflict copies and overwrites the index

**Severity:** wrong behaviour (all platforms. Unlikely: it needs one failed read of `preferences/sync-index.json`, for example an antivirus or backup tool holding the file on Windows, an I/O error, or OPFS under storage pressure. When it happens, every deletion since the last run is undone on every device, and every file changed on either side becomes a ` (2)` copy. The good index is then overwritten, so the next run cannot recover) · **Area:** `:data:source:local:implementation` (`SyncStateLocalSourceImpl`), `:data:repository:implementation` (`SyncRepositoryImpl.loadIndex`)

## Symptom
With a synced library, some songs deleted on this device and some on another since the last run: the index read
fails once as a run starts. The run carries on with an empty index. Songs deleted here come back, songs deleted
elsewhere are uploaded again, and songs edited on either side turn into ` (2)` copies. The wipe guard does not step
in, because it only applies to an index that is not empty.

## Cause
Two layers turn a read failure into "no index".

`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SyncStateLocalSourceImpl.kt:38`
and `:66-76`:

```kotlin
override suspend fun loadSyncIndex() = read(INDEX_FILE_NAME)
...
/** A document that cannot be read is treated as one that is not there: sync then starts from nothing. */
private suspend fun read(name: String) = try {
    fileStorage.readText(StorageDirectory.PREFERENCES, name)
} catch (exception: CancellationException) { throw exception } catch (exception: Exception) { ...; null }
```

`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt:621-628`:

```kotlin
private suspend fun loadIndex() = try {
    syncStateLocalSource.loadSyncIndex()?.let { json.decodeFromString<SyncIndexDocument>(it) } ?: SyncIndexDocument()
} catch (exception: CancellationException) { throw exception } catch (exception: Exception) {
    println("Could not read the sync index: ${exception.message}")
    SyncIndexDocument()
}
```

`runSynchronization` then writes that empty document over the file (`:336-340`, `saveIndex(document.copy(isRunInProgress
= true))`), and the engine plans against an empty index (`SyncEngine.kt:84`, then `SyncPlanner` treats every one-sided
file as new, `SyncPlanner.kt:139-148`, and every file on both sides as changed on both, `:136`).
`completePendingAuthorization` has the same problem. After a failed read it sees an index with no account and saves
an empty one over the file (`:583-586`).

`FileStorage.readText` already makes the distinction the index needs: null only for a file that is not there, and
`LibraryStorageException` for one that is there and cannot be read (`FileStorage.kt` KDoc; `JvmFileStorage.kt:68-70`
`failingAsStorage`; the web and iOS actuals do the same). The same rule already protects library files
(`LibraryFileLocalSource.readLibraryFile`) and the preferences (`data/source/local/api/CLAUDE.md`).

## Fix
1. `SyncStateLocalSourceImpl.kt`: stop swallowing the index read.
   - Replace `override suspend fun loadSyncIndex() = read(INDEX_FILE_NAME)` with
     `override suspend fun loadSyncIndex() = fileStorage.readText(StorageDirectory.PREFERENCES, INDEX_FILE_NAME)`.
   - `read` is now used only by `migratePlainFileIfPresent` for the credentials. Change its KDoc to
     `/** Credentials that cannot be read are treated as none, which the user answers by connecting again. */`. Its
     body stays, and so does the credentials-only log line. Simplify the log to
     `println("Could not read \"$name\": ${exception::class.simpleName}")`, since only the credentials file reaches
     it now.
   - The KDoc of `loadSyncCredentials` says "treated like the index below". Change it to
     `/** Unreadable credentials are treated as none, which the user answers by connecting again. */`.

2. `data/source/local/api/.../SyncStateLocalSource.kt`, the KDoc of `loadSyncIndex`:

   ```kotlin
   /**
    * What the last successful run saw, which is how the next one tells a change from a deletion. Null only when there is
    * none; one that is there and cannot be read throws [LibraryStorageException], since a run that took it for none
    * would bring back every file deleted since and upload again every file deleted elsewhere.
    */
   ```

3. `SyncRepositoryImpl.kt`, replace `loadIndex` (`:621-628`) with two functions:

   ```kotlin
   /**
    * Throws when the file is there and cannot be read: an index taken for none is a run that undoes every deletion
    * since the last one, and then writes the empty index over the good one. One that reads but does not decode is
    * worth nothing to anybody and starts from nothing, as a device that never synced does.
    */
   private suspend fun loadIndex(): SyncIndexDocument {
       val text = syncStateLocalSource.loadSyncIndex() ?: return SyncIndexDocument()
       return try {
           json.decodeFromString<SyncIndexDocument>(text)
       } catch (exception: CancellationException) {
           throw exception
       } catch (exception: Exception) {
           println("Could not decode the sync index: ${exception.message}")
           SyncIndexDocument()
       }
   }

   /** For the callers that only show what the index says or check whose it is, and must not throw because of it. */
   private suspend fun loadIndexOrNull() = try {
       loadIndex()
   } catch (exception: CancellationException) {
       throw exception
   } catch (exception: Exception) {
       println("Could not read the sync index: ${exception.message}")
       null
   }
   ```

4. Call sites:
   - `runSynchronization` (`:336`): leave `loadIndex().adoptedBy(connected.account)` as it is. It is inside the run's
     `try`, before `latestIndex` is set and before any write, so a read failure goes to
     `catch (exception: Exception)`. `toFailureReason` maps `LibraryStorageException` to `STORAGE`, and
     `finishRunCutShort(null, false)` writes nothing. The run ends as `Failure(STORAGE)` with the file untouched.
   - `restoreConnection` (`:184`): `val document = loadIndexOrNull() ?: SyncIndexDocument()`. Start up must never
     throw. A default document is not written here, since it has no running marker.
   - `completePendingAuthorization` (`:583-586`):

     ```kotlin
     // The account decides which remote folder the index describes, so one written for a different
     // account is worthless rather than merely stale. One that cannot be read is left for the run to find:
     // replaced here, it could be the good index of this very account.
     val document = loadIndexOrNull()?.adoptedBy(account)
     if (document != null && document.accountId != account.indexKey()) {
         saveIndex(SyncIndexDocument())
     }
     ```

     Leaving it is safe. The engine ignores an index filed under another account
     (`SyncEngine.kt:84`, `document.takeIf { it.accountId == accountId }`).

A decode failure keeps today's fallback. Atomic writes on every platform but Safari before 26 make a corrupt file
unlikely, and throwing there would leave the device failing every run until it disconnects.

## Tests
- `data/repository/implementation/src/commonTest/.../sync/FakeSyncCollaborators.kt`: give `FakeSyncStateLocalSource` a
  `var onLoadIndex: () -> Unit = {}` next to `onSaveIndex`, and make `loadSyncIndex()` call it before it returns
  `index`. Update the class KDoc if it lists the hooks.
- `SyncRepositoryImplTest.kt`:
  - `a run whose index cannot be read stops before anything moves`: prepare
    `stateLocalSource.index = json.encodeToString(SyncIndexDocument.of(providerId = SyncProviderId.DROPBOX.id, accountId
    = ACCOUNT.indexKey(), lastSyncedAt = 1, index = mapOf(song(1) to SyncIndexEntry(localHash =
    localContentHash("One".encodeToByteArray()), remoteRevision = "r1"))))`. Use a local
    `Json { prettyPrint = true; encodeDefaults = true }`. The provider holds `song(1)` at `"r1"` and the local library
    is empty, so the index says the song was deleted here. Call `restore()`, then set
    `stateLocalSource.onLoadIndex = { throw LibraryStorageException("Locked") }`, then `synchronize(ASK)` and
    `awaitOutcome()`. Expect `Failure(STORAGE)`, the index text unchanged, the local library still empty, and the
    provider still holding `song(1)`.
  - `restoring with an unreadable index still shows the account`: `onLoadIndex` throws from the start, and `restore()`
    returns `isConnected == true` with the state `Connected`.
- `data/source/local/implementation/src/desktopTest`: if a `SyncStateLocalSourceImpl` test exists, add one where the
  storage's `readText` throws and `loadSyncIndex()` throws `LibraryStorageException`. Otherwise none, since that
  function now simply delegates.

Run `./gradlew :data:repository:implementation:desktopTest :data:source:local:implementation:desktopTest`.

## Verify
1. Desktop, Dropbox-configured, synced library: make `preferences/sync-index.json` unreadable (`chmod 000` on macOS
   or Linux), tap Sync now. The status reads the storage failure, nothing is downloaded, and after `chmod 644` the file
   is byte for byte what it was. Sync again and the run is ordinary.
2. Restart the app with the file unreadable. Settings still shows the connected account.
3. Compile `:data:source:local:implementation` and `:data:repository:implementation` for desktop, wasmJs and
   `iosSimulatorArm64`.

## Docs
- `data/source/local/api/CLAUDE.md`, the `SyncStateLocalSource` bullet, append: "`loadSyncIndex` answers null only for
  an index that is not there; one that is there and cannot be read throws `LibraryStorageException`, since a run that
  took it for none would undo every deletion since the last one. Unreadable credentials are still treated as none,
  which connecting again answers."
- `data/repository/implementation/CLAUDE.md`, the `sync/` bullet, after "…which is how a device that cannot write ends
  a run as `SyncFailureReason.STORAGE`.": add "Reading it is the same: an index that is there and cannot be read ends
  the run as `STORAGE` before anything is written, and start up and a new connection leave such a file alone. Only one
  that reads and does not decode is taken for none."

## Touches
- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/SyncStateLocalSource.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SyncStateLocalSourceImpl.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncCollaborators.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `data/source/local/api/CLAUDE.md`, `data/repository/implementation/CLAUDE.md`

## Depends on
Nothing. 01 and 08 edit other lines of `SyncRepositoryImpl.runSynchronization`. Run them one after another.
