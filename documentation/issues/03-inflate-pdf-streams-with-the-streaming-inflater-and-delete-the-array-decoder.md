# Inflate the in-memory callers through the streaming inflater and delete the array decoder's second copy of DEFLATE

**Kind:** duplication  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the helper throws an `Error`, not `checkNotNull`'s `IllegalStateException`, which `PdfFile` catches as a damaged object; cancellation and allocation noted; `StreamingInflaterJvmTest`'s two in-memory comparisons added; the damaged-stream oracle compares with zlib only where both finish.
**Files:**
- `data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/zip/Inflater.kt`
- `data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/zip/StreamingInflater.kt`
- `data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/zip/CompletedWithoutSuspending.kt` (new, or beside `Inflater`)
- `data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/document/PdfFilters.kt` (only if its call changes)
- Tests: `data/formats/src/commonTest/kotlin/com/pandulapeter/campfire/data/formats/zip/ZipFuzzTest.kt`, `StreamingInflaterTest.kt`, `InflaterImportLimitTest.kt`, `DeflaterTest.kt`, `ZipReaderTest.kt`; `data/formats/src/desktopTest/kotlin/com/pandulapeter/campfire/data/formats/zip/InflaterTest.kt`, `StreamingInflaterJvmTest.kt`; `data/formats/src/commonTest/kotlin/com/pandulapeter/campfire/data/formats/document/PdfFuzzTest.kt`, `PdfFiltersTest.kt`
- Docs: `data/formats/CLAUDE.md` (§ `zip/`: "Reads STORED and DEFLATE (`Inflater` follows `puff.c`)")

## Problem

`data/formats` decodes DEFLATE twice. `Inflater.inflate(source: ByteArray, …)` runs `private class State` (`stored`,
`dynamic`, `codes`, `decode`, `copyBack`, `bits`, `ensureCapacity`, ~260 lines); `StreamingInflater.kt`'s
`private class StreamingInflate` is the same `puff.c` structure again over a `ZipSource` and a sliding window
(~290 lines). They share only the tables, `Huffman`, and the limits. Every hardening (a code-length check, an
incomplete-code rule, an output limit) has to be made in both, which `ZipFuzzTest` "damaged deflate streams inflate
alike in memory and streaming" exists to police.

`StreamingInflate`'s KDoc says why the array one is kept: "kept apart so that the in-memory one, which the PDF reader
and the differential tests rely on, stays as it is." The array decoder's only production caller is PDF FlateDecode:

```kotlin
// data/formats/.../document/PdfFilters.kt, PdfFilters.decode
predictor(Inflater.inflate(bytes, offset = 2, length = bytes.size - 2), params)
```

(verified with `git grep -n "Inflater\." -- '*.kt'`: the rest are `Inflater.MAX_ENTRY_SIZE` / `MAX_DECLARED_ENTRY_SIZE`
/ `INITIAL_CAPACITY_LIMIT` constants, the streaming entry points in `ZipWalk.kt`, and tests; the
`presentation` and `DeflaterRoundTripTest` hits are `java.util.zip.Inflater`).

Making `PdfFilters.decode` `suspend` so it can call `StreamingInflate` is not workable: `PdfFile.decode` is reached from
`PdfFile.resolve` (object streams, xref streams), which nearly every PDF function calls, and `PdfFonts.decode` is a
`sequence { }` builder that cannot suspend. The cascade would touch most of `document/`.

## Fix

Keep `Inflater.inflate(ByteArray, offset, length, expectedSize, maxOutput): ByteArray` as a non-suspending function,
re-implemented over `StreamingInflate`, and delete `State`:

1. A helper that runs a suspend block that never really suspends, on every platform (wasm has no `runBlocking`):

   ```kotlin
   /** [block]'s result, for a block whose only suspensions are `yield`s and calls that return at once. */
   internal fun <T> completedWithoutSuspending(block: suspend () -> T): T {
       var outcome: Result<T>? = null
       block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
       return (outcome ?: throw AssertionError("The block suspended.")).getOrThrow()
   }
   ```

   Not `checkNotNull`: its `IllegalStateException` is what `PdfFile.resolve` and its object parsing catch as a damaged
   object (`catch (_: IllegalStateException) { scan() }`), so a decoder that started to suspend would read as damaged
   PDFs instead of failing. An `Error` passes through those catches and every `ZipException` handler. Nothing resumes
   a suspended block afterwards (no dispatcher holds it), so a suspension can neither hang nor hand back part of the
   output.

   This holds for `StreamingInflate` over a `ByteArrayZipSource` (whose `read` copies at once) and a non-suspending
   output: its only suspension point of its own is `refill()`'s `yield()` (no `withContext`, no `ensureActive`), and
   kotlinx.coroutines 1.11.0's `yield` (`Yield.kt`) calls `context.ensureActive()`, a no-op without a `Job`, then
   returns `Unit` at once when `uCont.intercepted()` is not a `DispatchedContinuation`, which it never is in a context
   with no `ContinuationInterceptor`.

   Cancellation is as today: the array decoder never looked at the import's `Job` (it was not `suspend`), and the
   streaming one run in an empty context does not either; the PDF reader's own page, operator, and work yields are
   where a cancelled import stops. A 24 MiB stream blocks its thread for its inflate before and after.
