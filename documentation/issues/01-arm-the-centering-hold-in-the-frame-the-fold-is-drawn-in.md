# Arm the column centering hold before the fold's own frame is measured, not from a coroutine that runs after it

**Challenged:** amended — the premise is confirmed from the CMP 1.12.1 sources (not only "not guaranteed"); the release effect is reduced to `delay` then `isHolding = false`, with no early return and no generation counter, because the plan's early return would leave the hold stuck on for good if the effect body ever ran before the `SideEffect`, and cancellation already stops a superseded effect.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (the dispatch order differs per platform)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ColumnCenteringHold.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ColumnCenteringHoldTest.kt` (new)

## Problem

At 21173fed6 the hold is switched on only inside a `LaunchedEffect` (`ColumnCenteringHold.kt`, `rememberColumnCenteringHold`):

```kotlin
LaunchedEffect(*foldKeys) {
    if (!hasStarted[0]) { hasStarted[0] = true; return@LaunchedEffect }
    hold.isHolding = true
    try { delay(FOLD_RECENTER_DELAY_MILLIS) } finally { hold.isHolding = false }
}
```

and `SongSectionsLayout.kt` records the spreads on every lookahead measure while nothing holds:

```kotlin
if (isLookingAhead && centeringHold != null && !centeringHold.isHolding) {
    centeringHold.spreads = arrangement.columnSpreads
    centeringHold.columnCounts = grid.columnCounts
}
```

A `LaunchedEffect` body is *dispatched* when the composition is applied, not run inline. The tap's frame recomposes
with the folded heights and then measures. If the effect's coroutine has not run by then, that measure sees
`isHolding == false`, centers at once with the new heights and records those as `spreads`; when the coroutine then
sets `isHolding = true`, `heldSpreads` hands back the already re-centered spreads, so nothing is held and the
tapped header moves — the very thing the hold exists to prevent. Whether the coroutine runs before the measure depends
on the platform's frame dispatcher. Android's trampolined `AndroidUiDispatcher` likely runs it first. On the
`ComposeScene` platforms (desktop, iOS, wasmJs, and the screenshot tool's `ImageComposeScene`) it is **certain not
to**: in Compose Multiplatform 1.12.1 the `Recomposer`'s effect context is `FrameRecomposer`'s `trampolineDispatcher`,
a `FlushCoroutineDispatcher` flushed only by `performTrampolineDispatch` / `performFrameDispatch`, and a frame is
`performFrame` (whose `sendFrame` recomposes and applies, `SideEffect`s included) then `measureAndLayout` then `draw`
with no flush between (`SingleComposeSceneRenderingScope.render`). So on those platforms the fold's frame always
records the folded centering, and the hold never holds. Two reviewers reached this independently.

The same reset-in-`finally` also opens a window on a second fold within 900 ms: the old effect's cancellation runs
its `finally` (`isHolding = false`) when the old coroutine is resumed with the cancellation, which can be a frame
before the new effect's body sets it true again.

## Fix

Arm the hold synchronously in the apply phase of the composition that changed the keys, which always precedes that
frame's layout, and let the coroutine only release it:

- Add a small plain class (pure, testable), e.g.
  ```kotlin
  /** Tells whether the keys it is handed differ from the ones it was handed before, and never for the first ones. */
  internal class FoldKeyWatch {
      private var previous: List<Any?>? = null
      fun hasChanged(keys: List<Any?>): Boolean { val changed = previous != null && previous != keys; previous = keys; return changed }
  }
  ```
- In `rememberColumnCenteringHold`: `val watch = remember { FoldKeyWatch() }`, then
  ```kotlin
  SideEffect { if (watch.hasChanged(foldKeys.toList())) hold.isHolding = true }
  LaunchedEffect(*foldKeys) {
      delay(FOLD_RECENTER_DELAY_MILLIS)
      hold.isHolding = false
  }
  ```
  `SideEffect` runs synchronously when the composition is applied, before the layout of the same frame (also when the
  page is a pager's subcomposition: a subcomposition applies before its own children are measured), so the measure of
  the fold's frame already sees `isHolding == true` and does not overwrite `spreads` with the folded centering. Writing
  the state there is fine: it is only read in measure, which that frame runs anyway.
  The release needs neither an early return nor a generation counter. `FoldKeyWatch` and `LaunchedEffect` compare the
  keys by the same `equals`, so the effect restarts exactly when the hold is armed. A second fold cancels the first
  effect, and a cancelled `delay` never returns, so its release cannot run; the new effect releases 900 ms after the
  second fold. On first composition the effect only sets an already false `isHolding` to false. With no `finally`, a
  page that leaves the composition takes the hold with it. Do **not** add `if (!hold.isHolding) return@LaunchedEffect`:
  it relies on the effect body running after the `SideEffect`, which today's dispatchers do, but if one ever ran it
  inline, the hold would be armed after the check and never released. Drop `hasStarted`.
- Update the KDoc of `rememberColumnCenteringHold` and the hold paragraph of `CLAUDE.md` (the one naming
  `ColumnCenteringHold`) to say the hold is armed as the fold is composed and only released by the delay.

## Tests

`ColumnCenteringHoldTest` over `FoldKeyWatch`: the first keys are not a change; different keys are; equal keys
(including an equal `Set` in a new instance) are not; a change back to the first keys is a change. And over
`ColumnCenteringHold.heldSpreads`: null while not holding, null for different `columnCounts`, the recorded spreads
otherwise (if not already covered by `SectionGridTest`).

## Manual check

On the desktop and the web (and an iPhone if at hand): open a song read whole in one row with room to spare (two or
three columns), fold a section in the middle of a column. The headers above it stay put for about a second, then the
column glides to its new center. Unfold it; fold two sections in quick succession — neither tap misses its header.
