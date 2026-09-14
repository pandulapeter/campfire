# 64 · `SearchableTopAppBar`'s `LaunchedEffect` restarts on every frame of a predictive-back drag

**Severity:** low · **Area:** `:presentation` (`Search.kt`)

`Search.kt:159`: `LaunchedEffect(searchState.backProgress, isOpen, isClosedAndSettled)`. Each frame of the gesture
cancels and relaunches the effect, whose first branch only `snapTo`s the progress.

## Fix

Key the effect on `isOpen` and `isClosedAndSettled` only, and drive the snap from a `snapshotFlow { searchState.backProgress }`
collected inside it (`collect { if (it > 0f) recession.progress.snapTo(it) }`), so the gesture updates the animatable
without restarting the coroutine. Keep the two other branches as they are.
