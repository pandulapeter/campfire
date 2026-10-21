# Run Stop sharing and Stop sharing everything under the journal's application lock, so a run's shared-copy step never uploads a copy that was just stopped

**Kind:** bug (race) · **Severity:** low · **Platforms:** all
**Challenged:** amended — the deadlock section no longer claims a stop waiting through a disconnect answers
`NOT_CONNECTED` (the run is cancelled before the state leaves `Connected`; the outcome is unchanged from today), and the
test defines the `SONG_ENTRY` fixture it relied on, which does not exist.
**Files:**
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksRepositoryImpl.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksJournal.kt` (KDoc of `whileApplying`),
`data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SharedLinksRepository.kt` (KDoc),
`data/sync/implementation/CLAUDE.md` (Locks),
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/CLAUDE.md`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksRepositoryImplTest.kt`

## Problem

The run's shared-copy step holds `SharedLinksJournal.whileApplying` from start to finish (`SharedCopySync.kt:65`). It
reads the registry once, at the start (`:70`, from the mirror or `registry.read`). Then it settles every entry against
the run's listing `sharedFiles`, taken earlier in the run. A stale copy is re-uploaded in update mode against the
listed revision (`SharedCopySync.kt:231-237`):

```kotlin
if (!isCurrent) {
    when (folder.uploadSharedFile(shareId = entry.shareId, bytes = copy, expectedRevision = remote.revision)) {
        is RemoteWriteResult.Written -> Unit
        RemoteWriteResult.Conflict -> return
    }
}
```

Nothing between the start of the step and that upload reads the registry or the folder again. Stop sharing takes no
lock at all (`SharedLinksRepositoryImpl.kt:193-202`), and neither does `stopSharingEverythingNow` (`:286-307`):

```kotlin
override suspend fun stopSharing(shareId: String): SharingFailure? {
    val folder = connectedFolder() ?: return SharingFailure.NOT_CONNECTED
    failureOf("stop sharing $shareId") { folder.deleteSharedCopy(shareId); null }?.let { return it }
    return removeStopped(folder, setOf(shareId))
}
```

So this interleaving is possible:

1. The user edits a shared song, and the automatic run starts a second later.
2. The run lists the folder and reaches its last step, which reads the registry with copy X in it.
3. The user taps Stop sharing on X. The copy is deleted and the entry removed.
4. The step reaches X (packing is local; other entries' uploads come first) and uploads it with
   `{".tag":"update","update":"<X's old rev>"}`, `autorename:false` (`DropboxSyncProvider.kt:340-358`).

The step's final edit cannot bring the entry back: `SharedCopyChanges.applyTo` only updates entries the current list
still has. The only harm is the upload. Dropbox documents update mode as "overwrite if the given rev matches the
existing file's rev", and does not say what happens when the file was deleted.

- If Dropbox answers `path/conflict`, the code maps it to `RemoteWriteResult.Conflict` (`DropboxSyncProvider.kt:362-364`)
  and the step returns quietly. That is harmless. This is also what `FakeSyncProvider` does (`expectedRevision` against
  a missing file is a `Conflict`), which is why no test sees the race.
- If Dropbox writes it, a fresh copy of the song sits at the stopped share's path and no list names it. Stop sharing
  just told the user that its link "stops working". Whether the old link, made for the deleted file, serves the new
  upload is unknown. At best the copy is a leftover that only Stop sharing everything removes. At worst it is a working
  public link the user believes is dead.

The fix does not depend on which of the two Dropbox does, so the plan does not need to measure it.

`stopSharingEverythingNow` has the same window for every copy it deletes. The window is real but narrow: it needs a
stale copy and a tap during a run's last step. Hence low severity.

## Fix

Run both stop actions under `journal.whileApplying`, as `share()` already waits behind the step through
`applyPendingJournal` (42nd review, plan 03). Read the folder inside the lock. A disconnect that completed while the stop was
waiting then answers `NOT_CONNECTED` rather than acting with credentials that are gone (see the last deadlock point for
one still in progress).

```kotlin
override suspend fun stopSharing(shareId: String): SharingFailure? = journal.whileApplying {
    // Under the step's lock: a run's step that read this share before the stop would otherwise upload its copy again
    // afterwards, against the revision of a file that is gone.
    val folder = connectedFolder() ?: return@whileApplying SharingFailure.NOT_CONNECTED
    // The copy goes first: …
    failureOf("stop sharing $shareId") { folder.deleteSharedCopy(shareId); null }?.let { return@whileApplying it }
    removeStopped(folder, setOf(shareId))
}

