# 13 · The "last sync was interrupted" message is never visible

**Severity:** low · **Area:** `:data:repository:implementation`, `:domain:implementation`

## Cause

`SyncRepositoryImpl.restore()` (:128–139) sets `lastOutcome = Interrupted` when the index says a run was going, then
`RestoreSyncUseCaseImpl` (`SyncUseCaseImpls.kt:73–79`) starts a run whenever `isConnected`, and `runSynchronization`
(:225) sets `lastOutcome = null` before anything else. The status line is overwritten within the same start-up.

## Fix

Let the message be read: an interrupted run is **not** followed by an automatic one. The user sees the line, and
**Sync now** is right under it.

1. `SyncRepository.RestoreResult` gains `val wasInterrupted: Boolean`; `restore()` sets it from
   `document.isRunInProgress`.
2. `RestoreSyncUseCaseImpl`: `if (result.isConnected && !result.wasInterrupted) synchronizeLibrary()`.
3. The web's `didReturnFromAuthorization` path is unaffected (a fresh connection has no index).
4. Docs: `data/repository/implementation/CLAUDE.md` where the marker is described: "…is reported as interrupted next
   time, and that run is left for the user to start."

## Verification

Desktop: start a sync, kill the process mid-run, relaunch: Settings says the last sync was stopped; no run starts on
its own; **Sync now** completes it.
