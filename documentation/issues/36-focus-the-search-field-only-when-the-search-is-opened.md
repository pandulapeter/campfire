# Give the list screens' search field the focus and the keyboard only when the search is opened, not every time its screen is composed again

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (visible on Android and iOS, where focus brings up the keyboard)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SearchState.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/Search.kt, presentation/CLAUDE.md

**Challenged:** sound — `restoreNavigationState` (web address / Forward) goes through `reopen()`, which owes the focus; the import report's field and the pickers (plan 11) have fields of their own and do not use `SearchField`, so they are untouched.

## Problem

Live run (scratchpad live/74_75.png): on Songs, search "alpha", put the keyboard away with Back, open the result, go Back — the keyboard comes up again over the results, although the user had dismissed it.

Cause: `NavDisplay` composes only the entries on screen, so the Songs (or Setlists) screen leaves the composition once the song details screen has finished sliding in, and enters it afresh on the way back. `SearchField` (Search.kt:719–731) asks for the focus whenever it enters the composition with the search open:

```kotlin
if (isContentShown) {
    …
    LaunchedEffect(isOpening) {
        if (isOpening) {
            searchState.textFieldState.edit { placeCursorAtEnd() }
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
```

`isOpening` is `searchTransition.targetState` (Search.kt:541), which is `true` on the first frame of a screen whose search is still open, so the effect runs on every return — from a song, from another tab, or after the process is restored with the search open. It was meant for the search *being opened* (the KDoc: "Keyed on the search being opened rather than on the content arriving"). The `SearchState` it reads lives in the view model and survives all of these.

(The caret seen again in the Setlists search after saving the Edit setlist sheet, live/73, is the field still holding the focus Back left it with — Back hides the keyboard without clearing focus — and the window getting its focus back when the sheet's window closes; no keyboard came up there. It is not part of this fix.)

## Fix

Make the request a one-shot owed by an opening, held in `SearchState` next to the existing `focusRequests` (which already avoids exactly this for Ctrl/Cmd+F: "a field composed again on the way back to its screen must not take a request it has already answered").

In `SearchState.kt`:
```kotlin
/**
 * Whether the field still owes the latest opening the focus and the keyboard. The field takes it once ([takeFocusOnOpen]);
 * a field composed again with the search still open — on the way back from a song, or to its tab — finds it taken, and
 * leaves the keyboard the way the user left it.
 */
private var isFocusOwed = false

fun open() {
    textFieldState.clearText()
    isFocusOwed = true
    _isOpen.value = true
}

fun close() {
    isFocusOwed = false
    _isOpen.value = false
}

fun reopen() {
    isFocusOwed = true
    _isOpen.value = true
}

/** True once per opening, for the field to take the focus and the keyboard with. */
fun takeFocusOnOpen(): Boolean = isFocusOwed.also { isFocusOwed = false }
```
(A plain field is enough: it is read and written only from the field's effect and from the click handlers, both on the main thread, and nothing has to recompose on it.) `openOrFocus()` goes through `open()` or `focusRequests` and needs no change.

In `Search.kt`, the effect becomes:
```kotlin
LaunchedEffect(isOpening) {
    if (isOpening && searchState.takeFocusOnOpen()) {
        searchState.textFieldState.edit { placeCursorAtEnd() }
        focusRequester.requestFocus()
        keyboardController?.show()
    }
}
```
and its comment gains: "…and only once per opening: the screen leaves the composition while a song covers it, and a search the user had put the keyboard away in must not bring it back on the way back." The case the existing comment describes (opened again while still closing) still works, since `open()` owes the focus again.

Behaviour changes to note in the presentation/CLAUDE.md `SearchState` paragraph (line ~63, "a search left open is come back to with the caret where the typing stopped…"): add "— without the keyboard: the field takes the focus once per opening (`takeFocusOnOpen`), so coming back to a screen whose search is open, from a song or another tab, leaves the keyboard as the user left it". A search restored after the process was killed also opens without the keyboard now, and on the desktop and the web a screen come back to no longer puts the caret back in its open search (Ctrl / Cmd + F does, with the query selected).

Make sure the web build's browser Forward (`reopen()`) still focuses: it owes the focus like `open()`.

## Tests

None as a unit test is meaningful only for the flag itself; add one small `SearchStateTest` in `presentation/src/commonTest/.../ui/components/` if the executor wants it: `open()` then `takeFocusOnOpen()` is true once and false the second time; `close()` then `reopen()` makes it true again; a `SearchState(isInitiallyOpen = true)` starts with it false. (`SearchState` uses `TextFieldState` and `snapshotFlow`, which run on the desktop target.)

## Manual check

On an Android phone: Songs → search → type a word → Back to hide the keyboard → open a result → Back. The results are shown without the keyboard. Repeat switching to Setlists and back to Songs: no keyboard. Tap the close button and the search button again: the field takes the focus and the keyboard comes up. On the desktop, Ctrl/Cmd+F still selects the query in an open search.
