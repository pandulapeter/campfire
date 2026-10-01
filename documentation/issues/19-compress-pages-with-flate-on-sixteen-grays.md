# Compress pages with Flate on sixteen gray levels instead of run lengths on 256

**Kind:** performance / output  ·  **Severity:** high  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintPdfWriter.kt`, new `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintDeflater.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintPdfWriterTest.kt`, new `PrintDeflaterTest.kt` (commonTest) and a JVM round-trip in desktopTest, `presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRendererTest.kt`, root `CLAUDE.md` (Printing)
**Challenged:** amended — the sizes were measured with zlib's dynamic Huffman; the fixed-code encoder planned here measures ≈ 82 KB/page at chain depth 8 and ≈ 68 KB with depth 32 and lazy matching (re-measured on the live run's demo pages), so D2's numbers are corrected. The bit-level format (nibble order, padding, zlib header, Adler-32 byte order, LSB-first bit packing), the quantisation, where the encoder suspends, `PrintBytes` visibility and the plan-17 test's decoder are now specified.

**Decision D2** — taken 2026-10-01: the recommended option, as written below.

## Problem

Antialiased glyph edges break runs, so RunLength does little: measured 207 KB per page, 29.2 MB for a 141-page
setlist — too large to mail to a band, and every byte is held in memory several times on the way to the file.
Measured on the same pages: Flate on 8-bit 77 KB, **16 levels + Flate 55 KB (3.7× smaller)**, 1-bit + Flate 24 KB.
Sixteen grays at 216 dpi are indistinguishable from 256 in print.

## Fix

- **Packing.** Quantise each pixel to `q = (gray * 15 + 127) / 255` (rounding; `shr 4` would turn every 240–254 pixel
  white and darken mid-grays by up to 17), and pack two pixels a byte, **first pixel in the high nibble**, each row
  starting on a byte boundary (`rowBytes = (width + 1) / 2`; an odd width's last low nibble is 15, white). The packed
  page (`rowBytes * height`, 2.25 MB for A4) goes into one array `pdf()` allocates once and reuses, like plan 17's.
- **`PrintDeflater`** (internal, new file, pure Kotlin): a zlib stream — header bytes `78 01` (CMF/FLG, a multiple of
  31), one deflate block with `BFINAL = 1`, `BTYPE = 01` (fixed codes), end-of-block 256, then the Adler-32 of the
  uncompressed bytes **big-endian**. Bits are packed LSB first; Huffman codes are written most significant bit first
  (reversed into the LSB-first stream), extra bits least significant first. LZ77 over a 32 KB window with a 3-byte hash
  (`head`/`prev` `IntArray`s, allocated once per writer), chain depth ≤ 32 with one-step lazy matching (≈ 68 KB/page;
  depth 8 without lazy matching is simpler and ≈ 82 KB), **stopping the chain at the first match of 258** — every
  white run then costs one lookup and one 258-byte compare per 258 bytes (length code 285 + distance code 0: 13 bits).
  Distances must not reach before the start of the input.
- **Cost.** About 2.25 MB of input a page; with the 258 cutoff the white majority is ~1–2 operations a byte and the
  inked rows (~10–25 %) up to depth × compare length. Estimate: 10–25 ms a page on the JVM, 30–80 ms on wasm — 4–11 s
  for a 141-page setlist on the web, comparable to rasterising it, which plan 27's progress covers.
- **Suspending.** `PrintPdfWriter.addPage` becomes a `suspend fun` and the deflater yields (`yield()`, which also checks
  cancellation) after every 256 KB of input; plan 17's per-band yields in `pdf()` stay. `addPage` still consumes its
  input before returning, so the reused arrays are free for the next page. `PrintPdfWriterTest` moves into `runTest`.
- Plan 18's `PrintBytes` becomes `internal` (the deflater writes into the writer's scratch `PrintBytes`).
- Stream dictionary: `/ColorSpace /DeviceGray /BitsPerComponent 4 /Filter /FlateDecode` (no `/DecodeParms`). Remove
  `encodePrintRuns` and its test. Update the writer's KDoc and the root `CLAUDE.md` Printing paragraph: the page
  images are 16-level grayscale, no longer lossless.
- `:data:source:local:implementation` has an `Inflater` but `:presentation` cannot depend on it, so the test
  round-trips with `java.util.zip.Inflater` in `desktopTest`; commonTest pins known vectors (empty input →
  `78 01 03 00 00 00 00 01`, "a" × 1000, bytes 0..255) and the 4-bit packing of a 3-pixel row (`0, 255, 136` →
  `0x0F 0x8F`).

## Tests

As above, plus `PrintRendererTest`: tighten the size assertion to under 120 KB per page for its fixture, inflate a
page's stream to `ceil(width / 2) * height` bytes, and switch plan 17's page-comparison test from its RunLength decoder
to inflating and unpacking nibbles. A desktopTest round trip of random bytes, of 2 MB of `0xFF`, and of a buffer whose
matches would reach back exactly 32 768 bytes.

## Manual check

Export the demo setlist; open the PDF in Preview, Chrome, Adobe Reader and on Android/iOS Files; print one page and
compare with a print from before the change. Note the file size.
