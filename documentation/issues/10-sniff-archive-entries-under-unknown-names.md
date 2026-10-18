# Unpack an archive nested under an unknown name by peeking at its first bytes, inside archives and dropped folders

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (folder drops: desktop and web)
**Files:** `data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/zip/ZipReader.kt`,
`data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/zip/ZipContent.kt`,
`data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/zip/Inflater.kt`,
`data/formats/src/commonTest/kotlin/com/pandulapeter/campfire/data/formats/zip/ZipReaderTest.kt`,
`data/formats/src/desktopTest/kotlin/com/pandulapeter/campfire/data/formats/zip/InflaterTest.kt`,
`data/formats/CLAUDE.md`,
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ArchiveLocalSourceImpl.kt`,
`data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ArchiveLocalSourceTest.kt`,
`data/source/local/api/CLAUDE.md`,
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/importing/ImportLimits.kt`,
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/library/LibraryFiles.kt`,
`presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.desktop.kt`,
`presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.wasmJs.kt`
(`listenForDrops`), `data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/document/DocxTextExtractor.kt` (its `limitOf`),
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/platform/ReadAsImportedFilesTest.kt`,
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/importing/CLAUDE.md`

**Challenged:** amended — the reader charges `compressedBytesRead` once per entry (the draft charged a wanted peek twice,
which refuses a nested backup larger than half its archive), the peek's buffer is capped at the prefix (the inflater
allocates up to 1 MiB per stream on the declared size), the peek order and the failure reasons are pinned, the three
existing `limitOf` callers are named (the type change breaks them), the over-8 MB step (now 5) is one rule with the declared
size passed to `limitOf`, and folder walks peek before reading an unknown file (a dropped photo folder would otherwise be
read whole, file by file).

## Problem

A loose file under an unknown name is read and kept when its bytes are a zip's (`ImportBudget.readUnknown`), and
since the review's fix a `.txt`/`.cho`/`.json` is too. But an entry *inside* an archive is decided by its name alone,
before anything is inflated, and an unknown name is never read:

```kotlin
// ArchiveLocalSourceImpl.unpack
limitOf = { name ->
    when {
        name.isHidden -> null
        name.fileName.equals(SongbookProBackup.DATA_FILE_NAME, ignoreCase = true) -> ImportLimits.MAX_IMPORT_SIZE
        else -> ImportLimits.maxSizeOf(name.fileName).takeIf { it > 0 }
    }
},
// ImportLimits.maxSizeOf
else -> 0L
```

Verified at the reviewed commit with an outer zip holding `folder/<name>` where `<name>` is a real zip of `in.cho`:
`backup.bak`, `backup` (no extension), `Songs.backup`, and `x.onsong` each come back as one unread entry, their
songs lost (reported as skipped, so not silently). The realistic case: a `.zip` of a cloud folder that holds an
OnSong `.backup` or an OpenSongApp `.osb` next to loose songs. The desktop's folder drop has the same rule for a
file inside a dropped folder:

```kotlin
if (isInFolder && !LibraryFiles.isImportableFileName(file.name)) {
    ImportedFile.unread(file.name)
}
```

and the web's `listenForDrops` filters a dropped folder's files by `importableExtensions` in its JavaScript.

A second limitation from the regression read: a renamed backup over 8 MB under `.txt`/`.cho`/`.json` is reported
as too large rather than unpacked, since `ImportBudget.read` and `limitOf` apply `MAX_TEXT_FILE_SIZE` by name before
any byte is read.

## Fix

1. **Entry policy.** `ZipReader.read`'s `limitOf` becomes `(name: String, declaredSize: Long) -> EntryPolicy`, with
   `sealed interface EntryPolicy { data class Read(val limit: Long); data object Sniff; data object Skip }` in
   `ZipContent.kt`, and a second parameter `sniffedLimitOf: (name: String, prefix: ByteArray) -> Long? = { _, _ -> null }`.
   `Read` is today's `Long`, `Skip` today's null. The default `limitOf = { _, _ -> EntryPolicy.Read(Long.MAX_VALUE) }`.
   The type change breaks the three existing callers, which are updated mechanically: `DocxTextExtractor.kt`'s
   `limitOf` and the two in `ZipReaderTest` (`limitOf = { if (it == "hello.txt") null else … }`, `limitOf = { 8 }`).
2. **A `Sniff` entry, in this order:** unsupported (encrypted, ZIP64) → `NOT_WANTED` (nobody asked for it, so it is not
   reported as damaged); `compressedBytesRead + compressedSize > archive.size` → `UNREADABLE`, as today; otherwise
   **charge `compressedBytesRead += compressedSize` now, and never again for this entry**; peek `SNIFF_LENGTH = 512`
   bytes (STORED: `archive.copyOfRange(dataOffset, dataOffset + min(512, compressedSize))`; DEFLATE: the prefix mode
   below); a `ZipException` during the peek → `NOT_WANTED`; `sniffedLimitOf(name, prefix)` null → `NOT_WANTED`;
   otherwise the returned limit goes through today's checks (`uncompressedSize > limit` or past `maxTotalSize` →
   `TOO_LARGE`), then `chargedSize` is charged and the entry read whole as today, its CRC checked. A rejected entry costs
   no `chargedSize`. Charging the compressed bytes once is what bounds a hostile archive: every peek reads at most its
   own `compressedSize` of input and all of them together at most the archive's size, whatever the central directory
   says, and each produces at most 512 bytes. (Charging again on the full read, as the first draft said, would refuse a
   nested backup larger than half its archive as `UNREADABLE`.)
3. **`Inflater` prefix mode:** a `maxOutput: Int = -1` parameter (the object is `internal`, no Koin rule applies). With
   it, the output buffer starts at `maxOutput` bytes, not at `min(expectedSize, INITIAL_CAPACITY_LIMIT)` (65,534 peeks
   at 1 MiB each is 64 GiB of allocation churn); `emit`, `copyBack`, and `stored` copy only up to `maxOutput` (a back
   reference still checks `distance > outSize` first; a stored block longer than the rest is cut) and then end the
   inflate (a private flag checked by the block loop, or a private exception caught in `inflate`, never a
   `ZipException`); the declared-size and CRC checks are skipped. A stream shorter than the prefix ends normally and
   returns all of itself. `MAX_ENTRY_SIZE` stays.
4. **`ArchiveLocalSourceImpl`:** `limitOf` keeps `Skip` for hidden names and `Read(MAX_IMPORT_SIZE)` for SongbookPro's
   document; a name `maxSizeOf` gives 0 is `Sniff`; and `sniffedLimitOf` answers `ImportLimits.MAX_IMPORT_SIZE` (the
   reader's `maxTotalSize` still applies) when `LibraryFiles.isZipArchive(prefix)`, null otherwise. The nested-archive
   branch already unpacks an entry whose bytes are a zip. Plan 11 extends `sniffedLimitOf` to text.
5. **A renamed backup over 8 MB** (one rule, two places). A name read with the text limit (`.txt`, the ChordPro family,
   `.json`) whose size is over `MAX_TEXT_FILE_SIZE` is kept only when its bytes are a zip:
   - `ArchiveLocalSourceImpl.limitOf`: `Read(MAX_TEXT_FILE_SIZE)` where `declaredSize <= MAX_TEXT_FILE_SIZE` (every
     ordinary song, no peek); `Sniff` above it, where `sniffedLimitOf` answers `MAX_IMPORT_SIZE` for a zip prefix and
     `MAX_TEXT_FILE_SIZE` otherwise, so a real text over 8 MB is still reported `TOO_LARGE` by step 2's check.
   - `ImportBudget.read`: for such a name, a known `size` over the text limit (or bytes found over it) is no longer
     refused at once: the file is read with `remaining` as the limit and kept where `isZipArchive(bytes)`; anything else
     is handed back as `ImportedFile.unread(name, isTooLarge = true)` and gives back what it took, as `readUnknown`
     does. The cost is a read of at most 24 MB for a text that is then reported too large, which `readUnknown` already
     accepts for an unknown name.
6. **Desktop folder drops:** the `isInFolder && !isImportableFileName` branch goes. An unknown name inside a dropped
   folder is first peeked (`file.inputStream().use { it.readNBytes(512) }`), and read through `budget.read(...)` only
   where `ImportBudget.isWorthReadingUnknown(head)` (a new function in `data/model`, today `isZipArchive(head)`; plan 11
   adds text) says so; otherwise it is `ImportedFile.unread(name)`, as now. Without the peek a dropped photo folder is
   read whole, file by file, up to 10,000 entries of up to 24 MB each. A loose file named itself keeps today's
   `readUnknown`. The KDoc of `readAsImportedFiles` (the "rather than opened to see whether it is an archive" sentence)
   and `filesBelow` change accordingly (keep the hidden-names sentence).
7. **Web folder drops:** `listenForDrops` keeps its depth and entry caps but hands over every file (no
   `isImportable`, no empty stand-in `File`); drop the `importableExtensions` parameter. In Kotlin, `toImportedFile`
   peeks an unknown name first (`file.slice(0, 512).arrayBuffer()` in a new `js` helper) with the same
   `isWorthReadingUnknown`, but only for files that came out of a folder: the JS marks them (for example by returning
   `{file, isInFolder}` pairs, or a second array), since a loose pick or drop is read whatever it is called. If plan 09
   has removed `ACCEPTED_TYPES`, this leaves the file's `LibraryFiles` import unused; remove it, or `spotlessCheck` fails.
8. **Docs:** `data/formats/CLAUDE.md` ("Entries are asked about by name before inflating" → by name and declared size,
   or by a 512-byte peek for unknown names, each entry's compressed bytes charged once against the archive's size),
   `data/source/local/api/CLAUDE.md` (nested archives "by name or by bytes under any name but a document's" is then
   true for unknown names too: an unknown-name entry is peeked), the importing package note, and
   `presentation/src/desktopMain/CLAUDE.md` / `wasmJsMain/CLAUDE.md` (the folder rule: unknown names are peeked).

Do not read-then-refund instead of peeking: `chargedSize` is checked inside the same walk, and a 24 MiB archive of
highly compressible 8 MiB entries would be CPU-bound for minutes, since `compressedBytesRead` bounds input, not
inflation work.

## Tests

- `ZipReaderTest`: `a sniffed and rejected entry costs no budget` (a DEFLATE entry declaring 8 MiB, rejected by the
  callback → `chargedSize` unchanged, `unread` holds it as `NOT_WANTED`); `a sniffed entry that is wanted is read whole
  and charged once` (one entry that is 90% of the archive is read, not refused by the compressed-bytes bound);
  `a central directory pointing every entry at one blob peeks it once per its compressed size` (65,534 `Sniff`
  entries at one stream: all but the first `archive.size / compressedSize` are `UNREADABLE`).
- `InflaterTest`: `a prefix peek of a longer stream returns exactly the prefix` (fixed, dynamic, and a stored block
  longer than the prefix); `a prefix peek of a shorter stream returns the whole stream`; `a back reference across the
  prefix's end is cut, not refused`.
- `ArchiveLocalSourceTest`: `unpacks an archive nested under an unknown name by its bytes` (`backup.bak`,
  `Songs.backup`, `x.onsong`, and an extensionless `backup`, each a zip of `in.cho`); `a nested docx is still a
  document`; `a nested unknown file that is not an archive is reported skipped and costs nothing` (PNG bytes under
  `x.onsong`, then a song after it still fits a budget sized for the song alone); `a zip over the text limit under a
  text name is unpacked, a text over it is too large`.
- `ImportLimitsTest` (commonTest in `:data:source:local:implementation`): `a zip over the text limit under a text name
  is read whole` and `a text over its limit is still reported too large and gives its budget back`.
- `ReadAsImportedFilesTest` (presentation desktopTest): a folder holding `backup.bak` (a zip) yields its bytes; a
  folder holding `photo.heic` (non-zip bytes) yields it unread without reading past its head.
