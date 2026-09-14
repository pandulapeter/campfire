# 61 · Settings never retains its scroll position

**Severity:** low · **Area:** `:presentation` (`SettingsScreen.kt`, `CampfireViewModel`)

`SettingsScreen.kt:15` passes a fresh `ScrollPosition()` on each composition; the `DisposableEffect(state, position)`
in `ScrollPosition.kt` is torn down and re-registered on every recomposition and writes into a throwaway object. The
ViewModel holds only `songsScrollPosition` / `setlistsScrollPosition` (:181–182), although `ScrollPosition.kt`'s KDoc
says all three screens hand their position to the ViewModel.

## Fix

Add `internal val settingsScrollPosition = ScrollPosition()` to the ViewModel next to the other two and pass it from
`SettingsScreen`. Nothing else changes.
