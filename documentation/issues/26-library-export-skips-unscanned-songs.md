# 26 — A library export leaves out readable songs the scan has not seen, and calls them unexportable

**Severity:** incomplete backup with a misleading report (all platforms, narrow window) · **Area:** `:domain:implementation` (`ExportLibraryUseCaseImpl.kt`), `:data:repository` / `:data:source:local` (`loadSongFileNames`)

**Read, not run.** This was found by reading the library export at HEAD (2065e47f); it has not been reproduced in a
running build. The "Verification" section below is how to confirm it, and confirming it is the first step of the
work.

## What the user sees

"Export library" builds the archive from the **scan** of the songs folder and then compares it with the folder
listing; every song file in the folder that is not in the scan is reported after the save as "could not be exported".
But a file can be missing from the scan and perfectly readable:

- songs a sync run has downloaded and not yet rescanned for (the live rescan runs at intervals during a run, the full
  one at its end);
- a file put into the desktop library folder by hand since the app last read it (nothing watches the folder);
- a file whose read failed transiently during the scan (a Windows antivirus or sync client holding it open).

The user is told those songs could not be exported, and the archive — which is the backup, and on the web the only
copy anywhere else — does not hold them, although reading them now would have worked.

## Cause

`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportLibraryUseCaseImpl.kt:39-57`:

```kotlin
        val songs = songRepository.loadSongsIfNeeded() ?: return null
        val setlists = setlistRepository.loadSetlistsIfNeeded() ?: return null
        val skipped = mutableListOf<String>()
        val exportedSongFileNames = mutableSetOf<String>()
        val files = buildMap {
            songs.forEach { song ->
                // Not cached: this walks the whole library, and keeping all of it in memory afterwards is no use.
                val content = songContentRepository.loadSongContent(song.fileName, shouldCache = false)
                if (content != null) {
                    put("$SONGS_DIRECTORY/${song.fileName}", content.text.encodeToByteArray())
                    exportedSongFileNames += song.fileName
                }
            }
            ...
        }
        skipped.addAll(0, songRepository.loadSongFileNames().filter { it !in exportedSongFileNames }.sorted())
```

Only the scanned songs are ever read. The folder listing is used only to name what is missing, so a file that is in
the folder and not in the scan is reported whatever the reason it is not in the scan. The class KDoc (`:28-35`) says
the listing is there for "a song the scan skipped — unreadable, or too large to be a song"; it is also every song
written since the scan.

It cannot simply read every listed file: the scan skips files over `ImportLimits.MAX_TEXT_FILE_SIZE` (8 MiB,
`SongLocalSourceImpl.readSong`, `data/source/local/implementation/.../source/SongLocalSourceImpl.kt:118-122`) because
reading one whole "is what would take the app down", and `SongLocalSource.loadSongContent` (`:79`) has no such guard —
reading an unscanned file blindly would reintroduce exactly that. The listing already knows each file's size
(`FileStorage.list` returns `StoredFileInfo` with `size`), but `loadSongFileNames` (`:73`) throws it away.

## The change

Invoke the **`code-style`** skill before the first edit.

Hand the export the sizes with the names, read every listed file that is within the limit (the scanned ones and the
unscanned ones alike), and report only what really could not be read or is too large.

### `SongLocalSource` / `SongRepository`: names with sizes

`loadSongFileNames` has one caller, the library export (checked: `grep -rn loadSongFileNames` finds only it, its
repository pass-through and test fakes). Replace it rather than add a second method.

`data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/SongLocalSource.kt:27-32`:

```kotlin
    /**
     * Every song file in the library folder with its size in bytes, whether [loadSongs] could read it or not, without
     * opening any of them. What an export works from, since the songs a scan skipped, and the ones written since it
     * ran, are exactly the ones that would otherwise be left out of it without a word - and the size is what tells one
     * that can be read from one too large to be a song, which is not opened at all.
     */
    suspend fun loadSongFileSizes(): Map<String, Long>
```

