# 16 · A sync run in which files failed says "Last synced successfully", even when nothing reached the cloud

**Severity:** wrong behaviour (all platforms; certain with a full Dropbox, a full device, or one file name the
service refuses — and silent, so it hides that nothing is being backed up) · **Area:** `:data:model`
(`SyncSummary`, `SyncFailureReason`), `:data:source:remote:api`, `:data:source:remote:implementation`
(`DropboxSyncProvider`), `:data:repository:implementation` (`SyncEngine`, `SyncRepositoryImpl`), `:presentation`
(`SyncSettings`, strings)

## Symptom
The user's Dropbox is over quota. Every upload is refused, each refusal is a line in a log nobody sees, the run
ends, and Settings says "Last synced successfully on 2026-09-21 at 14:02" — on every run, while not one song has
reached the cloud. The same goes for a device that is out of space (every download fails), and for a single file
the service will never accept (`path/malformed_path`, `path/disallowed_name`): retried and failing on every run,
for ever, without a word. With no crash reporting and no visible log, that is indistinguishable from a working
backup until the phone is lost.

## Cause
"A failure on one file does not end the run" is implemented as "is not reported at all".
`SyncEngine.runOperation`
(`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt:246-250`):

```kotlin
} catch (exception: Exception) {
    // The index is left alone, so the next run sees this file as it was and tries again.
    println("Could not sync \"${operation.key.path}\": ${exception.message}")
    OperationOutcome()
}
```

`OperationOutcome()` is the same value as "there was nothing to do". `SyncSummary`
(`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/SyncState.kt:114-123`) has nowhere
to put a failure, so the pass ends as `Result.Completed`, `SyncRepositoryImpl` (`:285-299`) reports
`SyncOutcome.Success` and moves `lastSyncedAt` to now, and `SyncSettings.statusText()` shows the date.

On the provider's side a full Dropbox is a 409 whose summary starts `path/insufficient_space`, which
`ensureSuccessful()` (`DropboxSyncProvider.kt:365-374`) turns into the same `DropboxApiException` as any refusal of
one file — so a first upload of a whole library to a full account is a few thousand requests, every one of which
sends the file and is refused.

## Fix
Three parts: count and name the files that failed; do not call such a run the last successful one; and treat a full
cloud folder as what it is, a reason to stop uploading. Written against the code as plans 01, 06, 08, 09 and 10
leave it.

### The model
1. `data/model/.../domain/SyncState.kt`:

   ```kotlin
   /**
    * What one sync run did. [conflicts] holds the names the incoming copies landed under, so that the user can be told
    * where to look: a file changed on both sides is never merged, both versions are kept. [failed] holds the names of
    * the files that could not be moved: one of those does not end a run, but a run that has any is not one that left
    * the two sides in step, and must not be reported as if it were.
    */
   data class SyncSummary(
       val downloaded: Int = 0,
       val uploaded: Int = 0,
       val deletedLocally: Int = 0,
       val deletedRemotely: Int = 0,
       val conflicts: List<String> = emptyList(),
       val failed: List<String> = emptyList(),
   ) {
   ```

   `hasChanges` stays as it is. On `SyncOutcome.Success` add the KDoc "The run reached its end. Whether every file
   made it is in [SyncSummary.failed]." — the type is kept rather than split into a third outcome, because
   everything else a finished run has to say (the conflicts) is already in the summary.

   `SyncFailureReason` gets a fifth entry, before `UNKNOWN`:

   ```kotlin
   /** The cloud folder has no room left, so nothing more can be uploaded until the user frees some. */
   REMOTE_STORAGE_FULL,
   ```

### The provider contract and Dropbox
2. `data/source/remote/api/.../SyncProvider.kt`, next to the other two exceptions:

   ```kotlin
   /**
    * The service has no room left for what is being uploaded. Told apart from the refusal of one file because every
    * upload after it would be answered the same way, each only after sending its file.
    */
   class SyncRemoteStorageFullException(message: String, cause: Throwable? = null) : Exception(message, cause)
   ```

   and one more bullet in the interface KDoc's list of rules: "- An account that is out of space says so with
   [SyncRemoteStorageFullException], from [upload] only. It ends the run; any other refusal is one file's problem."

3. `DropboxSyncProvider.ensureSuccessful()` — a branch before the `else`:

   ```kotlin
   // A full account, which Dropbox reports per write: "path/insufficient_space/..".
   status == HttpStatusCode.Conflict && summary.contains("insufficient_space") ->
       SyncRemoteStorageFullException("Dropbox is full: $summary")
   ```

   `ensureSuccessful` runs outside `transport { }`, so nothing there has to let the new type through. (Inside plan
   10's `resolve`, it lands in the "the service answered, and the answer was no" clause, which is right: the copy
   is taken back.)

