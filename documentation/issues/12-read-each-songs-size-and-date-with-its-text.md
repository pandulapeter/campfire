# Read each song's size and date together with its text, instead of a metadata pass over the whole library before the first batch

**Kind:** performance (startup, rescans)  ·  **Severity:** low-medium  ·  **Platforms:** desktop, Android, web (iOS unchanged)
**Lane:** S  ·  **Files:**
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.kt`,
`data/source/local/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`,
`data/source/local/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt` (kept identical to the desktop copy),
`data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt`,
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SongLocalSourceImpl.kt`,
`data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorageTest.kt`,
`data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/LibraryListingTest.kt`,
`data/source/local/implementation/CLAUDE.md`

Land before plan 13 (same function).

**Challenged:** amended — the web `readScan` now spells out what `readFileTexts` already does and the sketch left out (the `isValidFileName` filter, `withContext(Dispatchers.Default)` + `failingAsStorage` with a whole-batch failure answered per entry), and answers a name that is a directory (`TypeMismatchError`, since `listEntryNames` lists `keys()` of every kind) as `Missing`, matching the JVM override and today's `listEntries`, which only lists files.

## Problem

`SongLocalSourceImpl.loadSongs` (`SongLocalSourceImpl.kt:63-77` at 491c4254a) starts with

```kotlin
val batches = fileStorage.list(StorageDirectory.SONGS).filter { LibraryFiles.isSongFileName(it.name) }.chunked(BATCH_SIZE)
```

and `list` answers name, size and modification time for **every** file before the first batch is read:

- JVM (`JvmFileStorage.kt:57-87`, both copies): a directory stream, then `Files.readAttributes(path,
  BasicFileAttributes::class.java)` per entry, **serially on one IO thread**. Then each batch's `readText` opens the
  file again. On a low-end Windows machine (NTFS plus Defender's on-access hooks) a stat is roughly 50–200 µs, so
  2,000 songs wait an estimated 100–400 ms before the first song is parsed; Android flash is cheaper (~40–100 ms).
- Web (`FileStorage.wasmJs.kt:265-279`, `listEntries`): `getFile()` on every entry (in parallel since the earlier
  plan 34), and then `readFileTexts` (:307-322) does `getFileHandle(name)` **and `getFile()` again** for every file.
  Every song costs two `getFile()` round trips to the browser's storage, and the first batch waits for all 2,000 of
  the first ones.
- iOS (`FileStorage.ios.kt:79-92`) prefetches the attributes with the enumeration in one call; it stays as it is.

What size and date are used for (`SongMappers.kt:28-53`, `StoredFileInfo.toSong`): `Song.size` (the library size in
Settings) and `Song.lastModified`. Neither decides the order (the list is sorted by name, then by the songs' own
metadata downstream) or sync (content hashes). The size also gates the read: `readSongs` (:149-155) skips a file over
`ImportLimits.MAX_TEXT_FILE_SIZE` **without reading it**, so a stray huge file is never loaded into memory. That
must stay.

The earlier performance review's plan 34 ("one-pass web scan", landed) made `listEntries` parallel and added the
batched `readTexts`; it left the second `getFile()` and the up-front stat pass. It was not rejected — this is the
part it did not cover.

## Fix

Let a scan list names only and get each file's size and date from the same per-file operation that reads it, inside
the batch. iOS keeps its prefetching listing through the default implementation.

1. **`FileStorage.kt`** — add, next to `BatchRead`:

   ```kotlin
   /** A file a library scan is going to read, and what the listing already knew about it, where it asked. */
   data class ScanEntry(val name: String, val info: StoredFileInfo?)

   /** What [FileStorage.readScan] answers about one file. */
   sealed interface ScannedFile {
       data class Text(val info: StoredFileInfo, val text: String) : ScannedFile
       /** There, and larger than the scan reads, so never read. */
       data class TooLarge(val info: StoredFileInfo) : ScannedFile
       data object Missing : ScannedFile
       class Failed(val cause: Exception) : ScannedFile
   }
   ```

   and two members with defaults that are today's behaviour:

   ```kotlin
   /** The files a scan reads, in [list]'s order and with its filtering. Where the platform can say more without
    *  asking each file, the entries carry it; otherwise [readScan] asks each file as it reads it. */
   suspend fun listForScan(directory: StorageDirectory): List<ScanEntry> = list(directory).map { ScanEntry(it.name, it) }

   /** One answer per entry, in order; a file over [maxSize] is reported, never read; a failure is its own answer. */
   suspend fun readScan(directory: StorageDirectory, entries: List<ScanEntry>, maxSize: Long): List<ScannedFile> =
       coroutineScope { entries.map { entry -> async { /* info = entry.info ?: info(directory, entry.name) ?: Missing;
           size > maxSize -> TooLarge(info); else readText(...)?.let { Text(info, it) } ?: Missing;
           CancellationException rethrown, any other Exception -> Failed */ } }.awaitAll() }
   ```

2. **`JvmFileStorage` (both copies, identical)** — override both:
   - `listForScan`: the directory stream of `list` with the same exception mapping (`NoSuchFileException` → empty,
     other `IOException` → `LibraryStorageException`), the same `TEMPORARY_FILE_SUFFIX` filter, `toLibraryName()` and
     `sortedBy { name }`, and `info = null`. No per-entry stat.
   - `readScan`: `withContext(Dispatchers.IO)`, one `async` per entry (the batch of 64 bounds the concurrency): for
     `file(directory, name).toPath()`, `Files.readAttributes(…, BasicFileAttributes::class.java)`
     (`NoSuchFileException` → `Missing`; another `IOException` → `Failed(LibraryStorageException(...))`); not a regular
     file → `Missing` (what `list` leaving it out amounts to); `size() > maxSize` → `TooLarge`; otherwise
     `readIfFile(name)?.decodeLibraryText()` → `Text(StoredFileInfo(name, attributes.size(),
     attributes.lastModifiedTime().toMillis()), text)`, null → `Missing`.
3. **`OpfsFileStorage` (web)** — override both:
   - `listForScan`: `listNames(directory)` (already `listEntryNames`, which filters the temporary suffixes and opens
     nothing), sorted, `info = null`.
   - `readScan`: one `js()` call per batch shaped like `readFileTexts`, but per name `getFileHandle` → `getFile()`
     once, then: `file.size > maxSize` → `"\u0003" + size + " " + lastModified`; else `arrayBuffer()` and the existing
     decode, answering `"\u0000" + size + " " + lastModified + " " + text` (or `"\u0001" + size + " " +
     lastModified` for the `decodeLibraryText` fallback, which then reads the bytes through `readBytes` as now),
     `null` for `NotFoundError`, `"\u0002" + name + ": " + message` for any other failure. Parse the two numbers off
     the front in Kotlin.
     Wrap it exactly as `readTexts` wraps `readFileTexts`: `withContext(Dispatchers.Default)`, names failing
     `isValidFileName` answered `Failed(IllegalArgumentException(…))` without being sent to the browser, the call inside
     `failingAsStorage(directory.displayName)`, and a rejection of the whole call answered as `Failed` for every entry.
     In the script, a `TypeMismatchError` (the name is a directory: `listEntryNames` lists `directory.keys()`, which
     includes subdirectories, where `listEntries` kept only `kind === 'file'`) resolves to `null` like `NotFoundError`,
     so it is `Missing` — what leaving it out of the listing amounted to — rather than a "Could not read the song" line.
4. **`SongLocalSourceImpl.loadSongs`**: `fileStorage.listForScan(SONGS).filter { LibraryFiles.isSongFileName(it.name)
   }.chunked(BATCH_SIZE)`; `readSongs(batch)` calls `fileStorage.readScan(SONGS, batch, ImportLimits.MAX_TEXT_FILE_SIZE)`
   and maps `Text` → `info.toSong(ChordProParser.summarize(text))` (in the existing `async`), `TooLarge` → the existing
   "Skipped the song … bytes is more than a song file can hold" line and null, `Missing` → null, `Failed` → the existing
   "Could not read the song" line and null. Batching, the doubling `onProgress` and the `Dispatchers.Default` context
   are unchanged. `loadSongFileSizes`, `loadSong` and the setlist scan keep using `list` / `info` (setlists are few).
5. Leave `list`, `readTexts` and `BatchRead` in place: other callers use them.
6. `data/source/local/implementation/CLAUDE.md`, the `FileStorage` bullet: `listForScan` / `readScan` — the song scan
   lists names only and reads each file's size and date with its text, so the first batch waits for 64 files rather
   than a stat of all of them (on the web, one `getFile()` per song instead of two); iOS lists with its attributes
   prefetched and goes through the defaults.

Must not change: the name order of the scan, the oversize skip happening before a read, a file that is there and
cannot be read being skipped with a log line (never reported as missing), and the null-versus-throw contract.

## Tests

In `JvmFileStorageTest.kt`:
- `listForScan` leaves out a `.campfire-….tmp` leftover, answers sorted names with `info == null`, answers empty for
  a missing directory and throws `LibraryStorageException` for an unreadable one (mirror the existing `list` cases).
- `readScan` answers `Text` whose `info` equals what `list` reports for the same file, `TooLarge` (carrying the file's size) for a file
  over a small `maxSize`,
  `Missing` for a name deleted after listing and for a directory with a song's name.
- On Windows-only behaviour (`isWindows = true` constructor parameter, as the existing device-name test does): a
  `_con.cho` on disk is listed as `con.cho` and read back.

In `LibraryListingTest.kt` (or wherever `SongLocalSourceImpl.loadSongs` is exercised over `JvmFileStorage`): an
oversize song is skipped, the rest load with the sizes and dates `list` reports.

The web override has no unit test (OPFS is browser-only); check it by hand as below, including a folder created
under `library/songs` with a `.cho` name from DevTools (`(await (await (await navigator.storage.getDirectory()).getDirectoryHandle('library')).getDirectoryHandle('songs')).getDirectoryHandle('x.cho', {create: true})`
— adjust to the actual OPFS layout), which must be skipped silently.

Run `./gradlew :data:source:local:implementation:desktopTest`.

## Manual check

- Web, ~2,000 songs in OPFS: DevTools Performance from reload to a complete list, before/after; the first partial list
  appears sooner.
- Desktop on Windows with ~2,000 songs: time from launch to the first partial list and to the full list.
- Settings → Library still shows the same library size as before the change.
