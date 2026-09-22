# 20 · Web: Ctrl / Cmd + S in the editor saves the song and also opens the browser's "Save page as" dialog

**Severity:** wrong behaviour (web only, every browser; certain for anyone who uses the shortcut the editor
advertises — the browser dialog pops over the app on every press, and a user who clicks "Save" in it downloads an
HTML shell of the app) · **Area:** `:presentation` (`wasmJsMain/ui/CampfireWebApp.kt`; the shortcut itself is in
`SongEditorScreen.kt`)

**Verifier:** The key test now also matches `event.code === 'KeyS'` (Compose resolves `Key.S` from the physical `code`, so on a Cyrillic or Greek layout the editor saved while the browser dialog still opened), and the editor's own shortcut gets `!isAltPressed` so that AltGr + S (Windows reports it as Ctrl + Alt; `ś` on Polish Programmer) types its character instead of saving.

## Symptom
1. Open the web build (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`), open a song in the editor, type something.
2. Press Ctrl + S (Cmd + S on macOS).

The song is saved (the Save button greys out), and at the same moment the browser's own "Save page as…" dialog
opens. Holding the keys repeats the dialog request. On the desktop and Android builds the same key only saves.

## Cause
The shortcut is handled on the text field in
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt:563-570`:

```kotlin
.onPreviewKeyEvent { keyEvent ->
    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.S && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)) {
        onSaveRequested()
        true
```

Returning `true` consumes the Compose event, but on the web the caret lives in a hidden `<input>` next to the canvas,
and that element's `keydown` listener only queues the DOM event for later processing — it never calls
`preventDefault` on it except for Tab (Compose Multiplatform 1.12.0, `ui-wasm-js`,
`webMain/androidx/compose/ui/platform/DomInputStrategy.kt`):

```kotlin
htmlInput.addEventListener("keydown", { evt ->
    nativeInputEventsProcessor.registerEvent(evt as KeyboardEvent)
    if (evt.keyCode == tabKeyCode) {
        evt.preventDefault()
    }
    ...
```

`NativeInputEventsProcessor.runCheckpoint` sends the key to Compose afterwards, when the browser has already acted on
the unprevented event. The canvas' own listener (`ComposeWindowInternal.web.kt`, `processKeyboardEvent`) does call
`preventDefault` on what Compose processes, but a key pressed in the hidden input never passes the canvas — the same
reason `startForwardingEscapeKey` exists (`CampfireWebApp.kt:118-150`). Nothing in the app prevents the default of
Ctrl / Cmd + S, so the browser runs its "Save page" command.

## Fix
In `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireWebApp.kt`, next to
`startForwardingEscapeKey` / `stopForwardingEscapeKey`, add a pair that keeps the browser's save command off the page
for as long as the app is on it, started and stopped exactly where the Escape forwarder is:

```kotlin
/**
 * Keeps Ctrl / Cmd + S from the browser, whose "Save page as" would otherwise open over the editor every time the
 * song is saved with it: the editor handles the key itself (see SongEditorScreen), but a key pressed in the hidden
 * `<input>` that holds the caret is handed to Compose only after the browser has already acted on it, so Compose
 * consuming it cannot stop the browser. Only the default is prevented, in the capture phase: the event still reaches
 * that input, and through it the editor. A page of this app saved as HTML is a copy of nothing.
 */
private fun startSuppressingBrowserSave() {
    js(
        """(function () {
            if (window.campfireSaveSuppressor) return;
            window.campfireSaveSuppressor = function (event) {
                // code, as Compose goes by it (Key.S is the physical key); key for a virtual keyboard, which has none.
                var isS = event.code === 'KeyS' || event.key === 's' || event.key === 'S';
                if ((event.ctrlKey || event.metaKey) && !event.altKey && isS) {
                    event.preventDefault();
                }
            };
            window.addEventListener('keydown', window.campfireSaveSuppressor, true);
        })()"""
    )
}
```

and the matching `stopSuppressingBrowserSave()` removing it with the same `true` capture flag and nulling the
global. Start and stop them in the `DisposableEffect(Unit)` that already starts and stops the Escape forwarder
(`CampfireWebApp.kt:36-39`), and mention the pair in `CampfireWebApp`'s KDoc next to the Escape forwarding.

Why this shape:
- Capture phase on `window`, so it runs before the input's own listener; `preventDefault` does not stop propagation,
  so `DomInputStrategy` still registers the event and the editor still saves.
- The canvas listener does not look at `defaultPrevented`, so Compose shortcuts on the canvas are unaffected.
- `Shift` is allowed through the check on purpose (Ctrl + Shift + S is also "Save as" in some browsers); `Alt` is
  excluded because AltGr on Windows arrives as Ctrl + Alt and types characters on some layouts (`ś` on Polish).
- Always, not only while the editor is open: saving the page is useless anywhere in this app, and tying it to the
  editor would need another channel from the view model to the shell for no gain.

Also, in `SongEditorScreen.kt:564`, exclude Alt from the editor's own check, for the same AltGr reason (on the
web and on Windows AltGr arrives as Ctrl + Alt, so AltGr + S — `ś` on the Polish Programmer layout — saves the song
and swallows the character today):

```kotlin
if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.S && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && !keyEvent.isAltPressed) {
```

(import `androidx.compose.ui.input.key.isAltPressed`). Ctrl + Alt + S is nobody's save shortcut.

Do **not** call `stopPropagation`/`stopImmediatePropagation` (the editor would never hear the key), and do not move
the shortcut to the window key handler: the field is where the text is.

## Tests
None (UI is untested).

## Verify
1. `./gradlew :app:web:wasmJsBrowserDevelopmentRun`, in Chrome, Firefox and Safari: open a song in the editor, type,
   press Ctrl / Cmd + S — the song is saved and no browser dialog opens. Hold the keys — still no dialog.
2. On the Songs screen (no field focused) press Ctrl / Cmd + S — nothing happens (no dialog).
3. Type into the editor with AltGr combinations on a Polish (Programmer) or German keyboard layout on Windows —
   characters still arrive, AltGr + S types `ś` and does not save (web and desktop).
3a. Switch the OS keyboard to Russian and press Ctrl + S in the editor: saved, no browser dialog.
4. Escape still closes dialogs holding a focused field (the forwarder is untouched).
5. `./gradlew :app:web:wasmJsBrowserDistribution`.

## Docs
`presentation/CLAUDE.md`, the `wasmJsMain/ui/CampfireWebApp.kt` bullet: after the Escape sentence add "For the same
reason — a key pressed in that hidden input reaches Compose only after the browser has acted on it — Ctrl / Cmd + S
has its default prevented on the window in the capture phase, or the browser's "Save page as" would open over the
editor every time a song is saved with the shortcut."

## Touches
- `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireWebApp.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt` (the `onPreviewKeyEvent` condition only)
- `presentation/CLAUDE.md`

## Depends on
19 (it edits `startForwardingEscapeKey` / `stopForwardingEscapeKey` in the same file, and this plan adds its pair
next to them and to the same `DisposableEffect`): apply after 19. 30, 31 and 11 also edit `SongEditorScreen.kt`
(other lines); schedule one after another.
