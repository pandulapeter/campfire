<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Reuse a conflict copy that already holds the incoming version instead of writing another one each attempt

**Challenged:** amended — a candidate the pass's index holds but its listing does not is not reused: that copy was
deleted on another device, the same pass's `DeleteLocal` (run beside the resolve) deletes it here, and the one
remaining record of the overwritten version would go with it and then be deleted remotely by the next pass. Such a
candidate gets a fresh copy as today. Test added.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Lane:** S
**Files:** `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/run/ConflictResolver.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/run/CLAUDE.md`,
`data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/LibraryFileLocalSource.kt`,
`data/source/local/api/CLAUDE.md`,
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/LibraryFileLocalSourceImpl.kt`,
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNames.kt`,
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/LibraryFileLocalSourceImplTest.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/FakeLibraryFileLocalSource.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/run/SyncEngineConflictTest.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/TwoDeviceSyncTest.kt`

## Problem

`ConflictResolver.resolveWith` (`run/ConflictResolver.kt`) writes the incoming version under a free name before it
uploads the local one. When the network fails, it keeps the copy:

```kotlin
val copyName = libraryFileLock.withLock {
    withContext(NonCancellable) {
        libraryFileLocalSource.writeLibraryFileToFreeName(key.kind, key.name, remote) { name ->
            SyncKey(kind = key.kind, name = name).folded().name in remoteNames
        }.also { onLocalFileChanged(SyncKey(kind = key.kind, name = it)) }
    }
}
val copyKey = SyncKey(kind = key.kind, name = copyName)
val uploaded = try {
    provider.upload(key.kind, key.name, localBytes, revision)
} catch (exception: CancellationException) {
    throw exception
} catch (exception: SyncNetworkException) {
    throw exception
}
```

Keeping it is right for one attempt, since nothing says whether the upload landed. But the file is still in conflict
on the next attempt (the index didn't move), and that attempt writes another copy. The previous copy is now taken
locally, and it has meanwhile been planned and uploaded as a new file of its own. Probe: three attempts whose upload throws
`SyncNetworkException` before writing, then one good run, leave `song_1 (2)` to `song_1 (5)` with identical bytes, on
the device and in the cloud. The other path does the same. When the upload is answered `Conflict` and the follow-up
`provider.download(key.kind, key.name)` throws for the network, the copy is kept although the remote version is still
there, and the next attempt adds one more.

A flaky connection (and lane T's retrying of dropped connections, which makes each attempt longer) turns one conflict
into a pile of identical songs on every device.

## Fix

Before writing a copy, look for a copy that already holds exactly the incoming bytes, and reuse it.

1. **`:data:source:local:api`**: add to `LibraryFileLocalSource`
   `suspend fun numberedVariantHolding(kind: LibraryFileKind, name: String, bytes: ByteArray): String?`. It answers
   the first existing file whose name is a numbered variant of [name], as `writeLibraryFileToFreeName` would have
   chosen it, and whose bytes equal [bytes], or null. Its KDoc should say that sync uses it so that one incoming version
   gets one copy however many attempts it takes. Put it beside `writeLibraryFileToFreeName` and mention it in
   `data/source/local/api/CLAUDE.md` if that file describes the method.
2. **`LibraryFileLocalSourceImpl`**: list the kind's directory, keep the names that fold
   (`LibraryFiles.identityKey`) to `base + arrivingCollisionSuffix(n) + extension` for some `n ≥ 2`, where `base` and
   `extension` come from `name.knownExtension()` as in `FileStorage.uniqueName`, and read each in ascending `n`. Answer
   the first whose bytes are equal. Put the name test in `FileNames.kt` next to `uniqueName` (for example
   `String.isNumberedVariantOf(name)`), so the naming rule stays in one place and the engine never learns it. Compare
   bytes, not a hash from the pass's listing: the file may have changed since the listing, and the check runs under
   `LibraryFileLock`.
3. **`ConflictResolver.resolveWith`**: inside the same `libraryFileLock.withLock { withContext(NonCancellable) { … } }`,
   ask `numberedVariantHolding(key.kind, key.name, remote)` first. If it answers a name, that is `copyName`, with
   `isReused = true` and no write and no `onLocalFileChanged`. Otherwise write to a free name as today. Then:
   - `discardCopy` runs **only for a copy written in this call** (`!isReused`), on both of its paths: the refused upload
     and the contested write that is not this device's. A reused copy may be the only place the remote version still
     lives, since the file may have been overwritten since the copy was made, and it may already be indexed and
     uploaded. A local deletion of it would then become a remote deletion in the next run.
   - The rest stays: once the upload of `key` goes through, the copy is uploaded with `expectedRevision = null` and
     indexed if `Written`. A reused copy that an earlier attempt or an earlier operation of this pass already uploaded
     answers `Conflict` there and keeps its own index entry. A copy listed but not yet indexed has identical bytes on
     both sides, which the next pass settles by content. Either way the summary names `copyName` as the conflict copy.
   - The `isTaken` filter (names the listing holds) applies only to picking a *free* name. A reused copy may well be in
     the listing, because the previous attempt uploaded it.
4. **Not a copy that is being deleted.** If the answer is a name `pass.index` holds while `pass.remoteFiles` lacks it,
   the copy went up once and was deleted elsewhere since; the planner makes it a `DeleteLocal` in this very pass (its
   bytes still match the index), which runs beside the resolve and would delete the reused copy, and the next pass would
   then delete the copy's upload remotely. That loses the incoming version, which today's fresh copy keeps. So such an
   answer is not reused: write to a free name as today (its `isTaken` filter and the local file make that the next
   number). The check is the resolver's (it has the pass); the local source stays a plain question about files.
5. Otherwise an index entry for the candidate doesn't disqualify it. The bytes equal the incoming version, so
   reusing it loses nothing, and an earlier attempt or an earlier operation of this pass may well have indexed it.

**Rejected: discarding the copy when the upload fails for the network.** `SyncNetworkException` can't say whether the
request reached the service. A connection dropped after Dropbox applied the write is exactly the case where the
remote version is gone and the copy is all that is left of it, and lane T's retries make "the write landed, the answer
didn't" more common, not less. A fix that keys on content is safe whichever way the request went.

`run/CLAUDE.md`, Conflicts bullet: add "A copy that already holds the incoming bytes under a numbered variant of the
name is reused rather than written again, and is never taken back, so retries after network failures leave one copy."

## Tests

`SyncEngineConflictTest` (the fake's `writeLibraryFileToFreeName` names variants `"$base ($n).$extension"`;
`FakeLibraryFileLocalSource.numberedVariantHolding` follows the same rule):

- `retries cut off by the network leave one copy`: from `an upload cut off by the network keeps the copy`, synchronize
  three times with `onUpload` throwing `SyncNetworkException` for `song(1)` only. Then clear it and synchronize once
  more. Exactly one `song_1 (2).cho` exists locally and in `provider.files`, with `THERE`'s bytes, and `song(1)` holds
  `HERE` on both sides.
- `a contested upload whose check is cut off by the network leaves one copy`: `onUpload` writes `song(1)` "elsewhere"
  (so the upload is `Conflict`), and `onDownload` throws `SyncNetworkException` on the first run only. After a second
  run, there is one copy.
- `a reused copy is not taken back when the service refuses the upload`: a local `song_1 (2).cho` holding `THERE` (left
  by an earlier attempt), with `onUpload` throwing a plain exception for `song(1)`. The copy is still there afterwards.
- `a copy deleted on another device is not reused`: the index and the local folder hold `song_1 (2).cho` with `THERE`'s
  bytes, the listing does not (deleted elsewhere), and `song(1)` is in conflict with `THERE` remotely. After this run and
  one more, a copy with `THERE`'s bytes exists locally and under the same name in `provider.files` (`(2)` or `(3)`,
  depending on whether the deletion ran first), and `song(1)` holds `HERE` on both sides.
- `a numbered file with other bytes is not reused`: a local `song_1 (2).cho` with different bytes → the copy goes to
  `song_1 (3).cho`, and `(2)` is untouched.

`LibraryFileLocalSourceImplTest`: `numberedVariantHolding` finds a ` (3)` variant with equal bytes past a ` (2)` with
other bytes. It ignores `song_12.cho` and `song_1 (2) copy.cho`, matches a variant spelled with a different case, and
answers null with no variants.

`TwoDeviceSyncTest`: `a conflict settled over a failing network leaves one copy on both devices`: two devices edit
`song_1` offline. Device B's first two runs fail on the upload for the network, the third succeeds. Device A then runs.
Both libraries hold `song_1` and exactly one `song_1 (2)`.
