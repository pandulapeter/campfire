# Replace the `isEditorDraft: Boolean` threaded through the song-edit API with a sealed `SongEditTarget`

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** M  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
(57 occurrences: `DialogType.SongEdit` and its seven implementations `SongTags`, `SongMetadata`, `SongLinks`,
`SongLanguages`, `SongPlaying`, `CoverArtSearch`, `RemoveSongCoverArt`; `showSongTagsDialog`, `showSongLanguagesDialog`,
`songForLabelEditing`, `showSongMetadataDialog`, `setSongMetadata`, `showSongLinksDialog`, `setSongLinks`,
`showSongCoverArtDialog`, `setSongCoverArt`, `coverArtQueryOf`, `songTextOf`, `editSong`, `setSongTags`,
`setSongLanguages`, `showSongPlayingDialog`, `setSongPlaying`, `setVisibleDialog`'s `_underlyingSongInfo` rule, the
"dialog closes with its song" collector's `DialogType.songFileName`); `screens/songDetails/SongMetadataActions.kt`
(15: `rememberSongInfoEditing`, `songPlayingAction`, `coverArtAction`, `songInfoEditingActions`),
`screens/songEditor/SongEditorScreen.kt` (3), `screens/songDetails/SongDetailsScreen.kt` (3),
`screens/songs/SongsScreen.kt` (1), `dialogs/Dialogs.kt` (7), `dialogs/CoverArtSearchSheet.kt` (6),
`dialogs/SongPlayingDialog.kt` (2), `dialogs/SongMetadataDialog.kt` (2), `dialogs/SongLinksDialog.kt` (2);
`presentation/CLAUDE.md` (the editor paragraph names `DialogType.SongEdit.isEditorDraft`)
**Depends on:** none (if plan 01 has started, land this before its `SongMetadataEditing` step)

## Problem

Every song-metadata edit can target either the file (through `SetChordPro*UseCase` and `editSongText`) or the text
being typed in the editor (through `editorTextEdits`). Which one is a boolean that is passed alongside the song through
the whole chain — composable helper → view model `show…Dialog` → `DialogType` field → dialog → view model `set…` →
`editSong`/`songTextOf` — about 100 times across 11 files:

```kotlin
fun setSongCoverArt(fileName: String, isEditorDraft: Boolean, url: String?) = editSong(fileName = fileName, isEditorDraft = isEditorDraft) { … }
private fun songTextOf(fileName: String, isEditorDraft: Boolean) = if (isEditorDraft) {
    retainedEditorField(fileName)?.text?.toString() ?: _editorDraft.value?.takeIf { it.fileName == fileName }?.text
} else {
    songTexts.value[fileName]
}
// call sites
viewModel.showSongCoverArtDialog(song = song, isEditorDraft = isEditorDraft)
DialogType.SongPlaying(song = …, setlistFileName = setlistFileName.takeUnless { isEditorDraft }, isEditorDraft = isEditorDraft, …)
```

A boolean parameter says nothing at a call site that passes a variable, it lets impossible combinations exist (a
`SongPlaying` with a setlist *and* `isEditorDraft = true`, which `setlistFileName.takeUnless { isEditorDraft }` guards
by hand), every `DialogType.SongEdit` defaults it to `false` so forgetting it at a new call site silently writes the
file under an open editor, and every branch on it is an `if` that a third target (say, a batch edit of several songs)
would turn into a chain.

## Fix

1. Add next to `DialogType` (`CampfireViewModel.kt` or wherever plan 01 step 0 moved it):
   ```kotlin
   /** Where a metadata edit lands: the file, or the text being typed in the editor open on that file. */
   sealed interface SongEditTarget {
       val fileName: String
       /** The file itself, written through the `SetChordPro*` use cases. */
       data class File(override val fileName: String) : SongEditTarget
       /** The editor's unsaved text, which only Save writes to the file. */
       data class EditorDraft(override val fileName: String) : SongEditTarget
   }
   ```
2. `DialogType.SongEdit` gets `val target: SongEditTarget` instead of `val isEditorDraft: Boolean` (no default — the
   compiler then finds every construction site), keeping `val song: Song` (the dialogs draw it). `SongPlaying` keeps its
   `setlistFileName`, which `showSongPlayingDialog` still sets only for `File`.
3. Every view model function that takes `(fileName, isEditorDraft)` takes `target: SongEditTarget`; every one that
   takes `(song, isEditorDraft)` takes `(song, target)`. The composable helpers (`rememberSongInfoEditing`,
   `songPlayingAction`, `coverArtAction`, `songInfoEditingActions`) take the target too; their two kinds of caller build
   it once — `SongEditTarget.File(song.fileName)` in the song details screen and the About sheet,
   `SongEditTarget.EditorDraft(fileName)` in `SongEditorScreen` — and remember it with the file name as the key. `songTextOf` and
   `editSong` become `when (target)`; `songForLabelEditing`, `showSongMetadataDialog`, `showSongCoverArtDialog` and
   `coverArtQueryOf` likewise. `setVisibleDialog`'s `dialogType.isEditorDraft` check becomes
   `dialogType.target is SongEditTarget.File`. The dialogs' `if (dialog.isEditorDraft) Res.string.done else Res.string.save`
   becomes `if (dialog.target is SongEditTarget.EditorDraft) …`.
   One commit; it is a mechanical, compiler-guided rename.

## Tests

None new are needed for a type change; if plan 01 has produced a `SongMetadataEditingTest`, add a case that an
`EditorDraft` target emits on `editorTextEdits` and never calls the `SetChordPro*` use case, and a `File` target the
reverse. Guard: `:presentation:desktopTest` (`dialogs/SongPlayingDraftTest`, `dialogs/SongLinksDraftTest`).

## Manual check

From the song details About sheet: edit tags, languages, links, details, song defaults and the cover — each writes the
file. From the editor's overflow menu and its preview card: the same six — each changes the field (undo-able), the
file is untouched until Save, and the tag/language sheets' button reads Done rather than Save.
