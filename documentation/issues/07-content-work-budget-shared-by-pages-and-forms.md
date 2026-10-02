# Give a PDF one work budget for all the content it interprets, so reused forms and shared page contents cannot multiply the cost
**Challenged:** amended — stream input is charged too (step 4, added after the challenge for streams sharing one far `endstream`); page content was charged twice (the concatenated length and again at the top of `interpret`), which halved the headroom a legitimate document gets; each byte is now charged once (pages before the concatenation, forms at `Do`), the charge yields every 4 MiB so a web import stays responsive and cancellable, and plan 02's padding charge joins the list of requires that become `PdfLimitException`.

**Kind:** performance (hostile input)  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractor.kt`, `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfFile.kt`, `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfSyntax.kt`, `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`, `data/source/local/implementation/CLAUDE.md`

## Problem
Operators are bounded per document (`require(++operators < 2_000_000)`), glyphs are bounded, and the decoded size of every
distinct stream is bounded (`decodedSize <= MAX_IMPORT_SIZE`), but **re-reading the same bytes is not**:
- `"Do"` calls `interpret(file.decode(form), ...)` for a form XObject each time it is invoked. `decode` is cached, but
  `interpret` builds a new `PdfSyntax` over the whole form and scans it again, and the per-instance `steps` budget of
  `PdfSyntax.next` (2,000,000) restarts for each. A form whose content is mostly whitespace/comments (no operators are
  counted, `skip()` is not charged) and a page that says `/X Do` 1.9 million times (6 bytes each: 11 MB of content,
  `operators` cap 2,000,000) costs 1.9 M x the form's length.
- Every page concatenates its `/Contents` into a fresh `ByteArray` (`val data = ByteArray(length.toInt())`) and interprets it,
  and many pages may name the same `/Contents` object: 2,000 pages (the page cap) x one 12 MB stream.

Measured on HEAD (probe): a 5 MB blank form invoked 100 times 1.26 s and 400 times 4.8 s (12 ms per `Do`, linear, so
1.9 M `Do`s is about 6 hours); 100 pages sharing one 12 MB contents stream (a 12 MB file) 1.25 s, 12.5 ms per page, so
2,000 pages is 25 s on the JVM and several minutes in Wasm. Nothing yields inside a form or inside `skip()`.

## Fix
1. Add `internal class PdfLimitException(message: String) : IllegalArgumentException(message)` (in `PdfTextExtractor.kt`
   or a small `PdfLimits.kt`): "a budget ran out", as opposed to "this object is malformed"; extending
   `IllegalArgumentException` keeps every existing test and the importer's catch working. Plan
   `09-one-bad-page-or-font-does-not-fail-the-document.md` depends on it, so it lives here.
2. In `extract`, `var interpretedBytes = 0L` charged by a local `suspend fun charge(bytes: Long)` that throws
   `PdfLimitException("PDF content work limit")` above `MAX_INTERPRETED_BYTES = 4 * ImportLimits.MAX_IMPORT_SIZE`
   (96 MiB, a new `internal const` next to `MAX_GLYPHS`, with a KDoc: every distinct stream fits in the 24 MiB decoded
   cap, so this leaves about three times that for header/footer forms and contents shared between pages before the
   document is called hostile). Charge **each byte once**: in `page()`, `charge(length)` right after `length` is summed
   and **before** `ByteArray(length.toInt())` is allocated (so 2,000 pages naming one 12 MB stream stop after about
   eight pages instead of allocating them all); in the `Do` branch, `charge(formData.size)` for the decoded form before
   `interpret` runs it. `interpret` itself charges nothing, or a page's content would count twice. Inside `charge`,
   `yield()` whenever `interpretedBytes` crosses another 4 MiB: whitespace and comments in a form or a contents
   stream are no operators, so today nothing yields while they are scanned, and on the web this is the one thread.
3. Convert the other budget `require`s in `extract` and `PdfFile` to `PdfLimitException` so plan 09 can tell them from format
   errors: `shown <= MAX_SHOWN_GLYPHS`, `textBytes`/`glyphCount`, **the padding charge that
   `02-monospace-gap-padding-is-charged-to-the-text-budget.md` added in `buildLines`** (it is the same text budget, and
   under plan 09 a plain `require` there would be caught as one page's failure and the next page would fail the same
   way), `operators`, `pages.size < 2000`, `PdfFile`'s `decodedSize` and `objects.size < MAX_OBJECTS`, the
   `length <= MAX_IMPORT_SIZE` in `page()`, and `PdfSyntax`'s `steps` (`++steps <= 2_000_000`; its `depth` and size
   checks stay plain `require`). `PdfFile`'s own `catch (_: IllegalArgumentException)` blocks (init, `resolve`, `scan`)
   keep catching these as they do today, since `PdfLimitException` is one; that is unchanged behaviour, not a leak.
4. Charge stream **input** too, not only decoded output: `PdfFile.decode` (or `PdfFilters.decode`'s caller) copies each
   stream's whole encoded range before inflating it, and after plan 01 many small streams that fall back to one shared
   far-away `endstream` each have a range as long as the file, so streams × file size is copied while `decodedSize`
   stays small. Add the encoded range length to a document-wide `encodedBytes` counter in `PdfFile`, checked before the
   copy against the same 96 MiB (`PdfLimitException("PDF stream input limit")`); a stream read again from the
   `decoded` cache is not charged again. Test (d) `manyStreamsSharingOneFarEndstreamHitTheInputBudget`: 200 objects
   `N 0 obj<</Length 999999999>>stream\nx` and one `endstream` after 1 MB of padding at the end, each named as a page's
   contents, throws `PdfLimitException` (or returns null) in `< 5.seconds`.
5. Add the limit to the `document/` paragraph of `data/source/local/implementation/CLAUDE.md` ("... operator, glyph and output limits"): "and one
   work budget of 96 MiB of interpreted content per document, so re-reading a form or a shared contents stream is paid for".
   The root CLAUDE.md states no such number.

Dependency: execute after `06-pdf-values-hash-once-so-font-and-stream-caches-are-cheap.md` (it makes `activeForms` cheap) and before
`09-one-bad-page-or-font-does-not-fail-the-document.md`.

## Tests
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`: (a) `aFormInvokedManyTimesHitsTheWorkBudget`: form stream of 5 MB of spaces (`PdfTestWriter.stream(" ".repeat(5_000_000), "/Type /XObject /Subtype /Form")`),
page `BT /F1 10 Tf (hi) Tj ET` + `"/X Do "`.repeat(40) with `/X` in the page's `/XObject` resources (build the file
with `PdfTestWriter` directly; `song()` has fixed resources): 40 x 5 MB = 200 MB, over the 96 MiB budget; assert
`extract` throws `PdfLimitException` in `< 5.seconds` (the budget stops it after about 19 forms; today all 40 are read
and "hi" comes back, so the test fails before the fix). (b) `aSmallFormUsedOnEveryPageIsFree`: 300 pages each
`Do`-ing a 50 KB form still extracts. File size must stay under 16 MiB (`PdfFile` refuses larger), so the 5 MB form is
fine. (c) `sharedContentsAreChargedBeforeTheyAreCopied`: 50 pages naming one 3 MB contents stream (150 MB) throws
`PdfLimitException`.

## Manual check
Import `songbook.pdf` and the Campfire export PDFs: unchanged.
