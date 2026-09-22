# 06 — Never hand out a half library as a backup

## What the user sees

The user presses **Export library as zip**, the file saves, no message appears, and they file it away as their
backup. It contains their setlists and none of their songs, or their whole library minus the four songs that
happened to be unreadable that day. They find out months later.

Two ways in:

1. **The scan failed.** One unreadable *directory* — the songs folder without read permission on Linux, an OPFS
   error on the web, an iOS file provider that is not responding — makes `loadSongsIfNeeded()` return null. The
   export turns that into an empty list and writes a zip of the setlists. A zip with setlists in it is not empty,
   so the "nothing to export" path never fires, the save succeeds, and the user is told nothing at all.
2. **Individual songs were skipped.** `readSong` deliberately swallows a file it cannot read and one over
   8 MB, with a `println` and nothing else, so those songs are not in `loadSongsIfNeeded()`'s list and not in the
   archive. That is the right behaviour for the song *list* — one bad file must not empty the library — but it
   means the export silently leaves them out too.

On the web this is the case that matters most: OPFS lives in the browser's storage, clearing site data removes it,
and persistence is only granted at the browser's discretion. The root `CLAUDE.md` says so: a refusal is reported in
Settings "next to the export that is the way to keep a copy elsewhere". **On the web, export is the backup.** A
backup that quietly omits part of the library is worse than no backup, because the user stops looking for one.

## Cause

`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportLibraryUseCaseImpl.kt:32-49`,
verified at HEAD `984861e4`:

```kotlin
    override suspend operator fun invoke(): ExportedFile? {
        val files = buildMap {
            songRepository.loadSongsIfNeeded().orEmpty().forEach { song ->
                // Not cached: this walks the whole library, and keeping all of it in memory afterwards is no use.
                songContentRepository.loadSongContent(song.fileName, shouldCache = false)
                    ?.let { put("$SONGS_DIRECTORY/${song.fileName}", it.text.encodeToByteArray()) }
            }
            setlistRepository.loadSetlistsIfNeeded().orEmpty().forEach { setlist ->
                setlistRepository.loadSetlistDocument(setlist.fileName)
                    ?.let { put("$SETLISTS_DIRECTORY/${setlist.fileName}", it.encodeToByteArray()) }
            }
        }
        return if (files.isEmpty()) {
            null
        } else {
            ExportedFile(name = ARCHIVE_NAME, mimeType = ExportedFile.ZIP_MIME_TYPE, bytes = archiveRepository.pack(files))
        }
    }
```

Three swallows in eighteen lines: `.orEmpty()` on line 34, `.orEmpty()` on line 39, and `?.let` on line 37 and
line 41, each of which drops a file whose content could not be read without recording that it did.

`loadSongsIfNeeded()` really does return null on a failed read —
`data/repository/implementation/src/commonMain/.../base/BaseLocalDataRepository.kt:79-81` and the private
`read()`/`readOnce()` below it, which return null and publish `DataState.Failure` when
`loadDataFromLocalSource()` throws.

The skipping is `data/source/local/implementation/src/commonMain/.../source/SongLocalSourceImpl.kt:116-131`:

```kotlin
    private suspend fun StoredFileInfo.readSong(): Song? = try {
        if (size > ImportLimits.MAX_TEXT_FILE_SIZE) {
            println("Skipped the song \"$name\": $size bytes is more than a song file can hold.")
            null
        } else {
            fileStorage.readText(StorageDirectory.SONGS, name)?.let { text ->
                toSong(ChordProParser.summarize(text))
            }
        }
    } catch (exception: CancellationException) {
        // A library scan that was cancelled is not a library of unreadable songs.
        throw exception
    } catch (exception: Exception) {
        println("Could not read the song \"$name\": ${exception.message}")
        null
    }
```

### What the export UI can show today

`presentation/.../ui/CampfireViewModel.kt:1566-1583`:

```kotlin
    private suspend fun save(filePicker: FilePicker, isShare: Boolean = false, export: suspend () -> ExportedFile?) = try {
        val file = export()
        when {
            file == null -> sendMessage(Message.ExportFailed)
            isShare -> filePicker.shareFile(file)
            filePicker.saveFile(file) && file.mimeType == ExportedFile.ZIP_MIME_TYPE && file.bytes.size > ImportLimits.MAX_IMPORT_SIZE ->
                sendMessage(Message.ExportTooLargeToImport)
        }
        Unit
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not export: ${exception.message}")
        sendMessage(Message.ExportFailed)
    }
```

