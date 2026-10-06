# Change a search's open state and its text in one snapshot, so reopening never applies the previous query

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SearchState.kt,
presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/components/SearchStateTest.kt (only if a
test turns out possible, see Tests)

## Problem

`SearchState.kt:45`, `:69-71`, `:82-86` (8ee010b36):
```kotlin
private val _isOpen = MutableStateFlow(isInitiallyOpen)
val activeQuery: Flow<String> = combine(_isOpen, snapshotFlow { textFieldState.text.toString() }) { isOpen, query ->
    if (isOpen) query else ""
}
fun open() {
    textFieldState.clearText()
    isFocusOwed = true
    _isOpen.value = true
}
```
The field keeps its text after `close()` by design. On reopening, `open()` clears it — a global snapshot write, which a
`snapshotFlow` only sees after the next apply notification (Compose's global snapshot manager sends it from the main
thread, later) — and then sets the `MutableStateFlow`, which a collector sees at once. `activeQuery` is combined into
`songGroups` and `setlistsWithSongs` (`CampfireViewModel.kt:949-955`, `:1060`), both `flowOn(Dispatchers.Default)`, so
the combine on Default receives `isOpen = true` while it still holds the old text and publishes the previous query's
ranked results, then the full list once `""` arrives. The order is deterministic; whether a frame shows the intermediate
list depends on timing (more likely for a large library, where ranking takes longer).

When it is composed, `SongSearchScrollAnchor.update` (`SongsScreen.kt:314-331`) sees contents change and drops its
anchor, and `ScrollToTopWhenChanged` (`:406-415`) sees the filter key change twice and scrolls to item 0: the documented
"opening search keeps the active section still" does not hold, and the old results flash. The setlists list gets the
same transient. Scenario: search "abc", close it with the cross, scroll the songs to the middle, tap search again.

## Fix

Make the open state a snapshot state too, so `open()`'s two writes reach `activeQuery` in one apply:
```kotlin
private val _isOpen = MutableStateFlow(isInitiallyOpen)
val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()
/** [isOpen] as snapshot state, written together with it, so that [activeQuery] reads it and the text in one snapshot. */
private var isOpenSnapshot by mutableStateOf(isInitiallyOpen)

val activeQuery: Flow<String> = snapshotFlow { if (isOpenSnapshot) textFieldState.text.toString() else "" }.distinctUntilChanged()

fun open() { textFieldState.clearText(); isFocusOwed = true; isOpenSnapshot = true; _isOpen.value = true }
fun close() { isFocusOwed = false; isOpenSnapshot = false; _isOpen.value = false }
fun reopen() { isFocusOwed = true; isOpenSnapshot = true; _isOpen.value = true }
```
(`openOrFocus` goes through `open()`.) The public `isOpen` stays a `StateFlow`, so its callers (the screens, the
persisted `SavedSearch`, the desktop Escape handler) are unchanged. The trade-off: on `close()`, `activeQuery` now turns
`""` one apply later than `isOpen` turns false, where today the two arrive together. Check the songs screen's
close-restores-the-position behaviour (`SongSearchScrollAnchor`) still holds; it already receives `songGroups` a
`Dispatchers.Default` computation after `isOpen`, so it cannot rely on them arriving in one frame — but verify by hand.
If it regresses, the alternative is to keep `_isOpen` in the combine and add a third input that `open()` bumps (a
generation counter, `MutableStateFlow<Int>`) together with a `MutableStateFlow<String>` mirror of the text cleared in
the same call — heavier; prefer the snapshot version.

Update the KDoc of `activeQuery`: the open state and the text are read in one snapshot so that reopening never narrows
the list by the query it is about to clear.

## Tests

`SearchStateTest` exists but tests focus bookkeeping only. A test of `activeQuery` would need a running snapshot apply
loop (`Snapshot.sendApplyNotifications()` in a `runTest`), which the project does not do elsewhere; none is required.

## Manual check

A library of a few thousand songs (import a large folder) on Android or desktop: search "a", close it with the cross,
scroll the list to the middle, tap search again. The list does not jump to the top and no search results flash; closing
the empty search returns to the exact position. Same on the Setlists screen.
