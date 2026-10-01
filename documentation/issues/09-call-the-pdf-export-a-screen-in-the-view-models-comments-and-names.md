# Call the PDF export a screen, not a sheet, in the view model's comments

**Challenged:** amended — `dismissSheet` is kept as the export's close call (its body is the "only while still on
screen" guard the export needs, and `setVisibleDialog` is private while `dismissDialog` has no guard), so the fix is to
name the export screen in its KDoc; added the two stale comments the plan missed (`printExportSaved`'s KDoc and the rest
of `pendingPrintSettings`' KDoc) and `PrintLayout.kt`'s.

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt` (the `PrintSource` KDoc, see below)

## Problem

ed54c7da9 turned the export sheet into a full screen, but comments still describe a sheet:
- `CampfireViewModel.kt`, the KDoc of `pendingPrintSettings`: "The export sheet's options as it last set them…", "at
  once when the sheet goes", "A sheet composed again within that moment, as a rotation does…";
- the KDoc of `pdfExportProgress`: "How far the export sheet's PDF has been drawn…";
- the KDoc of `printExportSaved`: "Emitted with the sheet an export was started from once its file is saved, for that
  sheet, and no other, to close.";
- in `exportPdf`: "// A share leaves the sheet open, since a second share or a save may follow…";
- in `setVisibleDialog`: "// However the export sheet goes - … - it is removed from the composition at once, so nothing
  in it could do this…" — no longer true: it stays composed while it slides away (plan 07 makes that harmless);
- `PrintLayout.kt`'s KDoc of `PrintSource`: "read once when the export sheet opens", "underneath the sheet".

`PrintExportHost` and `PrintExportScreen` close the screen with `viewModel.dismissSheet`, whose KDoc speaks only of
bottom sheets.

## Fix

Reword those comments to say "export screen". The `setVisibleDialog` one should say why the flush and the cancel are
done here: the screen stays drawn while it slides away, so its own disposal is too late, its export is cancelled here,
and (after plan 07) its Save, Share and options do nothing once it is no longer the visible dialog.

Keep `dismissSheet` as the call. Its body — `if (_visibleDialog.value == dialogType) dismissDialog()` — is exactly what
the export needs: `printExportSaved` can close a screen whose file was saved after the screen was closed and something
else put up (a picker that was already up is left to finish), and the back gesture's `onBackCompleted` closes the
export it was started on; neither may dismiss another dialog. `dismissDialog()` has no such guard and
`setVisibleDialog` is private. Extend `dismissSheet`'s KDoc instead: it is also how the export screen closes itself,
for the same reason — what closes it may arrive once another dialog has replaced it. Do not rename `dismissSheet`; it
has seven other callers.

`PrintLayout.kt` belongs to lane A (plans 01–04 edit it); the change here is two words in the `PrintSource` KDoc, which none of them touch,
and merges word by word if they do.

## Tests

None (comments).

## Manual check

None.
