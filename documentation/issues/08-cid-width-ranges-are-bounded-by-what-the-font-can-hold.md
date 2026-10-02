# Bound the expansion of a CID font's /W ranges instead of writing 65,536 map entries per range
**Challenged:** amended — a per-font cap alone still lets one shared `/W` array (an indirect object) be expanded by every font that names it: about 50,000 tiny font dictionaries x 131,072 writes is hours, cheaper per byte than the measured attack; a document-wide budget for font tables (widths and ToUnicode entries alike, kept on `PdfFile`, thrown as plan 07's `PdfLimitException`) now caps the total.

**Kind:** performance (hostile input)  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfFonts.kt`, `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfFile.kt`, `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`, `data/source/local/implementation/CLAUDE.md`

## Problem
`PdfFont.cidWidths()` expands every `first last width` range into a `HashMap`:

```kotlin
for (code in first..last) widths[code] = width
```

(~95). A `/W` array holds up to 100,000 values (the array parse limit), so about 33,000 ranges of `0 65535 500` mean 2.2 x 10^9
map writes for one font, with no yield. Overlapping ranges are the only way to make this large, since distinct codes
are at most 65,536.

Measured on HEAD (probe, Type0 font with a `/W` of 33,000 ranges of `0 65535 500`, a 460 KB file): `extract` spent **12.5 s**
before failing for another reason; a 16 MiB file may carry about 35 such fonts, i.e. minutes, and a Wasm build is slower.

## Fix
Count what is expanded and stop: in `cidWidths()` keep `var expanded = 0L`, add `last - first + 1` per range and the number of
array items per explicit list, and `require(expanded <= MAX_EXPANDED_WIDTHS)` with `private const val MAX_EXPANDED_WIDTHS = 131_072`
(twice the 65,536 codes a CID font can have: non-overlapping real fonts are at or under 65,536, so only a font that writes
the same codes over and over is refused; give it a KDoc saying exactly that). A font that fails here throws
`IllegalArgumentException` like the other malformed-font `require`s; with plan
`09-one-bad-page-or-font-does-not-fail-the-document.md` only that font is lost, until then the whole file, which is the
current behaviour for any bad font. Do not switch to a range table with binary search: overlapping ranges have a
"later one wins" meaning that a sorted table does not preserve, and the bounded expansion is both simple and fast
(131,072 puts is a few milliseconds).


**Document-wide bound (added by the challenge).** `PdfFont` instances are cached per font dictionary, but nothing stops
a file from declaring tens of thousands of distinct, tiny font dictionaries (`<< /Subtype /Type0 /DescendantFonts [d] >>`,
a few dozen bytes each, up to the 100,000-object limit) whose descendants all name **one** indirect `/W` array, and one
shared `ToUnicode` stream does the same for `cmap()` (up to 100,000 inserts per font). Each `Tf` naming a new one pays
the full per-font cost again. So also charge a counter that lives on the `PdfFile` every font already holds
(`internal fun chargeFontTables(entries: Int)` next to `decodedSize`): every width written in `cidWidths()` and
`simpleWidths()`, and every `insert` in `cmap()`, adds to it, and above `MAX_FONT_TABLE_ENTRIES = 1 shl 24` (16.8 M
map writes, about half a second on the JVM; a real document with dozens of fonts writes well under a million) it
throws `PdfLimitException("PDF font tables too large")` from `07-content-work-budget-shared-by-pages-and-forms.md`,
which runs before this plan, so `09-one-bad-page-or-font-does-not-fail-the-document.md` treats it as fatal rather than
as one bad font. Charge a range before expanding it, so the refusal comes before the writes.

## Tests
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`: `overlappingCidWidthRangesAreRejectedQuickly`: build `PdfFont(file, dictionary)` directly for a Type0
font whose descendant has `/W [ (0 65535 500) x 33000 ]`, `assertFailsWith<IllegalArgumentException>` and `measureTime < 2.seconds`
(12.5 s today). `manyFontsSharingOneWidthArrayHitTheDocumentBudget`: one indirect `/W [0 65535 500]` named by
300 distinct Type0 fonts, a content stream selecting each of them once (`/F1 10 Tf` ... `/F300 10 Tf`), expects
`PdfLimitException` (300 x 65,536 is past 2^24) quickly. And a positive test: a font with `/W [0 [500 600] 10 20 700]` still reports width 500 for code 0, 600 for code 1 and 700 for
code 15 (explicit lists and a range).

## Manual check
None; Campfire's own and LibreOffice's PDFs (Identity-H fonts with `/W`) are covered by `DocumentGoldenTest`.
