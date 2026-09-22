# 13 · Double-tapping "Sync now" stops the run it just started, and double-tapping "Stop syncing" starts it again

**Severity:** wrong behaviour (all platforms; likely for double-tappers and double-clickers, and for anyone who taps
"Stop syncing" again because the run takes a moment to wind down) · **Area:** `:presentation`
(`screens/settings/SyncSettings.kt`)

## Symptom
Settings → Library, Dropbox connected:
1. Double-tap "Sync now". The run starts and is immediately stopped; the line under the account says the last sync
   was interrupted.
2. While a run is going, tap "Stop syncing", and tap again as nothing seems to happen (or double-tap it). As soon as
   the run has finished winding down, the row is "Sync now" again and the second tap starts a new run — on a metered
   connection, the very thing the user was trying to stop.
3. After a run that stopped to ask about deletions, double-tap "Keep them and upload": the two question rows collapse
   as the run starts and the row under them slides up into the finger's place.
4. After a failed connection attempt (the reason is shown above "Connect to Dropbox"), double-click "Connect to
   Dropbox" on the desktop or the web: the stage changes to "Waiting for the browser…" with "Cancel" in the second row,
   which is where "Connect" was, and the second click cancels the attempt that just opened the browser (on the web
   the consent page then comes back to a connection that was given up on).

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SyncSettings.kt:208-222`
draws the two opposite actions in the same slot, swapped by the state the first tap produces:

```kotlin
if (syncState.isSyncing) {
    ActionListItem(
        title = stringResource(Res.string.settings_sync_cancel),
        ...
        onClick = viewModel::cancelSynchronization,
    )
} else {
    ActionListItem(
        title = stringResource(Res.string.settings_sync_now),
        ...
        onClick = { viewModel.synchronizeLibrary() },
    )
}
```

`isSyncing` is `progress != null` (`SyncState.kt:63`), and `SyncRepositoryImpl.runSynchronization` sets the progress
as the first thing the run does (`SyncRepositoryImpl.kt:313`), within a frame of the tap, so the second tap of a double
tap already lands on "Stop syncing". Going the other way, `cancelSynchronization` (`:296-299`) cancels the job at once
but the progress is only cleared after `finishRunCutShort` has written the index (`:380-382`, `:391`), so the row
turns back into "Sync now" a moment after the tap — just when a second tap arrives.

Above that slot, `SyncSettings.kt:193-207` puts the progress row and the deletion question rows, both
`AnimatedSettingsRow`s that expand and collapse as a run starts: the progress row expanding above the slot pushes it
down under the finger, and the question rows collapsing pull the row below them up.

The connecting stage has the same shape: `DisconnectedSyncSettings` (`:140-164`) draws the failure reason above the
Connect row, and `ConnectingSyncSettings` (`:168-181`) draws "Waiting for the browser…" above Cancel, so after a
failure the Cancel row appears where Connect was. (`connectSyncProvider` ignores a second Connect while one is running,
`CampfireViewModel.kt` `connectSyncProvider`, but Cancel is a different row.)

## Fix
Give every action a place of its own that the actions next to it never move into, instead of guarding by time.

1. `ConnectedSyncSettings` (`SyncSettings.kt:184-229`), new order:
   1. the account row (unchanged);
   2. the deletion question rows (unchanged, moved up above the button);
   3. "Sync now", always drawn, disabled while a run is going:
      ```kotlin
      // Never swapped for "Stop syncing" in its own place: the second tap of a double tap lands on whatever the first
      // one put under the finger, and a run was stopped the moment it started, or started again the moment it stopped.
      ActionListItem(
          title = stringResource(Res.string.settings_sync_now),
          icon = painterResource(Res.drawable.ic_sync),
          isEnabled = !syncState.isSyncing,
          isEmphasized = false,
          onClick = { viewModel.synchronizeLibrary() },
      )
      ```
   4. the progress row, now holding "Stop syncing" under its bar:
      ```kotlin
      AnimatedSettingsRow(value = syncState.progress) { progress ->
          Column {
              SyncProgressIndicator(progress = progress)
              ActionListItem(
                  title = stringResource(Res.string.settings_sync_cancel),
                  icon = painterResource(Res.drawable.ic_clear),
                  isEmphasized = false,
                  onClick = viewModel::cancelSynchronization,
              )
          }
      }
      ```
   5. "Disconnect" (unchanged).

   Why this order holds: the rows that come and go when a run starts are the question rows *above* "Sync now" (which
   collapse, pulling the disabled "Sync now" up under a finger that was on them) and the progress row *below* it
   (which expands downwards, away from it). "Stop syncing" sits at the bottom of the progress row, one bar and a line
   of text below "Sync now", so a second tap aimed at "Sync now" lands on the disabled row or on the bar. When a run
   ends, the progress row collapses with "Stop syncing" in it; while it collapses its "Stop syncing" is still
   tappable but `cancelSynchronization` with no run going is a no-op (`syncJob?.cancel()`), and what slides up into
   its place is "Disconnect", which only opens its confirmation dialog.

2. `DisconnectedSyncSettings` (`:136-165`): draw the Connect row first and the failure reason under it (swap the
   `AnimatedSettingsRow` and the `ActionListItem`). Connect is then the first row of its stage whether or not a reason
   is shown, and the first row of the connecting stage is the "Waiting for the browser…" text, which takes no taps: a
   second click passes through to the outgoing Connect row of the cross fade, which `connectSyncProvider` already
   ignores while an attempt runs. Update the KDoc of `SyncStage` (`:122-125`, "the invitation to connect with the reason
   above it") to "under it".

Do **not**:
- debounce the rows by time or delay enabling "Stop syncing";
- keep swapping the two labels in one row and try to guard it with a flag — the row legitimately means the other
  thing a frame later.

## Tests
None (UI is untested).

## Verify
Needs a build with sync (`campfire.dropbox.appKey` in `local.properties`) and a library of a few hundred songs so a run
takes a while.
1. Desktop and Android: double-tap "Sync now" twenty times in a row, waiting for each run to finish. Before: most runs
   end "interrupted". After: every run completes; the second tap lands on the disabled "Sync now".
2. During a run, double-tap "Stop syncing": the run stops and no new one starts; "Sync now" is enabled again once the
   progress row has gone.
3. Empty the Dropbox folder to get the deletions question; double-tap "Keep them and upload": one run, not stopped.
   Same for "Delete them here too" (on a throwaway library).
4. Disconnect, cut the network, press Connect to get a failure reason; restore the network and double-click Connect
   on the desktop and on the web: the browser/consent page opens and the attempt is not cancelled.
5. The rows still expand and collapse smoothly; the progress bar and the "N of M" text look as before.

## Docs
`presentation/CLAUDE.md`, the `ui/screens/settings/SyncSettings.kt` bullet:
- "`SyncState.ConnectionFailed` is drawn as the invitation to connect with the reason above it" → "… with the reason
  under it";
- after "an invitation to connect, or the account with what the last run did and the two things one can do about it."
  add: "No action ever takes the place of another as the state it starts changes: "Sync now" stays where it is and is
  disabled during a run, "Stop syncing" is part of the progress row under it, and the reason a connection failed is
  under the Connect row rather than above it — a double tap otherwise landed its second half on the opposite action."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SyncSettings.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 38 edits `statusText()` in the same file (not the rows above); schedule the two one after another.