### The engine
4. `SyncEngine.runOperation`: a third rethrown type, and the generic catch reports the file:

   ```kotlin
   } catch (exception: SyncNetworkException) {
       throw exception
   } catch (exception: SyncRemoteStorageFullException) {
       throw exception
   } catch (exception: Exception) {
       // The index is left alone, so the next run sees this file as it was and tries again.
       println("Could not sync \"${operation.key.path}\": ${exception.message}")
       OperationOutcome(summary = SyncSummary(failed = listOf(operation.key.name)))
   }
   ```

   `SyncSummary.plus` adds `failed = (failed + other.failed).distinct()` — a file that fails in the first pass
   fails again in the second, and is one file. The class KDoc's last paragraph becomes "…so only the failures that
   make every further call pointless - the credentials being refused, the service being unreachable, the remote
   folder being full - stop it. A file that failed is named in the summary, because a run that says nothing about
   it reads as a backup that works."

   This also covers plan 01's `RemoteFileTooLargeException`: a file too large to download is a file that could not
   be synced, and is named.

### The repository
5. `SyncRepositoryImpl.toFailureReason()`: `is SyncRemoteStorageFullException -> SyncFailureReason.REMOTE_STORAGE_FULL`.
   The run then ends through the ordinary failure branch (plan 08's `finishRunCutShort`: index kept, lists told).
   Downloads run before uploads, so everything incoming has arrived by then; the deletions planned after the
   uploads wait for the run after the user has made room, which is acceptable — they are kilobytes and free
   nothing worth having.

6. The `Completed` branch — `lastSyncedAt` means "the two sides were last known to be in step", in the state and in
   `sync-index.json` alike, so a run with failures leaves it where it was:

   ```kotlin
   is SyncEngine.Result.Completed -> {
       // Only a run that moved everything it set out to move is one the two sides were in step after. One with
       // failures keeps the time of the last run that was, which is also what "Last synced successfully" goes on
       // saying while the next run is going.
       val syncedAt = if (result.summary.failed.isEmpty()) {
           Clock.System.now().toEpochMilliseconds()
       } else {
           result.index.lastSyncedAt
       }
       saveIndex(result.index.copy(lastSyncedAt = syncedAt))
       if (result.summary.hasChanges) {
           rescanLibrary()
       }
       updateConnected {
           it.copy(
               progress = null,
               lastSyncedAt = syncedAt.takeIf { at -> at > 0 },
               lastOutcome = SyncOutcome.Success(result.summary),
           )
       }
   }
   ```

   (`result.index.lastSyncedAt` is the value the engine was handed; keep the existing comment above the rescan.)

### The UI
7. Strings, in the "Settings screen: sync" group of both files, after `settings_sync_failed_unknown`.

   `values/strings.xml`:

   ```xml
   <string name="settings_sync_failed_remote_full">The cloud folder is full, so nothing more can be uploaded. Free up some space there and sync again.</string>
   <plurals name="settings_sync_files_failed">
       <item quantity="one">One file could not be synced, and Campfire will try again next time: %2$s</item>
       <item quantity="other">%1$d files could not be synced, and Campfire will try again next time. Among them: %2$s</item>
   </plurals>
   ```

   `values-hu/strings.xml`:

   ```xml
   <string name="settings_sync_failed_remote_full">A felhőmappa megtelt, így több fájl nem tölthető fel. Szabadíts fel helyet, majd szinkronizálj újra.</string>
   <plurals name="settings_sync_files_failed">
       <item quantity="one">Egy fájlt nem sikerült szinkronizálni, a Campfire legközelebb újra megpróbálja: %2$s</item>
       <item quantity="other">%1$d fájlt nem sikerült szinkronizálni, a Campfire legközelebb újra megpróbálja. Köztük: %2$s</item>
   </plurals>
   ```

8. `presentation/.../ui/screens/settings/SyncSettings.kt`:
   - the run-failure `when` (`:242-247`) gets
     `SyncFailureReason.REMOTE_STORAGE_FULL -> Res.string.settings_sync_failed_remote_full`;
   - the connection-failure `when` (`:140-145`) lists `SyncFailureReason.REMOTE_STORAGE_FULL,` with `STORAGE` and
     `UNKNOWN` (it cannot happen while connecting, but the `when` is exhaustive);
   - the `Success` branch of `statusText()` becomes:

     ```kotlin
     // What a successful run moved is not something the user has to be told: the library is simply the same on
     // both sides now. What it could not move is, and so is a file that now exists twice; with neither, the one
     // thing only the app knows is that the run did finish, and when.
     is SyncOutcome.Success -> listOfNotNull(
         outcome.summary.failed.takeIf { it.isNotEmpty() }?.let { failed ->
             pluralStringResource(
                 Res.plurals.settings_sync_files_failed,
                 failed.size,
                 failed.size,
                 failed.take(MAXIMUM_NAMED_FILES).joinToString(),
             )
         },
         outcome.summary.conflicts.takeIf { it.isNotEmpty() }?.let {
             stringResource(Res.string.settings_sync_conflicts, it.joinToString())
         },
     ).ifEmpty { listOf(lastSyncedText(lastSyncedAt)) }.joinToString(separator = "\n")
     ```

     with, at the bottom of the file:

     ```kotlin
     /** A full disk fails every file of a run, and the line under the account is not the place for all their names. */
     private const val MAXIMUM_NAMED_FILES = 3
     ```

   Import the two new resources next to their neighbours.

Do not turn a run with failed files into `SyncOutcome.Failure`: the four failure texts all say the run did not
happen, while this one did — files moved, the index is written, the lists were rescanned.

## Tests
- `SyncEngineTest`:
  - `` `a file that cannot be written is named in the summary and the others still move` `` — remote `song(1..3)`,
    `FakeLibraryFileLocalSource.onWrite` (plan 10) throws `LibraryStorageException` for `song(2)`. Expect
    `Completed`, `summary.failed == listOf("song_2.cho")`, `downloaded == 2`, the index without `song(2)`.
  - `` `a file that fails in both passes is named once` `` — as above plus a contested upload that forces the second
    pass (the `isContested` pattern of the existing test); `summary.failed.size == 1`.
  - `` `a full remote folder ends the run` `` — `onUpload` throws `SyncRemoteStorageFullException("Full")`;
    `assertFailsWith<SyncRemoteStorageFullException>`.
- `SyncRepositoryImplTest`:
  - `` `a run in which a file failed keeps the time of the last run that was in step` `` — stored index with
    `lastSyncedAt = 42`, one remote song whose local write fails. Expect `SyncOutcome.Success` with one failed
    name, `lastSyncedAt == 42` in the state and in the stored document.
  - `` `a full remote folder is reported as that` `` — expect `SyncOutcome.Failure(SyncFailureReason.REMOTE_STORAGE_FULL)`.
- `DropboxRequestTest`: `` `reports a full account as that rather than as one refused file` `` — the handler answers
  `respondJson("""{"error_summary":"path/insufficient_space/..","error":{}}""", HttpStatusCode.Conflict)`;
  `assertFailsWith<SyncRemoteStorageFullException> { provider.upload(LibraryFileKind.SONG, "song.cho", ByteArray(1), null) }`.
  And `path/malformed_path/..` still fails with `DropboxApiException`.

## Verify
- The unit test command and the three compile checks.
- Desktop: sync a library, then make `library/songs` read-only (`chmod a-w`) and put two new songs into
  `Apps/Campfire/songs` on dropbox.com. **Sync now**: Settings shows "2 files could not be synced…" with both
  names, in English and, after switching the language, in Hungarian; the "last synced" date (visible while the next
  run is going) has not moved. `chmod u+w`, sync: the line goes back to the date, which is now a new one.
- A full Dropbox cannot be faked cheaply; the `DropboxRequestTest` case stands in for it.

## Docs
- `data/repository/implementation/CLAUDE.md`, `sync/` bullet: "only the two failures that make every further call
  pointless (the credentials refused, the service unreachable)" becomes three, with "the remote folder full"; add
  "A file that failed is named in `SyncSummary.failed`, and a run that has any does not move `lastSyncedAt`", and
  amend "only a completed one moves `lastSyncedAt`" to "only a completed one with no failed files".
- `data/model/CLAUDE.md`: mention `SyncSummary.failed` where `SyncSummary` is listed.
- `data/source/remote/api/CLAUDE.md` and `data/source/remote/implementation/CLAUDE.md`: where the two exceptions
  are described, add `SyncRemoteStorageFullException` and the `insufficient_space` mapping.
- `documentation/sync.md`, "When it runs": add "A file that cannot be moved does not hold up the others: Settings
  names it, and the next run tries again. A cloud folder that is full does stop the run, and Settings says so."
- Root `CLAUDE.md`, Sync section, the `SyncEngine` bullet: append "A file that fails on its own is named in the
  run's summary rather than ending it, and such a run does not count as the last successful one."

## Touches
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/SyncState.kt`
- `data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/SyncProvider.kt`
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt`
- `data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxRequestTest.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SyncSettings.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `data/repository/implementation/CLAUDE.md`
- `data/model/CLAUDE.md`
- `data/source/remote/api/CLAUDE.md`
- `data/source/remote/implementation/CLAUDE.md`
- `documentation/sync.md`
- `CLAUDE.md`

## Depends on
01 (the too-large failure it reports), 06 (test harness), 08 (the failure branch a full remote ends in), 10 (the
`onWrite` hook, and `resolve`'s handling of a refused upload).
