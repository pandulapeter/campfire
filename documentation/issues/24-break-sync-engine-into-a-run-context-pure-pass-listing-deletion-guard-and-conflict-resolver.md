# Break SyncEngine into a per-run context, a pure PassListing, a pure DeletionGuard and a ConflictResolver

**Challenged:** amended — named the visibility change the move needs (`SyncKey.folded()` is private to `SyncEngine.kt` and `caseCollisions` moves out), and aligned `ConflictResolver`'s planted-hash parameter with plan 22 as amended (the engine keeps its lambda parameter; no `PlantedContentHashes` type, no Koin definition for the resolver).

**Kind:** architecture  ·  **Severity:** medium  ·  **Effort:** L  ·  **Risk:** medium  ·  **Platforms:** all
**Files:** `data/repository/implementation/src/commonMain/.../implementation/sync/SyncEngine.kt` (`synchronize`, `readLocalStates`, `LocalListing`, `apply`, `runOperation`, `deleteRemotely`, `download`, `deleteLocally`, `upload`, `resolve`, `resolveWith`, `takeRemote`, `discardCopy`, `isSameContent`, `downloadWithinLimit`, `withoutForeignEntries`, `isTooManyToDeleteOutOf`, `MIN_DELETIONS_TO_ASK`); new `sync/SyncRun.kt`, `sync/PassListing.kt`, `sync/DeletionGuard.kt`, `sync/ConflictResolver.kt`; tests `sync/SyncEngineTest.kt` (guard, unchanged), new `sync/PassListingTest.kt`, `sync/DeletionGuardTest.kt`; `data/repository/implementation/CLAUDE.md` (names `resolveWith`, `takeRemote`, the guard)
**Depends on:** none. Coordinate with the concurrent lane that moves the sync model / name folding / summary helpers (`folded`, `foldRemoteNamesOntoLocal`, `foldIndexNamesOntoListings`, `SyncSummary.plus`) into files of their own: find them by name. Independent of 22.

## Problem

`SyncEngine.kt` is 929 lines, and its one public method threads the same per-run values through every private one by
hand:

```kotlin
private suspend fun apply(
    provider: SyncProvider, plan: List<SyncOperation>, index: Map<SyncKey, SyncIndexEntry>,
    remoteFiles: Map<SyncKey, RemoteFileState>, caseCollisions: Set<SyncKey>, onProgress: (SyncProgress) -> Unit,
    accountId: String, lastSyncedAt: Long, syncedPreferences: JsonObject?,
    onIndexChanged: suspend (snapshot: () -> SyncIndexDocument) -> Unit, onLocalFileChanged: suspend (SyncKey) -> Unit,
): PassOutcome
```

(11 parameters; `resolveWith` 8, `runOperation` 6, `download` 5, `deleteLocally` 5, `takeRemote` 5). `provider`,
`onLocalFileChanged` and `remoteFiles` are passed into nearly every function.

`synchronize` (170 lines) interleaves I/O with set arithmetic that is pure and decides what the run may touch: per pass
it lists, reads, then computes `tooLarge`/`unreadable`/`excluded`, `caseCollisions`, the `storable`/`unstorable`
partition, `foldRemoteNamesOntoLocal`, `foldIndexNamesOntoListings`, `index - tooLarge - unstorableKeys`, the
`KEEP_AND_UPLOAD` index and `syncedPreferences` pruning, the `KEEP_AND_DOWNLOAD` pruning, `planningIndex = index - unreadable`
— and then, inline, the deletion guard:

```kotlin
val localDeletions = plan.count { it is SyncOperation.DeleteLocal }
if (!deletionPolicy.waivesLocalGuard && planningIndex.isNotEmpty() && localDeletions.isTooManyToDeleteOutOf(planningIndex.size)) {
    return Result.DeletionsNeedConfirmation(count = localDeletions, total = planningIndex.size, direction = SyncDeletionDirection.LOCAL)
}
…
val hasLostEverythingLocally = local.isEmpty() && planningIndex.isNotEmpty()
if (!deletionPolicy.waivesRemoteGuard && planningIndex.isNotEmpty() && remoteDeletions > 0 &&
    (hasLostEverythingLocally || remoteDeletions.isTooManyToDeleteOutOf(planningIndex.size))) { … }
```

The guard is the one thing between a vanished library folder and an emptied cloud folder, and the pass preparation is
what decides which files a run may delete; both are pure, yet they can only be tested today through a full engine run
against `FakeSyncProvider` and `FakeLibraryFileLocalSource` (`SyncEngineTest`, 1887 lines). The threshold edges (4 vs 5
deletions, exactly half, all of them) and the `KEEP_*` pruning have no direct test.

## Fix

Behaviour-preserving; method bodies move verbatim with their comments. Each step one commit, `SyncEngineTest` green
after each.

1. **`DeletionGuard`** (pure, `internal object` in `DeletionGuard.kt`):

   ```kotlin
   internal object DeletionGuard {
       /** The question to ask before [plan] moves, or null where it may go ahead. This device is asked about first. */
       fun check(plan: List<SyncOperation>, planningIndexSize: Int, isLocalListingEmpty: Boolean, policy: SyncDeletionPolicy): SyncEngine.Result.DeletionsNeedConfirmation?
       internal fun Int.isTooManyToDeleteOutOf(total: Int): Boolean   // moved, KDoc included
       const val MIN_DELETIONS_TO_ASK = 5
   }
   ```

   `synchronize` calls `DeletionGuard.check(...)?.let { return it }` where the two `if`s are now.
