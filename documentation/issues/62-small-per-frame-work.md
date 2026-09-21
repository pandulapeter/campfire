# 62 · Scrolling a list allocates the fast scroller's estimate four times a frame, recomposes the app bar on every pixel, and recomposes every visible row when a scroll starts and ends

**Severity:** minor (performance; all platforms, every scroll of the Songs and Setlists lists, and of every screen with an app bar) · **Area:** `:presentation` (`components/FastScroller.kt`, `components/CampfireTopAppBar.kt`, `components/ListItemAnimation.kt`)

## Symptom
Nothing a user can point at on a fast device. On a slow phone, or in the web build, a long fling through the song
list is a little less smooth than it could be and produces a steady trickle of garbage; the app bar does work on
every scrolled pixel although it only ever changes once, as the list leaves its top; and the first frame of every
scroll is heavier than the ones after it.

Three independent items, A–C. Each has its own cause and fix below; A and B are changed, C is **confirmed but
deliberately left as it is** (the reason is the useful part).

## Cause

### A. `FastScroller`: the scroll estimate is rebuilt for every reader, four times per scrolled frame
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/FastScroller.kt:235`:

```kotlin
private val metrics: ScrollMetrics? get() = gridState.scrollMetrics()
```

`scrollMetrics()` (`:293-315`) builds a filtered list and a `distinctBy` set of the visible items and a
`ScrollMetrics` on every call. One `drawBehind` pass (`:145-159`) reads `state.thumbTop` — which asks for `metrics`
through `scrollFraction` and again through `thumbRange` → `thumbHeight` — and then `state.thumbHeight`: three calls.
The `isVisible` derived state (`:106`) re-evaluates `isScrollable` on the same frame: a fourth. While the thumb is
dragged the bubble's `offset { }` adds more through `thumbCenter`.

### B. `CampfireTopAppBar`: `overlappedFraction` is read while composing
`components/CampfireTopAppBar.kt:65`:

```kotlin
val isOverlapped = scrollBehavior.state.overlappedFraction > 0.01f
```

`overlappedFraction` is a getter over `contentOffset` (Material 3 `AppBar.kt:2304-2316`), a `mutableFloatStateOf`
that the pinned behaviour adds every consumed scroll delta to (`state.contentOffset += consumed.y`). The fraction
saturates at 1 after the first 64dp, but the *state* it is computed from keeps changing for as long as the list
moves, so the composable body runs again on every scrolled pixel of every scroll — not only the first 64dp, as the
review put it — to arrive at the same `true`. The `Surface` below is skipped each time (its arguments are equal), so
what is wasted is the body itself: the `animateFloatAsState` call, two `lerp`s and the lambda bookkeeping, on every
screen that has a bar.

### C. `listItemAnimation`: `isScrollInProgress` is read in every item
`components/ListItemAnimation.kt:139`:

```kotlin
placementSpec = if (isRearranging || !listState.isScrollInProgress) ITEM_PLACEMENT_SPEC else null,
```

The function returns a `Modifier`, so the read belongs to the scope that calls it — the content lambda of each lazy
item — and every visible row is recomposed with a different modifier in the frame a scroll starts and in the frame it
ends (`SongListItem` and Material's `ListItem` run again; their slot lambdas are unchanged and skipped).

## Fix

### A. One estimate per change of the list
`FastScroller.kt`, `FastScrollerState`: replace the getter with a derived state.

```kotlin
/**
 * Worked out once per change of the list's layout rather than once per reader: a single frame of a scroll asks for
 * it from the thumb's top, from its height and from its range, and from whether there is anything to scroll at all,
 * and every answer costs the two collections the visible items are filtered through.
 */
