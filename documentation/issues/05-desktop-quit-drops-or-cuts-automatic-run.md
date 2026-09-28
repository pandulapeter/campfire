# Let a desktop quit finish (or cleanly stop) the automatic sync run instead of dropping it or leaving it "interrupted"

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** desktop
**Files:** app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt (`requestExit`),
app/desktop/CLAUDE.md

## Problem
Every way out of the desktop app ends the process straight away once the editor is dealt with:
`requestExit` (`CampfireViewModel.kt:1321-1334`) waits only for `currentSaveJob`, then `exit()` →
`exitApplication()` (`CampfireDesktopApplication.kt:56-62`), or `response.performQuit()` for Cmd+Q / the app menu
(`:74-83`). Nothing looks at sync. Since cc4d6f55 runs start on their own after every change, which makes two cases
routine on the desktop:

1. **Save, then quit within 10 s.** The automatic run is still waiting (`AUTOMATIC_RUN_DELAY`) and dies with the
   process. The edit reaches the cloud folder only the next time *this computer* opens Campfire — possibly days later —
   while the user reasonably expects "sync after every change" to have carried it to their phone.
2. **Quit while a run is going.** On the desktop ON_PAUSE is the window losing focus (see the `isDesktopPlatform`
   ON_RESUME / `refreshIfStale` pairing at `CampfireApp.kt:218-223`), so `onAppPaused` → `startScheduledSynchronization`
   starts the waiting run the moment the user clicks another app — e.g. on the way to quitting from the Dock or the
   taskbar. The run has already written the "a run was going" marker (`SyncRepositoryImpl.kt:483`); the process ends;
   the next launch finds `isRunInProgress`, reports **Interrupted**, and `SynchronizeLibraryUseCaseImpl`
   (`SyncUseCaseImpls.kt:97`) deliberately **does not start the launch run**. So the next session does not sync at all
   until another edit or Sync now — the opposite of what the user did.

A run that is cancelled rather than killed clears the marker (`finishRunCutShort`, `SyncRepositoryImpl.kt:695-699`),
so even the minimal fix is cheap.

## Fix
1. Add to `CampfireViewModel` a `suspend fun settleSynchronizationBeforeExit()` (desktop-facing, but common code) that:
   - calls `startScheduledSynchronization()` so a waiting run starts now;
   - waits, with `withTimeoutOrNull(EXIT_SYNC_GRACE)` (e.g. 15 s), for `syncState` to show no progress;
   - on timeout calls `cancelSynchronization()` and waits (bounded, ~2 s) for progress to clear, so the index is
     written and the marker cleared by the run itself.
   It needs a way to see "a run is waiting": expose it from `SyncRepository` (e.g. `val hasScheduledRun: Boolean` or a
   `StateFlow`) through a use case, or make `startScheduledSynchronization()` return whether it started one.
2. In `requestExit`, after the unsaved-text question is settled and before `onExit()`, call it — but only on the
   desktop (a `isDesktopPlatform` check, or an `awaitSync: Boolean` parameter that the desktop shell passes as true).
   While it waits, hide the window (`window.isVisible = false` from the desktop shell, passed as a callback), so the
   quit looks immediate; `stopListeningForOtherInstances()` is already called first, so a new launch in that time waits
   for the lock (`claimSingleInstance`) and then starts normally.
3. For the macOS quit handler the same wait runs before `response.performQuit()`; a logout/shut-down tolerates a
   few seconds.
4. Document in `app/desktop/CLAUDE.md` (the window-close paragraph) and in the root CLAUDE.md Sync bullet about
   automatic runs.

## Verification
Desktop (`./gradlew :app:desktop:run`), connected to Dropbox:
1. Edit and save a song, close the window within 2 s. Expected: the window disappears, the process lingers a few
   seconds, the change is in the Dropbox file; the next launch shows a successful last sync and starts its launch run.
2. With a large library, press Sync now and quit at once: the window disappears; after the bound the run is stopped;
   the next launch does not say "interrupted" (the run cleared its marker) and syncs.
Add a `SyncRepositoryImplTest` case for whatever "a run is waiting" accessor is added.

## Conflicts
`CampfireViewModel.requestExit` (presentation lane), `SyncRepository` API (sync lane), plan 02 (changes
`startScheduledSynchronization`).

## Open decision
- A: wait for the run (bounded), window hidden — the change actually leaves the machine (recommended).
- B: only cancel-and-join a going run, and drop a waiting one — quits instantly, fixes the "interrupted" suppression
  of the next launch run, but case 1 stays.
The web has the same shape on tab close and cannot wait; it is left as is (a `beforeunload` prompt while a run is going
would be the only option, and is intrusive).