`SongLocalSourceImpl.kt:73`:

```kotlin
    override suspend fun loadSongFileSizes() = fileStorage.list(StorageDirectory.SONGS)
        .filter { LibraryFiles.isSongFileName(it.name) }
        .associate { it.name to it.size }
```

`SongRepository.kt:24-25` and `SongRepositoryImpl.kt:45`: the same rename, `suspend fun loadSongFileSizes(): Map<String, Long>`,
KDoc "See `SongLocalSource.loadSongFileSizes`. Never cached."

Where a platform's listing cannot tell a size (`StoredFileInfo.size` is always filled; OPFS answers it from the
`File` it opens anyway), nothing changes.

### `ExportLibraryUseCaseImpl.kt`

```kotlin
    /**
     * The archive mirrors the library's own layout, so that unpacking it into the library folder by hand is just as
     * good an import as the app's own.
     *
     * The songs are taken from the folder rather than from the scan: a song written since the scan (by a sync run
     * that has not rescanned yet, or put into the folder by hand) or one it could not read then is not in the song
     * list, and the folder is the only place that says it exists. Every one of them that is not too large to be a song
     * is read, so the archive holds every song file there is, and names the ones it could not read or would not open.
     */
    override suspend operator fun invoke(): ExportLibraryUseCase.Result? {
        // A scan that failed is not an empty library. Exporting what it managed to read would hand the user an archive
        // they will file away as a backup and find out about years later.
        songRepository.loadSongsIfNeeded() ?: return null
        val setlists = setlistRepository.loadSetlistsIfNeeded() ?: return null
        val songFileSizes = songRepository.loadSongFileSizes()
        val skipped = mutableListOf<String>()
        val files = buildMap {
            songFileSizes.keys.sorted().forEach { fileName ->
                // Not opened at all when it is larger than a song can be, which the scan skips for the same reason.
                val content = if (songFileSizes.getValue(fileName) > ImportLimits.MAX_TEXT_FILE_SIZE) {
                    null
                } else {
                    // Not cached: this walks the whole library, and keeping all of it in memory afterwards is no use.
                    songContentRepository.loadSongContent(fileName, shouldCache = false)
                }
                if (content == null) skipped += fileName else put("$SONGS_DIRECTORY/$fileName", content.text.encodeToByteArray())
            }
            setlists.forEach { setlist ->
                val document = setlistRepository.loadSetlistDocument(setlist.fileName)
                if (document == null) skipped += setlist.fileName else put("$SETLISTS_DIRECTORY/${setlist.fileName}", document.encodeToByteArray())
            }
        }
        if (files.isEmpty()) return null
        return ExportLibraryUseCase.Result(
            file = ExportedFile(name = ARCHIVE_NAME, mimeType = ExportedFile.ZIP_MIME_TYPE, bytes = archiveRepository.pack(files)),
            skippedFileNames = skipped,
        )
    }
```

Notes:

- The scan is still required to have succeeded (`?: return null`) — that guard is about a library that could not be
  read at all, and stays. Its *list* is no longer what the songs are taken from; a scanned song that has since been
  deleted is no longer listed and so is not reported, which is also what happens today (it is neither exported nor in
  the listing).
- The order of `skipped` stays "songs first, then setlists", as today's `addAll(0, …)` produced, and the songs are
  sorted (as today) because the walk is over the sorted names.
- `loadSongContent` returns null for a file that vanished or could not be read (`SongContentRepositoryImpl` catches,
  `data/repository/implementation/.../SongContentRepositoryImpl.kt:48-55`), which lands in `skipped` — correct: it
  really could not be exported.
- A file listed within the limit that has grown past it by the time it is read is read whole — the same window every
  read of a song file has; not worth a second size check.

## Tests

`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportLibraryUseCaseImplTest.kt`
— its `FakeSongRepository` takes `folder: List<String>`; make it `folder: Map<String, Long>` (default: every scanned
song at size 10) and implement `loadSongFileSizes() = folder`.

