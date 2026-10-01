# Measure each text once, skip the search for lines that fit, and yield while laying out

**Kind:** performance  ·  **Severity:** high (web), low elsewhere  ·  **Platforms:** all, felt on web
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt` (only the `layoutPrintDocument` call inside `produceState`)
**Challenged:** amended — the call-count test uses the same four chord names on every line (distinct names would each cost one first measurement and break the bound honestly), the bound is stated against the measurer the test passes in, and the memo's key follows plan 13's style.

## Problem

A 60-song setlist costs about 55 000 `TextMeasurer.measure` calls per layout (measured on desktop: 295 ms). On the web
`Dispatchers.Default` is the page's one thread and the layout never suspends — `coroutineContext.ensureActive()` in the
measure lambda only throws once the job is cancelled, and the cancellation cannot be delivered while the thread is
busy. So every option change freezes the page for the whole layout (estimated 1.5–3 s), and a slider drag queues one
full layout per step.

Where the calls go: `wrapPrintText` binary-searches even a line that fits (5–6 calls instead of 1); the chord loop
measures each chord name twice plus `" "` once per chord; `padLyricsToFitChords` measures them again;
`PrintRenderer.width()` is deliberately uncached.

## Fix

1. `wrapPrintText`: first `if (measure(text) <= width) return listOf(text)`.
2. In `layoutPrintDocument`, wrap `measure` in a memo keyed by `(text, size, bold)` restricted to strings of at most
   8 characters (chord names, `" "`, `" "`, `"M"`) — a bounded map, so substrings of lyrics are not kept alive.
   Plan 13 later changes the key to `(text, style)`; the length bound stays. `finishPrintDocument`'s page-number
   measurement (plan 02) goes through the same memoised function.
3. Make `layoutPrintDocument` a `suspend fun` and call `yield()` after each song and after every 50 placed blocks, so
   on the web a newer option change cancels the stale layout and the page keeps painting. The sheet's call site is
   already in a coroutine; drop the `ensureActive()` from the lambda (it is no longer needed: `yield()` checks
   cancellation). The yields go in the top-level loops over songs and blocks (inline `forEach`, so they may suspend);
   `rowsFor` and `place` stay non-suspending. The renderer the lambda measures with is created inside the
   `withContext` block and used by that coroutine alone, so resuming on another worker thread after a `yield` is safe.
   `PrintRendererTest` already calls the layout inside `runBlocking`, so it compiles unchanged.

## Tests

`PrintLayoutTest` (with `runTest`, which `commonTest` already depends on): count calls of the measure function the test
passes in for a 40-line song with the same four chords (`G`, `C`, `D`, `Em`) on every line, each line fitting the
column, and assert at most 12 per line on average; a fitting line costs exactly one call in `wrapPrintText`. Existing tests move into `runTest`.

## Manual check

Web build, a 40+ song setlist: drag the text size control — the sheet stays responsive and the preview settles on the
last value.
