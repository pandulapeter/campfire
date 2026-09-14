# 15 · `readLocalStates()` opens every library file at once

**Severity:** low (memory spike, OPFS handle pressure) · **Area:** `:data:repository:implementation` (`SyncEngine`)

## Cause

`SyncEngine.readLocalStates` (:103–114) launches one `async` per file with no bound, while `SongLocalSourceImpl.loadSongs`
deliberately batches the same directory 64 at a time. On the web (OPFS) and iOS that is thousands of simultaneous
reads holding all bytes at once.

## Fix

Bound it the same way: `libraryFileLocalSource.loadLibraryFiles().chunked(READ_BATCH_SIZE).flatMap { batch -> batch.map { async { … } }.awaitAll() }.filterNotNull()`
with `READ_BATCH_SIZE = 64`, and a one-line comment pointing at `SongLocalSourceImpl` for the reason. Hashing is
CPU work on `Dispatchers.Default` (the repository's scope) — fine.
