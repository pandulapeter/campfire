# 15 · Picking, opening, sharing or dropping a large file kills the app, and one irrelevant entry makes a whole archive unimportable

**Severity:** crash (Android foremost, where the heap is 192–256 MB; desktop and iOS for very large files; an allocation trap on the web; needs one wrong or large file in a selection) + wrong behaviour (all platforms; any zip holding one entry the reader cannot take) · **Area:** `:data:model` (`ImportedFile`, new `ImportLimits`), `:data:source:local:implementation` (`ZipReader`, `Inflater`, `ArchiveLocalSourceImpl`), `:data:source:local:api` / `:data:repository:*` (`unpack`), `:domain:implementation` (`PrepareImportUseCaseImpl`, `ImportFilesUseCaseImpl`), `:presentation` (the four `FilePicker`s, the desktop drop, the import messages), `:app:android`, `:app:ios`

## Symptom

1. **Android:** Import files → the system picker offers everything (`*/*`, on purpose) → the user selects the content
   of their songs folder, and a 400 MB video or a phone backup zip is among it (or they simply tap the wrong file).
   The app dies: `readBytes()` throws `OutOfMemoryError`, which is an `Error`, so none of the
   `catch (exception: Exception)` blocks on the way up sees it. The same through "open with", a share, and on desktop
   for a file picked, dropped (read on the AWT event thread, so the window also freezes while it lasts) or passed as
   an argument; on iOS `dataWithContentsOfURL` plus a second full copy in `toByteArray`.
2. **Any platform:** the user zips their songbook folder, which also holds a 70 MB rehearsal recording, a PDF
   compressed with Deflate64, or a `.DS_Store` with a bad CRC. The import reports the **whole archive** as skipped;
   none of the 300 songs next to that entry are imported.
3. **Android:** a 150 MB zip picked by mistake: 150 MB from the picker plus up to 256 MiB of inflated entries, all
   held at once. The cap that exists "so that a crafted archive is a `ZipException` rather than an allocation that
   would take the process with it" is above the heap of the platform it names.
4. A 50 MB log file renamed `.txt`: bytes, the decoded `String` (UTF-16, up to twice the size), the line list, the
   joined parts and the `part + "\n"` copies coexist in `planSongs`.

## Cause

Nothing on any way in asks how big a file is before reading all of it, and the zip reader inflates before it filters
(line numbers as of `29820b93`):

```kotlin
// presentation/src/androidMain/.../ui/platform/FilePicker.android.kt:149-150 — the name is known before the bytes
context.contentResolver.openInputStream(this)?.use { ImportedFile(name = displayName(context), bytes = it.readBytes()) }
// presentation/src/desktopMain/.../ui/platform/FilePicker.desktop.kt:47 and :83
ImportedFile(name = file.name, bytes = file.readBytes())
// presentation/src/desktopMain/.../ui/CampfireDesktopApp.kt:69 — inside DragAndDropTarget.onDrop, i.e. on the EDT
viewModel.importFiles(paths.readAsImportedFiles())
// app/ios/src/iosMain/.../IosFilePicker.kt:105
NSData.dataWithContentsOfURL(this)?.let { ImportedFile(name = lastPathComponent.orEmpty(), bytes = it.toByteArray()) }
// presentation/src/wasmJsMain/.../ui/platform/FilePicker.wasmJs.kt:119-126 — file.arrayBuffer() of whatever came
```

```kotlin
// data/source/local/implementation/.../zip/ZipReader.kt:60-77 — every non-directory entry is read, and any
// ZipException thrown for one of them (:55 encrypted, :58 zip64, :111 method, :114 CRC, Inflater) ends read()
entries += ZipEntry(name = name, bytes = readData(...))
// .../source/ArchiveLocalSourceImpl.kt:40-49 — hidden files are dropped only after ZipReader.read returned them
// .../zip/ZipReader.kt:140   const val MAX_ARCHIVE_SIZE = 256L shl 20
// .../zip/Inflater.kt:288    const val MAX_ENTRY_SIZE = 64 shl 20
```

`InflatedBytes` is also per `unpack` call, so ten archives in one selection get ten budgets, and
`PrepareImportUseCaseImpl.sort` (`:39-43`) puts a plain text file of any size into `songFiles`.

## Fix

**The policy, in one place.** Two numbers, used on every way in:

