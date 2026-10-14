<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Two-device sync scenarios — plan

Written 2026-10-14 against `312cbd101`. One of four test plans; independent of the others.

## Goal

The sync engine, planner, and synced preferences are already tested one run at a time. What no test covers is the
user's actual situation: **two devices taking turns against one cloud folder**, where each run sees what the other
did. Those are the bugs that cost data (an edit lost, a deletion resurrected, a conflict copy loop). A few scenario tests
over the real engine cover that.

Not the goal: a simulation framework, randomized fuzzing, or version-compatibility fixtures (mixed-version sync is
exercised constantly by hand against the production library).

## Approach

In `data/sync/implementation/src/commonTest`, next to the existing fixtures:

- One small helper, `Device`, holding what a device owns: a `FakeLibraryFileLocalSource`, its `SyncIndexDocument`,
  and a `LibraryFileLock`. `device.sync(cloud)` calls the existing `synchronize(...)` fixture and keeps the index it
  returns. The cloud is one `FakeSyncProvider` shared by both devices. That's the whole harness — maybe 30 lines.
- If a scenario needs synced preferences, the device also holds a preferences document and goes through the same
  public entry point the repository uses. Add that only with the first test that needs it.
- Assertions: both devices' libraries and the cloud's files, compared as name → content. Never the planner's internal
  actions.

## Scenarios (one test each)

1. A song created on A appears on B; edited on B, the edit reaches A.
2. A deletion on A removes the song from B; it does not come back after B syncs again.
3. A edits and B deletes the same song in between runs: the edit wins on both devices.
4. Both edit one song differently: one conflict copy, on both devices, and no further copies on later runs.
5. A rename on A (delete + create) arrives on B as one song, not two, after both have synced twice.
6. A song whose name differs only by case or Unicode form on the two devices settles on one file.
7. A setlist edited on both devices in different songs keeps both changes (if the engine merges them) or makes a
   conflict copy (if not) — assert what the docs say it does.
8. Synced preferences: A transposes song X, B sets the capo of song Y; both see both. A removes the transposition, B
   changes nothing: the removal reaches B.

Each scenario ends with one extra round of syncs on both devices, asserting nothing changes: the library has converged.

## Effort

About a day. If a scenario fails, that's a real bug: it gets its own fix commit, not a workaround in the test.

## Docs

One sentence in `data/sync/implementation/CLAUDE.md` saying where the scenario tests are. No inventory.