private val metrics: ScrollMetrics? by derivedStateOf { gridState.scrollMetrics() }
```

Add `import androidx.compose.runtime.getValue` if the file lacks it (`derivedStateOf` is already imported). Nothing
else changes: `thumbTop`, `thumbHeight`, `thumbRange`, `isScrollable` and `scrollToFraction` keep reading `metrics`.
`scrollMetrics()` reads only snapshot state (`canScrollForward`, `canScrollBackward`, `layoutInfo`,
`firstVisibleItemIndex`, `firstVisibleItemScrollOffset`), so the derived state is invalidated exactly when the old
getter would have returned something else; `ScrollMetrics` has no `equals`, so a new estimate always reaches the draw
block, as before. Do not rewrite `scrollMetrics()` itself to avoid the two collections: the `distinctBy` is what
keeps a pinned sticky header that is listed twice from skewing the average, and an allocation-free loop that is
exactly equivalent is more code than one allocation per frame is worth.

### B. Two recompositions per scroll instead of one per pixel
`CampfireTopAppBar.kt:65`:

```kotlin
// Derived, because the fraction is worked out from the list's content offset, which changes on every scrolled
// pixel, while the answer only changes as the list leaves its top and as it comes back to it.
val isOverlapped by remember(scrollBehavior) { derivedStateOf { scrollBehavior.state.overlappedFraction > 0.01f } }
```

Imports: `androidx.compose.runtime.derivedStateOf`, `androidx.compose.runtime.remember` (`getValue` is there for the
`by animateFloatAsState`). The first read computes the value synchronously, so the bar of a screen that is composed
already scrolled (a tab come back to, see `ScrollPosition`) is tinted from its first frame exactly as today — no
effect, no initial `false`. `KeepTopAppBarInSync` writes `contentOffset` and is picked up the same way.

### C. Left as it is
The read cannot leave composition, because what it decides is a *parameter* of `Modifier.animateItem(placementSpec =
…)`, and the lazy grid copies the spec out of the modifier element when it measures. The two ways around that both
change what is drawn, which `24428b46` and the long KDoc above the function exist to prevent:

- A custom `FiniteAnimationSpec` that answers "snap" while the list scrolls is **not** `null`. With a null spec the
  item is simply placed. With any spec, `LazyLayoutItemAnimation.animatePlacementDelta` first offsets the item by the
  (wrong, asked-for rather than scrolled) delta and only then launches the animation that takes the offset away, so
  the row is drawn far outside the list for one frame — the very glitch the null is there for.
- Hoisting the read into the list (`SongList`) and capturing the result in the grid's content lambda gives the grid a
  new content lambda twice per scroll, which rebuilds every interval of a 3 000 song list and still recomposes every
  visible row.

Wrapping every row in a `Box` that carries the modifier would let the row itself be skipped, at the price of one more
layout node per row for the whole of every fling, which is the wrong trade. What the two recompositions cost is a
row's own body, and plans 58 and 63 are what make that body cheap (no per-row collection, animation coroutine or
`Regex`). No code change here; add one sentence to the function's KDoc so the next reader does not try:

```
 * The scroll state is read here, while composing, on purpose: the spec is a parameter of the modifier, which the
 * grid reads when it measures, so there is no later phase to decide it in - every visible row is recomposed once as
 * a scroll starts and once as it ends, and a spec that snapped instead of being null would still displace the row for
 * a frame.
```

(after the paragraph ending "…since one with no spec at all is none.").

## Tests
None (UI is untested).

## Verify
1. `./gradlew :app:desktop:run` with a library long enough to scroll.
   - A: scroll by wheel, by dragging the thumb, by pressing the track, and to both ends: the thumb's position and
     height and the letter bubble behave exactly as before; a list that fits the window still shows no thumb and
     gains one when the window is made shorter. Search for something that leaves three songs: the scroller fades out.
   - B: on Songs, Setlists, song details and the editor, the bar tints and lifts as the content leaves the top and
     flattens when it returns — including after a jump to the top through a sticky header click, a new search query
     and a fast-scroller drag (the `KeepTopAppBarInSync` paths). Leave Songs scrolled, switch tab and back: the bar
     is tinted on its first frame, without fading in.
   - C: fling to the very top and bottom: no row slides in from outside the list; delete or rename a song with the
     list at rest: the rows still slide into place.
2. Android, Layout Inspector with recomposition counts: during a fling `CampfireTopAppBar` counts up by two per
   scroll rather than continuously, and `FastScroller` does not count up at all.
3. `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`

## Docs
None beyond the KDoc sentence in C.

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/FastScroller.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/CampfireTopAppBar.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItemAnimation.kt` (KDoc only)

## Depends on
Nothing. Plans 58 and 63 are what shrink the cost item C leaves in place; neither has to land first.