| Constant | Value | Applies to |
| --- | --- | --- |
| `ImportLimits.MAX_TEXT_FILE_SIZE` | 8 MiB | one song, setlist or collection file, picked or inside an archive |
| `ImportLimits.MAX_IMPORT_SIZE` | 24 MiB | one `.zip` as picked; everything one pick/drop/intent reads; everything one import holds once unpacked |

Why these: a song is 2–4 KB, so 8 MiB is a `{new_song}` collection of two to three thousand songs and 24 MiB is a
library of about eight thousand — more than twice the three thousand the rest of the app is sized for. The worst
moment of an import holds the picked bytes (≤ 24), the unpacked bytes (≤ 24) and their text, which is UTF-16
whenever a song has one character outside Latin-1 (every Hungarian song: `ő`, `ű`), so up to 2 × 24 more: about
96 MiB. AOSP's heap configuration for a 2 GB phone is `dalvik.vm.heapgrowthlimit=192m`, which is what
`ActivityManager.memoryClass` reports and what an app without `largeHeap` gets; 96 MiB next to a Compose app's own
40–60 MB fits, the current 256 + 64 cannot. The limits are constants rather than derived from `memoryClass` so that
an archive imports the same on every device, and `largeHeap` is not the answer (it is a request, not a guarantee, and
it does nothing for the web).

**Do not catch `OutOfMemoryError` anywhere.** The heap that threw it is the heap the handler runs in.

Extension first, size second: a file the import would not look inside is never read at all, whatever its size, and
is reported as skipped the way it is today; a file it would look inside but that is over its limit is reported
separately, as too large.

### 1. `:data:model` — the limits, the budget, and a way to say "not read"

`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportedFile.kt`:

```kotlin
/**
 * A file on its way into the library: what a file picker, a drop or an archive hands over, before anything has been
 * decided about it. [name] is a plain file name; entries coming out of an archive have their path stripped.
 *
 * A file that was not read is handed over all the same, with no bytes, so that it is reported rather than lost: one
 * the import would not look inside reads as skipped by its extension, one that could not be read holds nothing to
 * import, and one that was left unread for its size says so through [isTooLarge], see [ImportLimits].
 */
data class ImportedFile(
    val name: String,
    val bytes: ByteArray,
    val isTooLarge: Boolean = false,
) {

    override fun equals(other: Any?) =
        this === other || (other is ImportedFile && name == other.name && isTooLarge == other.isTooLarge && bytes.contentEquals(other.bytes))

    override fun hashCode() = 31 * (31 * name.hashCode() + isTooLarge.hashCode()) + bytes.contentHashCode()

    companion object {

        /** [name] as a file nobody read, see the class. */
        fun unread(name: String, isTooLarge: Boolean = false) = ImportedFile(name = name, bytes = ByteArray(0), isTooLarge = isTooLarge)
    }
}
```

New file `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportLimits.kt` (MPL header
as in its siblings):

```kotlin
package com.pandulapeter.campfire.data.model.domain

/**
 * How much an import is willing to read. A song is a few kilobytes of text, and every way into the library reads a
 * file whole, so without a ceiling one wrong file in a selection is an allocation the process does not survive: an
 * Android app has a heap of about 192 MB to itself, and running out of it cannot be caught.
 *
 * The numbers are the same on every platform, so that an archive imports the same everywhere. At its worst an import
 * holds what was picked, what that unpacked to and the text of it, which is two bytes a character as soon as a song
 * steps outside Latin-1: four times [MAX_IMPORT_SIZE], which has to fit next to the app itself.
 */
object ImportLimits {

    /** One song, setlist or collection of songs, picked or inside an archive: a few thousand songs in one file. */
    const val MAX_TEXT_FILE_SIZE = 8L shl 20

    /**
     * One archive as it was picked, everything one selection reads, and everything one import unpacks to: more than
     * twice a library of three thousand songs.
     */
    const val MAX_IMPORT_SIZE = 24L shl 20

    /**
     * How many bytes of a file called [name] an import reads, which is none for a file it would not look inside.
     * Asked before the file is opened wherever the name is known first, which is everywhere.
     */
    fun maxSizeOf(name: String) = when {
        name.endsWith(LibraryFiles.ARCHIVE_EXTENSION, ignoreCase = true) -> MAX_IMPORT_SIZE
        LibraryFiles.IMPORTABLE_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) } -> MAX_TEXT_FILE_SIZE
        else -> 0L
    }
}

/**
 * What one selection - a pick, a drop, the files of an intent - may still read. Every platform reads its files
 * through one of these, so the rule is written once: nothing the import would not look inside is read, nothing over
 * its own limit is, and the selection as a whole stops at [ImportLimits.MAX_IMPORT_SIZE].
 */
class ImportBudget {

    @PublishedApi
    internal var remaining = ImportLimits.MAX_IMPORT_SIZE

    /**
     * @param size What the platform says the size of the file is, null where it cannot say.
     * @param readBytes Reads the file, and no more than one byte past `limit` where [size] was null - which is how a
     *   file of unknown size is found out. Null for a file that cannot be read, which is then left out.
     */
    inline fun read(name: String, size: Long?, readBytes: (limit: Long) -> ByteArray?): ImportedFile? {
        val ownLimit = ImportLimits.maxSizeOf(name)
        if (ownLimit == 0L) return ImportedFile.unread(name)
        val limit = minOf(ownLimit, remaining)
        if (size != null && size > limit) return ImportedFile.unread(name, isTooLarge = true)
        val bytes = readBytes(limit) ?: return null
        if (bytes.size > limit) return ImportedFile.unread(name, isTooLarge = true)
        remaining -= bytes.size
        return ImportedFile(name = name, bytes = bytes)
    }
}
```

