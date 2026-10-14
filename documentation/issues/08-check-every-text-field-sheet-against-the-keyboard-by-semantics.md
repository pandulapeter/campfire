# Check every text-field sheet's header action and focused field stay above the keyboard on the smallest phone

**Challenged:** amended — four sheets have keyboard goldens, not five; the list of text-field sheets corrected and
completed with their openers; the check also requires the nodes' tops inside the window.

**Kind:** test  ·  **Severity:** low  ·  **Platforms:** desktop (UI tests)
**Files:** new `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/sheet/SheetKeyboardBudgetTest.kt`

## Problem

Root `CLAUDE.md` "Pinned height budget": a 360 × 640 dp phone with the keyboard up leaves ~330 dp; finishing actions sit
in the sheet header. Four sheets have keyboard goldens (new song, song metadata,
setlist details, cover search), compared on Linux only; the other text-field sheets have nothing, and a
golden only says "changed", not "broken".

## Fix

One test looping over a list of (name, how to open it) cases with each failure collected (as `GoldenRendersTest`
does). Cover every sheet with a text field, the golden ones included (the check is cheap and runs on every system):
`NewSongDialog` (Songs' New → `songs_create_song`), `SongMetadataDialog`, `CoverArtSearchSheet` (needs
`isCoverArtEnabled = true`), `SetlistDetailsDialog` both as edit (setlist's actions → `setlists_edit_details`, with the
dated resource) and as new (`setlists_new_setlist`), `SongLinksDialog`, `TagsSheet`, `LanguagesSheet`, and
`SongPlayingDialog` (Song defaults) from the About the song sheet's tiles, `EditorSearchSheet` (editor menu or its
promoted button: `song_editor_find_and_replace`), and `DeleteLibraryDialog` (Settings → Library →
`settings_library_delete`; the test types nothing and never presses its Delete). Leave out `SongDetailsLookupSheet`: it reaches the network,
which no test fake covers. Reuse `GoldenRendersTest`'s openers by moving them where both files see them (an `internal`
file in the `screenshots` test package, visible module-wide) rather than copying them.

For each: `runAppTest(window = TestWindow.PHONE)`, open the sheet, focus its field (`performClick` on the
`hasSetTextAction()` node), set `keyboardHeight = 310.dp` (`GoldenRendersTest`'s `KEYBOARD_HEIGHT`; share the
constant), `waitForIdle`, then assert with `fetchSemanticsNode().boundsInRoot` that the header's finishing action
(Save / Create / Done / Delete, or the header's close button where a sheet has no finishing action) and the focused
text field both lie fully inside the room left: `top >= 0` and `bottom <= 640 − 310` dp. 640 dp is not a short window
(`CampfireBottomSheet`: `windowHeight < SHORT_WINDOW_HEIGHT && isKeyboardVisible`), so the header is pinned and must be
visible. Strings from resources. Retire no golden in this plan.

## Tests

This is the test. If a sheet fails today, mark that case in the list as known-failing with the reason (the loop skips
it and says so) and list it in the lane report as a
finding for a later fix.
