# Return to the cover art sheet when "Remove cover" is cancelled, instead of closing it

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt

**Challenged:** sound

## Problem
The cover art sheet's bin (`CoverArtSearchSheet.kt` ~177) replaces the sheet with a confirmation:
`viewModel.showDialog(CampfireViewModel.DialogType.RemoveSongCoverArt(song = dialog.song, isEditorDraft = dialog.isEditorDraft))`.
Its Cancel (and its scrim/Back, the same `onDismissRequest`) is `Dialogs.kt` ~402-411 at 800ebde0b:

```kotlin
is CampfireViewModel.DialogType.RemoveSongCoverArt -> ConfirmationDialog(
    ...
    onDismiss = viewModel::dismissDialog,
```

and `dismissDialog()` is `setVisibleDialog(_underlyingSongInfo.value)` (`CampfireViewModel.kt` ~3274): it goes back to
About the song when the sheet was opened from there (eaa9d89ac), and otherwise to nothing. Either way the cover art
sheet the user was in is gone — Cancel means "don't remove", yet it also throws away the sheet, so someone who tapped
the bin to compare, or by mistake, has to reopen Set/Change cover art from the menu.

## Fix
Make Cancel put the cover art sheet back:

1. `Dialogs.kt`, the `RemoveSongCoverArt` branch:
   `onDismiss = { viewModel.showDialog(CampfireViewModel.DialogType.CoverArtSearch(song = dialog.song, isEditorDraft = dialog.isEditorDraft)) }`.
   The confirm path stays `viewModel.dismissDialog()` (removing the cover finishes the job, and eaa9d89ac already makes
   that return to About the song when it was the origin).
2. `CampfireViewModel.setVisibleDialog`: the cover art sheet coming back from its own confirmation has to keep the
   About the song sheet underneath it, the way the confirmation itself does. Extend the existing rule:
   ```kotlin
   _underlyingSongInfo.value = when {
       // The cover art sheet's Remove asks first, and either answer goes back to the sheet the cover art was opened from.
       previousDialog is DialogType.CoverArtSearch && dialogType is DialogType.RemoveSongCoverArt -> _underlyingSongInfo.value
       previousDialog is DialogType.RemoveSongCoverArt && dialogType is DialogType.CoverArtSearch -> _underlyingSongInfo.value
       else -> (previousDialog as? DialogType.SongInfo)
   }?.takeIf { ... unchanged ... }
   ```
   The existing `takeIf` already accepts `CoverArtSearch` for the same song.

What the sheet comes back with: `setVisibleDialog` starts a fresh search from the song's own artist/album/title (the
`previousDialog !is DialogType.CoverArtSearch` branch at its end) and the sheet's `rememberSaveable` fields start over,
so edited query fields, a selected record or a typed address are not restored. That is acceptable for a cancelled
removal (the cover is unchanged, which is what the sheet opens on); keeping the draft would need the sheet's state to
outlive its composition (hoisting it into the view model next to `coverArtSearch`), which is not worth it here.

## Tests
None: dialog routing in the view model is not covered by the pure-logic tests (it needs the whole view model).

## Manual check
- Song details → overflow → Change cover art → bin → Cancel: the cover art sheet is back (searching again), the cover
  unchanged. Back/scrim on the confirmation does the same.
- From About the song → cover button → bin → Cancel → close the cover sheet with ✕: About the song is back underneath.
- Bin → Remove: the cover is removed and you land where you did before (About the song if you came from it).
- From the editor's overflow (draft): bin → Cancel returns to the sheet; Remove clears the draft's cover.
