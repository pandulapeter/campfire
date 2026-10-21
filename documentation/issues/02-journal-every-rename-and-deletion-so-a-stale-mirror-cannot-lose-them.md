# Journal every rename and deletion while an account is connected, resolving them against the account's list when applied, so a stale mirror cannot lose them

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (builds with sync)
**Challenged:** amended — `ConnectionFailed` counts only while the provider still holds credentials (a failed first
connection's `StopAll` would otherwise wait in the journal and stop the next account's shares); the applier's extra
read only for `StopItem` / `StopAll`, and the cost stated as it is; the late by-name rename named as an accepted edge;
plan 03 now makes `share()` apply the journal first, which this plan's entries need.
**Files:**
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksRepositoryImpl.kt`,
`…/shared/SharedLinksJournal.kt`, `…/shared/SharedLinksJournalApplier.kt`, `…/shared/SharedCopyChanges.kt`,
`…/shared/SharedCopySync.kt`, `…/shared/CLAUDE.md`,
`data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SharedLinksRepository.kt` (KDoc),
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksRepositoryImplTest.kt`,
`…/commonTest/…/shared/SharedLinksJournalTest.kt`, `…/commonTest/…/shared/SharedCopySyncTest.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/TwoDeviceSyncTest.kt`

(`…/shared/` = `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/`.)
Line numbers are at the reviewed commit `a869c9ff7`; find the code by its snippet.

Lane R, after plan 01 (which this does not touch beyond sharing files). Plan 03 comes next and adds a `shareId` to
`Rename` entries where one is known; this plan's renames, noted for items the mirror does not name, carry none, and plan
03 keeps matching those by name. Plan 03 also puts every journal application under one mutex; write the new resolution
step below so that it runs inside whatever lock the applier and the run's step take.

## Problem

A device notes a rename or deletion only for an item its mirror (with its own journal applied) names.
`SharedLinksRepositoryImpl.kt:160-170`:

```kotlin
override suspend fun followRename(kind: SharedLinkKind, fileName: String, newFileName: String) = note("note the rename of $fileName") {
    if (sharedNow().entryFor(kind, fileName) == null) emptyList() else listOf(SharedLinksJournal.Entry.Rename(from = fileName, to = newFileName))
}

override suspend fun stopSharingDeleted(kind: SharedLinkKind, fileName: String) = note("note the deletion of $fileName") {
    listOfNotNull(sharedNow().entryFor(kind, fileName)?.let { SharedLinksJournal.Entry.Stop(it.shareId) })
}

override suspend fun stopSharingEverything() = note("note the deletion of the library") {
    sharedNow().links.map { SharedLinksJournal.Entry.Stop(it.shareId) }
}
```

The mirror is refreshed only by a run's `SharedCopySync`, by Settings / the export screen's `refresh`, and by this
device's own edits. The change feed ignores `shared-links.json` (`SyncRemoteWatcher`, `scheduling/CLAUDE.md`), and a
shared copy's upload is never in the feed, so another device's new share starts nothing here. So:

1. Phone A shares song X. Tablet B, in front since its last run (or offline), renames X to Y. B's mirror does not name
   X, so nothing is journaled. B's next run: the registry still names X, the library has no X, the entry becomes
   `orphaned`, and the link keeps serving the old X for good, even though the user renamed it in Campfire, the one case
   the design promises to follow (`domain/implementation/CLAUDE.md`: "A shared item's link then follows it or stops").
