# 12 · Sync operations trust the hash taken at listing time

**Severity:** medium (a save during a long run can be overwritten or deleted) · **Area:** `:data:repository:implementation` (`SyncEngine`)

## Symptom

During a long first sync the user saves an edit to a song that is planned as `Download` (changed remotely, unchanged
when the run listed the library): the edit is replaced by the remote version. A song planned as `DeleteLocal` that is
edited meanwhile is deleted. `upload` already re-reads and guards the symmetric case ("deleted between the listing and
now", `SyncEngine.kt:219`); `download` (:204–209) and `DeleteLocal` (:170–173) do not.

## Fix

Pass the index into `runOperation` (it is available in `apply` as `index`) and re-check the local file inside the two
operations that overwrite or remove it:

1. **`download`**: after the existing "same bytes already here" shortcut, before writing:

   ```kotlin
   val indexEntry = index[key]
   if (local != null && indexEntry != null && localContentHash(local) != indexEntry.localHash) {
       // Edited since the listing: this is now a file changed on both sides, and it is resolved as one.
       return resolve(provider, SyncOperation.Resolve(key, operation.revision), contentHashes)
   }
   ```

   (A `local` with no index entry means the planner already decided `Download` for a first contact; leave that path.)

2. **`DeleteLocal`**: read the file first; if it is gone, `OperationOutcome(removals = setOf(key))`; if its hash
   differs from `index[key]?.localHash`, do not delete — run `upload(provider, SyncOperation.Upload(key, expectedRevision = null))`
   instead (edit beats deletion, the same rule the planner applies).

3. Both operations become "read → decide → act"; the window between the read and the write is now one file's worth
   rather than the whole run. Note that in the KDoc.

4. Tests: with the engine harness from 01/11, a fake local source whose file changes between `readLocalStates` and
   the operation (mutate the fake's map from the provider's `download` call) must produce a ` (2)` copy, not an
   overwrite.
