# Accept a PDF whose "%PDF-" header is not at byte 0
**Challenged:** amended — the fix now says where the new `bytes` property must sit (first, before the `PdfStreamEnds` index of plan 01 and before `init`) and that the size limit is checked on the input; the approach is sound: a producer whose offsets count the junk too fails the xref/object-number checks and is read by the recovery scan.

**Kind:** robustness  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfFile.kt`, `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`

## Problem
`PdfFile.init` requires `bytes.latin1(0, minOf(5, bytes.size)) == "%PDF-"`. The PDF specification (and every reader) allows the
header to sit anywhere in the first 1,024 bytes, and mail gateways, MIME wrappers and some generators prepend a line. Such a file
is "not readable" here and opens everywhere else.

## Fix
Find the header: `val header = bytes.indexOfHeader(limit = 1024)` (a plain scan of the first 1,024 bytes for `%PDF-`; require it
found). Per the specification every offset in the file (`startxref`, xref entries) is relative to the header, so when `header > 0`
work on `bytes.copyOfRange(header, bytes.size)` instead (a one-off copy of at most 16 MiB); turn the constructor property
`private val bytes` into a parameter (`input`) and a `private val bytes = ...` initialised from it, declared as the
**first** member of the class: Kotlin runs property initialisers and `init` blocks in declaration order, and the
`PdfStreamEnds` index that `01-recovery-scan-finds-stream-ends-once.md` adds as a property must be built over the
shifted array, not the input. Check `MAX_DOCUMENT_FILE_SIZE` on the input before searching. Keep the `%PDF-` test as
the only acceptance rule (no version parsing). The recovery scan finds objects wherever they
are, so a file whose offsets are relative to byte 0 instead still reads through it.

## Tests
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`: `aHeaderAfterJunkIsAccepted`: `"junk line\n".encodeToByteArray() + PdfTestWriter.song("BT /F1 10 Tf (hi) Tj ET")`
extracts "hi"; the same junk 2,000 bytes long is still rejected (`assertFailsWith<IllegalArgumentException>` from `PdfFile`).

## Manual check
None.
