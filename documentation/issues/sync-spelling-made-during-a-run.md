<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Notice a file made under another spelling of a name while a run downloads that name

**Kind:** sync stuck on one file  ·  **Severity:** low (nothing lost, needs a rename by hand)  ·  **Platforms:** Android,
Linux, and the web (file systems that tell case or Unicode form apart)
**Files:** `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/run/SyncEngine.kt`
(`download`), `ConflictResolver.kt` (`takeRemote`),
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/SyncSimulation.kt`
(`newName`, `rename`, which keep the running device from it today)

## Problem

Names are matched ignoring case and Unicode form when a run lists both sides, but once planned, a download decides by
the exact name the plan gave it: before the request and again just before the write, it reads only that name. A file
made, or renamed by hand, during the run under another spelling of the same name (`hallelujah.cho` while the run is
downloading the folder's `Hallelujah.cho`) is not seen, and the download writes the folder's version next to it. On a
file system that tells the spellings apart the library then holds two files the service takes for one: the next run
uploads one of them, the other is refused as a case collision and reported as a file that could not be synced on every
run, until the user renames one. On macOS, Windows, and iOS the two names are one file, the second read sees the save,
and it becomes a conflict as intended.

The app's own names are normalized (NFC, lowercase), so the app does not make such a file itself; it takes a file
named by hand in the library folder, an import of another app's file kept under its name, or a rename to a spelling
another device uses, made while a run is going.

## Reproduction

Found by `SyncSimulationTest` (seeds 273 and 7385 at 60 steps, before the simulation kept the running device from it).
Deterministically: device C syncs `Hallelujah.cho` into a `FakeSyncProvider`; device D, with an empty library, syncs
with `cloud.onDownload = { d.library.files[song("hallelujah")] = HERE }`. D ends with both `Hallelujah.cho` and
`hallelujah.cho`.

## Options

1. **Ask the folded name under the lock.** `LibraryFileLocalSource` gains a call that reads a name under any spelling
   (the storage already folds in `uniqueName` and `numberedVariantHolding`), and `download` and `takeRemote` use it for
   their check before the write, resolving a file found under another spelling as the conflict it is. One listing per
   downloaded file, which is the cost the `FileStorage` notes warn about on OPFS.
2. **Fold once per pass instead**: before a pass's downloads, list the library again and drop or re-plan any download
   whose name now folds onto another local file. Cheaper, but leaves the window between that listing and the write.
3. **Leave it**: the failure is reported, nothing is lost, and the user's rename settles it.

## Recommendation

Option 3 for now, given that nothing is lost and the app never makes such a name; revisit with option 1 if the
"could not sync" report is ever seen for a case pair. Either way the simulation's restriction stays documented where
it is.
