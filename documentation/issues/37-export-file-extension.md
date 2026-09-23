# 37 — An exported file can lose its extension (desktop) or gain `.txt` (Android)

**Severity:** exported file not importable as it is (desktop), or renamed (Android) · **Area:** `:presentation`
(`desktopMain/.../platform/FilePicker.desktop.kt`, `androidMain/.../platform/FilePicker.android.kt`)

**Read, not run.** This was found by reading the code at HEAD (2065e47f) and, for Android, the storage provider's
file naming rule; it has not been reproduced. The Android half in particular depends on the document provider the
user saves into, and confirming it on a device is the first step of the work.

## What the user sees

- **Desktop:** Export a song, a setlist or the whole library; in the save dialog, retype the name as `backup` (no
  extension). The file is written as `backup`. Importing it later — by the picker, a drop, or "open with" — reports it
  as skipped: the import decides what a file is by its extension.
- **Android:** Export a single song. The system "Save to" screen proposes `adele-hello.cho`; the file that lands in
  Downloads is likely `adele-hello.cho.txt`. It still imports (`.txt` is a song extension), but the name is not the
  one the app chose, it no longer opens with Campfire from a file manager (the manifest's "open with" claims the
  ChordPro extensions only), and exporting again makes `adele-hello.cho (1).txt`.

## Cause

**Desktop,** `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.desktop.kt:47-61`:

```kotlin
    override suspend fun saveFile(file: ExportedFile): Boolean {
        val target = withContext(Dispatchers.Main) {
            FileDialog(null as Frame?, DIALOG_TITLE, FileDialog.SAVE).run {
                this.file = file.name
                isVisible = true
                val directory = this.directory
                val name = this.file
                if (directory == null || name == null) null else File(directory, name)
            }
        } ?: return false
```

`java.awt.FileDialog` has no file type here (on purpose: `.cho` has no registered type anywhere, see the object's
KDoc), so neither the macOS panel nor the Windows dialog adds an extension to what the user typed, and the name is
taken as it comes back. `PrepareImportUseCaseImpl` then skips a name that ends in no song, setlist or archive
extension.

**Android,** `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.android.kt:61-64`:

```kotlin
    val createText = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportedFile.TEXT_MIME_TYPE),
        picker::onSaveLocationPicked,
    )
```

`TEXT_MIME_TYPE` is `text/plain`. The platform's `FileSystemProvider` (behind Downloads and local storage) names a
new document with `FileUtils.buildUniqueFile(parent, mimeType, displayName)`, whose `splitFileName` keeps the display
name's extension only if that extension maps to the requested MIME type (or equals the type's own extension); `cho`
maps to nothing, so the name is kept whole and the type's extension is appended: `adele-hello.cho` + `.txt`. For
`application/octet-stream` the rule keeps the display name as it is. (From the AOSP `FileUtils.splitFileName` rule;
cloud providers such as Drive apply their own and are the part to check on a device.) Zip exports are unaffected:
`application/zip` maps to `zip`.

## The change

Invoke the **`code-style`** skill before the first edit.

### Desktop: give the name its extension back

```kotlin
    override suspend fun saveFile(file: ExportedFile): Boolean {
        val target = withContext(Dispatchers.Main) {
            FileDialog(null as Frame?, DIALOG_TITLE, FileDialog.SAVE).run {
                this.file = file.name
                isVisible = true
                val directory = this.directory
                val name = this.file
                if (directory == null || name == null) null else File(directory, name).withExtensionOf(file.name)
            }
        } ?: return false
        ...
    }
```

and, in the same file:

```kotlin
/**
 * The file the user chose, with the extension of the one the app offered put back if they typed a name without it.
 * The dialog has no file type to add one by (see [DesktopFilePicker]), and a file named without its extension is one
 * the import skips, since that is what decides what a file is. Not where a file already has that name: the dialog
 * asked about replacing the name that was typed, and nothing it did not ask about is written over, so the name is
 * then left exactly as typed.
 */
private fun File.withExtensionOf(offeredName: String): File {
    val extension = offeredName.substringAfterLast('.', missingDelimiterValue = "")
    if (extension.isEmpty() || name.endsWith(".$extension", ignoreCase = true)) return this
    val extended = File(parentFile, "$name.$extension")
    return if (extended.exists()) this else extended
}
```

A name typed with a different extension (`song.txt` for a `.cho`) gets `.cho` added (`song.txt.cho`), which is what an
import needs; that is the user fighting the dialog and rare. The single-song exports are always `.cho` (library
names, and after plan 25 header names with `.cho`), the setlist and library exports `.zip`.

### Android: ask for a type that no provider renames

```kotlin
    // A song leaves as the .cho file it is, and .cho maps to no MIME type, so asking for text/plain had the storage
    // provider append .txt to the name (it keeps a name's extension only where the extension maps to the type asked
    // for). The generic binary type is the one it leaves every name alone for. Sharing still says text/plain, which
    // is what decides the apps a share is offered to.
    val createText = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SONG_EXPORT_MIME_TYPE),
        picker::onSaveLocationPicked,
    )
```

with, in the companion or at the bottom of the file:

```kotlin
private const val SONG_EXPORT_MIME_TYPE = "application/octet-stream"
```

`shareFile` keeps `type = file.mimeType` (`:179`): the share sheet's targets are chosen by it, and "text/plain" is
what lets a song be shared to a notes app or a chat.

Web and iOS are not changed: the web writes a download under the name given, and the iOS export picker keeps the
extension the URL has.

## Tests

- **No unit test is possible**: both are platform pickers in `:presentation`, which has no test source set. The
  desktop helper is pure but lives in the desktop source set of an untested module; it is small enough to be checked
  by the steps below.
- Compile check: `./gradlew :presentation:compileKotlinDesktop :presentation:compileDebugKotlinAndroid`

## Verification

1. **Desktop** (`./gradlew :app:desktop:run`), macOS and Windows if at hand:
   - Songs → a song → ⋮ → Export → rename to `test` → Save. **Before:** a file `test`. **After:** `test.cho`.
   - Settings → Library → Export library → rename to `backup` → **after:** `backup.zip`; import it → nothing skipped.
   - Export again as `backup` with `backup.zip` already there → the dialog does not ask (it asked about `backup`),
     and the file is written as `backup` rather than over `backup.zip`.
   - Keep the proposed name → unchanged behaviour (`….cho` / `….zip`).
2. **Android** (emulator and a real phone):
   - A song → ⋮ → Export → save into Downloads. **Before (to confirm):** `….cho.txt` in the Files app.
     **After:** `….cho`, and tapping it in Files offers "Open with Campfire".
   - The same into Google Drive if signed in: note what Drive names it (Drive may apply its own rule; `.cho` is the
     expectation).
   - Setlist and library exports: still `….zip`.
   - Share (⋮ → Share) to a notes or chat app: still offered, still arrives as text.
   - The process-death path: start an export, kill the app from the picker (`adb shell am kill`), finish the save →
     the file is filled from `cacheDir/pending_export`, named as above.

## Docs

- `presentation/CLAUDE.md`, `ui/platform/FilePicker.kt` bullet: add "A song is exported through `CreateDocument` as
  `application/octet-stream` on Android, since a storage provider appends `.txt` to a `.cho` saved as text; the desktop
  dialog has no file type, so a name typed without its extension gets the offered one back, unless a file already
  has that name."

## Files touched

- `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.desktop.kt`
- `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.android.kt`
- `presentation/CLAUDE.md`

## Depends on

Nothing. Plan 25 (lane C, export names follow the header) changes what `ExportedFile.name` is; this plan only reads
its extension, which stays `.cho` / `.zip`, so either order.