`read` is `inline` so that the web's `readBytes` can suspend. `ImportPlan` gets
`val oversizedFileNames: List<String> = emptyList()` (KDoc: "Files the import would have looked inside but did not
read, because they are larger than [ImportLimits] allows.") and `Summary` an `oversizedCount: Int` filled from it;
`ImportResult` gets the same `oversizedFileNames` field. Neither counts them among the skipped.

### 2. `ZipReader` — ask before inflating, and fail one entry rather than the archive

`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/`.
New file `ZipContent.kt`:

```kotlin
/** What [ZipReader.read] made of an archive: the entries it read, and the names of the ones it did not, with why. */
internal class ZipContent(
    val entries: List<ZipEntry>,
    val unread: List<UnreadZipEntry>,
)

internal data class UnreadZipEntry(
    val name: String,
    val reason: Reason,
) {

    enum class Reason {
        /** The caller had no use for it, so nothing but its name was looked at. */
        NOT_WANTED,

        /** Larger than the caller allows a file of that name to be, or than what is left of the archive's limit. */
        TOO_LARGE,

        /** Encrypted, ZIP64, compressed with a method other than DEFLATE, or damaged. */
        UNREADABLE,
    }
}
```

`ZipReader.read` becomes (the header parsing between the two comments stays exactly as it is):

```kotlin
    /**
     * The file entries of [archive], directories skipped. Names are returned as stored (forward slashes, possibly with
     * sub-directories), decoded as UTF-8.
     *
     * Only an archive that cannot be walked at all throws. An entry that cannot be read - or that the caller does not
     * want, or that is too large - costs nothing but its name: the songs next to a recording, a PDF or a damaged
     * `.DS_Store` are still perfectly good.
     *
     * @param maxTotalSize how many bytes the entries read may add up to once inflated. Sizes are checked as the
     * central directory declares them, before an entry is read; the inflater holds an entry to what it declared.
     * @param limitOf how large the entry of that name may be, null for one that is not to be read at all.
     */
    fun read(
        archive: ByteArray,
        maxTotalSize: Long = ImportLimits.MAX_IMPORT_SIZE,
        limitOf: (name: String) -> Long? = { Long.MAX_VALUE },
    ): ZipContent {
        // ... unchanged up to `var position = centralDirectoryOffset.toInt()`, plus:
        val unread = mutableListOf<UnreadZipEntry>()
        repeat(totalEntries) {
            // ... unchanged header fields and `name`; the two `throw ZipException` for an encrypted and a ZIP64 entry
            // are removed from here and become the UNREADABLE branch below.
            if (!name.endsWith("/")) {
                val limit = limitOf(name)
                val isUnsupported = flags and 0x0001 != 0 ||
                    compressedSize == 0xFFFFFFFFL || uncompressedSize == 0xFFFFFFFFL || localHeaderOffset == 0xFFFFFFFFL
                when {
                    limit == null -> unread += UnreadZipEntry(name, UnreadZipEntry.Reason.NOT_WANTED)
                    isUnsupported -> unread += UnreadZipEntry(name, UnreadZipEntry.Reason.UNREADABLE)
                    uncompressedSize > limit || totalSize + uncompressedSize > maxTotalSize ->
                        unread += UnreadZipEntry(name, UnreadZipEntry.Reason.TOO_LARGE)

                    else -> try {
                        entries += ZipEntry(name = name, bytes = readData(/* as today */))
                        totalSize += uncompressedSize
                    } catch (exception: ZipException) {
                        println("Could not read \"$name\": ${exception.message}")
                        unread += UnreadZipEntry(name, UnreadZipEntry.Reason.UNREADABLE)
                    }
                }
            }
            position += 46 + nameLength + extraLength + commentLength
        }
        return ZipContent(entries = entries, unread = unread)
    }
```

Catch `ZipException` only — it is what every malformed-input path in `readData`, `Inflater` and `requireBytes`
throws; anything else is a bug and should stay loud. Delete `ZipReader.MAX_ARCHIVE_SIZE`. In `Inflater.kt:288` set
`const val MAX_ENTRY_SIZE = ImportLimits.MAX_IMPORT_SIZE.toInt()` — hm, a `const` cannot call `toInt()`; write it as
`const val MAX_ENTRY_SIZE = 24 shl 20` with the KDoc "The most a single entry may inflate to, which is the most an
import unpacks to (`ImportLimits.MAX_IMPORT_SIZE`): the largest entry worth reading is an archive inside the
archive.", and pin the equality in a test (below).

### 3. `ArchiveLocalSource.unpack` — a budget from the caller, names before bytes

`data/source/local/api/.../ArchiveLocalSource.kt` and `data/repository/api/.../ArchiveRepository.kt`:
`suspend fun unpack(archive: ByteArray, maxSize: Long): List<ImportedFile>`; `ArchiveRepositoryImpl` passes it
through. Add to the local API's KDoc: "[maxSize] is how much the files may add up to, nested archives included; the
caller owns it because one import may unpack several archives. An entry that was not read — not something an import
looks inside, too large, or unreadable — is still returned, as `ImportedFile.unread`, so that it is reported."
(and drop the sentence "Everything else is returned as it is, unread.", which now means something else).

`ArchiveLocalSourceImpl`:

```kotlin
    override suspend fun unpack(archive: ByteArray, maxSize: Long): List<ImportedFile> = withContext(Dispatchers.Default) {
        unpack(archive = archive, depth = 1, inflated = InflatedBytes(limit = maxSize))
    }

    private fun unpack(archive: ByteArray, depth: Int, inflated: InflatedBytes): List<ImportedFile> {
        val content = ZipReader.read(
            archive = archive,
            maxTotalSize = inflated.limit - inflated.count,
            // Decided on the name alone, before anything is inflated: what the archiving tool wrote for itself (see
            // below) and what an import would not look inside cost nothing and cannot fail the archive.
            limitOf = { name -> name.fileName.takeUnless { it.startsWith(HIDDEN_NAME_PREFIX) }?.let(ImportLimits::maxSizeOf)?.takeIf { it > 0 } },
        )
        inflated.count += content.entries.sumOf { it.bytes.size.toLong() }
        val read = content.entries.flatMap { entry ->
            val file = ImportedFile(name = entry.name.fileName, bytes = entry.bytes)
            if (depth < MAX_DEPTH && file.name.endsWith(ZIP_EXTENSION, ignoreCase = true)) {
                try {
                    unpack(archive = file.bytes, depth = depth + 1, inflated = inflated)
                } catch (exception: Exception) {
                    println("Could not unpack \"${file.name}\": ${exception.message}")
                    listOf(ImportedFile.unread(file.name))
                }
            } else {
                listOf(file)
            }
        }
        val unread = content.unread
            .filterNot { it.name.fileName.startsWith(HIDDEN_NAME_PREFIX) }
            .map { ImportedFile.unread(name = it.name.fileName, isTooLarge = it.reason == UnreadZipEntry.Reason.TOO_LARGE) }
        return read + unread
    }

    /** The library is flat, so "songs/x.cho" and "x.cho" are the same file as far as an import is concerned. */
    private val String.fileName get() = substringAfterLast('/')

    private class InflatedBytes(val limit: Long) {
        var count = 0L
    }
```

Keep the long existing comment about AppleDouble files, moved above `limitOf`. Hidden entries stay unreported, as
today; everything else that was not read is now reported (a nested archive that fails used to vanish silently).
A nested `.zip` at `MAX_DEPTH` is returned with its bytes, as today, and `PrepareImportUseCaseImpl.sort` files it
under skipped.

### 4. `PrepareImportUseCaseImpl` — one budget per import, and the oversized list

```kotlin
        val oversizedFileNames = mutableListOf<String>()
        // What the import may still hold once everything is unpacked, shared by every archive and plain file of it.
        var remaining = ImportLimits.MAX_IMPORT_SIZE

        fun sort(file: ImportedFile) {
            val extension = file.name.substringAfterLast('.', "").lowercase()
            when {
                extension !in SONG_EXTENSIONS && extension != SETLIST_EXTENSION -> skippedFileNames += file.name
                file.isTooLarge || file.bytes.size > minOf(ImportLimits.MAX_TEXT_FILE_SIZE, remaining) -> oversizedFileNames += file.name
                else -> {
                    remaining -= file.bytes.size
                    if (extension == SETLIST_EXTENSION) setlistFiles += file else songFiles += file
                }
            }
        }

        files.forEach { file ->
            when {
                !file.name.endsWith(ARCHIVE_EXTENSION, ignoreCase = true) -> sort(file)
                file.isTooLarge || file.bytes.size > ImportLimits.MAX_IMPORT_SIZE -> oversizedFileNames += file.name
                else -> try {
                    archiveRepository.unpack(archive = file.bytes, maxSize = remaining).forEach(::sort)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    println("Could not unpack \"${file.name}\": ${exception.message}")
                    skippedFileNames += file.name
                }
            }
        }
```

and `oversizedFileNames = oversizedFileNames` in the returned `ImportPlan`. `ImportFilesUseCaseImpl.kt:95-99` copies
`plan.oversizedFileNames` into the `ImportResult`. The size checks in `sort` are not redundant with the platform
readers: they are what holds for a caller that did not go through `ImportBudget` (the demo library, a future one).
Plans 02 and 34 edit the same function (`planSongs`/`planSetlists` and the dispatcher); this plan only touches
`invoke` above the `return`.

### 5. Telling the user

New plural, in both `presentation/src/commonMain/composeResources/values/strings.xml` and `values-hu/strings.xml`,
after `import_conflicts_skipped`:

```xml
    <plurals name="import_oversized">
        <item quantity="one">One file is too large and cannot be imported.</item>
        <item quantity="other">%1$d files are too large and cannot be imported.</item>
    </plurals>
```

```xml
    <plurals name="import_oversized">
        <item quantity="one">Egy fájl túl nagy, ezt nem lehet importálni.</item>
        <item quantity="other">%1$d fájl túl nagy, ezeket nem lehet importálni.</item>
    </plurals>
```

- `CampfireViewModel.Message`: add `data class ImportOversized(val count: Int) : Message`. In `applyImportPlan`,
  inside `if (shouldAnnounceResult)`, after `ImportFinished`:
  `if (result.oversizedFileNames.isNotEmpty()) _messages.send(Message.ImportOversized(result.oversizedFileNames.size))`.
  It is a message of its own rather than a fifth number in `import_result`, which already reads as a row of counts.
- `CampfireApp.kt`, the `when (current)` at `:439`: 
  `is CampfireViewModel.Message.ImportOversized -> pluralStringResource(Res.plurals.import_oversized, current.count, current.count)`
  (the `com.pandulapeter.campfire.presentation.localization` one).
- `Dialogs.kt:388-390`, after the skipped note:
  `if (summary.oversizedCount > 0) { ImportConflictsNote(pluralStringResource(Res.plurals.import_oversized, summary.oversizedCount, summary.oversizedCount)) }`.

### 6. The four ways in

**Android** — `FilePicker.android.kt`. `pickFiles` ends in `withContext(Dispatchers.IO) { uris.toImportedFiles(context) }`;
replace `Uri.toImportedFile` and `displayName` with:

```kotlin
/**
 * Reads the documents the system handed over - picked, opened with Campfire, shared to it or dropped onto it -
 * within one [ImportBudget]. One that cannot be read is left out, so that one bad file does not lose the ones next to it.
 */
fun List<Uri>.toImportedFiles(context: Context): List<ImportedFile> {
    val budget = ImportBudget()
    return mapNotNull { uri ->
        try {
            val (name, size) = uri.nameAndSize(context)
            budget.read(name = name, size = size) { limit ->
                // The size is the provider's own claim and is missing as often as not, so the read stops by itself.
                context.contentResolver.openInputStream(uri)?.use { it.readAtMost(limit.toInt() + 1) }
            }
        } catch (exception: Exception) {
            println("Could not read \"$uri\": ${exception.message}")
            null
        }
    }
}

/** The extension is what the import goes by, and for a content URI only the display name carries it. */
private fun Uri.nameAndSize(context: Context): Pair<String, Long?> = context.contentResolver
    .query(this, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
    ?.use { cursor ->
        if (cursor.moveToFirst()) (cursor.getString(0) ?: fallbackName) to (if (cursor.isNull(1)) null else cursor.getLong(1)) else null
    }
    ?: (fallbackName to null)

private val Uri.fallbackName get() = lastPathSegment.orEmpty().substringAfterLast('/')

/** To the end of the stream or to [limit] bytes, whichever comes first. `readNBytes` would do, from API 33 on. */
private fun InputStream.readAtMost(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (output.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
```

`app/android/src/main/java/com/pandulapeter/campfire/CampfireActivity.kt:142` becomes
`withContext(Dispatchers.IO) { uris.toImportedFiles(this@CampfireActivity) }` (import renamed accordingly).

**Desktop** — `FilePicker.desktop.kt`: both readers go through one function, and `pickFiles` calls
`files.map { it.absolutePath }.readAsImportedFiles()` inside its `withContext(Dispatchers.IO)`:

```kotlin
fun List<String>.readAsImportedFiles(): List<ImportedFile> {
    val budget = ImportBudget()
    return mapNotNull { path ->
        val file = File(path)
        try {
            if (file.isFile) budget.read(name = file.name, size = file.length()) { file.readBytes() } else null
        } catch (exception: Exception) {
            println("Could not read \"$path\": ${exception.message}")
            null
        }
    }
}
```

`CampfireDesktopApp.kt`: the transferable is only valid inside `onDrop`, so the paths are still taken there, but the
read moves off the event thread. Add `val scope = rememberCoroutineScope()` above the `Box` and replace line 69 with:

```kotlin
                            // Only the paths are taken here: this is the AWT event thread, and the files are read off it.
                            scope.launch { viewModel.importFiles(withContext(Dispatchers.IO) { paths.readAsImportedFiles() }) }
```

(`import kotlinx.coroutines.IO`.) The read of `args` in `CampfireDesktopApplication.kt:42` is now bounded by the
same budget; moving it is left to plans 44/49, which rework how arguments arrive.

**iOS** — `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt`: `readImportedFile` takes the
budget, the picker passes one per pick (`val budget = ImportBudget()` before `urls.mapNotNull`), `openUrl` in
`IosFileImport.kt:38` passes a new one.

```kotlin
internal fun NSURL.readImportedFile(budget: ImportBudget): ImportedFile? {
    val isAccessible = startAccessingSecurityScopedResource()
    return try {
        val size = path?.let { NSFileManager.defaultManager.attributesOfItemAtPath(it, error = null) }?.get(NSFileSize) as? NSNumber
        budget.read(name = lastPathComponent.orEmpty(), size = size?.longLongValue) { limit ->
            // Mapped rather than loaded, so that a file whose attributes said nothing still gives its length away
            // before any of it is copied into the heap.
            NSData.dataWithContentsOfURL(this, options = NSDataReadingMappedIfSafe, error = null)
                ?.let { data -> if (data.length.toLong() > limit) ByteArray(limit.toInt() + 1) else data.toByteArray() }
        }
    } catch (exception: Exception) { /* as today */ } finally { /* as today */ }
}
```

The `ByteArray(limit + 1)` is the contract's "one byte past the limit" and is only reached when the attributes had
no size, which for a local file does not happen; if that reads as too clever, return `null` and accept that such a
file is left out unreported. Plan 51 edits the same two functions (Inbox clean-up, reading off the main thread).

**Web** — `FilePicker.wasmJs.kt`: add `private fun fileSize(file: JsAny): Double = js("file.size")` and

```kotlin
private suspend fun JsArray<JsAny>.toImportedFiles(): List<ImportedFile> {
    val budget = ImportBudget()
    return (0 until length).mapNotNull { index ->
        get(index)?.let { file ->
            budget.read(name = fileName(file).toString(), size = fileSize(file).toLong()) {
                fileBytes(file).await<Int8Array?>()?.toByteArray() ?: ByteArray(0)
            }
        }
    }
}
```

Plan 43 edits the same function (a dropped folder rejects `arrayBuffer()`); whichever lands second keeps both.

## Tests

`:data:source:local:implementation` (which is where `:data:model`'s rules are tested, see `LibraryTextDecodingTest`):

- New `commonTest/.../ImportLimitsTest.kt`:
  - `aFileTheImportWouldNotLookInsideIsNeverRead`: `ImportBudget().read("video.mp4", size = 1) { error("read") }` →
    `ImportedFile.unread("video.mp4")`, `isTooLarge == false`.
  - `aFileOverItsDeclaredSizeIsNotRead`: `"a.cho"`, `size = MAX_TEXT_FILE_SIZE + 1`, lambda fails if called →
    `isTooLarge`.
  - `aFileOfUnknownSizeIsFoundOutWhileReading`: `size = null`, lambda returns `ByteArray((limit + 1).toInt())` →
    `isTooLarge`, and the `limit` it was given is `MAX_TEXT_FILE_SIZE`.
  - `theSelectionSharesOneBudget`: three `.zip` reads of 10 MiB each (`size = 10L shl 20`, lambda returns that many
    bytes) → third is `isTooLarge`; a following 1 MiB `.cho` is still read.
  - `anUnreadableFileIsLeftOut`: lambda returns null → null.
  - `theInflaterAllowsWhatAnImportDoes`: `assertEquals(ImportLimits.MAX_IMPORT_SIZE, Inflater.MAX_ENTRY_SIZE.toLong())`.
- `ZipReaderTest`: every `ZipReader.read(x).size` / `[i]` becomes `.entries.size` / `.entries[i]`. Rename and change:
  - `rejectsAnUnsupportedCompressionMethod` → `leavesOutAnEntryWithAnUnsupportedCompressionMethod`: the other entry
    is still in `entries`, `unread == listOf(UnreadZipEntry("hello.txt"-or-whatever-the-fixture-names-it, UNREADABLE))`.
  - `rejectsAnEntryWithABadChecksum`, `rejectsAnEncryptedEntry`, `rejectsAnEntryDeclaringMoreThanItCouldEverInflateTo`,
    `rejectsADeclaredSizeTheStreamDoesNotProduceWithoutAllocatingIt` (declare `16L shl 20` instead of 48, which is now
    over the entry limit), `rejectsAnEntryWhoseEndOverflowsAnInt` → the same shape: no exception, the entry is in
    `unread` as `UNREADABLE`. Keep the direct `Inflater.inflate` assertion of the last one as it is.
  - `rejectsAnArchiveOverTheTotalLimit` → `stopsReadingAtTheTotalLimit`: `maxTotalSize = 14` reads the 6 byte entry
    and reports the 9 byte one as `TOO_LARGE`.
  - New `doesNotReadWhatTheCallerDoesNotWant`: corrupt the data of one entry (as the checksum test does) and pass
    `limitOf = { if (it == thatName) null else Long.MAX_VALUE }` → no exception, reason `NOT_WANTED` — which proves
    the entry was never inflated. New `leavesOutAnEntryOverItsOwnLimit`: `limitOf = { 8 }` → the 9 byte entry is
    `TOO_LARGE`, the 6 byte one read.
  - `rejectsATruncatedArchive` and `rejectsAnEmptyInput` stay as they are: those archives cannot be walked.
- `ArchiveLocalSourceTest` (`desktopTest`): every `unpack(archive)` gets `maxSize = ImportLimits.MAX_IMPORT_SIZE`. New:
  - `` `reports what it did not read instead of failing` ``: `a.cho`, `notes.pdf` (any bytes), `b.cho` →
    names `[a.cho, b.cho, notes.pdf]`, the last with empty bytes and `isTooLarge == false`.
  - `` `stops at the size it was given` ``: two 600-byte songs, `maxSize = 1000` → the first read, the second
    `isTooLarge`.
  - `` `reports an archive inside it that cannot be read` ``: `more.zip` holding `byteArrayOf(1, 2, 3)` →
    `[a.cho, more.zip]`, the latter unread.
- `ZipReaderJvmTest` / `ZipRoundTripTest`: `.entries` where they index the result.

## Verify

1. Unit tests and the three compile checks from the README of this folder.
2. Android (emulator with 2 GB RAM, `adb shell getprop dalvik.vm.heapgrowthlimit` to confirm 192m): `adb push` a
   300 MB file as `big.zip`, a 300 MB `video.mp4`, a 20 MB `songs.txt` and two real songs into Downloads. Import files
   → select all five: the two songs are imported, the first snackbar says "… · 1 skipped" (the video), the second
   "3 files are too large…" — wait, two: `big.zip` and `songs.txt`. No crash, and `adb logcat` shows no
   `OutOfMemoryError`. Repeat through "Share → Campfire" from the Files app.
3. Desktop: drop the same five files on the window; the window keeps repainting while they are read (drag it
   around), same two snackbars.
4. Any platform: zip a folder holding three songs, an mp3 and a PDF (on macOS, from Finder, so `__MACOSX` is in it).
   Import: 3 songs imported, 2 skipped. Before this change the archive was either fine or skipped whole depending on
   the mp3's size.
5. Export the library, import the archive back: "… N already there · 0 skipped", no oversized message.
6. Web: pick a 100 MB file named `x.cho`: the tab's memory does not move (DevTools → Memory), the message appears.

## Docs

- `data/source/local/implementation/CLAUDE.md`, the `zip/` bullet: replace "an entry may inflate to 64 MiB … and one
  import (`ArchiveLocalSourceImpl.unpack`, nested archives included) to 256 MiB, so a corrupted or crafted archive is
  a `ZipException` …" with: "an entry is asked about by name before it is inflated — hidden files and anything an
  import would not look inside are never read, a song over `ImportLimits.MAX_TEXT_FILE_SIZE` is not either, and the
  caller says how much the whole import may still unpack to (`ImportLimits.MAX_IMPORT_SIZE`, 24 MiB, nested archives
  included). An entry that cannot be read is left out and reported by name; only an archive that cannot be walked
  at all is a `ZipException`. The buffer still starts at no more than 1 MiB whatever the central directory claims."
- `data/model/CLAUDE.md`: in the `domain/` bullet, after "`ImportedFile` / `ExportedFile` / `ImportResult`", add
  "`ImportLimits` and `ImportBudget` — how much an import reads (8 MiB a file, 24 MiB a selection and an unpacked
  import, sized for an Android heap) and the one function every platform reads its incoming files through, so that
  a file the import would not look inside is never read and one that is too large is reported rather than loaded".
- `domain/implementation/CLAUDE.md`, the `PrepareImportUseCaseImpl` bullet: add "It owns the import's size budget:
  every archive unpacks into what the ones before it left, and a file over its limit goes to
  `ImportPlan.oversizedFileNames`, which the UI reports on its own line."
- `presentation/CLAUDE.md`: where the import snackbar is described (`CampfireApp.kt` bullet, "the snackbars for
  import and export results"), add "— an import that left files out for their size says so in a second one".

## Touches

- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportedFile.kt`
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportLimits.kt` (new)
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportPlan.kt`
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportResult.kt`
- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/ArchiveLocalSource.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipReader.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipContent.kt` (new)
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/Inflater.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ArchiveLocalSourceImpl.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/ImportLimitsTest.kt` (new)
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipReaderTest.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipRoundTripTest.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipReaderJvmTest.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ArchiveLocalSourceTest.kt`
- `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/ArchiveRepository.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/ArchiveRepositoryImpl.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/PrepareImportUseCaseImpl.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImpl.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.android.kt`
- `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.desktop.kt`
- `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireDesktopApp.kt`
- `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.wasmJs.kt`
- `app/android/src/main/java/com/pandulapeter/campfire/CampfireActivity.kt`
- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt`
- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFileImport.kt`
- `data/source/local/implementation/CLAUDE.md`, `data/model/CLAUDE.md`, `domain/implementation/CLAUDE.md`, `presentation/CLAUDE.md`

## Depends on

Nothing has to land first. It shares `PrepareImportUseCaseImpl.kt` with plans 02 and 34 (this one only edits
`invoke`; 34 wraps it in `withContext(Dispatchers.Default)` and 02 rewrites `planSongs`/`planSetlists`), the web
`toImportedFiles` with 43, `readImportedFile`/`openUrl` on iOS with 51, `CampfireActivity.importFrom` with 04 and 52,
and `CampfireDesktopApp`/`readAsImportedFiles` with 44 and 49 — schedule those one after another, in any order.
