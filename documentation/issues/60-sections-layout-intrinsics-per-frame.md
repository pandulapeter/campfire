# 60 · `SongSectionsLayout` re-runs the intrinsic measurement loop on every frame of a transition or a window resize

**Severity:** medium-low (long songs only) · **Area:** `:presentation` (`SongLyrics.kt`, `SongSectionsLayout`)

`SongLyrics.kt:669–676` calls `maxIntrinsicHeight` on every section for each candidate column count on every measure
pass; `extraWidth` (`SongDetailsScreen.kt:530`) changes on every frame of a navigation transition and the width on
every frame of a desktop drag, and the block sits in a `LookaheadScope` with `animateBounds`, so each frame lays out
every paragraph K times.

## Fix

1. Cache `maxIntrinsicHeight` per `(section index, column width)` in a `remember`ed `HashMap<Long, Int>` keyed on
   `(index shl 32) or width` and cleared when `song`, `fontScale` or `shouldShowChords` change (those are the inputs
   of the sections' content). Intrinsics for the same width come back on every pass during a transition.
2. During a transition (`extraWidth != 0.dp`) skip the search and reuse the column count decided for the settled
   width: the layout already decides for `settledWidth`, so the answer is the same on every frame of the transition;
   keep it in a `remember { mutableIntStateOf }` and only re-run the loop when `settledWidth`, `availableHeight` or
   the cache inputs change.
3. Measure with a 300-line song on the desktop while dragging the window edge.
