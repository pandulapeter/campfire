# 10 · Resolving a sync conflict can lose the other device's version when the conflict copy cannot be written

**Severity:** data loss (all platforms; needs a failed local write — a full disk, the OPFS quota — or the app being
killed inside one network round trip, while a song has changed on two devices) · **Area:**
`:data:repository:implementation` (`SyncEngine.resolve`), `:data:source:remote:api` (the `upload` contract),
`:data:source:remote:implementation` (`DropboxSyncProvider.upload`)

## Symptom
`x.cho` was edited on the phone and on the tablet, and the phone's storage is full (or the browser's quota for the
web build is reached).

1. The phone syncs. Its version of `x.cho` goes up over the tablet's.
2. The ` (2)` copy that should hold the tablet's version cannot be written. The run carries on and reports the
   file as one that failed (or, before plan 16, reports nothing).
3. The tablet's version is now nowhere: overwritten in Dropbox, never written on the phone. The next run finds both
   sides holding the same text and records them as in step; the tablet, whose file is unchanged since its last run,
   simply downloads the phone's version over its own.

The same loss happens if the process is killed between the upload's answer and the local write.

## Cause
`resolve` in
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt:340-353`
overwrites the remote version while the only copy of it is a local variable:

```kotlin
val remote = provider.download(key.kind, key.name)
...
val uploaded = provider.upload(key.kind, key.name, local, operation.revision)
if (uploaded !is RemoteWriteResult.Written) return OperationOutcome(hasUnresolvedConflict = true)
val entries = mutableMapOf(key to SyncIndexEntry(localContentHash(local), uploaded.revision))

