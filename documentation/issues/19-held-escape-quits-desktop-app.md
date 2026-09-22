# 19 · Desktop: holding Escape a moment too long closes every screen and then quits the app; in the editor it flickers the unsaved-changes question

**Severity:** wrong behaviour (desktop: macOS and Windows for sure, Linux where the X server reports auto-repeat. Likely: holding Escape to "get out" is a common habit, and the key repeat delay is only 225–500 ms. The web build pops every screen the same way but cannot quit) · **Area:** `:presentation` (`desktopMain/ui/CampfireDesktopApp.kt`, `wasmJsMain/ui/CampfireWebApp.kt`), `:app:desktop` (`CampfireDesktopApplication.kt`)

## Symptom
1. `./gradlew :app:desktop:run`. From the setlists tab open a song. The stack is Songs, Setlists, Song.
2. Press and hold Escape for about a second.
3. The song closes, the setlists tab closes, and the window closes. The app quits without being asked to.
   Holding Escape over a dialog or a bottom sheet does the same: the dialog goes, then the screens, then the app.
4. In the editor with unsaved text, hold Escape. The "unsaved changes" question opens and closes about 30 times a
   second for as long as the key is down. Each repeat either dismisses it (the dialog's own back handler) or asks
   again (`navigateBack`). When the key is released, whether the dialog is left up is a coin toss.
5. Web (`:app:web:wasmJsBrowserDevelopmentRun`): holding Escape pops the whole stack down to the song list, and it
   flickers the editor's question the same way.

## Cause
Every auto-repeated key press is delivered as another `KeyDown`, and every one of them is treated as a fresh Escape.

`presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireDesktopApp.kt:98-118`:

```kotlin
fun CampfireViewModel.handleKeyEvent(keyEvent: KeyEvent, onExit: () -> Unit): Boolean {
    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
        if (visibleDialog.value != null || isAnyOverflowMenuOpen) return false
        val search = currentSearch
        when {
            search?.isOpen?.value == true -> search.close()
            backStack.size > 1 -> navigateBack()
            else -> requestExit(onExit)
        }
        return true
    }
    return false
}
```

AWT sends a `KEY_PRESSED` for every repeat of a held key, with no `KEY_RELEASED` in between on macOS and Windows. So
the first press pops, the repeats pop the rest, and the first repeat that finds `backStack.size == 1` calls
`requestExit`. When the handler returns `false` (a dialog is up), Compose's own `BackNavigationEventInput` answers
every `KeyDown` Escape with a back event (Compose Multiplatform 1.12,
`skikoMain/androidx/compose/ui/navigationevent/BackNavigationEventInput.kt:27-34`: `if (event.type ==
KeyEventType.KeyDown && event.key == Key.Escape) { dispatchOnBackCompleted() }`). That is what flickers the editor's
dialog.

On the web the canvas' own key listener does the same with the browser's repeated `keydown`s (`event.repeat ==
true`). The forwarder in `CampfireWebApp.kt:118-150` also passes repeats on from text fields.

## Fix
Only the first `KeyDown` of a held Escape means anything. Filter the repeats out before anyone else sees them. That
is a matter of whether the key has been released since, not of timing.

1. Desktop. In `CampfireDesktopApp.kt`, next to `handleKeyEvent`:

   ```kotlin
   /**
    * To be wired into the window's preview key handler, which sees every key event before anything in the window
    * does. Swallows the Escapes a held key repeats: AWT sends one `KEY_PRESSED` per repeat with no release in between,
    * and every one of them would otherwise be another back - holding the key a moment too long closed every screen
    * and then the app, and in the editor it opened and dismissed the unsaved changes question over and over.
    *
    * A release is recognised however it reaches the window, so a press that was consumed by something else still
    * ends. Where the platform reports a repeat as a release and a press (X11 without detectable auto-repeat), a repeat
    * looks like a new press and nothing can be done about it here.
    */
   fun handlePreviewKeyEvent(keyEvent: KeyEvent): Boolean {
       if (keyEvent.key != Key.Escape) return false
       return when (keyEvent.type) {
           KeyEventType.KeyDown -> isEscapeHeld.also { isEscapeHeld = true }
           KeyEventType.KeyUp -> false.also { isEscapeHeld = false }
           else -> false
       }
   }

   private var isEscapeHeld = false
   ```

   (A top-level `private var` is fine: there is one window and it is only touched on the AWT event thread.) Returning
   `true` for a repeat consumes it in the preview pass, so neither the focused field, nor Compose's back input, nor
   `handleKeyEvent` sees it.

2. `app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt:79-85`, add to the `Window`:

   ```kotlin
   onPreviewKeyEvent = ::handlePreviewKeyEvent,
   ```

   next to the existing `onKeyEvent`. It does not need the view model.

3. A window that loses focus while Escape is held never gets the release. Clear the flag when the window loses
   focus as well, or the first Escape after coming back is swallowed. Add a public `fun resetEscapeKey() { isEscapeHeld
   = false }` next to `handlePreviewKeyEvent`. In the `Window` content of `CampfireDesktopApplication.kt`, register a
   `java.awt.event.WindowFocusListener` in a `DisposableEffect(window)` whose `windowLostFocus` calls it, and remove the
   listener in `onDispose`.

4. Web. In `CampfireWebApp.kt`'s `startForwardingEscapeKey` (`:118-150`), register one more listener on `window` in
   the capture phase, which runs before the canvas' own listener:

   ```js
   window.campfireEscapeRepeatFilter = function (event) {
       if (event.key === 'Escape' && event.repeat) {
           event.preventDefault();
           event.stopImmediatePropagation();
       }
   };
   window.addEventListener('keydown', window.campfireEscapeRepeatFilter, true);
   ```

   Remove it in `stopForwardingEscapeKey` (with the same `true`). Extend the KDoc of `startForwardingEscapeKey` by a
   sentence: repeats of a held Escape are stopped before anything sees them, for the reason the desktop filters them.
   Check that a `preventDefault`ed repeat is not forwarded anyway: the forwarder already skips `defaultPrevented`
   events.

5. Do **not**:
   - add a time window ("ignore Escapes within 300 ms of the last one"). Two deliberate presses in quick succession
     are two backs, and the brief rules out time-based guards where state can decide.
   - move the whole of `handleKeyEvent` into the preview pass. It is deliberately after the focused children, so a
     field that handles Escape itself keeps doing so.
   - make the root Escape stop quitting. That is the documented behaviour, see `app/desktop/CLAUDE.md`.

## Tests
None (UI is untested).

## Verify
1. Desktop: Songs, then Setlists, then a song. Hold Escape for two seconds: only the song closes. Release and press
   again: Setlists closes. Once more: the app quits (the documented root behaviour).
2. Open the song display options sheet (or a delete confirmation) and hold Escape: only the sheet or dialog closes.
3. Editor with unsaved text: hold Escape. The question opens once and stays. Release, press Escape again: it
   closes (Compose's back → the dialog's own handler), and the editor stays.
4. Hold Escape, switch to another app with Cmd+Tab while still holding it, release, come back: the next single press
   of Escape works.
5. Type in the song search field, hold Escape: the search closes once, and nothing else happens.
6. Web: the same checks 1–3 in Chrome and Safari (no quit at the root; the stack stops after one pop).

## Docs
- `presentation/CLAUDE.md`, the `desktopMain/ui/CampfireDesktopApp.kt` bullet, after "…pops the back stack, or closes
  the app.": add "Only the first press of a held Escape counts: the window's preview key handler
  (`handlePreviewKeyEvent`) swallows the repeats AWT sends while the key stays down, which would otherwise have
  closed every screen and then the app."
- The same file's `wasmJsMain/ui/CampfireWebApp.kt` bullet, after "…calls `preventDefault` on everything it
  processes.": add "Repeats of a held Escape are stopped in the capture phase before the canvas sees them, for the
  same reason the desktop swallows them."
- `app/desktop/CLAUDE.md`, the entry point sentence "…whose `onKeyEvent` is wired to
  `CampfireViewModel.handleKeyEvent` (…)": add "and whose `onPreviewKeyEvent` drops the repeats of a held Escape".

## Touches
- `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireDesktopApp.kt`
- `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireWebApp.kt`
- `app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt`
- `presentation/CLAUDE.md`, `app/desktop/CLAUDE.md`

## Depends on
Nothing. 20 (`CampfireWebApp.kt`) and 24 (`CampfireDesktopApplication.kt`) build on this plan's edits and land after it;
25 edits another part of `CampfireDesktopApp.kt`.
