# Put the ChordPro reference above Revert in the editor's overflow menu, so the destructive entry stays last

**Kind:** UX / docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`, `presentation/CLAUDE.md`

## Problem

`presentation/CLAUDE.md` (`screens/songEditor/`) says: "Revert is, last in the overflow menu and behind a confirmation
(`DialogType.RevertChanges`)". Commit 4ca47bda2 added the ChordPro reference link after it; `EditorMenu` at dac1d9d59
lists:

```kotlin
items = editingActions.take(1) + listOfNotNull(coverArtAction) + editingActions.drop(1) + listOf(
    ActionsMenuItem(title = stringResource(Res.string.song_editor_prettify), ...),
    ActionsMenuItem(title = stringResource(Res.string.song_editor_revert), ..., onClick = onRevert),
    ActionsMenuItem(title = stringResource(Res.string.song_editor_chordpro_reference), ..., onClick = onOpenChordProReference),
),
```

So the doc is wrong, and the menu's one destructive entry now sits between two harmless ones, with the often-tapped
reference link right under it.

## Fix

Recommended: move the code, keep the doc. In `EditorMenu` put the `song_editor_chordpro_reference` item between Prettify
and Revert:

```kotlin
editingActions.take(1) + listOfNotNull(coverArtAction) + editingActions.drop(1) + listOf(prettify, chordProReference, revert)
```

(keeping each `ActionsMenuItem` exactly as it is, `isAlwaysInMenu = true` on all three). Then check
`presentation/CLAUDE.md` line "the overflow menu keeps the cover art, the revert and a link to the ChordPro directives
reference on chordpro.org" — reorder it to "the cover art, a link to the ChordPro directives reference on chordpro.org
(…) and the revert" so it reads in menu order; the "Revert is, last in the overflow menu" sentence then holds again.

Alternative: leave the menu and change the doc to "Revert is the last but one entry, before the reference". Not
recommended: a destructive entry belongs last, away from a link that is tapped often.

## Tests

None: menu order in a composable.

## Manual check

Open the editor, tap the overflow menu: … Prettify, ChordPro reference, Revert, in that order, in English and
Hungarian; the reference still opens chordpro.org's directives page, Revert still asks for confirmation.
