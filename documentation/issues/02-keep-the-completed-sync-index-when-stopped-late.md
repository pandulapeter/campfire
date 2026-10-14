<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Keep the completed index when a sync run is stopped after it was saved

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the base reported from inside `SyncedPreferencesSync` is the folder's document (`effectiveRemote`), not the merged one, which would revert this device's unsynced edits; the callback is now the fix rather than an option; `latestIndex` is also set from `result.index` at the top of the Completed branch; files and tests extended.
**Files:** `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncRunner.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/preferences/SyncedPreferencesSync.kt`,
the test helper `data/sync/implementation/src/commonTest/.../Device.kt` (passes the new callback), a test beside the
existing `SyncRunner`/`SyncRepositoryImplTest` tests in `data/sync/implementation/src/commonTest/`

## Problem

Reviewed at `6c3043996`. In `SyncRunner`, the Completed branch merges the synced preferences and saves the index:

```kotlin
is SyncEngine.Result.Completed -> {
    val syncedPreferences = synchronizePreferences(provider, result)
    ...
    indexStore.save(
        result.index.copy(
            lastSyncedAt = syncedAt,
            syncedPreferences = syncedPreferences ?: result.index.syncedPreferences,
        ),
    )
    // Reads only what the run changed, which for most runs is nothing.
    libraryRefresher.refreshAfterRun()
```

and a cancellation anywhere in the run goes to

```kotlin
} catch (exception: CancellationException) {
    withContext(NonCancellable) { finishRunCutShort(latestIndex) }
```

where `finishRunCutShort` does `latestIndex?.let { indexStore.saveQuietly(it().markedAsFinished()) }`. `latestIndex` is the
engine's last snapshot, which still holds the *pre-merge* `syncedPreferences` base and the old `lastSyncedAt`; it is
never replaced by the document the Completed branch built. A Stop that lands during `synchronizePreferences` (after it
wrote the merged preferences locally and to the folder), during the final `indexStore.save`, or during
`refreshAfterRun()` (seconds after a large first sync, while Stop is still offered) overwrites the completed index with
the old base.

Concretely: song S's transposition base is 0; device B sets +2 and syncs; device A's run merges +2 locally and the user
stops it in that window, so A's index base goes back to 0. Later B sets +4. A's next run sees local +2, base 0,
remote +4: both changed, "two changes keep this device's", so +2 is uploaded and B's +4 is reverted everywhere.

## Fix

Checked at `d356e8f25`: `latestIndex` is a local `var` of `run` (line ≈ 102), captured by the `onIndexChanged` lambda
and read only by `finishRunCutShort` after the engine has returned or thrown. The periodic writer never reads it: it is
handed the snapshot it writes (`scheduleIndexWrite(marked)`), and `indexWriteJob?.join()` runs before the `when`, so
reassigning it in the Completed branch races nothing. `SyncedPreferencesSync.synchronize` returns only at its end.

1. **Top of the Completed branch:** `latestIndex = { result.index }`. The engine's last `onIndexChanged` snapshot is
   not guaranteed to be the document it returned, and from here on the cut-short write should never be older than it.
2. **Inside `synchronizePreferences`:** give `SyncedPreferencesSync.synchronize` a non-defaulted
   `onLocalSettled: (JsonObject?) -> Unit` and call it right after the `if (mergedPreferences != snapshot) { … }` block
   (whether or not it wrote), with **`effectiveRemote`**, not `merged`. Until the upload lands, the folder still holds
   `effectiveRemote`, and the local preferences already hold everything it says (that is the same reasoning as the
   `Conflict` branch's `previous = effectiveRemote`). Recording `merged` as the base would be wrong: a value only this
   device changed is in `merged` but not in the folder yet, so the next run would read the folder's older value as a
   remote change against that base and revert the user's edit everywhere. With `effectiveRemote` as base, that value
   reads as a local change and goes up next run. A song `merged` pruned reads as removed locally and is pruned again.
   When `effectiveRemote` is just `previous` (folder document missing or unreadable), the report changes nothing.
   No call on the newer-format early return (nothing was written). In `SyncRunner`:
   `onLocalSettled = { base -> latestIndex = { result.index.copy(syncedPreferences = base) } }`, which keeps the old
   `lastSyncedAt`, since the run has not finished.
3. **The save:** build `val completed = result.index.copy(lastSyncedAt = …, syncedPreferences = …)`, set
   `latestIndex = { completed }` *before* `indexStore.save(completed)`, so a cancellation during the save or
   `refreshAfterRun()` rewrites the completed document (marked finished) instead of an older one.

The window between `uploadDocument` returning `Written` and `synchronize` returning is a `when` and a `return`, so it
needs nothing. If the upload landed but its answer was lost to the Stop (the web), base `effectiveRemote` with folder
and local both at `merged` merges to no change, so that case is safe too.

No interaction with plan 01 (engine and resolver writes; this one is the runner and the preferences sync). Same lane.

## Tests

- A runner test with a fake provider: complete a run whose preferences merge changes the base, cancel the run while
  `refreshAfterRun` is suspended (a gated fake refresher), then assert the saved index carries the merged
  `syncedPreferences` and the new `lastSyncedAt`, not the snapshot's.
- A runner test that cancels while the fake provider's `uploadDocument` for `preferences.json` is gated (after the local
  write): assert the saved index's `syncedPreferences` is the folder's document as it was downloaded and its
  `lastSyncedAt` is the old one. Seed it with a value only this device changed before the run (e.g. a transposition
  the folder does not have): the next, uninterrupted run must keep that value and upload it, not revert it to the
  folder's (which is what a `merged` base would do).
