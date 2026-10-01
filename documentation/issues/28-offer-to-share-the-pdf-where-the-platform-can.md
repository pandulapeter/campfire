# Offer to share the PDF where the platform can share

**Kind:** usability  ·  **Severity:** medium  ·  **Platforms:** Android and iOS (wherever `FilePicker.canShare`)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`, both `strings.xml`
**Challenged:** amended — iOS shares too; no generic "Share" string exists, so one is added; one Cancel replaces both buttons while rendering.

Runs after plan 27.

## Problem

The song and setlist menus offer **Share** next to Export when `filePicker.canShare` (Android and iOS; the desktop and
the web leave it false). The PDF can only be saved into a folder: `exportPdf` calls `save(filePicker)` without
`isShare`, so sending the setlist to the band's chat or to a print service means saving it, leaving the app and
finding the file.

## Fix

`exportPdf(…, isShare: Boolean)`; when `filePicker.canShare`, the action row shows "Share PDF" with `ic_share` beside
"Save PDF". The only existing share strings are `songs_share_song` ("Share song") and `setlists_share` ("Share
setlist"), which are wrong on a PDF, so add `print_share` ("Share PDF" / "PDF megosztása") in both languages. While
rendering, one **Cancel** replaces both buttons (plan 27); both drive the same progress and cancellation. A share does
not close the sheet (`save` only calls `onSaved` for a save), so a second share or a save can follow.

Android's `shareFile` copies to the cache directory and starts `ACTION_SEND` with `type = file.mimeType`, so
`ExportedFile("….pdf", "application/pdf", …)` reaches the chooser as `application/pdf`; iOS's `shareFile` hands the
file to `UIActivityViewController` and answers false, silently, when another controller is presented (not the case
for a Compose sheet). Android's `saveFile` creates the document with the generic binary type for anything that is
not the zip, which leaves the `.pdf` name alone; nothing to change there.

## Tests

None.

## Manual check

Android and iOS: Share a song's PDF to a messaging app and to the system print service; the sheet stays open.
Desktop and web: no Share button.
