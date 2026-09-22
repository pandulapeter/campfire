# 36 · A search opened again while it is still closing comes back without the keyboard

**Severity:** minor (Android and iOS; happens whenever the search button is tapped twice in quick succession from an
open search, i.e. close + reopen) · **Area:** `:presentation` (`components/Search.kt`: `SearchField`)

## Symptom
1. Android or iOS, songs (or setlists) screen. Tap the search button: the field opens, focused, keyboard up.
2. Tap the button again (it is the close mark now) and, before the field has finished collapsing, tap it a third time.

The search is open again, empty, with the caret in it — but the keyboard stays down, and the user has to tap into the
field to type. Opening it from fully closed always brings the keyboard, so the difference reads as the app ignoring
the tap.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/Search.kt:502-508` focuses the
field once, when its content enters the composition:

```kotlin
if (isContentShown) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        searchState.textFieldState.edit { placeCursorAtEnd() }
        focusRequester.requestFocus()
    }
```

`isContentShown` is `searchTransition.currentState || searchTransition.targetState` (`:338`), which stays true through
the whole close animation. Closing (`SearchAction`, `:434-441`, and `SearchBackHandler`, `:369-372`) hides the keyboard
but leaves the focus in the field. A reopen before the animation has ended therefore never leaves the composition:
the effect does not run again, and even if it did, requesting focus for a field that already has it shows no keyboard.

## Fix
Run the opening work whenever the search starts opening, not once per composition of the content, and ask for the
keyboard explicitly:

- Give `SearchField` a parameter `isOpening: Boolean` (KDoc: "Whether the search is open or on its way there, which is
  when the field takes the focus and the keyboard."), passed as `searchTransition.targetState` from
  `SearchableTopAppBarTitle` (`:334-341`).
- Replace the effect:

  ```kotlin
  // Keyed on the search being opened rather than on the content arriving: a search opened again while it was still
  // closing never left the composition, and kept the focus while the keyboard had been put away - so the keyboard is
  // asked for as well, since focusing a field that has the focus shows nothing.
  LaunchedEffect(isOpening) {
      if (isOpening) {
          searchState.textFieldState.edit { placeCursorAtEnd() }
          focusRequester.requestFocus()
          keyboardController?.show()
      }
  }
  ```

On a first open the content enters with `isOpening` already true, so it behaves exactly as before (the extra `show()`
is a no-op where the focus already brought the keyboard up; desktop has no software keyboard).

Do **not** clear the focus when closing to force the old effect to work: the closing field is still on screen and
keeping it focused is what keeps its caret where the user left it for the duration of the animation.

## Tests
None (UI is untested).

## Verify
1. Android and iOS: the repro above, and also open → close with the back gesture → reopen quickly. Before: no keyboard.
   After: keyboard up each time, caret in the empty field.
2. Opening from fully closed, closing, switching tabs with the search open and coming back (the caret goes to the end
   of the kept text): unchanged.
3. Desktop and web: the field takes the focus on every reopen and typing goes into it.

## Docs
None — `presentation/CLAUDE.md` already says the field puts the caret at the end "as it opens in any case"; this makes
that true for a reopen as well.

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/Search.kt`

## Depends on
Nothing. 18 adds an `inputTransformation` to the same `SearchField`; schedule the two one after another.
