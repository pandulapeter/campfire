# Hand an unreadable dropped or opened desktop file to the import empty, so it is reported as skipped

**Kind:** ux  ·  **Severity:** low  ·  **Platforms:** desktop
**Files:** presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.desktop.kt,
presentation/CLAUDE.md

## Problem

`FilePicker.desktop.kt:87-104` (8ee010b36), used by the file dialog, by drops (`CampfireDesktopApp.kt:89`) and by
"open with" arguments (`app/desktop/.../OpenedFiles.kt:36`):
```kotlin
}.mapNotNull { file ->
    try {
        if (file.isFile) budget.read(name = file.name, size = file.length()) { file.readBytes() } else null
    } catch (exception: Exception) {
        println("Could not read \"${file.path}\": ${exception.message}")
        null
    }
}
```
A file whose read throws (Linux: wrong owner or mode; Windows: locked by another process, a cloud placeholder that fails
to hydrate) is left out of the list. If it was the only one, `importFiles(emptyList())` queues nothing
(`enqueueImport` completes an empty batch at once): no snackbar, no report, nothing. Android's `toImportedFiles`
(`FilePicker.android.kt:265-284`) hands such a file over as `ImportedFile.unread(name)`, "which the import reports as
skipped: the user asked for it, and leaving it out would answer them with nothing at all - while one bad file still
does not lose the ones next to it"; presentation/CLAUDE.md describes the Android behaviour the same way. The web does the
same. The desktop KDoc's reason for leaving it out ("so that one bad file does not lose the ones next to it") is met by
the Android approach too.

## Fix

In the `catch`, return `ImportedFile.unread(file.name)` (from `:data:model`, `ImportedFile.kt`) instead of `null`. Keep
`else null` for a path that is not a file (a vanished path or a special file), which nobody can be told much about and
which the current behaviour already treats as "not chosen". Rewrite the KDoc's last sentence: "A file that cannot be read
is handed over empty, which the import reports as skipped, as on Android; one bad file still does not lose the ones next
to it." In presentation/CLAUDE.md, where `toImportedFiles` is described as handing an unreadable document over empty,
say the desktop's `readAsImportedFiles` does the same.

## Tests

None in commonTest (desktopMain code reading the file system). If `presentation` has a `desktopTest` source set in the
future, a test with a file made unreadable via `setReadable(false)` would cover it; not required.

## Manual check

Linux or macOS desktop build: `chmod 000 song.cho`, drop it onto the window alone: the import says one file was skipped
(snackbar or report) instead of nothing happening. Drop it together with a readable song: the readable one is imported
and the other is reported as skipped.
