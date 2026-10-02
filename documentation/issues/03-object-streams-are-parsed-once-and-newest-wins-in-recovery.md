# Parse each object stream's header once, return the object the reference names, and let the newest definition win in recovery
**Challenged:** amended — the lookup returns the requested object wherever the stream's header lists it (not null on an index mismatch), the recovery pass is kept apart from the once-only xref parse and leaves objects resolved before the scan alone, and test (b) is rewritten, since a content stream cannot live in an object stream.

**Kind:** performance + correctness  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfFile.kt`, `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt` (or a new `PdfFileTest.kt`)

## Problem
Two defects in `PdfFile.compressed(location)` and its caller `scan()`.

1. **Quadratic header parsing.** Every call re-reads the whole header of the object stream
   (`val entries = List(count) { header.word().toInt() to header.word().toInt() }`, up to `MAX_OBJECTS` = 100,000 pairs)
   even when all of its objects are already in `objects`. `resolve(reference)` reaches it for any xref type-2 entry that
   is not yet in `objects`, and an xref stream may declare 100,000 type-2 entries pointing at one stream whose header does
   not name them. Each such resolve also stores *a different object* under the requested reference: `compressed()` returns
   `objects[PdfReference(entries[location.index].first)]` (the object the header lists at that index), and `resolve`
   stores that under `value` whatever number `value` has.
   Measured on HEAD (probe: one `/Type /ObjStm` stream with n header pairs naming objects 100001..., and n type-2 xref
   entries for objects 3..n+2 pointing at it, then `file.resolve` for each): n = 5,000 took 2.07 s, n = 10,000 took
   8.2 s (x4: quadratic); n = 100,000 would be about 800 s. Real files never do this, so it is pure attack surface, but the
   fix is cheap.
2. **Oldest object stream wins in recovery.** In `scan()`, plain objects are recorded newest-last
   (`objects[reference] = value`, so an incremental update's object overrides the old one), but compressed ones go through
   `if (objects.containsKey(reference)) continue`, so the first definition wins: an object also defined plainly later in
   the file, or in a later object stream, is ignored in favor of the stale one. A repaired file whose `startxref` is broken
   therefore reads the text of the previous revision.

(Reviewer's note that `scan` calls `compressed` with generation 0 is not a bug: objects in an object stream always have
generation 0.)

## Fix
1. Cache each object stream's header: `private class ObjectStream(val data: ByteArray, val first: Int, val numbers: IntArray, val offsets: IntArray)`
   in `private val objectStreams = mutableMapOf<Int, ObjectStream>()`, built once per stream object number by a
   `private fun objectStream(number: Int): ObjectStream` with the same `require`s as today's `compressed()` on `/Type`,
   `/N`, `/First` and every entry (offset in range, number > 0).
2. xref path: `resolve` calls `compressed(location, value)`. On the first call for a stream (a `parsedStreams: MutableSet<Int>`)
   it runs today's eager loop once (skip what is already in `objects`, skip what the xref places in another stream,
   store the rest); every call then returns **`objects[value]`** — the object the reference names, wherever the header
   lists it, or null when the stream does not hold it, in which case nothing is stored under `value`. Do not compare
   `entries[location.index]` with `value`: an index that names another object is either a dead entry (null is right)
   or an off-by-one producer whose object is still in the stream (finding it by number is right), and today's code
   returns, and caches under `value`, the wrong object in both cases. Drop the `location.index in 0 until count`
   requirement with it; it no longer selects anything.
3. Recovery path (`scan()`): remember where each definition was found, `private val definedAt = mutableMapOf<PdfReference, Int>()`,
   `match.range.first` for plain objects (set where `scan` already does `objects[reference] = value`) and the object
   stream's own marker offset (`locations[stream].offset`) for compressed ones. Then visit the object streams found
   in ascending offset order and, for each entry of `objectStream(n)` (independently of `parsedStreams`, which belongs
   to the xref path), parse and store the object only when `definedAt[reference]` is absent or `<=` the stream's
   offset, recording that offset. An object that is in `objects` but not in `definedAt` was resolved through the xref
   before this lazy scan ran: leave it alone, as today's `containsKey` check does.

## Tests
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/` (`PdfTextExtractorTest.kt` or a new `PdfFileTest.kt`):
(a) timing: the probe above with n = 20,000 must finish `< 2.seconds` and `file.resolve(PdfReference(3))` must be null
(today about 33 s). (b) newest wins in recovery: objects in an object stream cannot be streams, so put the **page**
in the object streams and let it point at different contents. `PdfTestWriter` numbers objects by the order they are
added, so: catalog 1, page tree 2 (`/Kids [3 0 R]`, font resources), page 3 written plainly with `/Contents 5 0 R`,
font 4, content streams 5 ("Old"), 6 ("Newer") and 7 ("Newest"), then two `ObjStm` objects each defining object 3
(`PdfTestWriter.stream("3 0 << /Type /Page /Parent 2 0 R /Contents 6 0 R >>", "/Type /ObjStm /N 1 /First 4")`, raw and
uncompressed; the first one could name `/Contents 5 0 R` again, it only has to be older), written with
`write(brokenXref = true)`. It must read "Newer" (today the plain, older object 3 wins: "Old"). Then append
`"3 0 obj\n<< /Type /Page /Parent 2 0 R /Contents 7 0 R >>\nendobj\n"` to the bytes (after `%%EOF`, as an incremental
update without a usable xref would) and it must read "Newest". (c) an xref
type-2 entry whose index is off by one still resolves the object it names (the existing
`readsObjectStreamsAndXrefStreamsWithPngPrediction` shape with `mapOf(4 to (6 to 1))` against an `/N 1` stream).

## Manual check
None beyond the unit tests and re-importing `chrome.pdf` and `libreoffice.pdf` (both use object streams).
