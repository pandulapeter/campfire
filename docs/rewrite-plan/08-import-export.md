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

- **Zip handling reaches the use cases through an `ArchiveRepository`, not `ArchiveLocalSource` directly.** Section 1
  has the use case using the local source, but `:domain:implementation` only depends on `:data:repository:api`; a
  fifteen line pass-through repository was cheaper than breaking that. `pack` also takes a `Map<String, ByteArray>`
  rather than a list of `ImportedFile`s: "imported file" is the wrong word for something on its way out, and a map
  cannot name the same entry twice.
- **The export use cases return an `ExportedFile` (name, MIME type, bytes) rather than raw bytes.** Section 4 wants a
  lone song to leave as a `.cho` and everything else as a zip, which is a decision about the content and belongs
  where the content is; the platform's save dialog then only has to take the bytes. `ExportedFile.TEXT_MIME_TYPE`
  and `ZIP_MIME_TYPE` live next to it, which is also what the Android launchers are keyed on.
- **Naming a song stays in the storage layer.** `SongLocalSource.importSong(desiredFileName, text)` takes a *null*
  desired name for the parts of a file that held several songs and derives one from the metadata in the text itself,
  so the import rules never have to know how "Artist - Title.cho" is spelled. `SETLIST_EXTENSION` and
  `SONG_EXTENSION` moved to `LibraryFiles` in `:data:model` for the same reason: the import and export rules and the
  storage layer have to agree about them, and now there is one definition rather than two.
- **A setlist is parsed by the layer that writes them** (`SetlistRepository.parseSetlist`), since the document shape
  is a storage detail. It hands back a setlist carrying the file name it *would like* to have, and `importSetlist`
  turns that into a free one - so the collision rule stays in one place for songs and setlists alike.
- **`SongContentRepository.loadSongContent` gained a `shouldCache` flag.** Its cache is unbounded and exporting the
  library reads every song exactly once; without the flag a single export would leave the whole library in memory.
- **The desktop dialog is shown on the AWT event thread**, not on `Dispatchers.IO` as section 3 suggests. Both were
  tested and both block until the dialog is dismissed, but showing a modal AWT dialog is the event thread's job and
  relying on the macOS behaviour of the other case is not worth the saving. The reads and writes stay on IO.
- **The snackbar queues its messages instead of collecting them straight into the host.** The text of "imported 3
  songs" can only be built inside a composition (string resources are composable), so the message is parked in a
  `mutableStateListOf` first; that also makes two identical results in a row two separate snackbars.
- **`import_result` reads "Imported %1$d songs and %2$d setlists · %3$d skipped".** The string tables have no
  plurals, so the wording avoids needing them, the same way step 07's library summary does.
- The progress bar is shown by the two screens an import can be started from (Songs and Settings) rather than by the
  app shell, because the shell has no app bar of its own to put it under.
- Web drag and drop was left to step 10, as section 4 allows.

### Verified

- All four platforms build; `:chordpro:desktopTest` (41) and `:data:source:local:implementation:desktopTest` (28)
  pass.
- **The import and export rules, driven end to end on desktop** through a stand-in picker (a temporary
  `LaunchedEffect` in `app/desktop`'s `main`, removed afterwards; the file matches `HEAD`), starting from an empty
  library:
  - Three loose `.cho` files land as three songs. The same three again become ` (2)` copies, and a zip of the same
    three becomes ` (3)` copies - nothing is ever overwritten.
  - A `.txt` holding two songs separated by `{new_song}` becomes `Splitter - First Of Two.cho` and
    `Splitter - Second Of Two.cho`, each named from its own metadata.
  - A `.png` is skipped and reported: the snackbar reads "Imported 0 songs and 0 setlists · 1 skipped".
  - Exporting one song writes a file byte for byte identical to the one in the library (`cmp` clean, 544 bytes).
  - Exporting a setlist writes a zip holding the setlist document and both of its songs; `unzip -t` is clean and the
    document inside still carries `"transposition": 3`.
  - Exporting the library writes `campfire-library.zip` with every song under `songs/` and every setlist under
    `setlists/`; `unzip -t` is clean. Wiping the library and importing that archive back restores all eleven songs,
    the setlist, and the per-setlist transposition.
- **Desktop, the real dialog**: the app bar action opens the native macOS panel, the coroutine stays suspended for as
  long as it is up, and dismissing it with Escape returns an empty list and imports nothing.
- **Android** (emulator): "Import" opens `com.android.documentsui.picker.PickActivity`; picking `simple.cho` out of
  it (which the system lists as a "BIN file", which is why the picker has to accept `*/*`) imports it as "Campfire
  Song"; "Export library as zip" opens the system save sheet pre-filled with `campfire-library.zip`, and saving it
  writes an archive `unzip -t` accepts, holding `songs/simple.cho`.
- **Web** (Chrome, dev server): the picker's own `<input>` is created with the right `accept` list and, fed three
  files, imports two songs out of a `{new_song}` text file plus one `.cho` and skips a `.png` - the snackbar reads
  "Imported 3 songs and 0 setlists · 1 skipped" and the files are in the OPFS. Exporting the library produces a real
  `application/zip` blob named `campfire-library.zip` holding the three songs under `songs/`.
- **iOS is the gap.** The framework links, the app launches with the picker wired in and shows the enabled "Import"
  button, and the compiler checks `UIDocumentPickerDelegateProtocol` conformance for us, but the picker itself was
  never opened: driving the simulator needs it to be the frontmost application, which this machine would not grant.
  Presenting the document picker and reading what it hands back are unverified on iOS.
