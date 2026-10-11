# Drop the removed song details editing menu and edit button from the KDocs that still name them

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongMetadataDialog.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongPlayingDialog.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongPlayingControls.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongPlayingControlsRow.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SongActionHandler.kt`

## Problem

Commit d0fa48277 removed the song details screen's pencil menu (`components/SongEditingActions.kt` deleted) and its edit
button. The Song defaults sheet (`SongPlayingDialog`) is now opened only by the Song defaults group of the About the song
sheet (`dialogs/SongInfoSheet.kt`, `onEdit = editing.onEditDefaults`) and of the editor preview's card
(`screens/songDetails/SongInfoCard.kt`, `onEdit = editing?.onEditDefaults`); over the editor the About the song sheet is
the first entry of the editor's menu (`screens/songEditor/EditorMenu.kt`), which has no Song defaults entry of its own.
Five KDocs at d650f3cb0 still describe the old ways in:

- `dialogs/SongMetadataDialog.kt` ~L38–39: "How it is played — its key, capo, tempo and time — is not here but in the
  "Song defaults" sheet (`SongPlayingDialog`), opened from the song details editing menu and from the About the song
  sheet's Song defaults group."
- `dialogs/SongPlayingDialog.kt` ~L80–84: "— opened from the song details editing menu and from the About the song
  sheet's Song defaults group, edited as a draft and written on Save, the fields changed and only those. Opened from the
  editor's overflow menu or its preview's Song defaults tile it writes into the text being typed instead, on Done, like
  the editor's other metadata sheets."
- `screens/songDetails/SongPlayingControls.kt` ~L22–23: "while what the file itself declares, the time signature among
  it, is edited in the editing menu's "Song defaults" sheet (`SongPlayingDialog`)."
- `screens/songDetails/SongPlayingControlsRow.kt` ~L44–46: "What the file itself declares, the time signature among it,
  is edited in the "Song defaults" sheet of the editing menu, which is also where that difference is explained, so the
  page carries nothing but the song and its controls."
- `components/SongActionHandler.kt` ~L15: "What the song menus ([SongActions], [SetlistAssignmentsButton]) and the song
  details screen's edit button do with their song." Its only callers are `SongActions` (`actions.edit(song)` and the
  rest) and `SetlistAssignmentsButton` (`actions.chooseSetlists`); there is no edit button.

(The untitled Song defaults tiles are still drawn by `SongDefaultsGroup`, and `screens/songDetails/CLAUDE.md` calls them
"a Song defaults group", so "the Song defaults group" stays the right name for them in these KDocs.)

## Fix

Run the `code-style` skill first (it governs every `.kt` edit, KDoc included). Then, matching each by its text:

1. `SongMetadataDialog.kt`: "opened from the song details editing menu and from the About the song sheet's Song defaults
   group." → "opened from the Song defaults group of the About the song sheet and of the editor preview's card."
2. `SongPlayingDialog.kt`: "— opened from the song details editing menu and from the About the song sheet's Song
   defaults group, edited as a draft" → "— opened from the About the song sheet's Song defaults group, edited as a
   draft"; and "Opened from the editor's overflow menu or its preview's Song defaults tile it writes into the text being
   typed instead" → "Opened over the editor — from the About the song sheet its menu opens, or from its preview card's
   Song defaults group — it writes into the text being typed instead".
3. `SongPlayingControls.kt`: "is edited in the editing menu's "Song defaults" sheet (`SongPlayingDialog`)." → "is edited
   in the "Song defaults" sheet (`SongPlayingDialog`), opened from the About the song sheet."
4. `SongPlayingControlsRow.kt`: "is edited in the "Song defaults" sheet of the editing menu, which is also where" → "is
   edited in the "Song defaults" sheet the About the song sheet opens, which is also where".
5. `SongActionHandler.kt`: "What the song menus ([SongActions], [SetlistAssignmentsButton]) and the song details screen's
   edit button do with their song." → "What the song menus ([SongActions]) and the Choose setlists button
   ([SetlistAssignmentsButton]) do with their song."

Keep the existing line wrapping style of each KDoc (150 columns). No `CLAUDE.md` changes: they already describe the
current ways in.

## Tests

None: comments only. The build must still pass `spotlessCheck` (no code changes).

## Manual check

None.