1. Existing `names a song that is in the folder but not in the scan` becomes
   `names a song in the folder that is too large to be a song`: `folder = a, b, c at 10 and huge.cho at
   MAX_TEXT_FILE_SIZE + 1` → `skippedFileNames == ["huge.cho"]`, and the fake content repository was never asked for
   `huge.cho` (record the reads).
2. New `exports a song in the folder that the scan has not seen yet`: scan `[a, b]`, folder `a, b, new.cho` all small →
   archive holds `songs/new.cho`, `skippedFileNames` empty.
3. New `names a song in the folder it could not read`: scan `[a]`, folder `a, b`, `b.cho` unreadable → skipped `[b.cho]`.
4. The other existing tests stay as they are (scan failures still return null; the setlist and layout tests).

`:data:source:local:implementation` — one test that the listing reports sizes, in the desktop tests next to
`LibraryListingTest.kt` (it already builds a `JvmFileStorage` over a temporary folder): write `a.cho` (3 bytes) and
`notes.txt`, `SongLocalSourceImpl(storage).loadSongFileSizes() == mapOf("a.cho" to 3L)` (the `.txt` is not a song
file name — check `LibraryFiles.isSongFileName` before relying on that; use a clearly foreign extension if `.txt` is
accepted).

Test fakes implementing `SongLocalSource` / `SongRepository` that have `loadSongFileNames` get the renamed method
(`throw UnsupportedOperationException()` where they had it): `SongRepositoryImplTest.kt:156`,
`sync/LibrarySongLocalSource.kt:28`, `sync/FakeSyncCollaborators.kt:125` (all in
`data/repository/implementation/src/commonTest/...`), `GetScreenDataUseCaseImplTest.kt:186`.

Run the root unit test command.

## Verification

Desktop:

1. Start the app with a few songs. With it running, copy a new `.cho` file into the library's `songs/` folder (do not
   refresh). Also copy in a 9 MB file named `big.cho`.
2. Settings → export the library.
   - **Before:** the message after the save names the new song and `big.cho` as not exported; the zip lacks both.
   - **After:** the message names only `big.cho`; the zip holds the new song.
3. Unzip the archive: `songs/` holds every song file of the folder but `big.cho`.

## Docs

- `domain/implementation/CLAUDE.md`, the export bullet (line ~76): "one that could not read some files returns their
  names beside the archive, the songs held against the folder (`SongRepository.loadSongFileNames`) rather than the
  scan, so that a song the scan skipped is named too" becomes "the songs are taken from the folder
  (`SongRepository.loadSongFileSizes`) rather than from the scan, so that a song written since the scan is in the
  archive and one the scan skipped is read again; what cannot be read, or is larger than a song can be (and so is
  never opened), is named beside the archive".
- `data/source/local/api/CLAUDE.md`, the `SongLocalSource` bullet (line 15): if it names `loadSongFileNames`, rename it
  (grep before writing).

## Files touched

- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/SongLocalSource.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SongLocalSourceImpl.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/LibraryListingTest.kt`
- `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SongRepository.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongRepositoryImplTest.kt`, `sync/LibrarySongLocalSource.kt`, `sync/FakeSyncCollaborators.kt` (fakes)
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportLibraryUseCaseImpl.kt`
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportLibraryUseCaseImplTest.kt`, `GetScreenDataUseCaseImplTest.kt` (fakes)
- `domain/implementation/CLAUDE.md`, possibly `data/source/local/api/CLAUDE.md`

## Depends on

- Lane B plans that touch `SongLocalSourceImpl.kt` (10 — a file deleted mid-read, 11 — storage failures reported as
  unknown, 14 — APFS decomposed names on rename) edit other functions of it; merge. `FakeSyncCollaborators.kt` and
  `LibrarySongLocalSource.kt` are sync test fakes lane B also edits; the change here is one renamed method.
- Plan 27 edits `SongLocalSourceImpl.renameSong` and plan 23 adds a method to the same fakes' `SetlistRepository`
  siblings; merge.
