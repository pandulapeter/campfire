# Charge the spaces that monospace gap padding inserts to the text budget, so a PDF cannot expand to hundreds of megabytes
**Challenged:** sound

**Kind:** bug (out-of-memory on hostile or odd input; an `Error`, not caught by the importer)  ·  **Severity:** high  ·  **Platforms:** all (Android's ~192 MB heap and the web's Wasm memory fail first)
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractor.kt`, `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`, `data/source/local/implementation/CLAUDE.md`

## Problem
`PdfTextExtractor.buildLines` / `column()` bridges a gap between two glyphs with spaces, and for a monospace span the count
is taken from the gap:

```kotlin
val count = if (span.isMonospace) (gap / (span.size * 0.6)).roundToInt().coerceIn(1, 1000) else 1
spans += ExtractedDocument.Span(" ".repeat(count), previous!!.end, span.start, span.size, isMonospace = span.isMonospace)
```

(`PdfTextExtractor.kt` ~249). `show()` charges only the glyphs it records (`textBytes += value.length * 3`, `MAX_GLYPHS`),
never these spaces. `size` is clamped only to >= 0.1, so a Courier font at 0.1 pt with 6 pt of gap per glyph asks for
1000 spaces per glyph: the document limit of 1,000,000 glyphs becomes 10^9 characters (2 GB of strings) from a file of a
few megabytes. A glyph needs only a few bytes of content (`[(A) -600000] TJ`) and the stream is Flate-compressed, so the
16 MiB input limit is no obstacle. `DocumentLocalSourceImpl` catches `Exception` only, so the resulting
`OutOfMemoryError` takes the app down.

Measured on HEAD (throwaway probe; five 1000 pt full-width lines to keep `gutter()` from splitting the hostile row, then
`BT /F1 0.1 Tf ... [(A) -600000] TJ` x n): n = 30,000 glyphs (about 600 KB of content) returns a document of
**30,046,000 characters in 0.29 s, retaining +181 MB of heap**; n = 150,000 took 2.2 s and about 900 MB (survived only
because the JVM test heap is 4 GiB; Android's is ~192 MB). Realistic 1,000,000 glyphs would be about 6 GB.

## Fix
1. Thread the budget into line building: change `private fun buildLines(glyphs: List<Positioned>)` to take a
   `charge: (Int) -> Unit` (a local `fun charge(characters: Int)` in `extract` that does
   `textBytes += characters * 3L; require(textBytes <= ImportLimits.MAX_TEXT_FILE_SIZE) { "PDF text too large" }`) and pass it
   through the recursive calls (`columns`, `column`). In `column()` call `charge(count)` BEFORE `" ".repeat(count)`, for the
   non-monospace single space too.
2. Leave the `coerceIn(1, 1000)` as it is: a monospace line of 233+ columns is legitimate on a landscape page at 6 pt
   (842 / 3.6), so lowering it would change real output. The charge, not the cap, is what bounds the work: at
   `MAX_TEXT_FILE_SIZE` (8 MiB) / 3 the whole document may hold 2.8 M characters, padding included, which is the same
   ceiling the glyphs already live under.
3. This failure is a limit, not a format error: when plan `07-content-work-budget-shared-by-pages-and-forms.md` lands, make it
   throw that plan's `PdfLimitException` so plan `09-one-bad-page-or-font-does-not-fail-the-document.md` treats it as fatal. Until
   then `require` is right (the document ends as "not readable", which is the desired outcome).
4. Related, in another lane (do not edit here): `ChordSheetConverter.render` (~85 in `:chordpro`) pads columns the same way.
   It works on text the extractor has already bounded (2.8 M characters), so it needs no change for this plan; mention it
   to whoever owns the chordpro lane only if they raise the budget.

## Tests
In `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt` (commonTest): `monospaceGapPaddingCountsAgainstTheTextBudget`. Build the content string
as in the probe: five lines `BT /F1 1000 Tf 1 0 0 1 0 <y> Tm (<3400 x A>) Tj ET` at y = 100, 3100, ... then
`BT /F1 0.1 Tf 1 0 0 1 0 -500 Tm ` + `[(A) -600000] TJ `.repeat(30_000) + `ET`, wrap with `PdfTestWriter.song`. Today `extract`
returns 30 M characters; after the fix it must throw `IllegalArgumentException` (via `DocumentLocalSourceImpl.extract`
returning null) and well inside `< 2.seconds` and without the 180 MB allocation. Also a regression test that an ordinary
two-column Courier page still gets its spaces (the existing golden tests cover this; run them).

## Manual check
Import `campfire-columns-dense.pdf` and `songbook.pdf` from `src/desktopTest/resources/document` on the desktop app: output
unchanged. Optionally import a generated hostile file like the test's on an Android emulator: the import reports an
unreadable document instead of the app being killed.
