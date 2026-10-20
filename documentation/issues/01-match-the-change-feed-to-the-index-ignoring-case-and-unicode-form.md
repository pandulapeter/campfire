<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Match the change feed's names to the watch point's index ignoring case and Unicode form, as the run does

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the deletion test started from one song, so the run the fix brings would delete all of the index and stop at the deletion guard (`DeletionGuard`: a deletion of the whole index always asks) with the song still there; it now starts from two songs, one of them under two spellings.
**Files:**
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncWatchPoint.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncRemoteWatcherTest.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/CLAUDE.md`

## Problem

`SyncRemoteWatcher` decides whether the change feed holds another device's change by looking each reported name up in
the index the last completed run left (`SyncWatchPoint.kt:37-40`):

```kotlin
internal fun SyncWatchPoint.hasOtherChangesThanPreferences(changes: RemoteChanges) =
    changes.files.any { index[SyncKey(kind = it.kind, name = it.name)]?.remoteRevision != it.revision } ||
        changes.deletedFiles.any { SyncKey(kind = it.kind, name = it.name) in index } ||
        changes.documents.any { it != SyncedPreferencesDocument.FILE_NAME }
```

The lookup is by exact name, but the run does not key the index by the folder's spelling. `foldRemoteNamesOntoLocal`
(`run/SyncNameFolding.kt:30`) gives a remote file the local spelling where the two differ only by case or Unicode form
(Dropbox, the only provider, takes them for one file), and `foldIndexNamesOntoListings` (`run/SyncNameFolding.kt:49`)
moves an entry onto the listed spelling the same way. So with `Song.cho` on this device and `song.cho` in the folder,
the index holds `songs/Song.cho`, while the feed (`DropboxChangeFeed.changesSince`, which reports `entry.name`, the
folder's own spelling) reports `songs/song.cho`:

- **This device's own upload reads as another device's change.** Every edit of that song is uploaded under the local
  spelling to the file Dropbox keeps as `song.cho`; the feed reports `song.cho` with the new revision, the exact lookup
  finds no entry (`null != revision`), and the watcher schedules a full run for nothing after every edit.
- **Another device's deletion reads as this device's own.** Another device deletes `song.cho`; `songs/song.cho in
  index` is false, so nothing is scheduled and the deletion waits for the next run something else starts.

The root `CLAUDE.md` says whether two names are one file is always `LibraryFiles.identityKey` / `isSameLibraryName`;
this is the one place in sync that compares names exactly.

## Fix

In `SyncWatchPoint.kt`, look a reported name up exactly first and, where the index has no entry under that exact name,
by its folded form (`SyncKey.folded()` from `run/SyncNameFolding.kt`, which applies `LibraryFiles.identityKey`; it is
`internal` in the same module, so it can be imported). Build the folded lookup once per watch point, not per change:

```kotlin
/** The index by folded name, see [SyncKey.folded]: the run files an entry under this device's spelling, the feed names the folder's. */
private val indexByFolded: Map<SyncKey, List<SyncIndexEntry>> by lazy { index.entries.groupBy({ it.key.folded() }, { it.value }) }
```

`SyncWatchPoint` is a `data class`; a `lazy` in its body is fine (it does not take part in `equals`), or compute the
map in a small private helper the two checks share if a property in the body reads worse. Then:

- a written file is this device's own if its exact entry has the reported revision, or, with no exact entry, if any
  entry folding to the same key has it (revisions are compared for equality only, as now);
- a deleted file is another device's if its exact key is in the index, or, with no exact key, if any entry folds to it.

Keep the exact match first so a case-sensitive service, should one ever be added, keeps today's behaviour for names
it really holds twice; on such a service the folded fallback can at worst read this device's own deletion of one of
two case-twins as another device's and schedule a redundant run, which is harmless. Update the KDoc of
`hasOtherChangesThanPreferences` to say the names are matched as the run matches them, ignoring case and Unicode form.

In `scheduling/CLAUDE.md`, in the `SyncRemoteWatcher` bullet, after "A change whose revision the index holds, a
deletion of a file it no longer holds", add that names are matched ignoring case and Unicode form, as the run files
them (keep the paragraph under ~1,500 characters; it is close, so trim a phrase elsewhere in it if needed).

## Tests

In `SyncRemoteWatcherTest`, let `world(...)` take the folder's files and a case-ignoring provider
(`FakeSyncProvider(files = ..., account = TEST_ACCOUNT, ignoresCase = true)`; the fake already keeps the stored spelling
and puts it in its feed, as Dropbox does), then add:

- `` `this device's own upload under another spelling brings no run` ``: local `song("Song")` = ONE, folder
  `song("song")` = ONE, `ignoresCase = true`; `synchronizeAndWatch()`; then `world.local.files[song("Song")] = TWO`
  and `world.scheduler.synchronize(SyncDeletionPolicy.ASK)`; `advanceTimeBy(1.hours)`; assert `world.runs.count == 2`
  (today 3: the watcher schedules a run for the upload).
- `` `another device's deletion under another spelling is brought over` ``: local `song(1)` = ONE and `song("Song")` =
  TWO, folder `song(1)` = ONE and `song("song")` = TWO, `ignoresCase = true` (two songs, as in
  `` `another device's deletion is brought over` ``: with one, the run would delete the whole index, which
  `DeletionGuard` always stops to ask about); `synchronizeAndWatch()`; `world.provider.deleteElsewhere(song("song"))`;
  `advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)`; assert two runs and `world.local.files.keys == setOf(song(1))` (today
  one run and the file still there).

Check both fail before the change. If the first one already passes, the run's last listing came after its own upload
(the cursor is past it); then write it so the upload follows the watch point (an edit made after the first run and
carried by an automatic run, `world.scheduler.schedule()`), which is the case the finding describes. Run
`./gradlew :data:sync:implementation:desktopTest`.
