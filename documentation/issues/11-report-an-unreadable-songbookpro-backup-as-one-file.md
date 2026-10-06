# Report a SongbookPro backup whose library cannot be read as one unread file instead of importing its JSON as a song

**Kind:** bug · **Severity:** medium-low · **Platforms:** all
**Challenged:** amended — a `dataFile.txt` whose first line is a bare version number is recognised as SongbookPro's too (so a `.sbp`, whose contents beside the document are not known, is covered), the only caller and the test call sites are named, and the interaction with plan 12 is spelled out.
**Files:**
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/SongbookProBackup.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ArchiveLocalSourceImpl.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/SongbookProBackupTest.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ArchiveLocalSourceTest.kt`
- `data/source/local/implementation/CLAUDE.md` (the `backup/` bullet)

## Problem

`SongbookProBackup.read` answers null both for "this archive is not a SongbookPro library" and for "it is one, but its
`dataFile.txt` did not parse or holds no `songs` array" (`SongbookProBackup.kt:52-55` at 1c52e5347):

```kotlin
fun read(entries: List<ImportedFile>): List<ImportedFile>? {
    val data = entries.singleOrNull { it.name == DATA_FILE_NAME } ?: return null
    val library = parse(data.bytes.decodeLibraryText()) ?: return null
    val songs = library.array("songs") ?: return null
```

and `ArchiveLocalSourceImpl.unpack` then imports the backup's entries as ordinary files
(`ArchiveLocalSourceImpl.kt:79`):

```kotlin
SongbookProBackup.read(entries)?.let { return it }
val read = entries.flatMap { file -> … listOf(file) }
```

`dataFile.txt` is a `.txt`, so `PrepareImportUseCaseImpl` sends it through `ChordSheetConverter` as plain text — a
song whose lyrics are the raw JSON of the whole library — or, where it is larger than `ImportLimits.MAX_TEXT_FILE_SIZE`
(it was read up to `MAX_IMPORT_SIZE` because of its name, `ArchiveLocalSourceImpl.kt:70`), it is reported as too
large. Its bookkeeping (`settings.hive`, `dataFile.hash`; the KDoc of `SongbookProBackup` and the existing archive test
name these two as what a backup holds beside the document) is handed over unread and reported among the unsupported
files. Probed at 1c52e5347: a backup of `settings.hive`, `dataFile.hash` and a `dataFile.txt` of
`2.0\n{"library": {"songs": []}}` unpacked to `dataFile.txt` (30 bytes, read), `settings.hive` and `dataFile.hash`
(unread).

This is what a future SongbookPro format version, a truncated backup or one whose document has another shape looks
like: the user picked one backup and gets a garbage song plus three file names they never saw, instead of one line
saying the backup could not be read.

## Fix

Tell the two cases apart. Recommended:

- Make `read` return a small sealed result, e.g.

  ```kotlin
  sealed interface Result {
      /** Not SongbookPro's: the entries are imported as the files they are. */
      data object NotABackup : Result
      /** SongbookPro's, translated. */
      data class Library(val files: List<ImportedFile>) : Result
      /** SongbookPro's by its shape, but its document could not be read. */
      data object Unreadable : Result
  }
  ```

- An archive is SongbookPro's when it holds exactly one `dataFile.txt` **and** either the document reads as a library
  (as today) or one of SongbookPro's bookkeeping entries (`dataFile.hash`, `settings.hive`, kept as a constant set next
  to `DATA_FILE_NAME`) sits beside it. Without the bookkeeping, an unparsable `dataFile.txt` stays `NotABackup`, since a
  user's own zip may hold a text file of that name.
- Also recognise it when the document's first non-blank line is a bare version number (`Regex("""\d+(\.\d+)+""")`, the
  `1.0` / `2.0` line `parse` already skips) followed by more text. The KDoc says both `.sbpbackup` and `.sbp` hold
  `dataFile.txt`, but nothing in the repo shows that a shared `.sbp` carries the bookkeeping as well; the version line
  is SongbookPro's own and no text file a user writes starts with one, so it covers a `.sbp` (and a future format)
  without bookkeeping. `"Just some notes"` (the existing negative test) stays `NotABackup`.
- In `ArchiveLocalSourceImpl.unpack`: `Library` returns its files (today's behaviour), `Unreadable` returns
  `listOf(ImportedFile.unread(SongbookProBackup.DATA_FILE_NAME))` and drops the bookkeeping, `NotABackup` falls through.
  A `dataFile.txt` that was not read at all (too large for the whole import, so it is in `content.unread` rather than
  `entries`) with bookkeeping beside it is reported the same way, as one unread `dataFile.txt` with `isTooLarge` kept.

`SongbookProBackup.read` has one production caller, `ArchiveLocalSourceImpl.unpack`; the other callers are the
`read(...)` helper and the two `assertNull` calls in `SongbookProBackupTest`, which change with the result type
(`assertIs<Result.NotABackup>`, and the helper unwrapping `Library.files`).

How it meets plan 12 (same lane, different module): under a known name (`.sbpbackup`, `.sbp`, `.zip`) the report
names `dataFile.txt` as below. Under a name the import does not know, plan 12 as amended counts only entries that were
actually read, so the empty unread `dataFile.txt` is not "something an import reads" and the report names the picked
file itself — the better answer, at no cost here.

Better still for the report would be the name of the backup itself (`Library.sbpbackup`) rather than `dataFile.txt`,
but `unpack` does not know the name it was handed; leave that unless the executor finds it is cheap. An unread
`dataFile.txt` lands in `ImportPlan.skippedFileNames` ("Unsupported or invalid files"), which is the right bucket.

Update the `backup/` bullet of `data/source/local/implementation/CLAUDE.md`: a backup recognised by its bookkeeping
whose document cannot be read is reported as one unread `dataFile.txt`, its bookkeeping dropped.

## Tests

- `SongbookProBackupTest`: `read` of `dataFile.txt` = `"{\"library\": {}}"` plus `dataFile.hash` is `Unreadable`,
  and the same `dataFile.txt` alone is `NotABackup`; `"2.0\n{\"library\": {}}"` alone is `Unreadable` (the version
  line); the existing `anArchiveWithoutSongbookProsDocumentIsNotASongbookProLibrary`
  cases stay `NotABackup`; the existing happy paths become `Library`.
- `ArchiveLocalSourceTest`: an archive of `settings.hive`, `dataFile.hash` and an unparsable `dataFile.txt` unpacks to
  exactly `listOf("dataFile.txt")` with empty bytes; the existing
  `reads a SongbookPro backup inside an archive as its songs and sets, without its bookkeeping` is unchanged.

## Manual check

Take a real `.sbpbackup`, unzip it, replace `dataFile.txt` with `{}` and zip it back under the same name; import it:
the report names one unsupported file and no song of JSON appears in the library.
