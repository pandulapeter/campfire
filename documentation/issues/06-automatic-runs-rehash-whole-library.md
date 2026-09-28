# Stop re-reading and hashing the whole library on every automatic run

> **Rejected by the user on 2026-09-28** — every automatic run stays a full read. Do not execute.

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt, data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt, data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/LibraryChanges.kt, data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt, data/repository/implementation/CLAUDE.md

## Problem
Every run starts with `readLocalStates()` (`SyncEngine.kt:287-303`), which lists the library and reads and SHA-256s
**every** song and setlist file (`readLocalState` → `libraryFileLocalSource.readLibraryFile` + `localContentHash`,
`:305-307`, `Sha256.hashToHex` in pure Kotlin). Before cc4d6f55 that happened at launch and on Sync now. Now it happens
10 s after every change the app makes (`LibraryChanges` → `scheduleSynchronization`), i.e. after a tag toggle, a
transposition step in a setlist (`CampfireViewModel.changeTransposition` → `updateSetlist`), a setlist reorder, an
archive toggle, a cover picked — each one separated by more than 10 s is its own run. For a 2 000-song library that is
2 000 file opens and ~6–10 MB read and hashed per edit: a second or more of I/O on Android, and far more on the web,
where each OPFS read is its own `getFileHandle`/`getFile`/`arrayBuffer` round trip. It competes with the UI's own
reads (song details, the editor) and, on Android, keeps a foreground service and its notification up for the whole
time. A user going through a setlist tagging or transposing songs pays that for every song.

The run only needs fresh hashes for files that may have changed. Within one process, the app itself is what changes
the library, and it already announces every such change; the sync engine reports every file it writes
(`onLocalFileChanged`).

## Fix
1. Make `LibraryChanges` carry *which* files changed: `onLibraryChanged(kind: LibraryFileKind, fileName: String)`
   (and a `onLibraryChanged(kind, names)` overload for imports), emitting into a `MutableSharedFlow<SyncKey>`-like
   event with a large enough buffer, or — simpler and loss-free — keeping a `Mutex`-guarded `MutableSet<SyncKey>` of
   "dirty since the last run" inside `LibraryChanges` plus the existing `Unit` signal. Update every call site in
   `SongRepositoryImpl` / `SetlistRepositoryImpl` (a rename dirties both the old and the new name; a delete the old).
2. In `SyncEngine`, keep an in-memory `hashCache: MutableMap<SyncKey, Pair<Long /*size*/, String /*hash*/>>` filled by
   `readLocalState` and by every local write it makes (download, conflict copy, discard). Add a parameter
   `trustedKeys: ((SyncKey, Long) -> String?)?` or a `readMode` to `synchronize`: in *incremental* mode
   `readLocalStates()` still lists the library (cheap: names and sizes), reuses the cached hash for a file whose key is
   not dirty and whose listed size equals the cached size, and reads/hashes the rest.
3. `SyncRepositoryImpl`: runs started by `synchronize()` (launch, Sync now, connect, the deletion answers) use the full
   read and replace the cache; runs started by the debounce collector use incremental mode. Clear the cache on
   `disconnect`, `forgetStoredConnection` and whenever a `rescan()` of the library happens (the desktop's
   `refreshIfStale` on focus is what notices files edited outside the app).
4. Safety does not depend on the cache: `download` and `deleteLocally` already re-read the file and compare it with the
   *index* hash before writing (`SyncEngine.kt:478-499`, `:535-541`), and `upload` reads fresh bytes. The only effect of
   a stale cached hash (an external same-size edit while the app runs) is that the upload waits for the next full run.
5. `commonTest` in `SyncEngineTest`: an incremental run with an unchanged, non-dirty file does not call
   `readLibraryFile` for it (count reads in `FakeLibraryFileLocalSource`); a dirty file is re-read and uploaded; a file
   whose listed size changed is re-read.
6. Document the two modes in `data/repository/implementation/CLAUDE.md` (the "Automatic runs wait for the library to
   settle" paragraph) and in root `CLAUDE.md`'s Sync bullet about automatic runs.

## Verification
`./gradlew :data:repository:implementation:desktopTest`. Manual (desktop or web, connected, a library of a few hundred
songs): toggle a tag, wait for the automatic run; it should finish in roughly the time of one upload rather than the
time of a launch run (compare with Sync now).

## Conflicts
`SongRepositoryImpl` / `SetlistRepositoryImpl` call sites of `libraryChanges.onLibraryChanged()`; `plan 01` also
touches what an automatic run is (the index marker) — coordinate if both are taken.

## Open decision (only if the fix needs the user's choice)
- **A (recommended):** incremental hashing for automatic runs only, as above; launch and Sync now stay full reads, so
  anything edited outside the app is still caught at the next launch or manual run.
- **B:** leave every run a full read and accept the cost; optionally raise `AUTOMATIC_RUN_DELAY` so that fewer runs
  happen during a tagging session.
