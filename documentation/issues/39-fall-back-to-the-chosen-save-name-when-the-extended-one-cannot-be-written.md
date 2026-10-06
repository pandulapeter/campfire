# Fall back to the exact name the save dialog returned when the name with the extension added cannot be written

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** desktop (macOS, the sandboxed Mac App Store build)
**Files:** presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.desktop.kt
**Challenged:** amended — the fallback is taken only when the extended file was never created, so a write that failed
part way (a full disk, an I/O error) is reported as today instead of leaving a truncated `name.pdf` beside a second copy
named without the extension; the fallback is logged; plan 38 edits the same file (a different function).

## Problem

`FilePicker.desktop.kt:47-61` and `:73-78` (8ee010b36):
```kotlin
if (directory == null || name == null) null else File(directory, name).withExtensionOf(file.name)
...
return withContext(Dispatchers.IO) {
    target.writeBytes(file.bytes)
    true
}
...
val extended = File(parentFile, "$name.$extension")
return if (extended.exists()) this else extended
```
When the user types a name without the extension, the app writes `name.pdf` (or `.cho`, `.zip`) instead of the path the
dialog returned. The Mac App Store build is sandboxed with only `com.apple.security.files.user-selected.read-write`
(`app/desktop/app-store.entitlements`). Under the App Sandbox the save panel's powerbox extends the sandbox to exactly
the URL the user confirmed; a sibling with another extension is only reachable through an `NSIsRelatedItemType` document
type declaration, which the build's `Info.plist` (`app/desktop/build.gradle.kts`, `CFBundleDocumentTypes`) does not
make. So `writeBytes` on `…/setlist.pdf` fails with "Operation not permitted" and the user sees "Export failed" for a
save they confirmed. (`extended.exists()` may also be answered false there, since the sibling is outside the grant.) The
unsandboxed builds are unaffected.

Relied on: Apple's App Sandbox design guide (user-selected files, related items); not reproduced on a sandboxed build.
The path is rare — the save panel selects only the base name when editing, so the extension has to be deleted on
purpose — which is why this is low.

## Fix

Keep adding the extension (it is what makes the file importable again, per `withExtensionOf`'s KDoc), but when writing
the extended file fails, write the file the user chose instead:
```kotlin
override suspend fun saveFile(file: ExportedFile): Boolean {
    val chosen = withContext(Dispatchers.Main) {
        FileDialog(null as Frame?, DIALOG_TITLE, FileDialog.SAVE).run {
            this.file = file.name
            isVisible = true
            val directory = this.directory
            val name = this.file
            if (directory == null || name == null) null else File(directory, name)
        }
    } ?: return false
    return withContext(Dispatchers.IO) {
        val extended = chosen.withExtensionOf(file.name)
        try {
            extended.writeBytes(file.bytes)
        } catch (exception: Exception) {
            // A sandbox grants the one path the dialog returned, and a sibling with the extension added is not that
            // path: where it could not even be created, the file goes where the user said instead, without the
            // extension, rather than nowhere. One that was created and failed part way is a failure like any other.
            if (extended == chosen || extended.exists()) throw exception
            println("Could not write \"${extended.path}\", writing \"${chosen.path}\" instead: ${exception.message}")
            chosen.writeBytes(file.bytes)
        }
        true
    }
}
```
`writeBytes` opens the file before it writes anything, so a refused open (the sandbox, a name too long once the
extension is added) leaves nothing behind and takes the fallback, while a write that failed after the open leaves the
file there and propagates as today's "Export failed". Writing over `chosen` is safe: it is the name the dialog itself
asked about replacing. A second failure propagates as "Export failed". Add one sentence to `withExtensionOf`'s KDoc
about the fallback.

Plan 38 changes `readAsImportedFiles` in the same file; the two do not touch the same lines.

Stays a plan rather than a manual check alone: the change is a no-op wherever the premise is wrong (the extended write
succeeds), and the user's sandbox check of 2026-09-23 confirmed export only with the offered name kept.

## Tests

None: AWT dialog and file system; the sandbox cannot be reproduced in a unit test.

## Manual check

Mac App Store build (TestFlight) or a locally sandboxed ad hoc build: export a song as PDF, delete `.pdf` from the
offered name, Save. The file is written (as `name`, without the extension) and the screen closes with the saved
message. The unsandboxed build still writes `name.pdf`.
