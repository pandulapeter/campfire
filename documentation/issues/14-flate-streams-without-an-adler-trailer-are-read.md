# Read Flate streams that are missing their 4-byte Adler-32 trailer
**Challenged:** sound

**Kind:** robustness  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfFilters.kt`, `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfFiltersTest.kt`

## Problem
`PdfFilters.decode` hands the inflater `length = bytes.size - 6`, i.e. it assumes a 2-byte zlib header and a 4-byte checksum are
present, and `require(bytes.size >= 6)`. A stream whose trailer is missing or truncated (damaged files, writers that cut the
checksum, a `/Length` that stops early) loses the last four bytes of real deflate data, and the inflater ends mid-block with "Deflate
stream ended". The checksum itself is never verified, so it buys nothing. (Source of the reviewer's remark; verified by reading,
no fixture of such a stream ships.)

## Fix
Inflate `bytes.size - 2` bytes after the header (`Inflater.inflate(bytes, offset = 2, length = bytes.size - 2)`) and relax the guard to
`bytes.size >= 4`. `Inflater` stops at the end of the final block and ignores the rest of its input (checked: `inflate()` loops `while (last == 0)`
and never inspects what is left), so the trailer, an EOL, or nothing at all are all fine. Keep rejecting a preset dictionary
(`flags and 32`) and a bad header check.

## Tests
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfFiltersTest.kt`: encode with `PdfTestWriter.storedZlib` (existing helper) and decode (a) as is, (b) with the 4 trailer bytes
chopped off, (c) with 4 junk bytes appended: all three return the original bytes; a stream of fewer than 4 bytes is rejected.

## Manual check
None.
