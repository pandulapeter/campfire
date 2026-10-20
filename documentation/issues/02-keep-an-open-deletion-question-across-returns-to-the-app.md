<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Keep an open deletion question on screen across returns to the app instead of asking it again

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the root `CLAUDE.md`'s Sync invariant ("a run starts on every return to the front") is added to the files and reworded, since (a) makes it false; the test helper takes the seeded index document rather than a whole index source, so `RunCounter` (which counts the runs from the source's `onSaveIndex`) still sees every run, the guard-stopped ones included.
**Files:**
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncRunScheduler.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncRunSchedulerTest.kt`,
`data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SyncRepository.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/CLAUDE.md`,
`CLAUDE.md` (root)

## Problem

When the deletion guard stops a run, `SyncRunner` leaves `SyncOutcome.DeletionsNeedConfirmation` in the state, and
Settings shows the Keep / Delete rows (`SyncSettings.kt`, `AnimatedSettingsRow(value = (syncState.lastOutcome as?
SyncOutcome.DeletionsNeedConfirmation)?.direction)`) and a dot on the Library tab (`SettingsScreen.kt`,
`isSyncAnswerPending`). The watcher deliberately leaves that question alone (`SyncRunner.watchPoint`'s KDoc: an
ending other than a completed run "keeps ... one waiting for an answer about deletions, from being started again by
the feed").

A return to the front does not: `SyncRunScheduler.synchronizeOnReturn` (`SyncRunScheduler.kt:130-134`) only skips
after a stopped run:

```kotlin
fun synchronizeOnReturn() {
    val state = stateHolder.value as? SyncState.Connected ?: return
    if (state.lastOutcome == SyncOutcome.Interrupted) return
    if (startRun(SyncDeletionPolicy.ASK, isAutomatic = true)) scheduledRun.value = null
}
```

So every switch back to the app starts an ASK run, and `startRun` clears the outcome for it
(`stateHolder.updateConnected { it.copy(progress = SyncProgress(), lastOutcome = null) }`, `SyncRunScheduler.kt:186`):
the Keep / Delete rows and the tab's dot animate away, a full listing and rehash run, the guard stops it again, and the
rows animate back. Nothing moves either way (the guard stops the plan before any deletion), so the run only costs the
network, the battery, and a flicker of the very rows the user came back to answer; a user checking another app to
decide sees the question go away under them each time.

## Fix

**Decision:** how a return treats an open question — recommended: (a).

- **(a) A return leaves an open question alone**, as it already leaves a stopped run: add `DeletionsNeedConfirmation`
  to the early return. The question stays until answered, or until the user presses Sync now, edits the library (an
  automatic run from `schedule()` still asks again: a change of the user's own is a reason to look again), or relaunches
  (`lastOutcome` is not persisted, so a launch asks afresh). Nothing is lost by not looking: an ASK run that hits the
  guard moves no deletion and the answer is a policy applied to a plan computed when it is given (`run/CLAUDE.md`: "An
  answer waives its own direction only, for one run"), so a question that went stale meanwhile (the other side put the
  files back) is answered against the current folder, where Keep and Delete then find nothing left to do. This matches
  the root rule's intent ("a run that would delete most of the library on either side stops and asks"): asked once, it
  waits for the answer.
- **(b) Keep the outcome shown while the new run goes**, replacing it only with the run's own. This needs `startRun`
  (and `SyncRunner.run`, which clears it again once it holds the lock) to keep a `DeletionsNeedConfirmation`, and then
  Settings shows Keep / Delete beside a running run's progress; a tap on them then calls `synchronize(policy)`, which
  `startRun` refuses while a run is active, so the answer is silently dropped. Fixing that means disabling or queueing
  the answer in the UI, against the Settings rule that no action changes under the finger. More moving parts for a
  re-check that changes nothing in the usual case.

For (a), in `SyncRunScheduler.synchronizeOnReturn`:

```kotlin
// A stopped run is reported rather than repeated, as at a launch, and a question about deletions waits for its answer:
// asking it again would only take its rows off the screen for the length of a run that stops at the same place.
if (state.lastOutcome == SyncOutcome.Interrupted || state.lastOutcome is SyncOutcome.DeletionsNeedConfirmation) return
```

Update the KDoc above it and `SyncRepository.synchronizeOnReturn`'s KDoc ("or after a run that was stopped or cut
short, which is reported rather than repeated, as at a launch") to name the open deletion question too. In
`scheduling/CLAUDE.md`, change "except after `Interrupted`, which is reported rather than repeated, as at a launch" to
also except an open deletion question, which waits for its answer (Sync now and a library change still ask again).
In the root `CLAUDE.md`, Feature invariants, **Sync**: "a run starts on every return to the front" becomes "a run
starts on every return to the front, unless the last one was stopped or is waiting for an answer about deletions"
(the `Interrupted` exception was already missing there).

For (b) instead, drop the early-return change, keep `DeletionsNeedConfirmation` in `startRun` and `SyncRunner.run`'s
`lastOutcome = null` resets, and disable the four answer rows in `SyncSettings.kt` while `syncState.isSyncing`, with
a test that an answer given during a run is not lost; `presentation/.../ui/screens/settings/CLAUDE.md` then needs a
line on it.

## Tests

In `SyncRunSchedulerTest`, beside `` `returning to the app after a stopped run starts nothing` ``, add
`` `returning to the app leaves an open deletion question alone` ``: a world whose folder holds ten songs and whose
index (via a `FakeSyncIndexLocalSource` seeded as in `SyncRepositoryImplTest`'s
`` `a run that would empty the cloud folder says so in its outcome` ``) knows all ten while the local library is empty,
so the first `synchronize(SyncDeletionPolicy.ASK)` ends in `DeletionsNeedConfirmation`; then
`synchronizeOnReturn()`, `advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)`, and assert the outcome is still that
question and `world.runs.count` is unchanged (today it grows by one: `RunCounter` counts a run by the marker its
index save writes, which a guard-stopped run writes too). `world(...)` needs a parameter for the index document the
source starts with (`index: String? = null`, passed to `FakeSyncIndexLocalSource(index = index, onSaveIndex =
runs::onSaveIndex)`, so the counter keeps seeing every run; defaulted parameters are fine in a test helper). Run `./gradlew :data:sync:implementation:desktopTest`.
