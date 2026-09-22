# 47 · A crafted zip of a few megabytes keeps an import busy for half an hour (the web page frozen all that time)

**Severity:** hang (all platforms; on the web the page is frozen, since unpacking runs on `Dispatchers.Default`, the
page's only thread, and nothing in `ZipReader` yields or checks for cancellation; on Android and desktop the import's
progress never ends and cannot be cancelled; crafted archive only — the classic non-recursive zip bomb shape) ·
**Area:** `:data:source:local:implementation` (`zip/Inflater.kt`, `zip/ZipReader.kt`, `zip/ZipContent.kt`,
`source/ArchiveLocalSourceImpl.kt`)

## Symptom
Two archives, each well inside every limit an import applies (`ImportLimits.MAX_IMPORT_SIZE`, 24 MiB):

1. **Lying sizes.** One local header whose DEFLATE data is 24 MiB of zeros (24,464 bytes compressed), and N central
   directory records named `a.cho`, all pointing at that one local header, each declaring an uncompressed size of
   10 and a CRC of 0. Measured on desktop: 34 ms per record (1 → 33 ms, 10 → 350 ms, 40 → 1.35 s, also through
   `ArchiveLocalSourceImpl.unpack`). The central directory allows 65,534 records at ~51 bytes each, a 3.4 MB archive:
   **≈ 37 minutes** on a desktop JVM, several times that in Wasm and on a phone, and 65,534 allocations of 24–40 MiB
   on the way. Every record ends up "unreadable" and nothing is imported.
2. **Empty stored blocks.** A DEFLATE stream of ~20 MiB of empty non-final stored blocks (`00 00 00 FF FF`) and a
   final empty one, and N records declaring size 0 pointing at it: 6 ms per record per 4 MiB of input measured
   (100 records over 4 MiB → 630 ms), ≈ 30 minutes for 65,534 records over 20 MiB. These entries are even *read*
   successfully (they are empty), so no declared-size check helps.

## Cause
Nothing bounds the total **work** of one archive, only what it successfully produces:

- `zip/Inflater.kt:116-128`: the output cap is `MAX_ENTRY_SIZE` (24 MiB) for every entry, whatever it declared; the
  declared size is only compared once the stream has ended (`:41`):

  ```kotlin
  if (required > MAX_ENTRY_SIZE) {
      throw ZipException("Entry would inflate past $MAX_ENTRY_SIZE bytes.")
  }
  ```

  So an entry declaring 10 bytes is inflated to 24 MiB before it is found out. `ZipReader`'s own KDoc (`:29-31`)
  claims otherwise: "the inflater holds an entry to what it declared."
- `zip/ZipReader.kt:73-93`: the budget is charged only for entries that were read successfully (`totalSize +=
  uncompressedSize` after `readData` returned), so a damaged entry costs its full inflation and nothing of the budget,
  and the next one is tried from scratch:

  ```kotlin
  else -> try {
      entries += ZipEntry(name = name, bytes = readData(...))
      totalSize += uncompressedSize
  } catch (exception: ZipException) { ... unread += ... }
  ```

- Nothing relates the entries' compressed sizes to the archive: records may all point at the same bytes, so the input
  the inflater walks is `records × compressedSize` rather than the archive's size.

Before ddcb86e3 ("…import the rest of an archive holding an unreadable entry") the first damaged entry ended the whole
archive, which bounded this by accident; making damaged entries cost only their name is what made them repeatable.

## Fix
Make the work of one archive linear in its size plus `maxTotalSize`, in three small steps:

1. **The inflater holds an entry to what it declared.** In `Inflater.State` add
   `private val outputLimit = if (expectedSize >= 0) expectedSize else MAX_ENTRY_SIZE` and use it in `ensureCapacity`
   for both the check and the `copyOf(minOf(newSize, outputLimit))`, with the message
   `"Deflate stream produces more than the $outputLimit bytes it declared."`. The size check after the stream (`:41`)
   stays, for a stream that produces fewer. `expectedSize > MAX_ENTRY_SIZE` is still refused up front.
2. **An entry is charged before it is read.** In `ZipReader.read`, rename `totalSize` to `chargedSize` and move the
   `+= uncompressedSize` in front of the `try`, with a comment: a damaged entry has cost what it declared all the same,
   and without this one damaged entry can be read as many times as the central directory names it.
3. **Entries cannot share their bytes.** Keep `var compressedBytesRead = 0L`; a new branch before the `else`:

   ```kotlin
   // The entries of an archive never share their data, so together they cannot take up more of it than there is.
   // A central directory whose entries do is pointing several of them at the same bytes, which only a zip bomb does.
   compressedBytesRead + compressedSize > archive.size -> unread += UnreadZipEntry(name, UnreadZipEntry.Reason.UNREADABLE)
   ```

   and `compressedBytesRead += compressedSize` next to the charge in step 2.