So the machinery is already there: a snackbar queue (`Message`, rendered in `CampfireApp.kt:532-556`), a precedent
for a *warning after a successful save* (`Message.ExportTooLargeToImport`, whose KDoc reads "An archive that was
saved, but that the import would refuse for its size"), and a precedent for naming a count of files that did not
make it (`Message.ImportOversized`, `import_oversized`). Nothing new has to be invented.

## The change

Two rules, and the second is the one that matters:

- **A failed scan is a failed export.** Not a partial archive.
- **A song that could not be read is named, in a message that appears after the save.** Not silently omitted.

### 1. The use case reports what it left out

Give `ExportLibraryUseCase` a result that carries the archive and the names that did not make it, instead of a bare
`ExportedFile?`. `domain/api/.../useCases/ExportLibraryUseCase.kt`:

```kotlin
interface ExportLibraryUseCase {

    /**
     * A zip of every song and setlist. Null when the library is empty **or could not be read**: an archive missing
     * a library it was supposed to contain is worse than no archive, since the user files it away as a backup.
     */
    suspend operator fun invoke(): Result?

    /**
     * [file] plus the names the export could not read, which are left out of it. On the web an export is the only
     * copy of the library there is, so a name that is missing from the archive is told to the user rather than
     * logged.
     */
    data class Result(
        val file: ExportedFile,
        val skippedFileNames: List<String>,
    )
}
```

`ExportLibraryUseCaseImpl`:

```kotlin
    override suspend operator fun invoke(): ExportLibraryUseCase.Result? {
        // A scan that failed is not an empty library. Exporting what it managed to read would hand the user an
        // archive they will file away as a backup and find out about years later.
        val songs = songRepository.loadSongsIfNeeded() ?: return null
        val setlists = setlistRepository.loadSetlistsIfNeeded() ?: return null
        val skipped = mutableListOf<String>()
        val files = buildMap {
            songs.forEach { song ->
                // Not cached: this walks the whole library, and keeping all of it in memory afterwards is no use.
                val content = songContentRepository.loadSongContent(song.fileName, shouldCache = false)
                if (content == null) skipped += song.fileName else put("$SONGS_DIRECTORY/${song.fileName}", content.text.encodeToByteArray())
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

**The songs `readSong` skipped are a separate matter and are handled at the source**, because they never reach
this use case at all — they are not in `loadSongsIfNeeded()`'s list. Two options:

- **(a) Recommended.** Compare the archive against the *directory* rather than against the scan: have the use case
  ask the storage, through a new `SongRepository`/`SongLocalSource` call, for the song file names on disk
  (`fileStorage.listNames`, which already exists and opens nothing) and add to `skippedFileNames` every
  `LibraryFiles.isSongFileName` name that did not go into the archive. This catches both the unreadable file and
  the over-8 MB one without either of them having to be tracked through the scan, and it is the only version that
  is *provably* complete: the archive either holds every song file in the folder or names the ones it does not.
  `listNames` is the cheap call added precisely so that a name can be had without opening a file, so this costs one
  directory listing per export.
- (b) Have `SongLocalSource.loadSongs` report the names it skipped. More plumbing, through a signature the library
  scan uses on every launch, for the same answer.

Take (a).

### 2. The ViewModel says so

`save()` in `CampfireViewModel` is shared by four callers, three of which still hand out a plain `ExportedFile?`
(`exportSong`, `shareSong`, `exportSetlist`). Keep `save()` as it is and add the skipped-name message at the one
call site:

```kotlin
    fun exportLibrary(filePicker: FilePicker) = launchFileTransfer {
        val result = exportLibrary.invoke()
        save(filePicker) { result?.file }
        // After the save rather than instead of it: the archive is a real copy of everything that could be read,
        // and what it is missing is the one thing the user could not otherwise find out.
        result?.skippedFileNames?.takeIf { it.isNotEmpty() }?.let { sendMessage(Message.ExportSkippedFiles(it)) }
    }
```

Check how `save()` sequences against `filePicker.saveFile` on each platform before settling on this shape — on
Android the picker can finish long after the coroutine (`onExportFailed`'s KDoc says so), so the message may want
to be sent before the save rather than after, or `save()` may want an `onSaved` callback. Whichever way, the
message must not be sent when the export returned null, since `Message.ExportFailed` already covers that.

New message, next to `ExportTooLargeToImport` (`CampfireViewModel.kt:2009-2010`):

```kotlin
        /** An archive that was saved without the files it names: they could not be read, so they are not in it. */
        data class ExportSkippedFiles(val fileNames: List<String>) : Message
```

and in `CampfireApp.kt`, next to line 544:

```kotlin
        is CampfireViewModel.Message.ExportSkippedFiles -> pluralTextResource(
            Res.plurals.export_skipped_files,
            current.fileNames.size,
            current.fileNames.size,
            current.fileNames.joinToString(),
        )
```

`pluralTextResource` and not `pluralStringResource`: the sentence takes file names, which are text somebody else
wrote, and the localization plugin's formatter scans its own output a second time. `settings_sync_files_failed` in
`SyncSettings.kt` is the existing example of exactly this pairing — copy its shape, including how it caps the
list of names if it does.

### 3. Strings — both files

`presentation/src/commonMain/composeResources/values/strings.xml`, next to `export_too_large_to_import` (line 32):

```xml
    <plurals name="export_skipped_files">
        <item quantity="one">One file could not be read and is not in the archive: %2$s</item>
        <item quantity="other">%1$d files could not be read and are not in the archive. Among them: %2$s</item>
    </plurals>
```

`values-hu/strings.xml`:

```xml
    <plurals name="export_skipped_files">
        <item quantity="one">Egy fájlt nem sikerült beolvasni, így nincs benne az archívumban: %2$s</item>
        <item quantity="other">%1$d fájlt nem sikerült beolvasni, így nincsenek benne az archívumban. Köztük: %2$s</item>
    </plurals>
```

A counted sentence whose singular reads differently is a `<plurals>` and not a second key — which is why this is a
plural rather than two strings.

## Tests

`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/` — the
module already has `commonTest` (it holds `ImportPlanner` and `GetScreenDataUseCaseImplTest`, whose fakes at
`GetScreenDataUseCaseImplTest.kt:185` show the shape of a `SongRepository` fake). Add
`ExportLibraryUseCaseImplTest.kt`:

1. `exports nothing when the song scan failed` — `loadSongsIfNeeded()` returns null, setlists return two.
   Assert the result is null, so the user gets "Export failed" rather than a zip of setlists. **This is the
   regression test for the reported bug.**
2. `exports nothing when the setlist scan failed` — the mirror.
3. `exports nothing for an empty library` — both return empty lists; assert null, which is the behaviour that
   already exists and must be kept.
4. `names the songs it could not read` — three songs, `loadSongContent` returns null for the middle one. Assert
   the archive holds two entries and `skippedFileNames` is exactly that one file name.
5. `names a song that is in the folder but not in the scan` — the storage lists four song files, the scan returns
   three (the fourth being the one `readSong` skipped for its size). Assert `skippedFileNames` holds the fourth.
   This is the test that pins option (a).
6. `names a setlist it could not read` — the same for `loadSetlistDocument`.
7. `keeps the archive layout` — assert the keys are `songs/…` and `setlists/…`, since the KDoc promises that
   unpacking the archive into the library folder by hand is as good an import as the app's own.

## Verification

```
./gradlew :domain:implementation:desktopTest
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
./gradlew :app:desktop:run
./gradlew :app:android:assembleDebug
./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64
./gradlew :app:web:wasmJsBrowserDistribution
```

Manual:

- Desktop: export a library with a few songs and confirm the zip is unchanged and no message appears.
- Desktop, Linux or macOS: `chmod 000` one song file in `library/songs`, press **Export library as zip**, and
  confirm the archive saves, holds every other song, and a snackbar names the unreadable one. Then `chmod 000` the
  whole `library/songs` directory and confirm the export fails with "Export failed" instead of writing a
  setlists-only zip. `chmod` back afterwards.
- Desktop: drop a 20 MB file named `huge.cho` into `library/songs`, rescan, export. The song list ignores it as it
  does today, and the export must now name it.
- **Needs a device**: repeat the first case on Android and iOS to confirm the message renders and the picker still
  completes. On Android check the case where the picker finishes after the ViewModel is gone.
- Web: repeat in the browser — this is the platform the plan exists for. Confirm the download still starts and the
  snackbar appears over it.
- Switch the app to Hungarian and confirm both plural forms read correctly.

## Docs

`domain/implementation/CLAUDE.md` — whatever sentence describes the export use case now has to say that a failed
scan is a failed export and that the skipped names come back with the archive. Read the file and update the
matching sentence.

`documentation/features.md:44-45` — the user-facing promise:

> - **Export** a single song, a setlist, or the entire library as a zip of plain text files that any other tool can
>   read.

is still true, but the paragraph at line 97 is the one that sells the export as the way out:

> Everything can be exported at any time as a zip any other ChordPro tool can read, so leaving is as easy as
> arriving.

Add, in the sentence's own voice, that an export that could not read part of the library says so rather than
handing over an incomplete copy.

Root `CLAUDE.md`, the Web section — this sentence points the user at the export as the answer to a refused
persistence, and is the reason the export has to be trustworthy:

> a refusal says so there, next to the export that is the way to keep a copy elsewhere.

It stays true; no change needed, but read it before wording the new message so the two agree.

## Files touched

- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/ExportLibraryUseCase.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportLibraryUseCaseImpl.kt`
- `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SongRepository.kt` and
  its impl, plus `SongLocalSource` and `SongLocalSourceImpl`, for the "song file names on disk" call of option (a)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportLibraryUseCaseImplTest.kt` (new)
- `domain/implementation/CLAUDE.md`, `documentation/features.md`

## Depends on

Nothing. It touches `CampfireViewModel` and `CampfireApp`, which other lanes are also editing — rebase rather than
merge those two files.

## Rules

- Load the `code-style` skill before the first edit. A new file needs the MPL-2.0 header.
- Both language files get the new plural, read with the localization `pluralTextResource` (file names are text
  somebody else wrote), never the `org.jetbrains.compose.resources` variant.
- `commonMain` stays JVM-free.
- Keep `Message` a sealed interface of data classes/objects and keep the queue's numbering behaviour intact — two
  identical results in a row are still two messages (`CampfireApp.kt:525-530`).
