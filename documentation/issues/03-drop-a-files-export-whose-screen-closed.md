<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Drop a Files export whose export screen closed before its file was ready

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (likeliest on the web and Android)
**Challenged:** sound
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/ExportController.kt`, its test if one exists for `save`
(`presentation/src/commonTest/.../screens/export/`), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/CLAUDE.md` only if a sentence there becomes false

## Problem

Reviewed at `6c3043996`. `ExportController.exportFiles` checks once that its screen is the dialog on screen, then
builds the file and hands it to the picker:

```kotlin
fun exportFiles(filePicker: FilePicker, dialog: DialogType.Export, songFileNames: Set<String>?, isShare: Boolean) {
    if (dialogHost.visibleDialog.value != dialog) return
    val setlist = dialog.setlist
    launchFileTransfer {
        save(
            ...
            onSaved = { dialogHost.dismissSheet(dialog) },
        ) {
            when {
                setlist != null -> exportSetlist.invoke(setlist.fileName, songFileNames)
                ...
```

`save` calls `export()` and then `filePicker.saveFile(file)` / `shareFile(file)` with no second check. Building a
setlist's zip reads every song from disk (`useCache = false`) and can take hundreds of milliseconds on OPFS or a
low-end phone, with no progress shown for Files (only the PDF sets `pdfExportProgress`). A Back pressed meanwhile slides
the screen away, and then the save dialog or share sheet comes up over the setlist screen, followed by the
"Setlist exported" snackbar. The PDF path is protected (`cancelPdfExport`, called from the view model's dialog-change
listener with the comment "An export nobody is looking at any more would put its picker up over whatever is on screen by
then."); the Files path is not, against the export `CLAUDE.md` rule that Save and Share do nothing once the screen is
no longer the dialog on screen.

## Fix

Recommended: give `save` a `isWanted: () -> Boolean = { true }` parameter checked right after `export()` returns and
before the picker (a file built for a screen that has gone is dropped silently: no picker, no message), and pass
`isWanted = { dialogHost.visibleDialog.value == dialog }` from `exportFiles`. `exportLibrary` (a Settings row, not a
dialog) keeps the default.

Not recommended: tracking the Files job and cancelling it from the listener like `pdfExportJob` — more state for the
same outcome, since a Files export has no progress to cancel from the screen.

Keep the rule of `launchFileTransfer`'s KDoc: nothing new may suspend between the tap and the picker.

## Tests

If `ExportController` has a commonTest with fakes for `FilePicker` and `DialogHost`, add: a Files export whose dialog
is replaced while `exportSetlist` is suspended (a gated fake) never calls `saveFile` and sends no message. If there is
no such harness, a UI test is not worth building for this; say so in the commit's lane report.