override suspend fun stopSharingEverythingNow(): SharingFailure? = journal.whileApplying {
    val folder = connectedFolder() ?: return@whileApplying SharingFailure.NOT_CONNECTED
    … (body unchanged, `return@failureOf` stays; the early `return failure` becomes `return@whileApplying failure`)
}
```

Deadlock check, all verified at `5514fcf76`:

- `whileApplying` is a non-reentrant `Mutex`. The stops are called only from `StopSharingUseCaseImpl` and
  `StopSharingEverythingUseCaseImpl` (UI). Neither the step nor the applier calls them: both delete through
  `takeFromJournal` → `folder.deleteSharedCopy`, which is not the repository.
- Inside the lock the stops call only `deleteSharedCopy`, `registry.read`/`edit`, `folder.list()`, and
  `journal.append`. `append` takes the journal's file `mutex`, which is separate and taken inside `whileApplying` by the
  step too. None of them takes `whileApplying` or the run lock.
- Lock order stays run lock → `whileApplying`. The stops never take the run lock. A disconnect cancels the run, and
  `withLock` frees the step's hold on cancellation, so a waiting stop proceeds. **It does not reliably find no
  connected folder**: `disconnect()` cancels the run (`scheduler.stopForConnectionChange()`) *before* it forgets the
  credentials and sets `Disconnected` (inside `runner.withRunLock`, after `provider.disconnect()`), so the stop usually
  still sees `Connected` and acts while the credentials go. It then fails like any request without credentials, or
  finishes, and its `removeStopped` may append a `Stop` to a journal `forgetSharedLinks` has just cleared. That is
  exactly what a stop tapped a moment before Disconnect does today (no lock at all), so the fix makes nothing worse,
  and a leftover `Stop(shareId)` names an id no other account's list holds, so applying it later changes nothing. Do
  not claim `NOT_CONNECTED` for this case in KDoc or docs; reading the folder inside the lock only covers a disconnect
  that completed while the stop waited.

The UI wait: a stop tapped during a step waits for the rest of the step. That can be a few uploads, each bounded by the
transport's timeouts. Settings → Sharing and the export screen already show "Stopping sharing…" (`LinkExportState.Stopping`,
`51bf86096`), and `share()` already accepts the same wait.

Docs:
- `data/sync/implementation/CLAUDE.md` → Locks, `SharedLinksJournal.whileApplying`: add "and by Stop sharing and Stop
  sharing everything, so a step never uploads a copy stopped after it read the list".
- `shared/CLAUDE.md` → Sharing: add one clause saying that the stops wait for a run's shared-copy step.
- `SharedLinksRepository.stopSharing` / `stopSharingEverythingNow` KDoc: "may wait for a sync run's shared-copy step".
- The KDoc of the `SharedLinksRepositoryImpl` class ("Never under the run lock…") stays true.
- `SharedLinksJournal.whileApplying` KDoc: mention that the stops hold it too.

## Tests

In `SharedLinksRepositoryImplTest` (`./gradlew desktopTest`):
`stopping a share while a run's step is uploading its copy waits for the step`

**Build the step and the repository on one `SharedLinksJournal` instance** (plus one mirror and one registry over one
`FakeSharedLinksLocalSource`). Construct `SharedCopySync(…)` directly, as
`SharedCopySyncTest.\`the run and the applier never apply one entry twice\`` does, and construct
`SharedLinksRepositoryImpl(…)` with that journal: give the `repository(…)` helper a `journal` parameter, or build it
inline. Do **not** use `Device` or the `sharedCopySync(…)` fixture. Each of them builds its own `SharedLinksJournal`,
and so its own mutex, and the test would pass without the fix. That is the kind of fake that hid races in the 42nd
review.

- `SONG_ENTRY` exists in no test file at HEAD (`SharedCopySyncTest` has only `SETLIST`, privately): define it in the
  test's companion as `sharedEntry(shareId = "songshare0000001", kind = SharedLinkKind.SONG, fileName = "song.cho")`
  (`SharedCopyFixtures.kt`; the fake refuses ids with characters other than letters, digits, `-`, and `_`). `cloud` below
  is a local `FakeSyncProvider()` passed to the `repository(…)` helper as its `provider`; `share` and `entryOf` are the
  fixtures' `FakeSyncProvider` extensions.
- `cloud.share(SONG_ENTRY)` (copy bytes `"copy"`, revision `s-<id>`). The library is a `FakeLibraryFileLocalSource`
  holding the song under `SONG_ENTRY.fileName`. Call the step with `index = emptyMap()`, so the song is read and the copy
  rebuilt (no `built`, the hash differs from `"copy"`), with `sharedFiles = cloud.list().sharedFiles` and
  `registryRevision = cloud.list().documentRevisions[SharedLinksFormat.NAME]`.
- Set `cloud.onUploadSharedFile = { id -> if (id == SONG_ENTRY.shareId) { stopping = this@runTest.launch { stopResult = repository.stopSharing(id) }; repeat(10) { yield() }; copyDuringUpload = cloud.sharedFiles[id] } }`.
- Run `step.synchronize(…)`, then `stopping.join()`.
- Assert `copyDuringUpload` is not null: the stop had not deleted the copy while the step was uploading. Then assert
  `stopResult == null`, `SONG_ENTRY.shareId !in cloud.sharedFiles`, and that the registry names no such entry.

Why it fails without the fix: the stop takes no lock, so during the `yield`s the launched coroutine runs to the end.
The fake's delete and registry edit never suspend for real, so the stop deletes the copy and `copyDuringUpload` is
`null`. With the fix, the stop waits on `whileApplying`, which the step holds, and runs only after `synchronize`
returns.

Add a second, shorter test with the same setup for `stopSharingEverythingNow` (its own lock wrap is a separate line that
could be forgotten). Existing stop tests must still pass unchanged: with nothing holding the lock, the stops behave as
before.
