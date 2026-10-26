# Name an import preview's pages after their review, and forget their inferred keys when the preview goes

**Kind:** bug · **Severity:** low · **Platforms:** all
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportPreviewing.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ImportPreviewState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/songText/InferredSongKeys.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportPreviewingTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/songText/InferredSongKeysTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/songText/CLAUDE.md` ("never pruned")
**Challenged:** amended — forgetting every name on leaving the composition raced with the same review's preview opened
again: `openReviewPreview` is allowed as soon as the import screen is on top, while the popped preview still slides out,
so a row tapped within that exit transition infers nothing (the names are still held), then the old entry's `onDispose`
forgets them and the new preview's visible page has no key until it is paged away from. A preview now forgets its
review's names on leaving only once that review is no longer the report, and sweeps every other review's names on
entering; `forget` takes a predicate. The `state/songText/CLAUDE.md` "never pruned" line is updated too.

**Lane P, after plan 05**, whose `ImportReport.Review.id` / `ImportPreview.reviewId` this plan uses. If plan 05 was
dropped, add `val id: String = Uuid.random().toString()` to `ImportReport.Review` here (step 1 of plan 05) and read the
id from `ImportPreviewState.review.id`.

## Problem

A preview's pages are keyed by a name that depends only on the entry's place in the plan
(`state/importing/ImportPreviewing.kt`):

```kotlin
internal fun importPreviewFileName(index: Int) = "$IMPORT_PREVIEW_FILE_NAME_PREFIX$index"
...
private const val IMPORT_PREVIEW_FILE_NAME_PREFIX = ":import-preview:"
```

`InferImportPreviewKeys` (`screens/songDetails/ImportPreviewState.kt`) hands those names to the view model's
`InferredSongKeys`, which keeps keys and the texts they came from for the view model's life:

```kotlin
private val inferredTexts = mutableMapOf<String, String>()
private val _keys = MutableStateFlow(emptyMap<String, String>())
...
suspend fun inferFor(contents: List<Pair<String, String>>) {
    val changed = contents.filter { (fileName, text) -> inferredTexts[fileName] != text }
    if (changed.isEmpty()) return
    val inferred = withContext(dispatcher) { ... }
```

So the first song of every import is `:import-preview:0`. Preview a song with no `{key}` whose chords are in G, leave
the import, then preview another import's first song (no `{key}` either, in D): the app bar, the read only line, and
the Transposition control name **G** on the first frame (`SongPages.kt` / `SongDetailsAppBar.kt` read
`inferredKeys[song.fileName]`), and switch to **D** once the scale walk on `Dispatchers.Default` finishes — a wrong
first frame corrected by a visible change, which the root `CLAUDE.md` forbids ("Data arriving or a wrong initial value
is fixed at the source so the first frame is right"). A new first song whose key cannot be told shows the old key until
the walk removes it. Also, every previewed text stays in `inferredTexts` (whole song texts, under names no file has)
until the process ends.

## Fix

1. Page names carry the review: `importPreviewFileName(reviewId: String, index: Int) =
   "$IMPORT_PREVIEW_FILE_NAME_PREFIX$reviewId:$index"`, still with no song extension. `rememberImportPreviewState`
   passes `review.id` (`importPreviewSongOf(entry, importPreviewFileName(review.id, index))`). Within one review an
   index always names the same entry (a joining batch only appends), so names stay stable while the preview pages, and
   `rememberPageKeys` (the single-song preview's page keeping its key once written) is unaffected. Update the KDoc.
2. `InferredSongKeys` gains `fun forget(isForgotten: (fileName: String) -> Boolean)`, removing the matching names from
   `inferredTexts` and from `_keys` (one `_keys.update`); KDoc: for names that stop meaning anything, a preview's pages.
   It knows nothing of previews; `ImportPreviewing.kt` gains `internal fun isImportPreviewFileName(fileName: String,
   reviewId: String? = null)` — any preview's page name, or only that review's (`"$PREFIX$reviewId:"`).
3. `InferImportPreviewKeys` (`ImportPreviewState.kt`) keeps one review's page keys at most, and none once it is over:
   ```kotlin
   val reviewId = importPreview.review.id
   // Another review's pages are never shown again (plan 05 cuts a preview over any other review).
   LaunchedEffect(reviewId) { inferredKeys.forget { isImportPreviewFileName(it) && !isImportPreviewFileName(it, reviewId) } }
   DisposableEffect(reviewId) {
       onDispose {
           // Only once the review is over: while it is up, a row tapped as this preview slides out opens the same pages
           // again, which find their keys held, and would lose them here.
           if ((viewModel.importReportState.importReport.value as? ImportReport.Review)?.id != reviewId) {
               inferredKeys.forget { isImportPreviewFileName(it, reviewId) }
           }
       }
   }
   ```
   (keep the existing `LaunchedEffect(pages, currentPage)` that infers). It leaves after the review's Import (the report
   is `Importing` / `Finished`), Back from a single-song preview (the report is null), and the single-song preview's
   rewrite into the song's own screen (`SongDetailsScreen` calls it only while `importPreview != null`; the report was
   set to null in the same main-thread step, and the song's real file name has its own key from `SongTextStore`). Back
   from a review-pager preview keeps the names while the review stays; if the review is then left with no preview open,
   they are swept by the next preview's entry — at most one review's walked pages are held. All of it runs on the main
   thread, as `inferFor`'s bookkeeping does (it returns from `withContext(dispatcher)` to the caller's context), and an
   `inferFor` cut off by its effect leaving is cancelled at the end of `withContext`, so nothing is written back after a
   forget; no locking.
   `inferredKeys` is `viewModel.songTextStore.inferredKeys`, shared with the library's songs, whose names (with a song
   extension) never match the prefix.

Step 1 alone fixes what is seen; step 2–3 stop the retention. Recommended: both. Docs: in
`screens/songDetails/CLAUDE.md`'s import preview section, "under page names no library file can have, one set per
review (`importPreviewFileName`), whose inferred keys are forgotten once the review is over"; in `state/songText/CLAUDE.md`
and `InferredSongKeys`' KDoc, "never pruned" becomes "never pruned, but for an import preview's pages".

## Tests

- `ImportPreviewingTest`: the existing name tests take a review id; add that the same index of two reviews gives two
  names, and that no name ends in `.cho`.
- `ImportPreviewingTest`: `isImportPreviewFileName` matches any review's page names, only its own with a review id, and
  never a library name (`a.cho`).
- New `InferredSongKeysTest` (`state/songText/`, `Dispatchers.Unconfined` as `FakeSongFiles` builds it): a key inferred
  for a name is gone from `keys` after a `forget` matching it, one it does not match stays, and `inferFor` of the same
  name and text after `forget` walks it again (count calls of the `inferKey` lambda).
- The disposal is composition: covered by `:tools:screenshots:test` staying green.
