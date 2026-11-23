# Run the page model once per page read from a link

**Challenged:** amended — the memo must not run under the class's existing `Mutex` (`pageSong()` takes it and Kotlin's `Mutex` is not reentrant, so wrapping `songLinesOf` in it deadlocks every crop); it is checked and stored in short critical sections of its own, the model run outside them, stored only once a run completes; it wraps the whole answer so plan 04's move of the mapping into `PageSongLines.of` merges with it; no `CLAUDE.md` names `PageCropperImpl`'s runs, so none changes.

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** Android, iOS, desktop
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/PageCropperImpl.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/` (a new test file)

## Problem

A page read from its link goes through the page model twice. `ImportWork.pageFiles` runs it only to learn whether the
page holds a song (`ImportWork.kt:348`):

```kotlin
        val holdsSong = try {
            useCases.pageCropper.songLinesOf(page.text.lines()) != null
```

then hands the same text on as a page-text file, and `PrepareImportUseCaseImpl` crops it again
(`PrepareImportUseCaseImpl.kt:229` → `PageTextCrop.cropped`):

```kotlin
                    val cropped = if (file.isPageText && whole != listOf(original)) PageTextCrop.cropped(original, pageCropper, logger) else null
```

```kotlin
        val found = try {
            cropper.songLinesOf(lines)
```

Measured on the JVM (Apple Silicon, `PageSong.crop` over the shipped `page-model.bin`, warm): **~45 ms for 300 lines,
~220 ms for 1,500 lines** (the model reads at most 1,500). A long chord page is several hundred lines, so the second run
adds roughly 50–200 ms on a desktop, and a few times that on a mid-range phone, on the import screen's wait.

## Fix

**Recommended: a one-entry memo in `PageCropperImpl.songLinesOf`**, keyed by the lines: keep the last `lines` and its
answer (`PageSongLines?`), and return the answer when the next call's `lines` are equal (`==`, O(n), negligible beside
the model). A different text recomputes and replaces it.

How, exactly, since the class's lifecycle has traps:
- **Never under the existing `mutex`.** `songLinesOf` calls `pageSong()`, which takes `mutex.withLock`, and Kotlin's
  `Mutex` is not reentrant: a `songLinesOf` body inside that lock suspends forever on the first page. Guard the memo
  with a second `Mutex` (or hold it as one immutable `Pair` in a field read and replaced in one step) and hold it only
  to compare and to store, never across `pageSong()` or the model run, so two crops are never serialized behind it.
- **Store only a finished answer.** Compute outside the lock; store after the run returns. A run cancelled (the import
  left) or one that throws (no model: `IllegalStateException`) stores nothing, so the next call runs the model as
  today. Two concurrent calls with the same lines may both run it; that is fine.
- **Wrap the whole answer**, the `PageSongLines?` the function returns, not the `crop` inside it: plan 04 replaces the
  mapping in this function with `PageSongLines.of(crop)`, and a memo around the outer answer merges with that in either
  order. A null answer (no song) is memoized as such (a field for "has an entry" beside the answer, not `null` as
  "empty").
- Only one entry, so at most one page's lines (a page's text, a few hundred KB at the very most) are held for the life
  of the process; a different text replaces them.

Why this form:
- **No API or model change.** The alternative, carrying the found `PageSongLines` on `ImportedFile` (in `:data:model`)
  from `ImportWork` to `PrepareImportUseCaseImpl`, threads a field through a shared model, its `equals`/`hashCode`, and
  every copy, for one caller.
- **Correct whatever happens to the text between the two calls.** The file's text is not always the page's:
  `sharedTextsToImportedFiles` runs `withoutSourceLink` (drops a last line that is only a link, and quotation marks
  around the rest), and the bytes are decoded again. Indices carried across would point into a different list of
  lines; a memo keyed by the lines just misses and recomputes in those rare cases.
- `PageCropperImpl` is a `@Single`, so `ImportWork` and the prepare use case share it.

Only `songLinesOf` is memoized; `documentScoresOf` runs once per document already.

**Drop this plan if** a timing on Android (a `measureTimeMillis` around the second `songLinesOf` on a page of a few
hundred lines, logged once) comes out under ~10 ms.

## Tests

A unit test that two calls with equal lines run the model once. `PageCropperImpl` reads its weights from compose
resources; if `Res.readBytes` works in `presentation`'s desktop tests, test it directly (the same instance returned for
equal lines, `assertSame`; a new answer for other lines). If it does not, put the memo in a small internal class that
takes the crop as a `suspend (List<String>) -> PageSongLines?`, used by `PageCropperImpl`, and test that with a counting
lambda.
