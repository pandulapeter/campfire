# Ignore a held key's repeats in the Space / M metronome shortcut on the web and the desktop

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** web, desktop
**Files:** presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireWebApp.kt, presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireDesktopApp.kt, app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt (only if the reset function is renamed), presentation/CLAUDE.md

## Problem

Space (on the Metronome tab) and M (on a song) toggle the click, and every key-down counts, including the repeats the
system sends while the key is held (about 30 a second after a short delay). Holding the key a moment too long — easy
with a foot pedal mapped to Space, or a slow finger — starts and stops the click over and over, each one a fresh start
from beat one and, on Android-like platforms, a notification; on the web each also closes and reopens the audio
context.

Web, `CampfireWebApp.kt`, `MetronomeShortcutEffect`, no `repeat` check:

```kotlin
val keyEvent = event.unsafeCast<KeyboardEvent>()
if (keyEvent.defaultPrevented || keyEvent.ctrlKey || keyEvent.metaKey || keyEvent.altKey || isTypingTarget(keyEvent)) return@listener
val isSpace = when (keyEvent.code) { "Space" -> true; "KeyM" -> false; else -> return@listener }
if (viewModel.toggleMetronomeByKey(isSpace)) keyEvent.preventDefault()
```

Desktop, `CampfireDesktopApp.kt`, `handleKeyEvent`:

```kotlin
if (
    keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Spacebar || keyEvent.key == Key.M) &&
    !keyEvent.isCtrlPressed && !keyEvent.isMetaPressed && !keyEvent.isAltPressed
) {
    return toggleMetronomeByKey(isSpace = keyEvent.key == Key.Spacebar)
}
```

AWT sends one `KEY_PRESSED` per repeat with no release between them and has no repeat flag; the same file already
solves this for Escape with `handlePreviewKeyEvent` / `isEscapeHeld` / `resetEscapeKey`.

## Fix

**Web:** in `MetronomeShortcutEffect`, after the `isSpace` match, swallow a repeat without toggling:

```kotlin
if (keyEvent.repeat) {
    // A held key repeats its key-down; only the first press is a request, the rest would start and stop the click
    // thirty times a second.
    return@listener
}
```

(`org.w3c.dom.events.KeyboardEvent.repeat` exists in kotlinx-browser; if it does not resolve, read it with a one-line
`js("event.repeat === true")` helper like `isTypingTarget`.) Whether to `preventDefault` a repeat does not matter: the
page is one canvas and does not scroll.

**Desktop:** track the held state in the preview handler, which sees every event, without consuming Space or M there
(a text field must still get a held Space's repeats):

- Add `private var heldMetronomeKey: Key? = null` and `private var isMetronomeKeyRepeat = false` next to `isEscapeHeld`.
- In `handlePreviewKeyEvent`, before the Escape early return: for `Key.Spacebar` / `Key.M`, on `KeyDown` set
  `isMetronomeKeyRepeat = heldMetronomeKey == keyEvent.key` then `heldMetronomeKey = keyEvent.key`; on `KeyUp` of the
  held key set `heldMetronomeKey = null`; return `false` either way.
- In `handleKeyEvent`'s Space/M branch, `if (isMetronomeKeyRepeat) return false` before calling
  `toggleMetronomeByKey`.
- In `resetEscapeKey`, also clear `heldMetronomeKey` (a window that loses focus with the key down never gets its
  release). Optionally rename it to `resetHeldKeys` and update its one caller in
  `app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt` (`windowLostFocus`); keeping the
  name avoids touching `:app:desktop`. Update the KDoc of `handlePreviewKeyEvent` to say it also notes held Space / M.

As with Escape, X11 without detectable auto-repeat reports repeats as release + press, which nothing here can tell
from new presses.

In `presentation/CLAUDE.md`, where it says Space and M toggle the click on the desktop and the web, add "only on the
first press of a held key". (presentation/CLAUDE.md is shared with the lane that owns `presentation/src/commonMain`;
this is a one-clause edit.)

## Tests

None: key-event plumbing in platform shells, outside the pure logic that is unit tested.

## Manual check

Desktop (`./gradlew :app:desktop:run`) and web (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`): on the Metronome
tab hold Space for two seconds — the click starts once and keeps playing; release and press again — it stops. On a
song, the same with M. Holding Space in a text field (e.g. the editor) still types repeated spaces. On the desktop,
hold Space, switch windows with the mouse while holding, release, come back, press Space once — it toggles.
