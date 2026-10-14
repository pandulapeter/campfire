# Add a UI test that the editor's Save, Ctrl+S, and Discard reach (or leave) the song file

**Challenged:** amended — real package and openers named; Back replaced by the editor's Close (the offscreen host has
no Escape-to-back); the typed text asserted position-independently; the discard barrier's way back to Songs named.

**Kind:** test  ·  **Severity:** medium  ·  **Platforms:** desktop (UI tests)
**Files:** new `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorSaveTest.kt`,
`tools/screenshots/CLAUDE.md` only if a harness rule changes

## Problem

The main data-writing path, editing a song and saving it, has unit tests for its parts (`EditorSession`,
`EditorDraftLocalSource`, `EditorFieldSaver`) and two goldens, but nothing drives it end to end: a broken wiring of the
header's Save, of Ctrl/Cmd+S (root `CLAUDE.md`: "Ctrl/Cmd + S presses Save, Create, or Done"), or of the unsaved
changes question would pass every test.

## Fix

Add `EditorSaveTest` using `runAppTest` (read `tools/screenshots/CLAUDE.md` first; strings through `string(Res.string.x)`,
waits through `waitForLibrary` / `waitUntil`, never sleeps). Opening the editor is `GoldenRendersTest.openEditor`'s
path: tap the song card (`"House of the Rising Sun"` with a click action), the details screen's actions button
(content description `songs_actions`), then `songs_edit_song`. The field is the one node with `hasSetTextAction()`
(a `BasicTextField`; on the 360 dp phone only the Edit pane shows). Where `performTextInput` puts the caret is not this
test's business: type a unique text containing a newline (`"\nA line typed by a test"`) and assert the saved file
contains it and, with that text removed once, equals the original (`demoSongText(HOUSE_OF_THE_RISING_SUN)`). Three
cases on `HOUSE_OF_THE_RISING_SUN`:
1. `the editor's Save writes the typed line into the song file`: type, press the header's Save (content description
   `Res.string.save`; the only one on screen while no dialog is up), `waitForLibrary` until the file contains the text;
   assert as above and that no other song file changed.
2. `Ctrl+S saves like the Save button`: same, with `onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }`
   (`PedalCoverageTest` sends keys through the root the same way; the editor's `saveShortcut` is an `onPreviewKeyEvent`
   on the column around the focused field, so it hears it).
3. `discarding the unsaved changes leaves the file as it was`: type, press the editor's Close (content description
   `Res.string.close`; the editor has no Back, and the offscreen host has no Escape-to-back — that is the desktop
   window's handler, which the test does not compose), answer `Res.string.song_editor_discard` in the unsaved changes
   question, then — as a write barrier — leave the details screen (content description `Res.string.back`), create a
   song from the New menu as `NewSongDialogTest` does, and wait for it; assert the house song is byte-identical.

## Tests

This is the test. Check each case fails with its wiring removed (scratch edit).