val copyName = libraryFileLocalSource.writeLibraryFileToFreeName(key.kind, key.name, remote)   // may throw
```

A `LibraryStorageException` from the last line is caught in `runOperation` as one file's problem.

The order is deliberate. Commit `0aaa713e` moved the copy *after* the upload because the upload is the step that
can turn out to be contested: a second device writing `x.cho` in the same moment makes it a
`RemoteWriteResult.Conflict`, and a copy already on disk by then was uploaded as a new file by the next pass while
`x.cho` itself was resolved again into a second copy. `SyncEngineTest`'s
`a conflict that is contested while it is resolved leaves one copy rather than two` pins that. So the property to
keep is: **no copy survives an upload that somebody else won.**

Reading the provider turned up a second hole of the same family that neither order closes.
`DropboxSyncProvider.request` retries a call that was answered with a 5xx, and Dropbox can answer 5xx to a write
that did land; the retry then carries a revision that is no longer current and comes back as `path/conflict`. The
engine reads that as "contested", returns without a copy — and the remote version it just overwrote is gone.

## Fix
**The copy is written first, and taken back only when it is known that the remote version is still there.** Two
designs were weighed:

- *Upload the incoming version to the remote under its ` (2)` name first, then the local one over the original.*
  It also never holds the losing version only in memory, but it pays for that in the wrong place. The copy needs a
  name that is free on both sides, which `writeLibraryFileToFreeName` cannot give (it only sees the library). A
  contested main upload then has to be compensated with a remote *delete*, a request that can fail — and a copy
  left behind remotely is downloaded by every device, which is `0aaa713e`'s two-copies problem spread to all of
  them. Until that delete lands, any other device syncing in between sees and downloads the provisional copy.
- *Write the copy into the library first, then upload, and delete the copy when the upload was refused* (the
  reviewer's suggestion). The provisional state is a file on this device only, visible to nobody else, and taking
  it back is a local delete. This is the one to use, with two corrections to the suggestion:
  - **not** "delete the copy on any exception". A `SyncNetworkException` or a cancellation during the upload says
    nothing about whether the write landed; deleting the copy then can lose the version just as today. The copy is
    only taken back when the service *answered* and the answer was no. What an ambiguous failure costs instead is
    that the next run, if the upload had not landed, resolves again and leaves a second identical copy: a
    duplicate is an annoyance, a lost version is lost work — the rule `SyncPlanner` already lives by.
  - on `Conflict`, look before deleting: if the remote file now holds exactly the local bytes, the "conflict" is
    the echo of this device's own write (the retried upload above), the remote version *was* overwritten, and the
    copy is the only place it still exists.

Written against `SyncEngine.kt` as plans 01 and 09 leave it.

1. **`SyncEngine.resolve`** — replace everything from `val remote = …` to the end of the function:

   ```kotlin
   val remote = downloadWithinLimit(provider, key, remoteFiles)
   if (remote.contentEquals(local)) {
       return OperationOutcome(entries = mapOf(key to SyncIndexEntry(localContentHash(local), operation.revision)))
   }
   val copyName = libraryFileLocalSource.writeLibraryFileToFreeName(key.kind, key.name, remote)
   val copyKey = SyncKey(kind = key.kind, name = copyName)
   val uploaded = try {
       provider.upload(key.kind, key.name, local, operation.revision)
   } catch (exception: CancellationException) {
       // Nothing says whether the write landed, so the copy stays: see the KDoc.
       throw exception
   } catch (exception: SyncNetworkException) {
       throw exception
   } catch (exception: Exception) {
       // The service answered, and the answer was no. The remote version is where it was, and a copy kept now would
       // be joined by another one every time this file is resolved again.
       discardCopy(copyKey, remote)
       throw exception
   }
   if (uploaded !is RemoteWriteResult.Written) {
       // Contested - unless what is there now is what was just sent, which is how a write looks that landed and was
       // then retried. In that case the remote version is gone from the service, and the copy is all there is of it.
       val isOwnWrite = provider.download(key.kind, key.name).contentEquals(local)
       if (!isOwnWrite) discardCopy(copyKey, remote)
       return OperationOutcome(
           summary = if (isOwnWrite) SyncSummary(conflicts = listOf(copyName)) else SyncSummary(),
           hasUnresolvedConflict = true,
       )
   }
   val entries = mutableMapOf(key to SyncIndexEntry(localContentHash(local), uploaded.revision))
   val copyUploaded = provider.upload(copyKey.kind, copyKey.name, remote, expectedRevision = null)
   if (copyUploaded is RemoteWriteResult.Written) {
       entries[copyKey] = SyncIndexEntry(localContentHash(remote), copyUploaded.revision)
   }
   // Counted as one upload even when the copy also went up: what the user needs to know is that one file was
   // in two states, and that both of them survived under the name in the summary.
   return OperationOutcome(
       entries = entries,
       summary = SyncSummary(uploaded = 1, downloaded = 1, conflicts = listOf(copyName)),
   )
   ```

   In the own-write case the second pass finds `x.cho` identical on both sides and records it, and uploads the copy
   as the new local file it is.

2. **New private function** under `resolve`:

   ```kotlin
   /**
    * Takes back a copy whose file turned out not to have been overwritten remotely. Read before it is deleted, like
    * everything else this class removes: only the bytes that were written a moment ago are taken back.
    */
   private suspend fun discardCopy(copyKey: SyncKey, written: ByteArray) {
       try {
           if (libraryFileLocalSource.readLibraryFile(copyKey.kind, copyKey.name)?.contentEquals(written) == true) {
               libraryFileLocalSource.deleteLibraryFile(copyKey.kind, copyKey.name)
           }
       } catch (exception: CancellationException) {
           throw exception
       } catch (exception: Exception) {
           // A copy too many is the harmless way for this to go wrong.
           println("Could not remove the unused copy \"${copyKey.path}\": ${exception.message}")
       }
   }
   ```

3. **Rewrite the second paragraph of `resolve`'s KDoc** (present tense, no history):

   ```kotlin
    * The incoming version is on disk before the local one goes up, because going up is what destroys it on the
    * remote: held only in memory across that request, a copy that then cannot be written - a full disk, a process
    * that is killed - would be a version that exists nowhere. The upload can still turn out to be contested, a second
    * device resolving the same file in the same moment, and a copy left on disk then would go up as a new file on the
    * next pass while the file itself was resolved again into another one. So the copy is taken back whenever the
    * service has said that the remote version is still there: a refusal, or a conflict that is not the echo of this
    * device's own write. Where nothing says either way - the network dropped, the run was stopped - it stays, and the
    * worst that follows is a second identical copy.
   ```

   and in `apply`'s KDoc nothing changes (the ordering between groups is unaffected).

4. **`data/source/remote/api/.../SyncProvider.kt`**, KDoc of `upload` — make the rule the engine now relies on part
   of the contract. Append: "Throws [SyncNetworkException] whenever it cannot tell whether the write went through.
   Any other exception means it did not: the engine takes back work it did in preparation for the write on the
   strength of that."

5. **`DropboxSyncProvider.upload`** — the one place that could break that contract is a 2xx answer whose body
   cannot be read, which today surfaces as a `SerializationException`. Replace the last line with:

   ```kotlin
   val metadata = try {
       json.decodeFromString<DropboxFileMetadata>(response.bodyAsText())
   } catch (exception: SerializationException) {
       // The write went through and only its receipt is unreadable, which the contract files under "cannot tell".
       throw SyncNetworkException("Dropbox's answer to an upload could not be read.", exception)
   }
   return RemoteWriteResult.Written(metadata.rev)
   ```

   (`kotlinx.serialization.SerializationException`; check the import is not already there.)

Do not move the copy back after the upload "because a commit said so": that commit's property is kept by step 1
and pinned by its test, which must keep passing unchanged.

## Tests
Fakes: `FakeLibraryFileLocalSource` gets `var onWrite: (SyncKey) -> Unit = {}`, called by `writeLibraryFile` and by
`writeLibraryFileToFreeName` (with the name it chose) before storing; `FakeSyncProvider.onUpload` is the hook plan
08 added.

`SyncEngineTest`, with `here` / `there` / `original` as in the existing contested test (library `song(1)` = here,
remote = there, index = original):

- existing `` `a conflict that is contested while it is resolved leaves one copy rather than two` `` — unchanged,
  must still pass (first pass: copy written, upload refused, remote holds somebody else's bytes, copy discarded).
- `` `a conflict whose copy cannot be written leaves the remote version alone` `` — `onWrite` throws
  `LibraryStorageException("Full")` for any key other than `song(1)`. Expect `Result.Completed`,
  `provider.files.getValue(song(1)).first` still `there`, `local.files.keys == setOf(song(1))`, no conflicts in the
  summary.
- `` `an upload that landed before it was reported as contested keeps the copy` `` — `onUpload` for `song(1)`, first
  call only, stores `here to "r7"` in `provider.files` itself, so the fake then answers `Conflict`. Expect after
  the run: `song(1)` is `here` on both sides, `"song_1 (2).cho"` is `there` on both sides, and the summary names
  the copy once.
- `` `an upload the service refuses takes the copy back` `` — `onUpload` throws `IllegalStateException("Refused")`
  for `song(1)`. Expect `Result.Completed`, no ` (2)` file locally, remote still `there`.
- `` `an upload cut off by the network keeps the copy` `` — `onUpload` throws `SyncNetworkException("Offline")`.
  Expect `assertFailsWith<SyncNetworkException>` and `"song_1 (2).cho"` holding `there` locally.

`DropboxRequestTest` (`:data:source:remote:implementation`), for step 5:
`` `an upload whose receipt cannot be read is one that may have landed` `` — the handler answers the upload with
`respond(content = "<html>", status = HttpStatusCode.OK)`; expect
`assertFailsWith<SyncNetworkException> { provider.upload(LibraryFileKind.SONG, "song.cho", ByteArray(1), null) }`.

## Verify
- The unit test command, then the three compile checks (step 5 is in the remote implementation).
- By hand on the desktop build: sync a song, edit it locally and on dropbox.com, then make `library/songs`
  read-only (`chmod a-w`) and press **Sync now**. The run reports the file as failed; on dropbox.com the song still
  holds the web edit (before the fix it holds the local one). Make the folder writable, sync: the local text is
  under the name, the web edit is `x (2).cho`, on both sides.

## Docs
`data/repository/implementation/CLAUDE.md`, the `sync/` bullet: add "A conflict's incoming version is written next
to the local one *before* the local one goes up, and taken back if the service then says the remote file is still
there, so the version that loses is never held only in memory."

## Touches
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/SyncProvider.kt`
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt`
- `data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxRequestTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeLibraryFileLocalSource.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
- `data/repository/implementation/CLAUDE.md`

## Depends on
01 and 09 (same function chain), 08 (`FakeSyncProvider.onUpload`). Plan 07 edits other parts of
`DropboxSyncProvider.kt`; no overlap with step 5 beyond the file.
