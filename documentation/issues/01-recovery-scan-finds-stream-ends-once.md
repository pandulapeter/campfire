# Make the PDF recovery scan linear by finding every `endstream` once instead of searching for it per object
**Challenged:** amended — the index alone left a second quadratic (the whitespace walk after a declared `/Length`, and the copying `sliceArray` check; measured on HEAD, 2,000 objects whose `/Length` points into one 2 MB block take 2.1 s), so the fix now asks the index first, bounds that walk and the whitespace check to 1 KiB, and notes the ordering with plan 13.

**Kind:** performance (hostile input hangs the import)  ·  **Severity:** high  ·  **Platforms:** all (worst on the web, where the scan runs on the one UI thread's worker with no way to stop it but closing the tab)
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfSyntax.kt`, `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfFile.kt`, `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt` (or a new `PdfFileTest.kt` next to it), `data/source/local/implementation/CLAUDE.md`

## Problem
`PdfFile.scan()` (the recovery path, entered whenever `startxref`/the xref table is missing or broken) finds every
`N G obj` marker with a regex and parses the object after it with a fresh `PdfSyntax`. When that object is a
dictionary followed by `stream` and the dictionary has no usable `/Length`, `PdfSyntax.next` falls back to

```kotlin
else indexOf("endstream", start).also { require(it >= start) }
...
position = indexOf("endstream", start).also { require(it >= start) }
```

(`PdfSyntax.kt` lines ~74, ~78 and ~118: `fun indexOf(value: String, start: Int)` is a per-byte loop with a
`value.indices.all { ... }` lambda per position). If the file has no `endstream` after that point the search walks to
the end of the file, fails, the scan moves to the next marker, and does it again. Cost is markers x file size; the
limits are 100,000 markers (`MAX_OBJECTS`) in a 16 MiB file, and nothing in `PdfFile.init` yields.

Measured on HEAD (desktop JVM, throwaway probe, file = `%PDF-1.7\n` + n times `"$i 0 obj\n<< /A 1 >>\nstream\nab\n"`,
about 30 bytes a marker, no `endstream` anywhere, no xref):

| markers | file | time |
|---|---|---|
| 5,000 | 150 KB | 0.48 s |
| 10,000 | 300 KB | 1.2 s |
| 20,000 | 600 KB | 5.7 s |

Quadratic (x4.7 for x2). At 100,000 markers in a 16 MiB file this is on the order of 10^12 byte comparisons, i.e. tens of
minutes on the JVM and far longer in Wasm, and it cannot be cancelled. A 600 KB hostile file freezes the import for
seconds. (A flat run of unterminated arrays was measured as linear, 0.3 s to 0.57 s for 5k to 10k markers, because of
the 64 nesting limit; that shape needs nothing.)

## Fix
1. In `PdfSyntax`, replace the byte-by-byte `indexOf(value, start)` for `"endstream"` with an index built once per
   byte array: a small `internal class PdfStreamEnds(bytes: ByteArray)` that lazily scans the array once, with a plain
   loop that tests `bytes[i] == 'e'` first (no lambda), into a sorted `IntArray` of every offset where `endstream`
   starts (the word has no border with itself, so occurrences never overlap and one pass finds them all), with
   `fun firstAtOrAfter(start: Int): Int` (binary search, -1 when none). `PdfSyntax` takes it as an optional
   constructor parameter `streamEnds: PdfStreamEnds = PdfStreamEnds(bytes)` (lazy, so a parser that never meets a
   stream never builds it).
2. In the stream branch of `PdfSyntax.next`, ask the index **first**: `val first = streamEnds.firstAtOrAfter(start);
   require(first >= 0)`. Every successful parse today ends at an `endstream` at or after `start`, so a stream with none
   after it fails exactly as before, but without walking anything. Then:
   - no usable `/Length`: `end = first` (what `indexOf` returned);
   - a declared `/Length`: `position = end; skip()` stays, but **bounded to 1,024 bytes** of whitespace/comments (a
     `skip(limit)` variant or an inline loop); if the bounded walk does not land on `endstream`, fall back to `first`
     as today. Without the bound the index fixes only the "no `endstream`" shape: a hostile file whose every object
     has its own `endstream` but declares a `/Length` pointing into one shared multi-megabyte whitespace block makes
     each object walk that block, then fall back and succeed, and the scan moves on to the next object — markers x
     block again (and the same per resolved object on the xref path). A real producer puts an EOL, at most a few bytes,
     between the data and `endstream`; when the walk is cut short the fallback still finds the same `endstream`, and
     the only difference is that trailing whitespace stays inside the stream's range, which neither the content
     parser nor the filters care about.
   - the `bytes.sliceArray(end until position).all { whitespace }` check becomes a non-copying loop over at most
     1,024 bytes (a longer distance counts as "not whitespace", i.e. `actualEnd = position`). Today it copies up to
     the whole file per object before `all` gets to short-circuit.
3. `PdfFile` creates ONE `PdfStreamEnds(bytes)` and passes it to every `PdfSyntax` it makes for the file itself
   (`indirect`, `xref`, `scan`). Parsers over decoded data (`compressed`, content streams, CMaps) keep the default.
   It must be built over the array `PdfFile` actually parses: plan
   `13-pdf-header-may-follow-junk-in-the-first-kilobyte.md` replaces that array by a copy starting at the header, so
   declare the index after the final `bytes` property (Kotlin initialises properties in declaration order).
4. Keep the public `PdfSyntax.indexOf` for the inline-image `EI` search in `PdfTextExtractor`, but rewrite its inner
   comparison as a first-byte test followed by a plain loop (no `all {}` lambda); it advances monotonically and is
   linear already.
5. No further budget in `scan()`: plan `07-content-work-budget-shared-by-pages-and-forms.md` owns the document-wide one.

Drop this plan if the `endstream` index turns out to change which stream ends are chosen for a valid file (it must not:
the first `endstream` at or after the stream start is exactly what `indexOf` returned, and the bounded walk only
differs for more than 1 KiB of whitespace after a declared length).

## Tests
In `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/` (commonTest, pure): `recoveryScanOverManyUnterminatedStreamsIsFast`: build the string above with n = 20,000,
call `PdfFile(bytes)` then `catalog()` inside `assertFails` / try-catch (it ends in "PDF has no catalog"), measure with
`kotlin.time.measureTime` and assert `< 2.seconds` (currently 5.7 s; after the fix a few milliseconds, so the bound is
generous for CI). Add a positive test: a file with a broken `startxref` whose streams carry no `/Length` but do have
`endstream` still extracts its text (use `PdfTestWriter.song(..., brokenXref = true)` with a stream whose
dictionary `/Length` is replaced by a missing key) so the index is proven to pick the same ends as before. And `aLengthPointingIntoSharedWhitespaceIsLinear`: 5,000
objects `"$i 0 obj\n<< /Length L >>\nstream\nab\nendstream\nendobj\n"` whose `L` points each one's end into one
2 MB block of spaces followed by `x` at the end of the file; `PdfFile(bytes)` (no xref, so the scan runs) must finish
`< 2.seconds` (today, and with the index alone, every object walks the 2 MB block: measured by the challenge on HEAD,
2,000 objects take 2.1 s, so 5,000 take about 5 s; after the fix 5,000 x 1 KiB). Set each `/Length` with a fixed
width (zero-padded) so the offsets can be computed before the lengths are written in.

## Manual check
Import a PDF made of a few hundred thousand repeated `1 0 obj << >> stream` lines with no `endstream`
(`python3 -c "print('%PDF-1.7'); [print(f'{i} 0 obj\n<< >>\nstream\nab') for i in range(100000)]" > bad.pdf`)
on the desktop and the web build: the import ends at once with "no readable text" instead of hanging.
