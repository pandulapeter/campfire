# Read the next batch of songs while the current one is being parsed

**Kind:** performance (startup, rescans)  ·  **Severity:** low-medium  ·  **Platforms:** all
**Lane:** S  ·  **Files:**
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SongLocalSourceImpl.kt`,
`data/source/local/implementation/CLAUDE.md` (only if it describes the batch loop)

Land after plan 12, which changes the same function; the fix is written so it applies whichever of the two read calls
(`readTexts` today, `readScan` after plan 12) is in place.

## Problem

`SongLocalSourceImpl.loadSongs` (`SongLocalSourceImpl.kt:63-77` at 491c4254a):

```kotlin
batches.forEachIndexed { index, batch ->
    songs += readSongs(batch)
    …
}
```

and `readSongs` (:149-171) first awaits `fileStorage.readTexts(StorageDirectory.SONGS, …)` for all 64 files, then
starts the 64 `ChordProParser.summarize` parses and awaits them all. Only then does the next batch's read start. So
the storage sits idle while the CPU parses, and the CPU sits idle (apart from decoding) while the storage reads:
reads and parses never overlap across batches.

For 2,000 songs on a 4-core Cortex-A53 phone that is ~32 batches × (10–15 ms of reading + 15–30 ms of parsing),
estimated; overlapping them hides most of the shorter phase, saving roughly 0.3–0.6 s to a complete list. On the web
the reads are browser promises and the parse runs on the one thread, so the overlap is as large there. The first frame
is not affected (it shows the first partial batch either way); the time to a complete, sorted list and every rescan
are.

## Fix

Split `readSongs` into the read and the parse, and start batch *n + 1*'s read before parsing batch *n*:

```kotlin
override suspend fun loadSongs(onProgress: (List<Song>) -> Unit): List<Song> = withContext(Dispatchers.Default) {
    coroutineScope {
        val songs = mutableListOf<Song>()
        val batches = … // unchanged
        var publishedCount = 0
        // One batch read ahead: the storage reads the next files while these are parsed, rather than the two taking turns.
        var nextRead = batches.firstOrNull()?.let { batch -> async { readBatch(batch) } }
        batches.forEachIndexed { index, batch ->
            val read = checkNotNull(nextRead).await()
            nextRead = batches.getOrNull(index + 1)?.let { next -> async { readBatch(next) } }
            songs += parseBatch(batch, read)
            if (index < batches.lastIndex && songs.size >= publishedCount * 2) { … unchanged … }
        }
        songs
    }
}
```

- `readBatch` is today's oversize filter plus the `readTexts` call (after plan 12: the `readScan` call), returning the
  readable files paired with their answers; `parseBatch` is today's `async { … ChordProParser.summarize … }.awaitAll()`
  with the same per-file `try`/`catch` and log lines.
- At most two batches (128 texts) are held at once, and at most 128 reads are outstanding only for the instant the
  next read starts while the previous parse runs — the concurrency bound of 64 reads per batch still holds per batch.
  Keep the KDoc's explanation of the bound accurate: "at most one batch being read while the previous one is parsed".
- Cancellation: both `async`s are children of the `coroutineScope`, so a cancelled scan cancels the read ahead too
  (`BaseLocalDataRepository.readOnce` relies on a cancelled read throwing `CancellationException`).
- The doubling `onProgress` and "the last batch is not published" rule are unchanged.

## Tests

None new required: ordering and content of the result are unchanged, and the existing
`:data:source:local:implementation:desktopTest` suites that load a library through `SongLocalSourceImpl` (e.g.
`LibraryListingTest`) must still pass: `./gradlew :data:source:local:implementation:desktopTest`. If a test there
asserts the `onProgress` sequence, it should pass unchanged; add one that loads more than two batches (e.g. 150 songs)
and checks the result equals a one-at-a-time read if none exists.

## Manual check

Android low-end phone (or the 360×640 emulator with CPU throttled) and the web build, with ~2,000 songs: time from
launch to the complete list (the fast scroller's last section present), before and after; the partial list still
appears and grows as before.
