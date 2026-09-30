# Stop a running sync run before deleting the library, so that the cloud deletion the dialog promised is carried out

**Challenged:** amended — the `Boolean` return would not compile as written: `SynchronizeLibraryUseCaseImpl.invoke` (`domain/implementation/.../useCases/SyncUseCaseImpls.kt`, line 109) is an expression body `= syncRepository.synchronize(deletionPolicy)` and overrides an interface `invoke` that returns Unit, so it must become a block body (see Fix); the lock description corrected; the tests and the cancel-then-start ordering were traced and hold.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (an account must be connected)
**Files:** `data/repository/api/.../SyncRepository.kt` and `data/repository/implementation/.../SyncRepositoryImpl.kt` (the `Boolean` return), `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/SyncUseCaseImpls.kt`, `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/DeleteLibraryUseCaseImpl.kt`,
`domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/DeleteLibraryUseCase.kt` (its KDoc),
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/DeleteLibraryUseCaseImplTest.kt` (new),
`CLAUDE.md` (root, the Cover art paragraph's sentence about the deletion), `domain/implementation/CLAUDE.md` if it describes the use case

## Problem

The Settings dialog that deletes the library says, in the error color while an account is connected, that the cloud
folder and every synced device lose the library too, and the typed `DELETE` is documented as the answer the sync
guard would otherwise stop to ask for. `DeleteLibraryUseCaseImpl` (at 9ab7ca54e) starts that run last:

```kotlin
override suspend operator fun invoke() = withContext(NonCancellable) {
    val songsFailure = runCatchingFailure { songRepository.deleteAllSongs() }
    val setlistsFailure = runCatchingFailure { setlistRepository.deleteAllSetlists() }
    userPreferencesRepository.updateUserPreferences { preferences ->
        preferences.copy(transpositions = emptyMap(), foldedSections = emptyMap())
    }
    syncRepository.synchronize(SyncDeletionPolicy.DELETE_REMOTELY)
    (songsFailure ?: setlistsFailure)?.let { throw it } ?: Unit
}
```

`SyncRepositoryImpl.synchronize` drops the request whenever a run is already going, and says nothing:

```kotlin
override fun synchronize(deletionPolicy: SyncDeletionPolicy) {
    // The run starts after every change an automatic one is waiting for, so it carries them too.
    if (startRun(deletionPolicy)) scheduledRunDueAt.value = null
}
…
private fun startRun(deletionPolicy: SyncDeletionPolicy, isAutomatic: Boolean = false): Boolean {
    val current = syncJob.load()
    if (current?.isActive == true) return false
```

A run is going more often than it looks: the launch run, the automatic run ten seconds after any edit, or Sync now.
Scenario: the launch run is in its transfer phase; the user opens Settings, types `DELETE`. The songs and setlists are
deleted (each waits for `LibraryFileLock`, which the run holds between its passes, so they land between the run's
passes or after it), every deletion calls `onLibraryChanged`, which schedules an ordinary `ASK` run, and
`synchronize(DELETE_REMOTELY)` returns false. The `DELETE_REMOTELY` policy is a parameter of the one run that never
started, so it is gone. The chained `ASK` run then plans the deletion of every file in the cloud folder, hits the
remote guard and stops: Settings asks "Delete them from the cloud too?" after the user has just typed the answer into
a dialog that said the folder goes too. Nothing is lost, but the promised outcome takes a second answer, and a run that
was mid-download when the lock was released can bring part of the library back first. The `DeleteLibraryUseCase` KDoc
admits the dropped request; the root `CLAUDE.md` (line 545) says the deletion "then starts a sync run with the
deletions allowed" without the caveat.

## Fix

Stop whatever run is going before the deletion starts, so that the run this use case starts is never the one that
loses the race. In `DeleteLibraryUseCaseImpl.invoke`, first:

```kotlin
// A run that is going would keep the deletion's own run from starting, and its answer with it; what it had already
// moved stays moved, and the run below carries the rest.
syncRepository.cancelSynchronization()
```

`cancelSynchronization` sets `syncJob` to null and cancels the job (`syncJob.exchange(null)?.cancel()`), so
`startRun` at the end of the use case finds no active job and starts the `DELETE_REMOTELY` run, which waits on
the repository's own `mutex` (`runSynchronization` body) for the cancelled run's clean-up (`finishRunCutShort`, which
writes the index marked finished) to end. `LibraryFileLock` is only held around local reads and writes, never across
a request, so the deletions in between take it as soon as the cancelled pass lets go; nothing is deleted under a pass.
A file the cancelled run had downloaded but whose index entry was not yet reported can come back in the new run (local
missing, remote present, unknown to the index); that exists today and this plan does not change it. The cancelled run leaves the index's "a run was going" marker set, and the run this
use case starts clears it by finishing, so a launch after that reports no interruption; a cancelled *automatic* run
is never reported anyway. Keep the order otherwise: songs, setlists, preferences, then the run, first failure thrown
last, all under `NonCancellable`.

Make the dropped case visible too, so that it can never come back silently: change `SyncRepository.synchronize` to
return the `Boolean` `startRun` already computes (`fun synchronize(deletionPolicy: SyncDeletionPolicy = ASK): Boolean`),
update its KDoc ("false where a run is already going, which the request then does not survive"), and in the use case
`check(syncRepository.synchronize(SyncDeletionPolicy.DELETE_REMOTELY)) { "…" }` is too strong for a state that
`cancelSynchronization` makes impossible in practice; log it instead
(`println("The deletion's sync run could not be started, a run was already going.")`) so that the desktop log names it.
The one other caller that does not merely ignore the result is `SynchronizeLibraryUseCaseImpl.invoke` in
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/SyncUseCaseImpls.kt`
(`override operator fun invoke(deletionPolicy: SyncDeletionPolicy) = syncRepository.synchronize(deletionPolicy)`): as
an expression body its return type would become `Boolean` and fail to override `SynchronizeLibraryUseCase.invoke`
(Unit). Make it a block body (`{ syncRepository.synchronize(deletionPolicy) }` with an explicit Unit return, i.e.
`override operator fun invoke(deletionPolicy: SyncDeletionPolicy) { syncRepository.synchronize(deletionPolicy) }`);
the interface stays as it is. `SyncRepositoryImplTest` calls `repository.synchronize(...)` as statements and needs no
change, and no other class implements `SyncRepository` (grep `SyncRepository` over `*.kt`: only `SyncRepositoryImpl`).
If the executor would rather not touch the signature at all, skipping the Boolean change and the log line is an
acceptable reduction: the `cancelSynchronization()` call is the fix. Rewrite the `DeleteLibraryUseCase` KDoc sentence that admits
the drop to say the run going is stopped first. In the root `CLAUDE.md` line 545, make it "stops any run that is going
and then starts a sync run with the deletions allowed". Check `domain/implementation/CLAUDE.md` for a sentence about
the use case and keep it in step.

## Tests

New `DeleteLibraryUseCaseImplTest` in `domain/implementation/src/commonTest/.../useCases/`, with hand-written fakes
of `SongRepository`, `SetlistRepository`, `UserPreferencesRepository` and `SyncRepository` that append to one shared
`MutableList<String>` of events (the fakes in `ImportFilesUseCaseImplTest` and `GetScreenDataUseCaseImplTest` show the
shape; every interface method not needed throws `NotImplementedError`):

- `stopsTheRunGoingBeforeDeletingAndStartsOneWithDeletionsAllowedAfter`: events are exactly
  `cancelSynchronization`, `deleteAllSongs`, `deleteAllSetlists`, `updateUserPreferences`, `synchronize(DELETE_REMOTELY)`.
- `startsTheRunAndThrowsWhenASongCouldNotBeDeleted`: `deleteAllSongs` throws an `IllegalStateException`; the setlists
  are still deleted, `synchronize(DELETE_REMOTELY)` is still called, and the exception is rethrown after it.
- `clearsTheTranspositionsAndTheFoldedSections`: the preferences update receives a copy with both maps empty.

## Manual check

Connect Dropbox on the desktop build with a library of about fifty songs, press Sync now, and while the progress is
visible type `DELETE` in Settings → Library. The library empties, the run in progress stops, a new run starts on its
own, and it ends with the cloud folder empty and no "Delete them from the cloud too?" question.
