# Memoize the hash of PDF dictionaries, arrays and streams so the font, decode and form caches stop deep-hashing on every lookup
**Challenged:** sound

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfSyntax.kt`, `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`

## Problem
The caches in `PdfTextExtractor.extract` and `PdfFile` are keyed by data classes that hash their whole contents:
`fonts = mutableMapOf<PdfDictionary, PdfFont>()` (`fonts.getOrPut(it) { PdfFont(file, it) }` on **every** `Tf` operator),
`PdfFile.decoded = mutableMapOf<PdfStream, ByteArray>()` (every `file.decode`) and `activeForms = mutableSetOf<PdfStream>()`
(every `Do`). `PdfDictionary`, `PdfArray` and `PdfStream` are data classes over `Map`/`List`, so `hashCode()` walks
everything underneath: a font dictionary with a `/Widths` array of 100,000 numbers (the per-array parse limit) costs about
0.1 ms per `Tf`. A content stream may hold up to 2,000,000 operators.

Measured on HEAD (probe): font with 90,000 widths, page content `/F1 10 Tf` repeated n times then `(hi) Tj`: n = 5,000
0.53 s, n = 20,000 2.0 s (0.1 ms per `Tf`); extrapolated 200 s for 2,000,000 operators on the JVM, with several such
arrays per font dictionary it is hours, and nothing yields between.

## Fix
Make the hash cheap, not the keys: in `PdfSyntax.kt` give `PdfDictionary`, `PdfArray` and `PdfStream` a memoized hash.
They are immutable after parsing (the parser builds the map/list and then wraps it), so

```kotlin
internal data class PdfArray(val values: List<PdfValue>) : PdfValue {
    private var hash = 0
    override fun hashCode(): Int { if (hash == 0) hash = values.hashCode().takeIf { it != 0 } ?: 1; return hash }
}
```

(same for `PdfDictionary` over `values`, and for `PdfStream` over `dictionary`, `offset`, `length` and the array's identity as
today). A lookup that finds the very same instance then costs one cached int and the identity shortcut of `equals`;
only the first hash of a value pays the full walk (linear, once). `equals` is unchanged, so distinct-but-equal
dictionaries still compare equal. Add a KDoc line saying "never mutate `values` after construction". Do not switch the maps to
reference keys: the cache keys are resolved dictionaries and the same font can legitimately be reached through
different resource dictionaries.

## Tests
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`: `manyFontSelectionsOfALargeFontAreFast`: font dictionary with `/FirstChar 0 /Widths [600 x 90000]`,
content `BT` + `"/F1 10 Tf "`.repeat(100_000) + `(hi) Tj ET`; assert the extract returns "hi" and `measureTime < 3.seconds`
(about 10 s today, tens of milliseconds after). A second test: two equal dictionaries parsed separately are `==` and
have equal `hashCode()`, and a different one is not equal.

## Manual check
None.
