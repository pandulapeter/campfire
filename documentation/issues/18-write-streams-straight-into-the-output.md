# Write image streams straight into the output instead of through three copies

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintPdfWriter.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintPdfWriterTest.kt`

## Problem

A page's compressed image is copied four times before it is in the file: `encodePrintRuns` grows a doubling buffer and
`result()` copies it; `stream()` copies it into `body` and `body.result()` copies again; `objectBytes` copies into
`output`; `finish()` copies the whole document once more while the doubling buffer (up to 2× the file) is still alive.
For an 80 MB export that is a ~208 MB peak in the writer alone, on top of the save path's own copies (web: +2×).

## Fix

- `stream()` writes the dictionary, then the data, directly into `output` (record the object offset first); no `body`.
- The encoder takes the destination `PrintBytes` and appends to it; since `/Length` must precede the data, encode into
  one reusable scratch `PrintBytes` owned by the writer (`reset()` between pages) and append from it — one copy.
- `finish()` keeps `result()` (one unavoidable copy with the `ByteArray` contract) but `PrintBytes` grows by 1.5× above
  16 MB, and guards `size + count` against `Int` overflow with a clear `IllegalStateException("The PDF is too large")`.

## Tests

`PrintPdfWriterTest`: existing xref/offset test still passes; a test for `PrintBytes.reset()` reuse producing identical
output for two identical pages; the overflow guard through a tiny injected limit if one is added, otherwise none.

## Manual check

Covered by plan 17's memory check.
