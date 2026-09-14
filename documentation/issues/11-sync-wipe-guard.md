# 11 · A sync run that would delete most of the library stops and asks

**Severity:** medium (by design today, unguarded) · **Area:** `:data:repository:*`, `:domain:*`, `:presentation` (Settings) · **Decision:** threshold, stop and ask

## Symptom

The user deletes or renames `Apps/Campfire/songs` on dropbox.com "to start over", or reconnects a device to an
account whose folder was emptied. The next run on *every* device deletes every song it has not edited since the last
sync, and the setlists too. Edited files survive through edit-beats-deletion, which is exactly why this is silent:
the library shrinks to whatever was recently touched.

## Behaviour to build

Before applying a plan, the engine counts the `DeleteLocal` operations. If they would remove **more than half of the
files the index knows** and **at least 5 files**, or **every** indexed file, the run stops before anything moves and
reports `SyncOutcome.DeletionsNeedConfirmation(count, total)`. Settings shows that under the account line with two
actions:

- **Delete them here too** — runs again with the deletions allowed.
- **Keep them and upload** — runs again treating those files as new on this device: their index entries are dropped,
  so the planner sees "local only, no index" and uploads them.

Disconnecting or a later ordinary run does not silently resolve it: the question stays until answered or the
remote folder is back.

## Fix

1. **Model.** In `data/model/.../SyncState.kt` add
   `data class DeletionsNeedConfirmation(val count: Int, val total: Int) : SyncOutcome` and
   `enum class SyncDeletionPolicy { ASK, DELETE_LOCALLY, KEEP_AND_UPLOAD }`.

2. **Repository contract.** `SyncRepository.synchronize(deletionPolicy: SyncDeletionPolicy = SyncDeletionPolicy.ASK)`;
   `SynchronizeLibraryUseCase` gets the same parameter with the same default.

3. **Engine.** `SyncEngine.synchronize(…, deletionPolicy)`: after `SyncPlanner.plan(...)`:

   ```kotlin
   val deletions = plan.count { it is SyncOperation.DeleteLocal }
   if (deletionPolicy == ASK && index.isNotEmpty() && deletions >= MIN_DELETIONS_TO_ASK && (deletions == index.size || deletions * 2 > index.size)) {
       return Result.DeletionsNeedConfirmation(count = deletions, total = index.size)
   }
   ```

   Turn `SyncEngine.Result` into a sealed type (`Completed(summary, index)` and the new one). For
   `KEEP_AND_UPLOAD`, before planning, drop every index entry whose key is local-present and remote-absent
   (`index = index.filterKeys { it in localKeys && it !in remoteKeys }.let { index - it.keys }`) — the planner then
   yields `Upload(expectedRevision = null)` for them. For `DELETE_LOCALLY`, plan and apply as today.
   `MIN_DELETIONS_TO_ASK = 5`; put the rule and its reason in the KDoc.

4. **Repository.** In `runSynchronization`, on `Result.DeletionsNeedConfirmation`: `clearRunInProgress()`, no rescan,
   `updateConnected { it.copy(progress = null, lastOutcome = SyncOutcome.DeletionsNeedConfirmation(...)) }`.
   `runSynchronization` takes the policy from `synchronize(policy)`; store the policy for the job, not in state.

5. **UI.** `SyncSettings.kt`: in `statusText`, a new branch for the outcome using a plural
   `settings_sync_deletions_pending` ("%1$d of your %2$d synced files are gone from the cloud folder. Delete them
   here too, or keep them and upload them again?" — write the Hungarian too). Add two `ActionListItem` rows keyed
   `sync_delete_locally` and `sync_keep_and_upload` shown only while `lastOutcome` is that type, calling
   `viewModel.synchronizeLibrary(SyncDeletionPolicy.DELETE_LOCALLY / KEEP_AND_UPLOAD)`. The **Sync now** row stays and
   runs with `ASK` (which asks again — fine). The Android notification strings are unaffected.

6. **Docs.** Root `CLAUDE.md` Sync section, `data/repository/implementation/CLAUDE.md`, `documentation/sync.md`: one
   paragraph on the guard and the two answers.

7. **Tests.** `SyncPlannerTest` stays as is (the planner is unchanged). Add an engine-level test with fakes if issue
   01 introduced the harness: an index of 10, remote empty, local unchanged → `DeletionsNeedConfirmation(10, 10)`;
   with `KEEP_AND_UPLOAD` → 10 uploads; with `DELETE_LOCALLY` → 10 local deletions. Also 2 deletions out of 10 → runs
   as normal.

## Verification

Desktop + Dropbox: sync 10 songs, delete the `songs` folder on dropbox.com, **Sync now**: the question appears, the
library is intact. **Keep them and upload**: the folder is repopulated. Repeat with **Delete them here too**.
