# Let an editor with nothing typed follow its file when the file changes underneath it

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (desktop and iOS most often, sync everywhere)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt (comments only),
presentation/CLAUDE.md

## Problem
The editor's field is built once from the text the file had when the editor opened (`SongEditorScreen`,
SongEditorScreen.kt:184-193 and the `rememberSaveable` at 264-286) and never follows the file afterwards. The view
model does follow it: every rescan invalidates every held text (`SongRepositoryImpl.rescan` →
`songContentRepository.invalidate()`), a sync run invalidates the files it wrote (`refresh(fileNames)`), and
`CampfireViewModel.rereadSongTexts` (CampfireViewModel.kt:1080-1100) puts the new text into `songTexts`, which the
editor's file is always kept in (`pruneSongTexts`, 1061-1073). `hasUnsavedEditorChanges` is then
`draft.text != songTexts[draft.fileName]` (558-560), so an editor in which **nothing was typed** now reports unsaved
changes, its text being the *old* version of the file:

- Save is enabled (SongEditorScreen.kt:398) and writes the old text over the new file (`writeEditorText` passes no
  `expectedText`, 1688-1701).
- Back / Escape / Close asks the `UnsavedChanges` question (`navigateBack`, 1301-1309) about changes the user never
  made; "Save" there does the same revert.
- Since cc4d6f55 a save schedules a sync run ten seconds later, so the revert is uploaded and reaches every other
  device on its own: the other device's (or the other program's) edit is gone everywhere.

Concrete scenarios:
1. Desktop, the workflow `refreshIfStale` exists for (CampfireViewModel.kt:1358-1366): the song is open in
   Campfire's editor, untouched; the user edits the same `.cho` in a text editor next to it, saves, and clicks back
   into Campfire. The focus rescan brings the new text into `songTexts`; Campfire's editor still shows the old text,
   now with Save enabled and an unsaved-changes question on Escape.
2. iOS: the file is changed through the Files app while the editor is open in the background; `ON_START` rescans.
3. Any platform with sync: the app is launched, the launch sync run is still downloading, and the user opens a song
   in the editor; the run then writes the newer version of that song.

The documented rule ("an editor whose file changed underneath it has unsaved changes", presentation/CLAUDE.md, the
`songTexts` paragraph; the comment at CampfireViewModel.kt:963-968) exists to protect a *draft*; for an editor holding
nothing of its own it turns the stale copy into a draft and invites the user to write it back.

## Fix
In `LoadedSongEditor` (SongEditorScreen.kt), next to `RevertOnRequest`, add a small effect that follows the file for as
long as the field holds exactly what the file held before it changed:

```kotlin
@Composable
private fun FollowFileWhileUntouched(viewModel: CampfireViewModel, fileName: String, textFieldState: TextFieldState) =
    LaunchedEffect(textFieldState, fileName) {
        var previous = viewModel.songTexts.value[fileName]
        viewModel.songTexts.map { it[fileName] }.distinctUntilChanged().collect { current ->
            val base = previous
            previous = current
            // Only a field that held the file as it was: anything typed (or a draft reopened on launch) is kept, and
            // a file that has gone is left to the existing "file is gone" path.
            if (current != null && base != null && current != base && textFieldState.text.contentEquals(base)) {
                textFieldState.replaceAll(current)
            }
        }
    }
```

- `replaceAll` (SongEditorScreen.kt:853-861) goes through the field's own editing, so the update is one step of the
  undo history (undo takes the user back to the old text if they want it) and the caret is kept, clamped.
- The field's new text reaches `onEditorTextChanged` through `ReportDraft`, and `hasUnsavedEditorChanges` goes back to
  false by itself.
- A save updates `songTexts` to the saved text (`writeSongContent`, 1710-1713), which the field then equals, so an
  editor that has been saved follows later changes again; a field with typed text never equals the base and is never
  replaced (today's behaviour, deliberately kept).
- A recovered draft (`recoverEditorDraft`) differs from the file from the start and is never followed.
- Optionally announce it (a snackbar such as "Updated from the file"), which needs a string in both `strings.xml` files.

Update the documentation of the rule to say that only an editor with text of its own gets unsaved changes from a
changed file: presentation/CLAUDE.md (the `songTexts` sentence in the `CampfireViewModel.kt` bullet and the editor
bullet's "The screen reports the text to the view model…" paragraph) and the comment in `CampfireViewModel.init`
(963-968).

No pure logic worth extracting; if the implementer prefers, the decision
`shouldFollow(base, current, fieldText)` can be a top-level internal function with a `:presentation` `commonTest`.

## Verification
- `./gradlew :presentation:desktopTest` (if the helper is extracted).
- Desktop manual: open a song in the editor, type nothing, edit the same file in another text editor and save, wait
  ten seconds, click back into Campfire: the editor shows the new text, Save is disabled, Escape leaves without a
  question. Repeat after typing one character in Campfire first: the editor keeps its own text, Save is enabled and
  Escape asks, as today.
- Sync (two devices): edit a song on device A; on device B open that song's editor before the launch sync run has
  finished; after the run the editor shows A's version and Save is disabled.

## Conflicts
`SongEditorScreen.kt` is also touched by plan 18 (keyboard padding); both are additive and in different functions.
presentation/CLAUDE.md is shared with most lanes.

## Open decision
1. Follow silently (recommended), 2. follow and show a snackbar, 3. keep today's behaviour but make Save pass the
text the field opened from as `expectedText` and ask before overwriting a changed file. Option 1 matches what the
details screen already does for a file that changes while it is shown.