2. `Inflater.inflate(ByteArray, …)` becomes: range check as now; `StreamingInflate(ByteArrayZipSource(source), offset, length, maxOutput, outputLimit = …)`
   writing into a `ByteArrayBuilder` started at `minOf(expectedSize, INITIAL_CAPACITY_LIMIT)` (or `maxOutput`), with
   `outputLimit` = `expectedSize` where declared, `maxOutput` for a prefix, else `MAX_ENTRY_SIZE`; then the existing
   size check (`"Deflate stream produced … instead of the expected …"`). Keep its KDoc and its exceptions: `ZipException`
   on malformed data, more than `MAX_DECLARED_ENTRY_SIZE` declared, or more/fewer than declared. `StreamingInflate`'s
   constructor is private to `StreamingInflater.kt`, so put the array entry point there, or make the class `internal`.
3. Delete `Inflater.State` and `PrefixComplete` if the streaming file has its own (it does). Keep the tables, `Huffman`,
   and the constants in `Inflater` (the streaming code imports them). Update `StreamingInflate`'s KDoc ("kept apart so
   that the in-memory one … stays as it is") and `Inflater`'s ("mirrors puff.c … bit by bit") to say there is one
   decoder. Update `data/formats/CLAUDE.md` § `zip/` in the same commit.

`PdfFilters` needs no change. The array entry point's `expectedSize` and `maxOutput` have no production caller (PDF
inflates whole, with neither); keep them anyway so the tests calling them stay as they are. Performance: the array
path gains one copy per output byte (window → builder) and one 64 KiB input buffer copy per refill, against a
bit-by-bit Huffman decode that dominates; PDF streams are capped at `MAX_ENTRY_SIZE` (24 MiB). Each call also
allocates the 128 KiB window and the 64 KiB input buffer where the array decoder allocated only its output (1 KiB to
start), which a PDF of thousands of small streams pays per stream: size `input` to
`minOf(INPUT_BUFFER_SIZE.toLong(), length).toInt()` (a buffer holding the whole range needs only the one refill), and
leave the window. No measurement is needed. If `PdfFuzzTest` or the PDF import corpus run noticeably slower,
say so in the commit and keep the change.

Drop this plan if `completedWithoutSuspending` throws "The block suspended." in any test on any target: then the
in-memory path must stay a decoder of its own.

## Tests

- The differential test loses its in-memory oracle. In `ZipFuzzTest`, replace "damaged deflate streams inflate alike in
  memory and streaming" with invariants over the one decoder: a prefix of `maxOutput` bytes is a prefix of the whole
  output where both succeed; a stream inflating whole to N bytes inflates to the same bytes with `declaredSize = N`
  into a sink, whose SHA-256 is set exactly when it was not abandoned; anything else throws only `ZipException`. Keep
  "damaged deflate streams inflate or are refused" as it is (it calls the array entry point).
- Add an oracle that is not this code: in `desktopTest`, `InflaterTest` / `StreamingInflaterJvmTest` already compare
  with `java.util.zip.Inflater` on valid fixtures; add a fuzz case there inflating `ZipFuzzTest`-style generated data
  deflated by `java.util.zip.Deflater` at random levels and strategies, compared with the input; and one over the same
  mutated streams as `ZipFuzzTest`, inflated by `java.util.zip.Inflater(true)` too: where both finish, the same bytes.
  Do not compare which of them refuses: the two are not equally strict (this decoder takes a distance code of one
  symbol at any length, zlib only at length 1, and refuses an incomplete literal/length code zlib takes as a single
  length-1 code), so a damaged stream may be read by one alone.
- `StreamingInflaterTest` cases that compare `Inflater.inflate(stream, …)` with the streaming call ("a prefix is what
  the in-memory inflater peeks", "fixed blocks … inflate to the same bytes", "stored blocks … at every buffer
  boundary", "back references of 32768 bytes …") now compare a function with itself: rewrite them to compare with the
  known input (`data`, or its first `maxOutput` bytes), or delete the duplicates. The same in `StreamingInflaterJvmTest`:
  "inflates zlib's raw deflate at every level to the same bytes as the in-memory inflater" compares with `fixture`, and
  "refuses a truncated stream like the in-memory inflater" keeps only its streaming assertion (the array one is
  `InflaterTest`'s "rejects garbage").
- A test of `completedWithoutSuspending`: it returns a value through a `yield()`, rethrows an exception (a
  `ZipException` arrives as itself), and throws an `AssertionError` for a block that really suspends
  (`suspendCoroutine { }`).
- `InflaterImportLimitTest`, `DeflaterTest`, `ZipReaderTest`, `PdfFiltersTest`, and `PdfFuzzTest` pass unchanged.
