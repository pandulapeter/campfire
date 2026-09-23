# 31 — Web: the browser's Forward into a search reopens it empty

**Severity:** wrong state (web) · **Area:** `:presentation` (`components/SearchState.kt`, `CampfireViewModel.kt`)

**Read, not run.** This was found by reading the code at HEAD (2065e47f); it has not been reproduced in a running
build. The "Verification" section below is how to confirm it, and confirming it is the first step of the work.

## What the user sees

In the web build, on the Songs (or Setlists) screen: open the search and type `love`. The address becomes `/search`.
Press the browser's Back: the search closes, the list is whole again, the address is `/`. Press Forward: the search
opens again — but empty, the whole library listed under an empty field, rather than the `love` results the entry was
left on. Every other Forward in the app puts back what was there (the settings tab, the page of a setlist's pager).

## Cause

Forward is answered by `BrowserHistoryEffect.goForward` (`wasmJsMain/.../navigation/BrowserHistory.kt:196`), which
calls `CampfireViewModel.restoreNavigationState`, and that opens a search through `SearchState.open()`
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:929-931`):

```kotlin
        listOf(songsSearch to state.isSongsSearchOpen, setlistsSearch to state.isSetlistsSearchOpen).forEach { (search, isOpen) ->
            if (isOpen != search.isOpen.value) if (isOpen) search.open() else search.close()
        }
```

`open()` is the search button's action and empties the field on purpose
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SearchState.kt:73-81`):

```kotlin
    /** Opens onto an empty field rather than onto the last search, which is a question the user has moved on from. */
    fun open() {
        textFieldState.clearText()
        _isOpen.value = true
    }

    fun close() {
        _isOpen.value = false
    }
```

`close()` keeps the text (by design, so the field still reads right while it animates away), so the query is still
in the field when Forward arrives — `open()` is what throws it away. A step back into history is not the user asking
for a new search; it is going back to the one that was there.

`restoreNavigationState` has one other caller, `navigateOnLaunch` (`:962`), for the address the page was loaded on.
There the field is empty anyway (the web restores no saved state), so the difference does not show.

## The change

Invoke the **`code-style`** skill before the first edit.

Add to `SearchState` (after `close()`):

```kotlin
    /**
     * Opens the search onto whatever its field still holds, which is what going back to a search the user left is:
     * the browser's Forward returning to it. The search button asks [open] instead, since that is a new question.
     */
    fun reopen() {
        _isOpen.value = true
    }
```

and use it in `restoreNavigationState` (`CampfireViewModel.kt:930`):

```kotlin
            if (isOpen != search.isOpen.value) if (isOpen) search.reopen() else search.close()
```

Nothing else changes: the field puts the caret at the end whenever the search is opening (`Search.kt:515-518`), and
`activeQuery` narrows the list by the text again the moment `isOpen` is true.

Extend the KDoc of `restoreNavigationState` (`:921-925`) with: "A search it opens is reopened on the text its field
still holds, since this is stepping back to a place rather than asking anything new."

## Tests

- **No unit test is possible**: `SearchState` wraps a Compose `TextFieldState` and `:presentation` has no test source
  set.
- Compile check: `./gradlew :presentation:compileKotlinWasmJs :presentation:compileKotlinDesktop`

## Verification

1. `./gradlew :app:web:wasmJsBrowserDevelopmentRun`, demo library or any library with a few songs.
2. Songs → search → type `ho`. The address is `…/search`, the list is narrowed.
3. Browser Back: the search closes, the list is whole, the address is `…/`.
4. Browser Forward:
   - **Before:** the search is open and empty, the whole library is listed.
   - **After:** the search is open with `ho` in it, the caret after the `o`, and the list narrowed as in step 2.
5. The same on the Setlists screen (`…/setlists/search`).
6. Regression: the search button still opens an empty field after a search was closed with text in it; a page loaded
   directly on `…/search` opens the search empty.

## Docs

- `presentation/CLAUDE.md`, the `wasmJsMain/ui/navigation/` bullet: after "Forward restores the `NavigationState`
  recorded when the entry was last on top," add "a search with the text its field still holds,".

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SearchState.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/CLAUDE.md`

## Depends on

Nothing. Lane D order: 33, **31**, 30, 29, 34, … (one line of `CampfireViewModel.kt`).
