# 34 — The song details screen swallows arrow keys pressed with a modifier, so Alt + Left and Cmd + Left page the setlist instead of going back

**Severity:** wrong behaviour (desktop and web; the web is where it costs the most) · **Area:** `:presentation`
(`screens/songDetails/SongKeyboardShortcuts.kt`)

## What the user sees

Reading a song on the web build: **Alt + Left** (Chrome's and Firefox's Back on Windows and Linux) does not go back
— it turns to the previous song of the setlist, or does nothing at all on a song opened from the library, and the
browser never sees the key. On a Mac, **Cmd + Left** / **Cmd + Right** (Back and Forward in Safari and Chrome) do the
same. On the desktop build the same presses page the setlist rather than being left to whatever else would have had
them.

The keys work everywhere else in the app; it is only the song details screen, which is the one screen that claims the
arrows.

## Cause

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongKeyboardShortcuts.kt:50-65`
handles the four arrows over the whole screen, in the preview pass, without looking at the modifiers:

```kotlin
    return this
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { keyEvent ->
            // Key repeats arrive as further KeyDown events, which is what makes a held arrow scroll continuously.
            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val action = when (keyEvent.key) {
                Key.DirectionUp -> onScrollUp
                Key.DirectionDown -> onScrollDown
                Key.DirectionLeft -> onPreviousSong
                Key.DirectionRight -> onNextSong
                else -> null
            } ?: return@onPreviewKeyEvent false
            action()
            true
        }
```

The screen takes focus as it opens (`focusRequester.requestFocus()` at `:49`), so it is on the focus path for every
key pressed while it is up, and returning `true` consumes the event — which on the web is a `preventDefault` on the
canvas' listener, so the browser's own Back never happens. The navigation the press was meant for is the app's own
back stack there ("the browser's history is the app's back stack", root `CLAUDE.md`), so this is the app eating its
own Back.

`Key.DirectionLeft` / `Key.DirectionRight` are also the only two that can be `null` (at the ends of a setlist, or
with one song to read), which is why the bug looks intermittent: at the last song of a setlist Alt + Left is left
alone and works.

## The change

Invoke the **`code-style`** skill before the first edit. `commonMain` stays JVM-free; the three properties used are
Compose's own (`androidx.compose.ui.input.key`).

In `SongKeyboardShortcuts.kt`, add the three imports

```kotlin
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
```

and the guard, directly under the `KeyDown` check:

```kotlin
            // Key repeats arrive as further KeyDown events, which is what makes a held arrow scroll continuously.
            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            // An arrow pressed with Alt, Meta or Ctrl belongs to whoever sent it rather than to the reader: Alt + Left
            // is the browser's Back, which on the web is this app's own back stack, and Cmd + Left and Cmd + Right are
            // Back and Forward on a Mac. A page turner pedal sends the arrows on their own, so nothing is lost by
            // leaving those presses alone. Shift is not among them: it modifies a selection, and there is nothing on
            // this screen to select.
            if (keyEvent.isAltPressed || keyEvent.isMetaPressed || keyEvent.isCtrlPressed) return@onPreviewKeyEvent false
```

Extend the KDoc above the function, after "Consuming all four takes two dimensional focus traversal away from this
screen…": "Only the four on their own: an arrow with Alt, Meta or Ctrl is somebody else's shortcut — the browser's
Back among them — and is left unconsumed."

### The screen's other shortcuts, checked

All of these were read at HEAD; only the arrows are wrong.

- `screens/songDetails/FontScaleGestures.kt:103` — text size on scroll already **requires** a modifier:
  `if (event.type == PointerEventType.Scroll && (event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed))`. Correct as it is.
- `screens/songEditor/SongEditorScreen.kt:603` — save:
  `keyEvent.key == Key.S && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && !keyEvent.isAltPressed`. Correct.
- `desktopMain/ui/CampfireDesktopApp.kt:112` — Ctrl / Cmd + F: the same shape, `!keyEvent.isAltPressed` included.
  Correct.
- `wasmJsMain/ui/CampfireWebApp.kt:95` — the web's Ctrl / Cmd + F capture listener:
  `(keyEvent.ctrlKey || keyEvent.metaKey) && !keyEvent.altKey`. Correct.
- `desktopMain/ui/CampfireDesktopApp.kt:114-131` — Escape is answered whatever the modifiers are. **Left as it is, on
  purpose:** Alt + Escape and Cmd + Escape are taken by the window manager before the app ever sees them, and Escape
  does not mean anything else with a modifier held — unlike an arrow, which is half of half a dozen system shortcuts.
  Say so in the commit message rather than changing it.

## Tests

No unit test is possible: this is a Compose `Modifier` and `:presentation` has no test source set (the UI is untested
by policy). The behaviour is a manual check.

Compile check: `./gradlew :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs`

## Verification

1. **Web, which is where it matters.** `./gradlew :app:web:wasmJsBrowserDevelopmentRun`, open the printed address,
   import or plant the demo library, open a song from a setlist and page to the middle of it.
   - On Windows/Linux (or with a keyboard that has Alt): **Alt + Left** must go back to the setlist, and **Alt +
     Right** must come forward again. Before the fix, Alt + Left turns to the previous song.
   - On macOS: **Cmd + Left** / **Cmd + Right**.
   - The plain arrows must still work: Left and Right page the setlist, Up and Down scroll the song, held Up or Down
     scrolls continuously.
2. **Desktop.** `./gradlew :app:desktop:run`. Plain arrows page and scroll as before; Cmd/Alt + Left and Right no
   longer page. Escape still closes the screen, and Ctrl / Cmd + F still opens the search from the list screens.
3. **A page turner pedal, if one is at hand** (Android or iOS, paired over Bluetooth): it sends the arrows on their
   own, so it is unaffected. Worth one pass on a device if a pedal is available; not a blocker otherwise, since
   neither Android nor iOS delivers Alt or Meta with an arrow from such a device.

## Docs

- `presentation/CLAUDE.md`, line 76, the `screens/songDetails/SongKeyboardShortcuts.kt` bullet, currently ends the
  consuming argument with:

  > Consuming all four takes two dimensional focus traversal away from this screen, which is the trade — the keys are
  > worth more to a reader here than they are to Tab, which still traverses everything. Left and Right are left
  > unconsumed at the ends of the setlist and when there is a single song to read.

  Add to that second sentence: "…, and all four are left unconsumed when Alt, Meta or Ctrl is held: Alt + Left is the
  browser's Back, which on the web is the app's own back stack, and Cmd + Left and Cmd + Right are Back and Forward
  on a Mac."
- `documentation/features.md:21-22` ("The **arrow keys** scroll the song and step through the setlist — which is what
  a page turner pedal sends…") stays true as written; a pedal sends the arrows alone. No change.

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongKeyboardShortcuts.kt`
- `presentation/CLAUDE.md`

## Depends on

Nothing.