4. **The charge crosses nesting levels.** Add `val chargedSize: Long` to `ZipContent` (KDoc: what the archive's reads
   cost the budget, damaged entries included) and in `ArchiveLocalSourceImpl.unpack` (`:68`) replace
   `inflated.count += content.entries.sumOf { it.bytes.size.toLong() }` with `inflated.count += content.chargedSize`.
   For an archive whose entries all read, the two numbers are equal (the inflater verifies each entry's size), so
   nothing an honest archive imports changes.

Do **not** add a wall-clock timeout or a cap on the number of entries: an export of 5,000 songs is a legitimate archive
of 5,000 entries, and the bounds above are exact. Do not stop at the first damaged entry again (ddcb86e3 is right that
the songs next to a damaged `.DS_Store` are still good).

## Tests
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipReaderTest.kt`,
with a helper `overlappingArchive(stream: ByteArray, count: Int, declaredSize: Long, crc: Long, method: Int = 8)` that
writes one local header with `stream` as its data and `count` central directory records pointing at offset 0 (the
existing `deflatedArchive` is the template):
- `an entry that inflates past what it declared is left out without inflating the rest`:
  `Inflater.inflate(stream, expectedSize = 3)` for the existing 10-byte "hello" stream throws a `ZipException` whose
  message names 3 bytes (today it names "produced 5 bytes instead of the expected 3" after the fact).
- `entries sharing their data are read once`: a stored-block stream of 1,000 bytes (`01 E8 03 17 FC` + 1,000 bytes)
  with its correct CRC and size, three records: `entries.size == 1`, the other two `UNREADABLE`.
- `a damaged entry is charged what it declared`: two records over the 10-byte "hello" stream (not overlapping —
  write it twice), the first with a wrong CRC and both declaring 5, `maxTotalSize = 9`: the first is `UNREADABLE`, the
  second `TOO_LARGE` (today the second is read).
- `many records lying about one stream cost at most the archive`: 200 records over a stream that inflates to 1 MiB
  (hand-built: 16 stored blocks of 65,535 bytes of `a`, each `00 FF FF 00 00` + data, the last one's header byte `01`),
  declaring 10 bytes and a CRC of 0 each: `entries` is empty and all 200 are `UNREADABLE` (measured after the fix:
  2 ms; today: 200 inflations of 1 MiB).
`desktopTest/…/source/ArchiveLocalSourceTest.kt`:
- `a nested archive's damaged entries count against the whole import`: an outer archive (written with `ZipWriter`)
  holding two nested zips in this order, `a.zip` with one damaged `x.cho` of `D = 100` bytes (written with
  `ZipWriter`, which stores, and one byte of its content changed afterwards so that its checksum fails), and `b.zip`
  with one good 20-byte `song.cho`; call `unpack` with `maxSize = a.size + b.size + D + 10`, and assert that the
  `song.cho` it returns `isTooLarge`. Today `song.cho` imports; after the
  fix it is reported too large. (The outer archive's own entries are all read before any nested one is opened, so the
  song has to sit in a *second* nested archive for the charge to reach it.)
Existing tests (`stopsReadingAtTheTotalLimit`, `leavesOutADeclaredSizeTheStreamDoesNotProduceWithoutAllocatingIt`,
the JVM round trips, `InflaterTest`) must stay green unchanged (verified in a worktree with all four steps applied;
`leavesOutAnEntryWhoseEndOverflowsAnInt` now leaves its entry out through step 3's branch rather than `readData`, still
as `UNREADABLE`).

## Verify
1. `./gradlew :data:source:local:implementation:desktopTest`.
2. Build the 3.4 MB "lying sizes" archive (a few lines of Python with `zlib.compressobj(9, zlib.DEFLATED, -15)` over
   24 MiB of zeros and 65,534 central records pointing at offset 0) and import it on desktop and on the web build:
   the import finishes at once reporting the entries as unreadable; the web page never freezes.
3. Export the library from a device with a few hundred songs and a setlist and import it on another: everything
   imports as before.

## Docs
- `data/source/local/implementation/CLAUDE.md`, `zip/` bullet, after "the caller says how much the whole import may
  still unpack to (…24 MiB, nested archives included)": add "— charged before an entry is read, so a damaged entry
  costs what it declared, and an inflating entry is stopped at the size it declared rather than at the 24 MiB any
  entry may reach; the entries' compressed sizes may not add up to more than the archive holds, which is what a
  central directory pointing many entries at the same bytes (a zip bomb) runs into."
- `ZipReader.read`'s KDoc (`:29-31`) then states what is true.

## Touches
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/Inflater.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipReader.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipContent.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ArchiveLocalSourceImpl.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipReaderTest.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ArchiveLocalSourceTest.kt`
- `data/source/local/implementation/CLAUDE.md`

## Depends on
Nothing.
