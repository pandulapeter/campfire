# Add a UI test that "Update file name" renames the file and the setlists that name it, and nothing else

**Challenged:** amended — the setlist resource, the sheet's opener and field, where Update file name is offered and when,
and a literal expected name (computing it with `LibraryFiles` would test the naming with itself).

**Kind:** test  ·  **Severity:** low  ·  **Platforms:** desktop (UI tests)
**Files:** new test in `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/`
(the screen the rename is driven from); no new resource

## Problem

Renames (`ui/CLAUDE.md` Renames; `SongFilesController`) move the file, then rewrite references. Unit tests cover the
naming (`LibraryFiles`, `FileNames`) and the reference walk over fakes (`SongReferencesTest`, `SongFilesControllerTest`),
but nothing checks the running app ends with the new file, no old file, and every
setlist naming the new one.

## Fix

`runAppTest(setlists = listOf("campfire_tests-an_evening_by_the_fire.setlist.json"))` (names `HOUSE_OF_THE_RISING_SUN`).
Open the song, then its Edit song details form as `GoldenRendersTest.openSongMetadata` does (the About the song card,
then its duration tile); replace the field labelled `Res.string.song_editor_insert_title` with `"Rising Sun Blues"`
(`performTextReplacement`) and press the header's `Res.string.save` (a text button). `waitForLibrary` until the house
file's text is the original with `{title: House of the Rising Sun}` replaced by `{title: Rising Sun Blues}` — Save
alone must not rename (root `CLAUDE.md`: renames only when asked); assert the old name is still there. Then open the
details screen's actions menu (content description `Res.string.songs_actions`) and press `Res.string.songs_update_file_name`
(offered only once the rescan sets `Song.canUpdateFileName`, so wait for the item rather than tap at once).
`waitForLibrary` until the old file is gone. Assert: the library's song files are the old set with the house song's
name swapped for the literal `traditional_american-rising_sun_blues.cho`, its text is the saved text above, the
setlist's `songs[0].file` (decoded with `Json.parseToJsonElement`) is the new name, and no other song file changed.

## Tests

This is the test.
