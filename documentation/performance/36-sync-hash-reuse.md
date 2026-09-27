<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

> **Decision:** Rejected by the user on 2026-09-27: not executed; content keeps deciding what changed.
# 36 — Reuse local hashes when a file's metadata is unchanged — DECISION NEEDED

| | |
|---|---|
| Lane | E |
| Impact | medium (every sync run re-reads and re-hashes the whole library) |
| Confidence | medium on the win; the risk is the point of the decision |
| Platforms | all (see the web section) |
| Files | `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`, `…/sync/SyncIndexDocument.kt`, `…/sync/SyncPlanner.kt` (`SyncIndexEntry`, line 51), `data/repository/implementation/src/commonTest/…/sync/SyncEngineTest.kt`, `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFile.kt` (KDoc), `data/repository/implementation/CLAUDE.md`, `CLAUDE.md` (the Sync section) |
| Depends on / conflicts with | Conflicts with 37 (both touch `SyncEngine.kt`). Independent of 35, which removes the start-up overlap on its own. |
| Commit message | `Reuse a file's last hash in a sync run when its size and modification time are what the index recorded.` |

## Problem
- Every pass of every run reads and SHA-256-hashes every library file (`SyncEngine.kt:285-304`, `readLocalStates` / `readLocalState`), even when nothing has changed on either side.
- For about 2000 songs that is 2000 reads plus about 6 MB hashed on every launch while connected, and on every "Sync now".
- On the web it runs on the page's only thread, with the per-byte copy of plan 33 on top.
- `loadLibraryFiles()` already lists `size` and `lastModified` for each file (`LibraryFileLocalSourceImpl.kt:30-35, 57-58`), but nothing uses them.

## The rule this touches
The root `CLAUDE.md` says: "Content decides what changed, never a clock: the platforms disagree about modification times and the web has none." The `LibraryFile` KDoc says the same ("[lastModified] is 0 where the platform cannot say (the web)"). That KDoc is out of date: `OpfsFileStorage.listEntries` (`FileStorage.wasmJs.kt:209`) reports `file.lastModified`.

What this plan proposes:
- The decision stays by content: the planner still compares hashes.
- Only the *computing* of a hash is skipped, when size and modification time are both exactly what they were when that hash was taken.
- A clock never says a file changed. It can only say a file did not.

That direction is the dangerous one: a wrong "did not change" makes the planner treat an edited file as unchanged.
- The edit is not uploaded.
- If the other side changed that file too, the remote version is **downloaded over the local edit with no conflict copy**, because the planner thinks the local side is untouched.

## When the reused hash would be wrong
A file changes content while keeping the same size and the same recorded modification time:

- **Tools that restore timestamps.** Unzipping a backup, `cp -p`, `rsync -t`, a Finder restore or Time Machine, all into the desktop library folder or into the iOS library folder through Files. The size must also match, which for small text edits (a chord changed from `[G]` to `[C]`) is common.
- **Coarse clocks.** FAT or exFAT (2 s or 10 ms) on removable storage or network shares on desktop. The Android and iOS libraries are app-private (ext4/f2fs/APFS, with nanosecond times), so less exposed.
- **The web.**
  - OPFS files are written only by this app (`createWritable` or the `opfs-writer.js` sync access handle).
  - Chromium updates `File.lastModified` when a writable closes.
  - **Not verified:** whether Safari and Firefox update `lastModified` for writes through `createSyncAccessHandle` (the worker path Safari before 26 uses), and at what precision. If one of them keeps a stale value, every edit there would be missed.
  - Outside writers do not exist on the web, so there the risk is only the browser's own behaviour.

## Options
- **A. Do not do it.** Keep hashing everything. Plan 35 already removes the start-up overlap; the cost that remains is one full read per run, off the screen's way.
- **B. Reuse on size + modification time**, only where `lastModified > 0`. The web is included only after each browser engine has been checked, including the worker write path; until then pass `lastModified = 0` on the web so it always hashes.
- **C. B plus a change time where the platform has one.** A change time cannot be set by user tools, so it closes the timestamp-restoring hole:
  - JVM: `Files.getAttribute(path, "unix:ctime")`, where the `unix` view exists. Android supports it only from API 26, and the view there needs checking.
  - iOS: `NSURLAttributeModificationDateKey`.
  - No equivalent on the web.
  - This needs a new field on `StoredFileInfo`/`LibraryFile`, so it is a larger change.
- **D. In-memory only.** Hashes are kept for this process, keyed by (name, size, mtime), and invalidated by the repositories' own writes. It never trusts metadata across launches, so it does not help the launch run, which is the expensive one.

Recommendation: **A or C**. B is the version that can silently overwrite an edit made on a desktop by a timestamp-restoring tool.

## Fix (if B or C is chosen)
1. `SyncIndexDocument.Entry` gains `size: Long = -1` and `lastModified: Long = 0` (and `changedAt: Long = 0` for C). The defaults mean "unknown", so an index written by an older version always hashes.
2. `readLocalStates` takes the `LibraryFile` from the listing, which is metadata observed *before* the read, and reuses `index[key].localHash` when `lastModified > 0 && size == entry.size && lastModified == entry.lastModified` (plus `changedAt` for C). Otherwise it reads and hashes as now.
   - Recording what was seen before the read is safe: a write that lands during the read leaves a newer modification time, so the next run hashes again.
3. Everywhere the engine writes an index entry for a local file (download 489, upload, conflict copy 603), record the metadata of the file as written. Call `info` after the write, or leave the fields unknown so the next run hashes; unknown is the simple, safe choice.
4. Unchanged: the planner, deletion guards, a file that could not be read, too-large files.

## Verification
- Tests: `./gradlew :data:repository:implementation:desktopTest`. `SyncEngineTest` cases:
  - Unchanged metadata skips the read (the fake local source counts reads).
  - A size or modification-time change reads.
  - `lastModified == 0` always reads.
  - An old index without the fields always reads.
- **Manual checks:**
  - Desktop, connected, about 2000 songs: time a no-op "Sync now" before and after.
  - Then the adversarial case: `cp -p` an edited same-size song over the library copy, restoring its old modification time, and sync. With B the edit is missed, which is expected and is exactly the risk above. With C it must upload.
- **Docs:** reword the "Content decides what changed, never a clock" sentence in the root `CLAUDE.md` Sync section and the `LibraryFile` KDoc to match whichever option is chosen, and describe the rule in `data/repository/implementation/CLAUDE.md`.
