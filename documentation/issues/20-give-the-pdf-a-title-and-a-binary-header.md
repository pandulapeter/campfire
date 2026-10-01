# Give the PDF a title and the binary header line the format recommends

**Kind:** output quality  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintPdfWriter.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRenderer.kt` (`pdf` signature only), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt` (the `pdf(snapshot)` call), `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintPdfWriterTest.kt`

## Problem

`output.text("%PDF-1.4\n%Campfire\n")` — the second line should hold four bytes ≥ 128 so mail gateways and transfer
tools treat the file as binary. There is no `/Info` dictionary, so viewers and print dialogs title the document by its
file name (or "Untitled"), and screen readers announce nothing.

## Fix

- Header: `%PDF-1.4\n%` + bytes `E2 E3 CF D3` + `\n`.
- `PrintPdfWriter(width, height, title)`: write `<< /Title <FEFF…> /Producer (Campfire) >>` as an object in `finish()`
  (the title as a UTF-16BE hex string, so no escaping and any script works) and add `/Info n 0 R` to the trailer.
  No `/CreationDate`: common code has no clock dependency here and the file's own date serves.
- `PrintRenderer.pdf(document, title)`; the sheet passes `source.title`.

## Tests

`PrintPdfWriterTest`: the output contains `/Title <FEFF` followed by the hex of a title with `ő` and `)`; byte 10–13
are ≥ 128; the xref still resolves every object including the info one.

## Manual check

Open an exported PDF in Preview → Tools → Show Inspector: the title is the song's or setlist's.
