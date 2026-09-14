# 08 · The zip inflater allocates the declared uncompressed size up front, with no cap on output

**Severity:** high (process crash on a crafted or corrupted archive) · **Area:** `:data:source:local:implementation` (zip)

## Symptom

A ~100-byte zip whose one DEFLATE entry declares `uncompressedSize = 0x7FFFFFFF` is an `OutOfMemoryError` on Android
(an `Error`, so the import's `catch (exception: Exception)` in `PrepareImportUseCaseImpl` does not catch it) and an
uncatchable trap on the web. A corrupted central directory does the same with a smaller number. Separately, a 1 MB
archive of highly compressible entries expands to ~1 GB in memory because every inflated entry of every nested level
(depth 3) is kept.

## Cause

- `Inflater.State` (`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/Inflater.kt:54`):
  `out = ByteArray(if (expectedSize > 0) expectedSize else 1024)`.
- `ZipReader.readEntries` passes the central directory's `uncompressedSize` straight through (:43, :64, :100).
- `ArchiveLocalSourceImpl.unpack` (:35–61) keeps everything.

## Fix

1. **Cap the initial allocation**, keep the growth path: in `Inflater.State`,

   ```kotlin
   private var out = ByteArray(if (expectedSize > 0) minOf(expectedSize, INITIAL_CAPACITY_LIMIT) else 1024)
   ```

   with `INITIAL_CAPACITY_LIMIT = 1 shl 20` (1 MiB). `ensureCapacity` already doubles; `output()` already trims.

2. **Cap the output per entry**: add `MAX_ENTRY_SIZE = 64 shl 20` (64 MiB) to `Inflater` and throw
   `ZipException("Entry would inflate past $MAX_ENTRY_SIZE bytes.")` in `ensureCapacity` when `required` exceeds it,
   and also up front when `expectedSize > MAX_ENTRY_SIZE`. Every song and setlist is text of a few kilobytes; 64 MiB
   is a generous ceiling.

3. **Cap the total across an archive**: `ZipReader.read` sums `uncompressedSize` of the entries as it goes and throws
   when the running total exceeds `MAX_ARCHIVE_SIZE = 256 shl 20`; `ArchiveLocalSourceImpl.unpack` does the same
   across nesting levels with a counter threaded through the recursion (nested archives count their inflated bytes
   too). A rejected nested archive is already skipped with a log line; a rejected top-level one becomes a skipped
   file in the plan (it is caught in `PrepareImportUseCaseImpl` and added to `skippedFileNames`).

4. On Android only, `OutOfMemoryError` from anywhere else in the import can still happen with a legitimately huge
   archive; not worth catching `Error`. The caps above make it unreachable through this path.

5. Tests in `data/source/local/implementation/src/commonTest` (there is a zip test suite): a hand-built archive whose
   central directory declares 2 GiB for a 10-byte deflate stream must throw `ZipException`, not allocate; an archive
   over the total cap must throw.

## Verification

Unit tests; then import a legitimate 300-song export on the web build and confirm it still works.
