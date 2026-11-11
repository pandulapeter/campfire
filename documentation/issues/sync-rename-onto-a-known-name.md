<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Keep a song renamed back onto a name the index knows when another device deleted that name

**Kind:** data loss in sync  ·  **Severity:** medium (rare path, whole song gone)  ·  **Platforms:** all
**Files:** `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/run/SyncPlanner.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/SyncSimulation.kt`
(`rename`, which leaves this case out of the "saved version survives" invariant today)

## Problem

A rename reaches sync as a deletion of the old name and a new file under the new one, and `SyncPlanner` knows no moves.
The new file only reads as new where the index has no entry for its name. When the index already has an entry for the
target name holding the very same bytes, the renamed file reads as that file, unchanged since the last run, so a
deletion of that name on another device wins (`DeleteLocal`), while the rename's other half deletes the old name from
the folder (`DeleteRemote`). The song is then gone from every device, though nobody deleted it: one device deleted a
name it saw twice, the other renamed the song onto it.

The index holds both names with the same bytes after a run that uploaded a rename's new name but was cut off before
its deletion of the old one (offline, stopped, refused), or wherever the library held one song twice.

## Reproduction

Found by `SyncSimulationTest` (seed 667 at 60 steps, before the simulation left the case out). Deterministically, with
two devices in step on `song_1.cho` = P and a `FakeSyncProvider`:

1. A renames `song_1.cho` to `song_one.cho`; A's run uploads `song_one.cho`, and the deletion of `song_1.cho` is refused
   (`cloud.refusedDeletions`). A's index now has both names, at P.
2. B syncs (gets `song_one.cho`), deletes the duplicate `song_1.cho`, and syncs: the folder no longer has `song_1.cho`.
3. A renames `song_one.cho` back to `song_1.cho` and syncs: `song_1.cho` is deleted here (unchanged, gone there) and
   `song_one.cho` is deleted there (gone here, unchanged there).
4. B syncs and deletes `song_one.cho`. P is on no device and not in the folder.

## Options

1. **A pass never deletes a version on both sides.** In `SyncPlanner.plan`, turn a `DeleteLocal` whose local hash is the
   index hash of a key the same plan deletes remotely into `Upload(expectedRevision = null)`. Pure, a few lines, tested in
   `SyncPlannerTest`; matches the planner's own rule that a song that reappears is an annoyance and one that vanishes is
   lost work. Costs a deleted duplicate coming back where two devices each deleted one of two identical files.
2. **Detect moves in the planner**: pair a local file with no index entry to an index entry with no local file by hash.
   Larger, and the case here is not even a file with no index entry.
3. **Leave it**: document that a rename back onto a name another device deleted meanwhile follows that deletion.

## Recommendation

Option 1, with a `SyncPlannerTest` case for the plan and a `TwoDeviceSyncTest` case for the steps above; then drop the
`hasSeen` exception from `SyncSimulation.rename` (and `Device.hasSeen` with it) so the simulation checks renames whole.