2. **`PassListing`** (pure): a function `preparePass(...)` returning a `PreparedPass` data class:

   ```kotlin
   internal fun preparePass(
       index: Map<SyncKey, SyncIndexEntry>, listed: List<RemoteFile>, local: List<LocalFileState>,
       tooLarge: Set<SyncKey>, unreadable: Set<SyncKey>, canHoldFileName: (LibraryFileKind, String) -> Boolean,
       deletionPolicy: SyncDeletionPolicy, syncedPreferences: JsonObject?,
   ): PreparedPass
   internal data class PreparedPass(
       val local: List<LocalFileState>, val remote: List<RemoteFileState>, val index: Map<SyncKey, SyncIndexEntry>,
       val planningIndex: Map<SyncKey, SyncIndexEntry>, val caseCollisions: Set<SyncKey>,
       val unstorable: List<String>, val syncedPreferences: JsonObject?,
   )
   ```

   holding exactly the lines of `synchronize` from `val excluded = tooLarge + unreadable` to
   `val planningIndex = index - unreadable`. `canHoldFileName` is non-suspending on `LibraryFileLocalSource`, so the
   engine passes `libraryFileLocalSource::canHoldFileName`. What stays in `synchronize`: `provider.list()`,
   `withoutForeignEntries` (I/O, before the pure step, as today), `readLocalStates`, the `summary` bookkeeping for
   `tooLarge` (pass 0), `unreadable` (every pass) and `unstorable` (pass 0), `SyncPlanner.plan`, the guard, `apply`.
3. **`SyncRun`** context: a private class created once per `synchronize` call holding `provider`, `accountId`,
   `lastSyncedAt`, `onProgress`, `onIndexChanged`, `onLocalFileChanged`; and a per-pass `SyncPass(run, index,
   remoteFiles, caseCollisions, syncedPreferences)`. `apply`, `runOperation`, `download`, `deleteLocally`, `upload`,
   `resolve`, `deleteRemotely`, `downloadWithinLimit`, `isSameContent` become members of (or take) `SyncPass`, so
   `apply(pass, plan)`, `download(operation)`, `upload(operation)`. Mind the KDoc on `apply` about the snapshot lambda
   reading the pass's own map under `results` — it moves with the code and stays true.
4. **`ConflictResolver`**: `resolveWith`, `takeRemote`, `discardCopy` move to a class constructed by the engine with
   `libraryFileLocalSource`, `libraryFileLock`, `setlistComparison`, `plantedContentHash` (the engine keeps its
   constructor, which the tests call ~70 times). `resolveWith`'s 8 parameters become `(pass, key, revision, localBytes,
   remote, indexEntry)`. `resolve` and `download` call it. `plantedContentHash` stays the engine's
   `suspend (SyncKey) -> String?` with its `{ null }` default (plan 22 builds the engine in a `@Single` module function
   and introduces no `PlantedContentHashes` type), so `ConflictResolver` takes the same function type.

Visibility the moves need: `SyncKey.folded()` is a `private` top-level function in `SyncEngine.kt` at 2940b0e0a and
`caseCollisions` (moving to `PassListing.kt`) calls it, so it becomes `internal` — or is used from wherever the
concurrent lane's name-folding file put it; plan 29 later replaces its body with `LibraryFiles.identityKey`. The
`KEEP_AND_UPLOAD` block's two inline `normalizedToNfc().lowercase()` move into `preparePass` verbatim (plan 29 finds
them there). `ConflictResolver` must not be a Koin definition: it is built by the engine, which is itself built by
plan 22's module function.

`SyncEngine.kt` should end near 450 lines; no visibility above `internal` changes. Update the CLAUDE.md sentences that
name `resolveWith`/`takeRemote`/the guard.

## Tests

- New `DeletionGuardTest`: 4 local deletions of 8 → null; 5 of 9 → LOCAL; 5 of 10 → null (not more than half); 3 of 3 →
  LOCAL (whole index); 0 deletions → null; empty planning index → null; remote side the same, plus
  `isLocalListingEmpty` with 1 remote deletion of 20 → REMOTE; both sides over → LOCAL first; `DELETE_LOCALLY` waives
  only the local guard (remote still asks); `KEEP_AND_DOWNLOAD` waives only the remote one.
- New `PassListingTest`: a too-large file and its index entry are left out on both sides; an unreadable file keeps its
  index entry but is not in `planningIndex`; a remote name the device cannot hold is reported and never folded onto a
  local name; a remote `Song.cho` takes the local `song.cho` spelling; an orphaned index entry moves to the one listed
  spelling; two local names differing in case are `caseCollisions`; `KEEP_AND_UPLOAD` drops the entries of here-only
  files and prunes those songs (case- and NFC-folded) from `syncedPreferences`; `KEEP_AND_DOWNLOAD` drops the entries of
  there-only files; a non-library remote name is filtered out.
- Guard: all of `SyncEngineTest`, unchanged.

## Manual check

none — covered by tests (optionally one Dropbox run on desktop: Sync now with a few edits on both sides and one
conflict, to see the ` (2)` copy appear as before).
