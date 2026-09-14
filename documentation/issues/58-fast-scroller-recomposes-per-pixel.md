# 58 · `FastScroller` recomposes on every scrolled pixel

**Severity:** medium-low · **Area:** `:presentation` (`FastScroller.kt`)

`FastScroller.kt:93` reads `state.isScrollable`, which goes through `scrollMetrics()` (:294–308: `layoutInfo`,
`firstVisibleItemIndex`, `firstVisibleItemScrollOffset`) in composition, and :114 reads
`gridState.firstVisibleItemIndex` in composition. Thumb geometry is correctly confined to draw/layout lambdas, but
the visibility and the bubble label are not.

## Fix

```kotlin
val isVisible by remember(state) { derivedStateOf { state.isScrollable } }
val label by remember(gridState, labelForItem) { derivedStateOf { labelForItem(gridState.firstVisibleItemIndex) } }
```

`derivedStateOf` only invalidates when the *result* changes (a Boolean, a String), so scrolling within one section
no longer recomposes the scroller. Check `FastScrollerState.isScrollable` is a plain getter reading snapshot state,
so the derived state tracks it.
