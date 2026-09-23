# 42 — Ctrl / Cmd + S does nothing in the editor while the preview is the only pane, or before the text is clicked

**Severity:** shortcut silently dead (desktop, web; any hardware keyboard) · **Area:** `:presentation`
(`screens/songEditor/SongEditorScreen.kt`)

**Read, not run.** This was found by reading the code at HEAD (2065e47f); it has not been reproduced in a running
build. The "Verification" section below is how to confirm it, and confirming it is the first step of the work.

## What the user sees

In the editor, with unsaved changes:

- switch to **Preview** and press Ctrl / Cmd + S: nothing is saved, and nothing says so. On the web the browser's own
  "Save page as" is suppressed as well, so the key does nothing at all.
- (found while checking the above) open the editor and press Ctrl / Cmd + S before clicking into the text — or after
  switching between Edit and Split, which composes the field again — nothing either.

## Cause

The shortcut lives on the text field
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt:599-609`):

```kotlin
            // The same save as the app bar's button, for the hand that reaches for the keyboard instead. It lives on
            // the field rather than in the window's key handler, which has no way to reach this text. Not with Alt held:
            // AltGr arrives as Ctrl + Alt on Windows and the web, and AltGr + S types a character on some layouts.
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.S && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && !keyEvent.isAltPressed) {
                    onSaveRequested()
                    true
                } else {
                    false
                }
            }
```

Key events only travel along the focus path, from the root to the focused node. When the preview is the only pane
(`:474-480`) the field is not composed, so nothing in the editor is focused and the handler does not exist; when the
field is composed but has not been clicked (the editor opens with nothing focused — `SongEditorScreen.kt` requests
focus nowhere, and the toolbar's buttons take none, `EditorToolbar.kt:315`), the handler exists but no event is
routed through it. The web shell prevents the browser's default for the key in the capture phase
(`CampfireWebApp.kt:216-231`), so there it is simply swallowed.

## The change

Invoke the **`code-style`** skill before the first edit.

**Corrected from the reviewer's fix** ("move the handler to the editor root Column's `onPreviewKeyEvent`"): moving it
is right, but on its own it still hears nothing in the preview, because nothing inside the Column is focused. The
editor has to hold the focus itself when nothing in it does, the way the song details screen does for its arrow keys
(`songKeyboardShortcuts`: "The screen takes focus as it opens, because nothing on it would otherwise ever be
focused").

In `LoadedSongEditor`, before the `Column` (`:320`):

```kotlin
    // Key events only travel along the focus path, and nothing in the editor is focused until the text is clicked,
    // nor at all while the preview is the only pane - or after the panes change places, which composes the field
    // again. So the editor takes the focus itself as it opens and whenever the panes change, and the save shortcut
    // sits in its preview pass, where it hears the key wherever inside the editor the focus has gone since: the field
    // once it is clicked, the preview, a button of the bar.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(panes) { focusRequester.requestFocus() }
```

and on the `Column` (`:320-322`):

```kotlin
    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .focusRequester(focusRequester)
            .focusable()
            // The same save as the app bar's button, for the hand that reaches for the keyboard instead. Not with Alt
            // held: AltGr arrives as Ctrl + Alt on Windows and the web, and AltGr + S types a character on some layouts.
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.S && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && !keyEvent.isAltPressed) {
                    onSaveRequested()
                    true
                } else {
                    false
                }
            }
    ) {
```

and remove the handler from `ChordProTextField` (`:599-609`) together with its `onSaveRequested` parameter
(`:558`) and the argument passed at `:438`. `onSaveRequested` (`:296-301`) already checks `hasUnsavedChanges`, so a
press with nothing to save does nothing, as today.

Imports: `androidx.compose.foundation.focusable`, `androidx.compose.ui.focus.FocusRequester`,
`androidx.compose.ui.focus.focusRequester` (`onPreviewKeyEvent` and the key imports are already there).

What focusing the editor does elsewhere: nothing visible — `focusable()` draws no indication — and on a touch device
no keyboard comes up, since only a text field asks for one. Switching panes already took the focus away from the
field (it is composed again), so requesting it for the editor on that change takes nothing from the user. Tab
traversal starts from the editor instead of from nowhere.

**Web, to confirm first:** Compose for the web delivers a key to its canvas. With the field gone, whether the canvas
has the browser's focus decides whether the key reaches Compose at all. If step 3 of the verification fails on the
web only, the fix is in `CampfireWebApp.kt`: have the save suppressor (`:216-231`) hand the event to the canvas when
its target is not the canvas, the way `startForwardingEscapeKey` hands over Escape (`:168-205`) — a synthetic
`KeyboardEvent` with `key`, `code`, `ctrlKey`, `metaKey` copied, `bubbles: false` — since that suppressor has already
called `preventDefault` and the Escape forwarder's `defaultPrevented` test would skip it.

## Tests

- **No unit test is possible** (UI, untested by policy).
- Compile check: `./gradlew :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs :presentation:compileDebugKotlinAndroid :presentation:compileKotlinIosSimulatorArm64`

## Verification

1. **Desktop** (`./gradlew :app:desktop:run`), a song → ⋮ → Edit, type a character (the Save button enables).
2. Switch to Preview (narrow the window if Split is showing, or pick Preview). Press Cmd / Ctrl + S.
   - **Before:** nothing; the Save button stays enabled. **After:** saved; the button disables.
3. **Web** (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`): the same. Also check the browser's "Save page as" does
   not open. If the save does not happen here only, apply the web fallback above and repeat.
4. In a window wide enough for Split: type a character in Edit, switch to Split (the field is composed again and
   holds no caret), press Ctrl / Cmd + S without clicking. **Before:** nothing. **After:** saved.
5. With the caret in the field: the shortcut still saves (the preview pass of the Column sees it first) and the `s`
   is not typed.
6. Regressions: AltGr + S on a Windows keyboard layout that uses it still types its character; Escape still asks the
   unsaved changes question (the window handler and back handlers are unaffected); on Android with a hardware
   keyboard, Ctrl + S saves; on a touch device, opening the editor does not bring the keyboard up.

## Docs

- `presentation/CLAUDE.md`, `screens/songEditor/` bullet: "an explicit Save action (and Ctrl / Cmd + S)" → "an explicit
  Save action (and Ctrl / Cmd + S, heard by the whole editor, which takes the focus itself as it opens and whenever its
  panes change, since nothing in it is focused before the text is clicked or while only the preview shows)".
- `CampfireWebApp.kt`'s `startSuppressingBrowserSave` KDoc says "the editor handles the key itself (see
  SongEditorScreen)… the event still reaches that input, and through it the editor" — still true with the field
  focused; if the web fallback is applied, extend it with "or, while no field has the caret, is handed to the canvas".

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`
- `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireWebApp.kt` (only if the web
  fallback is needed)
- `presentation/CLAUDE.md`

## Depends on

After 36 (it edits the padding right under the handler being removed from the same modifier chain). Before 44 (also
`LoadedSongEditor`). Nothing outside lane D.
