# Open the export screen on Link from Settings' Show QR code without storing Link as the export format

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (builds with sync)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/DialogType.kt`,
`…/ui/screens/settings/SharingSections.kt`, `…/ui/dialogs/SavedDialogRestorer.kt`, `…/ui/CampfireViewModel.kt`,
`…/ui/screens/export/ExportScreen.kt`, `…/ui/screens/export/ExportState.kt`, `…/ui/screens/export/ExportController.kt`
(`choosePrintFormat` removed if unused), `…/ui/screens/export/ExportFormats.kt`, `…/ui/screens/export/CLAUDE.md`,
`…/ui/screens/settings/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/ExportFormatsTest.kt`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SharingTabTest.kt`

(`…/ui/` = `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.) Another change may have
moved `exportDialogOf` (it now matches names with `isSameLibraryName`) and plan 01 (lane R) may touch
`SharingSections`: locate the code below by its snippet, not by line.

## Problem

Settings → Sharing's **Show QR code** opens the item's export screen on Link by writing the stored export format
(`SharingSections.kt`):

```kotlin
onShowQrCode = { share ->
    exportDialogOf(viewModel, share)?.let { dialog ->
        scope.launch {
            viewModel.exportController.choosePrintFormat(PrintSettings.Format.LINK)
            viewModel.dialogHost.showDialog(dialog)
        }
    }
},
```

and `ExportController.choosePrintFormat` persists it:

```kotlin
/** Has the next export screen open on [format], once the preferences it is kept with have been read. */
suspend fun choosePrintFormat(format: PrintSettings.Format) {
    val current = pendingPrintSettings.value ?: userPreferencesState.mapNotNull { it.data }.first().printSettings
    setPrintSettings(current.copy(format = format))
}
```

`PrintSettings.format` is the user's remembered export format (`screens/export/CLAUDE.md`: "`PrintSettings` (format
included) are local preferences"). After looking at one QR code from Settings, every later **Export song** / **Export
setlist** opens on Link, a format the user never picked there, and someone who prints from Campfire finds their PDF
export replaced by a "Connect Dropbox"/"Create link" page. The same write happens in `CampfireViewModel`'s consent
answer:

```kotlin
savedDialogRestorer.reopenExport(target) { exportController.choosePrintFormat(PrintSettings.Format.LINK) }
```

There the user had picked Link before tapping Connect, so the stored format is usually already Link; it is the same
mechanism and should move with it.

## Fix

Carry the format as part of the one export dialog instead of a preference write:

1. `DialogType.Export` gains `val format: PrintSettings.Format? = null` ("the format this screen opens on, for this
   opening only; null for the stored one"). Data class equality includes it, which is right: `isOpen = visibleDialog ==
   dialog` compares the same object.
2. `SharingSections`' `exportDialogOf` builds `DialogType.Export(song = it, format = PrintSettings.Format.LINK)` /
   `(setlist = it, format = …)`, and `onShowQrCode` just calls `showDialog(dialog)` (no coroutine needed).
   `SavedDialogRestorer.showExport` builds its dialogs the same way and `reopenExport` loses its `beforeShowing`
   parameter (the caller in `CampfireViewModel` passes only the target). Remove `choosePrintFormat` if nothing else
   calls it.
3. `ExportScreen`'s initial settings: `(pending ?: initialSettings).let { stored -> dialog.format?.let { stored.copy(format = it) } ?: stored }.withFormatIn(formats)`;
   put that in a pure function beside `withFormatIn` in `ExportFormats.kt` so it is tested.
4. What gets stored afterwards: on Link, `PrintOptions` shows only the format choice and `LinkOptions` (no
   `PrintSettings` row), so the only `update` an overridden screen can make is the user picking a format, which is
   theirs to keep: store it as today. Nothing else is written, so leaving without touching anything keeps the stored
   format. Check this still holds after plan 01.
5. Restoration: the dialog lives in `DialogHost` in the view model, so an Activity recreated by rotation gets the same
   `DialogType.Export` back with its `format`. But `ExportState.settings` is a plain `remember(dialog)`, re-initialised
   on recreation: a user who switched from the overridden Link to PDF and then rotated would be put back on Link. Keep
   the user's pick in this screen in a `rememberSaveable(dialog)` (`Format?`, null until they pick one) and take it
   before `dialog.format`. An export screen is not restored after process death (`SavedDialog` has no export entry),
   so nothing more is needed there.
6. Docs: `screens/export/CLAUDE.md` › Entry and sources (Settings' Show QR code and the consent answer open it on Link
   for that opening only, the stored format untouched); `screens/settings/CLAUDE.md` if it describes Show QR code.

## Tests

- `ExportFormatsTest`: the initial settings function takes the dialog's format over the stored one, falls back to PDF
  where the format is not offered (`withFormatIn`), and keeps the stored format when the dialog names none.
- UI test in `SharingTabTest` (see `tools/screenshots/CLAUDE.md`), after "a shared song's show qr code opens its export
  screen on the link": close the export, then open the same song's export from its menu (**Export song**) and assert it
  opens on the format stored before (the PDF's Save button / the PDF format chip selected, found by resource strings),
  not on Link.
