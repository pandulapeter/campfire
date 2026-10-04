# Make the docs say where the song metadata editors are offered: in every editor pane, and on song details outside performance mode and archived setlists

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt, presentation/CLAUDE.md, CLAUDE.md

**Challenged:** sound

## Problem

Two descriptions of the same menus disagree with the code at 800ebde0b.

1. **The editor's overflow menu.** `EditorMenu`'s KDoc (SongEditorScreen.kt:868–873) says it holds "the metadata editors of [editingActions] while the preview's card that has them as buttons is out of sight", and presentation/CLAUDE.md:233 says the menu holds them "while the preview, and with it the card, is out of sight in the Edit pane". The call site passes them unconditionally (SongEditorScreen.kt:466–468):
   ```kotlin
   EditorMenu(
       modifier = Modifier.overlappingAction(start = ACTION_BUTTON_OVERLAP, end = 0.dp),
       editingActions = songInfoEditingActions(songInfoEditing),
   ```
   The root CLAUDE.md already says the right thing ("The editor's overflow menu offers these actions in every pane").

2. **The song details menus.** Root CLAUDE.md (the Conventions bullet on links, around line 251–255) says the About the song sheet's groups have "an edit button outside performance mode" and the song details overflow menu offers the metadata actions "outside performance mode". The code also withholds them for a song opened from an **archived** setlist: SongDetailsScreen.kt:182
   ```kotlin
   val isReadOnly = isPerformanceModeEnabled || setlists.any { it.fileName == destination.setlistFileName && it.isArchived }
   ```
   feeds `SongActions(isEditAndExportOnly = isReadOnly, …)` (:484), which drops `fileEditItems` (the metadata editors and cover art) and Update file name (SongActions.kt:329–348), and the sheet's own check (Dialogs.kt, `SongInfo` sheet: `val isReadOnly = isPerformanceModeEnabled || setlists.any { … it.isArchived }`, `actions = if (isReadOnly) null else …`) drops its edit buttons the same way.

## Fix

Docs only; no behaviour change.

1. Replace the `EditorMenu` KDoc's first sentence with: "The actions of the editor that are neither writing the file nor undoing a keystroke, behind the same overflow button the song details screen uses: the metadata editors of [editingActions] — in every pane, so they are a tap away also while the preview's card that has them as buttons is out of sight — with cover art next to Edit song details where covers are on, then prettifying and the revert."
2. presentation/CLAUDE.md:233: change "and holds those four as well (`songInfoEditingActions`, the same callbacks) while the preview, and with it the card, is out of sight in the Edit pane;" to "and holds those four as well (`songInfoEditingActions`, the same callbacks) in every pane, so they are there while the preview, and with it the card, is out of sight;".
3. Root CLAUDE.md, the links bullet: change "each group with an edit button outside performance mode" to "each group with an edit button outside performance mode and outside an archived setlist", and "the song details overflow menu offers them outside performance mode" to "the song details overflow menu offers them outside performance mode and for a song not opened from an archived setlist (which leaves it Edit and Export, as an archived setlist's own song menus do)".

Check the matching bullet in presentation/CLAUDE.md about the song details app bar / overflow menu (search "isEditAndExportOnly" or "archived" near `SongDetailsScreen`) and add the archived case there too if it only names performance mode.

## Tests

None: documentation.

## Manual check

None beyond reading: open a song from an archived setlist and confirm its overflow menu shows only Edit and Export (plus the leading items) and its About the song sheet has no edit buttons, matching the new text; in the editor's Edit, Preview and Split panes the overflow menu lists Edit song details, Manage tags, Manage languages and Manage links in each.
