# Take the unsaved changes dialog's focus from inside the dialog, so that Ctrl / Cmd + S answers it on Android too

**Kind:** bug · **Severity:** low · **Platforms:** Android (with a hardware keyboard)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`

## Problem

`UnsavedChangesDialog` in `Dialogs.kt` asks for the focus from the composition that shows the dialog:

```kotlin
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        modifier = Modifier
            .saveShortcut { if (!isSaving) onSave() }
            .focusRequester(focusRequester)
            .focusTarget(),
```

The `focusRequester` node lives in the dialog's own composition. On Android `Dialog` composes its content only once its
window is attached, on a later frame than the one the effect's coroutine runs on, so `requestFocus()` finds no node:
Compose 1.12 logs "FocusRequester is not initialized" and returns false, and nothing asks again. Ctrl+S is then ignored
until a button has been clicked or tabbed to — contrary to the root `CLAUDE.md` ("answers the editor's unsaved changes
question with Save"). Desktop, iOS and the web compose the dialog layer synchronously, so they work. (This is
likely the pre-existing `FocusRequester is not initialized` warning the eighteenth review set aside.)

## Fix

Make the request from inside the dialog's composition, a frame in, the way `CampfireBottomSheet` does for its sheet
focus (`LaunchedEffect(Unit) { withFrameNanos { }; if (!sheetFocusState.hasFocus) sheetFocus.requestFocus() }`):

- Keep `val focusRequester = remember { FocusRequester() }` and the modifier chain on `AlertDialog` as they are.
- Remove the `LaunchedEffect(Unit) { focusRequester.requestFocus() }` line above `AlertDialog`.
- In the `title` slot, which is composed with the dialog's content, request it:

```kotlin
        title = {
            // Here rather than next to the requester: on Android the dialog's content is composed a frame after the
            // composition that shows it, and a request made from there finds no target yet.
            LaunchedEffect(Unit) {
                withFrameNanos { }
                focusRequester.requestFocus()
            }
            Text(stringResource(Res.string.song_editor_unsaved_changes))
        },
```

Update the comment above `val focusRequester` so it no longer implies the request is made there. Import
`androidx.compose.runtime.withFrameNanos` if it is not already imported in `Dialogs.kt`.

## Tests

None: focus timing in a platform dialog window is UI behaviour (only pure logic is tested). Compile with
`./gradlew :app:desktop:compileKotlin :app:android:compileDebugKotlin`.

## Manual check

Android emulator or tablet with a hardware keyboard: edit a song, press Esc, the unsaved changes dialog appears; press
Ctrl+S without clicking anything → the song is saved and the editor closes; logcat holds no "FocusRequester is not
initialized". Repeat on the desktop (Cmd+S) to see nothing regressed.
