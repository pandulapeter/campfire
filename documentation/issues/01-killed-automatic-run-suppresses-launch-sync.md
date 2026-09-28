# Do not let an interrupted automatic run cancel the next launch's sync

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** ios | web | android
**Files:** data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncIndexDocument.kt, data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt, data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt, data/repository/implementation/CLAUDE.md, CLAUDE.md

## Problem
A run writes `isRunInProgress = true` into the index before anything moves (`SyncRepositoryImpl.kt:483`), and a launch
that finds it reports *Interrupted* and starts **no** run (`restoreConnection` `:252-264`,
`RestoreSyncUseCaseImpl` in `SyncUseCaseImpls.kt:97`). That rule was made when every run was one the user had asked
for or the launch run, so "interrupted" was rare and worth reading.

Since cc4d6f55, `onAppPaused` (ON_PAUSE, `CampfireApp.kt:228` → `CampfireViewModel.kt:1573`) starts any waiting
automatic run the moment the app stops being in front. That now makes the marker the normal outcome of a very ordinary
sequence:

- iOS: edit a song (or toggle a tag), open the app switcher (ON_PAUSE → run starts, marker written within
  milliseconds) and swipe Campfire away before the run finishes.
- Web: edit, close the tab within ten seconds (visibility change → ON_PAUSE → run starts and writes the marker to OPFS,
  then the page is torn down).
- Android on OEMs that kill the process with the task, same as iOS.

Next launch: the Settings screen says the sync was interrupted (which nobody sees unless they open it), and no launch
run happens — so neither the edit made last time nor anything other devices changed meanwhile is synced until the user
presses Sync now or makes another change. Before cc4d6f55 the same sequence left no marker and the launch run synced
everything.

## Fix
Tell user-started runs from automatic ones in the marker, and only let the former suppress the launch run:

1. `SyncIndexDocument`: add `val isAutomaticRunInProgress: Boolean = false` (default keeps old files decoding), or
   change the marker to an enum-like string `runInProgress: String = ""` (`""`, `"user"`, `"automatic"`) — the boolean
   is the smaller change. `of(...)` writes it false.
2. `SyncRepositoryImpl.startRun` / `runSynchronization`: pass `isAutomatic` (true only from the debounce collector at
   `:166`) and write `document.copy(isRunInProgress = true, isAutomaticRunInProgress = isAutomatic)` at `:480`/`:483`;
   the snapshot lambda in `latestIndex` keeps the same flags; every place that clears `isRunInProgress` clears both.
3. `restoreConnection`: `lastOutcome = Interrupted` only when `isRunInProgress && !isAutomaticRunInProgress`, and
   `wasInterrupted` the same, so a killed automatic run is followed by an ordinary launch run. Still clear the marker as
   today (`:255-257`).
4. `SyncRepositoryImplTest`: a restore over an index with `isRunInProgress = true, isAutomaticRunInProgress = true`
   answers `wasInterrupted = false` and no Interrupted outcome; with `isAutomaticRunInProgress = false` it behaves as
   today.
5. Update the interrupted-run sentences in `data/repository/implementation/CLAUDE.md` and root `CLAUDE.md` (Sync:
   "that run is left for the user to start rather than started on launch" → only for a run the user or a launch started).

## Verification
`./gradlew :data:repository:implementation:desktopTest`. Manual (iOS simulator, connected): toggle a tag, go to the app
switcher at once and swipe the app away; relaunch. Expected: no "interrupted" message, a launch run starts and uploads
the tag. Repeat with Sync now on a large library + swipe: still reported as interrupted, no launch run.

## Conflicts
`plan 06` also threads an "automatic" flag through `startRun`; if both are done, use one parameter for both.

## Open decision (only if the fix needs the user's choice)
- **A (recommended):** as above — an interrupted automatic run is not reported and does not suppress the launch run.
- **B:** keep reporting it, but let the launch run start anyway for automatic ones (the message is then replaced
  within seconds, which is the reason the rule exists).
- **C:** keep today's behaviour; the next edit starts a run anyway.
