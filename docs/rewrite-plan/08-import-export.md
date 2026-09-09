# Step 08: import and export

**Goal:** import `.cho`-like files and `.zip` archives, export a single song, a setlist (with its songs) or the whole
library as a zip, through native file pickers on every platform.

**Depends on:** 03, 05, 07.

## 1. Domain

`:data:model`:
```kotlin
data class ImportedFile(val name: String, val bytes: ByteArray)   // what a picker hands over

data class ImportResult(
    val importedSongFileNames: List<String>,
    val importedSetlistFileNames: List<String>,
    val skippedFileNames: List<String>      // unsupported extension, unreadable text, empty
)
```

Use cases (`:domain`):

| Use case | Signature | Behaviour |
| --- | --- | --- |
| `ImportFilesUseCase` | `suspend operator fun invoke(files: List<ImportedFile>): ImportResult` | See §2. |
| `ExportSongsUseCase` | `suspend operator fun invoke(fileNames: List<String>): ByteArray` | zip of the given songs. |
| `ExportSetlistUseCase` | `suspend operator fun invoke(setlistFileName: String): ByteArray` | zip of the setlist JSON + its songs (missing ones skipped). |
| `ExportLibraryUseCase` | `suspend operator fun invoke(): ByteArray` | zip of every song and setlist, songs under `songs/`, setlists under `setlists/`. |

Zip handling lives in the local source implementation (it owns the zip package): add to `:data:source:local:api`

```kotlin
interface ArchiveLocalSource {
    suspend fun unpack(archive: ByteArray): List<ImportedFile>   // recursive: zips inside zips are unpacked too, max depth 3
    suspend fun pack(files: List<ImportedFile>): ByteArray
}
```
implemented with `ZipReader` / `ZipWriter` from step 03 on `Dispatchers.Default` and bound in `dataLocalSourceModule`.

## 2. Import rules (`ImportFilesUseCaseImpl`)

For each incoming file, by lower-cased extension:

- `.zip` → `ArchiveLocalSource.unpack`, then process the entries with these same rules (path stripped:
  `name.substringAfterLast('/')`).
- `.cho`, `.chordpro`, `.chopro`, `.crd`, `.pro`, `.txt` → decode as UTF-8 (strip BOM); if the decode fails or the
  text has no non-blank line → skipped. Split with `ChordProSplitter.split`. For each part: target name = if the
  part is the only one, the original base name with `.cho`; otherwise `songFileName(title, artist)` from the part's
  metadata (fallback: original base name + ` (n)`). Make it unique with `uniqueName`, write via
  `SongRepository.saveSong`.
- `.setlist.json` → parse as `SetlistDocument`; on success save with a unique file name and `priority = max + 1`,
  keeping the entries as they are (they refer to song file names; if the zip also contained those songs and they
  were renamed by the collision rule, remap them: keep a `Map<originalName, storedName>` for the songs imported in
  the same batch and apply it). Parse failure → skipped.
- Anything else → skipped.

Never overwrite. After the batch call `SongRepository.rescan()` once (cheaper than updating the list per file for big
imports) and `SetlistRepository` reload.

## 3. Platform file pickers

`presentation/.../platform/FilePicker.kt` (commonMain):

```kotlin
interface FilePicker {
    /** Opens the system picker for one or more files; empty list when cancelled. */
    suspend fun pickFiles(): List<ImportedFile>
    /** Lets the user save the bytes under the suggested name (a "Save as" dialog, share sheet or download). */
    suspend fun saveFile(suggestedName: String, mimeType: String, bytes: ByteArray): Boolean
}

val LocalFilePicker = staticCompositionLocalOf<FilePicker> { error("No FilePicker provided") }
```

Each platform shell provides it around `CampfireApp`:

- **Android** (`CampfireAndroidApp.kt`): `rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments())`
  with mime types `["*/*"]` (the `.cho` extension has no registered mime type; filter by extension afterwards) and
  `CreateDocument(mimeType)` for saving; bridge the callback into a `suspendCancellableCoroutine` held in a
  `remember`ed `AndroidFilePicker`. Read the `Uri`s through `contentResolver.openInputStream` and the display name
  through `OpenableColumns.DISPLAY_NAME`. Requires `androidx.activity:activity-compose` (already a dependency).
- **iOS** (`CampfireIosApp.kt` / `app:ios`): `UIDocumentPickerViewController(forOpeningContentTypes = [UTType.data, UTType.zip], asCopy = true)`
  with `allowsMultipleSelection = true`, presented from the root view controller; the delegate resumes the
  coroutine. For saving, write to `NSTemporaryDirectory()` and present
  `UIDocumentPickerViewController(forExporting = [url])`. Access the picked URLs inside
  `startAccessingSecurityScopedResource()` / `stop…`.
- **Desktop** (`CampfireDesktopApp.kt`): `java.awt.FileDialog(window, title, FileDialog.LOAD)` with
  `isMultipleMode = true` (`Frame`-less is fine: pass `null` parent); `FileDialog.SAVE` with `file = suggestedName`.
  Run on `Dispatchers.IO`. (Use `FileDialog` rather than `JFileChooser`: it is native on macOS.)
- **Web** (`CampfireWebApp.kt`): create an `<input type="file" multiple accept=".cho,.chordpro,.chopro,.crd,.pro,.txt,.zip,.json">`
  element, `click()` it, await the `change` event, read each `File` via `arrayBuffer()`; cancellation has no event
  in every browser: also resolve on the window `focus` event after a short delay with an empty list. Save: `Blob` +
  `URL.createObjectURL` + a temporary `<a download>` click.

## 4. UI wiring

- Enable the "Import" buttons from step 07 (empty state, Settings) → `LocalFilePicker.current.pickFiles()` →
  `viewModel.importFiles(files)` → snackbar "%1$d songs imported" (`import_result`, plural-safe wording: "Imported
  %1$d song(s), %2$d setlist(s); %3$d file(s) skipped"; hu "%1$d dal és %2$d setlist importálva, %3$d fájl kihagyva").
  Show a `LinearProgressIndicator` under the app bar while importing (big zips on web are slow).
- Enable "Export" in the song context menu (`ExportSongsUseCase(listOf(fileName))` → `saveFile("<fileName>", "text/plain", bytes)`:
  a single song is exported as the raw `.cho`, not a zip) and in the song details overflow.
- Setlist header menu gets "Export setlist" (`setlists_export`; hu "Setlist exportálása") → zip named
  `<title>.zip`.
- Settings "Export library as zip" → `campfire-library.zip`, mime `application/zip`.
- Web: also accept files dropped anywhere on the page (`// TODO(step 10)` if it takes more than an hour; step 10 covers
  drag and drop everywhere).

## 5. Strings

`import_result`, `import_failed` ("Import failed"; hu "Az importálás nem sikerült"), `export_failed` ("Export failed";
hu "Az exportálás nem sikerült"), `setlists_export`, and the enabled `songs_import` / `settings_import` /
`settings_export_all` from step 07.

## Verify

- Full build for all four platforms.
- On each platform: import the three sample files one by one and as a zip (create it with `zip -r samples.zip docs/rewrite-plan/samples`);
  importing the same zip twice yields ` (2)` copies, nothing overwritten. A `.txt` with two songs separated by
  `{new_song}` becomes two files. Export a song, open the result in a text editor: identical to the library file.
  Export the library, extract it with the OS tool (`unzip -t` reports OK), import it into a fresh install (delete the
  app data first): songs, setlists and per-setlist transpositions are back.
- Web: Chrome, Safari and Firefox on macOS: picker opens, download lands in the Downloads folder.

## Execution notes

_(filled in by the executing agent)_