2. Same, but B deletes X: nothing is journaled, the entry becomes `orphaned`, and **a song the user deleted stays
   public** behind its link, contrary to the delete confirmation (`songs_delete_song_confirmation_shared`: "its shared
   link stops working") on a device whose mirror happened to know it.
3. Delete library (`DeleteLibraryUseCaseImpl` → `stopSharingEverything`) stops only the shares B's mirror knows.

## Fix

Options considered:

- **A. Read the list in `note()` before deciding, when connected.** Breaks the contract that these calls "never wait for
  the service" (`SharedLinksRepository` KDoc): the rename and delete use cases await them under `NonCancellable`, so a
  slow network would hold up a rename. Rejected.
- **B. Note every rename and deletion while an account is connected, and resolve them against the list when they are
  applied (recommended).** The applier and the run's step read the list anyway; a rename of an unshared item changes
  nothing there and is dropped as applied. Costs, while online, one list download per rename (the edit's read) and two per
  deletion (the resolving read, then the edit's), no write where nothing is shared, and nothing while nothing is noted.
- **C. Refresh the mirror when the feed reports `shared-links.json`, without a run.** Narrows the window but cannot
  close it: no feed while offline, in the background, or during a run; the first rename after a return to the front
  can still beat the run. Could be added later for Settings' freshness; not the fix.

Carry out B:

1. **When to note.** Only while this device holds an account's credentials, since the journal is forgotten with them
   (`forgetSharedLinks`): add a private `isAccountStored()` = `stateHolder.value is SyncState.Connected`, or a
   `SyncState.ConnectionFailed` whose provider still holds credentials (`isConnected()`, a failure to ask counting as
   no). A `ConnectionFailed` without credentials (a first connection that failed) must not count: nothing would ever
   forget what it noted (a first connection finds no index, so `completePendingAuthorization` clears nothing), and a
   `StopAll` from a Delete library made then would stop every share of whichever account is connected next. Where it is false, keep today's rule (note only what `sharedNow()`
   names; with a cleared mirror that is nothing).
2. **Renames** (`followRename`): with an account stored, always note `Rename(from = fileName, to = newFileName)`. The
   journal-applied lookup (`sharedNow()`) stays for the other branch and for plan 03's `shareId`.
3. **Deletions** (`stopSharingDeleted`): where `sharedNow()` names the item, note `Stop(shareId)` as now. Otherwise, with
   an account stored, note a new entry resolved later:
   ```kotlin
   /** The library file [fileName] of [kind] was deleted in Campfire; whatever share of it the list names when this is applied stops. */
   @Serializable
   @SerialName("stopItem")
   data class StopItem(val kind: String, val fileName: String) : Entry
   ```
   (`kind` is `SharedLinkKind.id`, as the registry stores it.)
4. **Delete library** (`stopSharingEverything`): note `Stop` for every share `sharedNow()` names, as now, plus one new
   `@SerialName("stopAll") data object StopAll : Entry` (with an account stored), which stops every share the list names
   when applied. The library is emptied on every synced device by the same run (`DELETE_REMOTELY`), so stopping every
   share listed then is what the user asked for. Accepted edge: a share another device makes between the deletion and
   its application (offline for days) is stopped too; say so in `shared/CLAUDE.md`. A share made on this device after
   the deletion is not caught: plan 03 makes `share()` apply the journal first.
5. **Resolving.** Today `takeFromJournal` (`SharedLinksJournalApplier.kt:76-79`) deletes a stopped copy before the list
   is read (`deleteSharedCopy`, landed during the review in `SharedCopyDeletion.kt`: an id no copy can have counts as
   already gone; keep using it):
   ```kotlin
   internal suspend fun SharedCopyChanges.takeFromJournal(entry: SharedLinksJournal.Entry, folder: SyncFolder) {
       if (entry is SharedLinksJournal.Entry.Stop) folder.deleteSharedCopy(entry.shareId)
       take(entry)
   }
   ```
   Give it the list it resolves against: `takeFromJournal(entry, folder, document)`, where `StopItem` becomes the
   `shareId` of `applyTo(document).entryFor(kind, fileName)` (the renames journaled before it already applied, so it
   finds an item under the name it had when deleted) and `StopAll` every `shareId` of `applyTo(document).links`; each
   resolved share is then handled as a `Stop` (copy deleted first with `deleteSharedCopy`, then `remove(shareId)`). Nothing found is nothing to
   do, and the entry still counts as applied.
   - `SharedLinksJournalApplier.apply` must now read the list first where the journal holds a `StopItem` or `StopAll`
     (`registry.read(folder)`; on `FromNewerVersion` apply nothing and keep the journal) and pass that document; a
     journal of `Rename` and `Stop` entries only resolves nothing and skips that read, as today. The final `registry.edit` re-reads as now; removals
     are by `shareId`, so a list that changed between the two reads is still edited correctly.
   - `SharedCopySync.synchronize` already has `document` before it applies `pending`; pass it.
   - `SharedCopyChanges.take(entry)` keeps handling `Rename` and `Stop`; resolution lives in one function both callers
     use, so the two never resolve differently.
   - Accepted edge, say so in `shared/CLAUDE.md`: a `Rename` without a `shareId` (an item the mirror did not name)
     matches by name when applied, so one applied late (offline, or the applier failing) also moves a share another
     device made meanwhile of a new item under the old name. This device's own shares are safe (plan 03).
6. **Journal format.** The journal is device-local. New entry types decode only in builds that have them; an older
   build reading a journal with `stopItem` or `stopAll` fails `decodeFromString` and counts the journal as empty
   (`SharedLinksJournal.read`'s `recovering` fallback): downgrades are not supported, so accept it and say so in the
   `SharedLinksJournal` KDoc. Old journal files read unchanged.
7. **Docs.** `shared/CLAUDE.md` ("The domain's use cases note an entry … only for an item shared by the mirror as the
   journal would leave it") and the `SharedLinksRepository` KDoc of `followRename` ("Nothing is noted for an item
   [shares] does not name") / `stopSharingDeleted` / `stopSharingEverything` describe the new rule: noted whenever an
   account is connected, resolved when applied.

## Tests

`./gradlew :data:sync:implementation:desktopTest`

- `SharedLinksRepositoryImplTest`:
  - `a rename of an item another device shared since the last read is followed`: share `a.cho` "elsewhere" (write the
    registry with `provider.writeDocumentElsewhere`, add its copy to `provider.sharedFiles`) without refreshing this
    repository; `followRename(SONG, "a.cho", "b.cho")`; `runCurrent()`; the registry entry is named `b.cho` and keeps
    its `shareId`.
  - `a deletion of an item another device shared since the last read stops it`: same setup, `stopSharingDeleted`; the
    copy is gone from `provider.sharedFiles`, then the entry from the registry.
  - `a deleted library stops shares the mirror never saw`: same setup, `stopSharingEverything`.
  - Rewrite `an item that is not shared notes nothing`: while connected, a rename or deletion of an unshared item is
    noted, applied, changes nothing in the registry, and leaves the journal empty; while disconnected, or `ConnectionFailed` with no credentials stored, nothing is noted.
- `SharedLinksJournalTest`: `stopItem` and `stopAll` survive a write and read; a journal written before this change
  (only `rename` and `stop`) still reads.
- `SharedCopySyncTest`: a `StopItem` journaled after a `Rename` of the same item, applied by a run, deletes that share's
  copy and removes its entry.
- `TwoDeviceSyncTest`: `a song shared on one device and renamed on another that had not seen the share keeps its link`
  (B syncs, A shares and syncs, B renames the file and journals the `Rename` the repository now notes with `Device.journal`,
  B syncs, A syncs; the entry is `ok` under the new name on both).
