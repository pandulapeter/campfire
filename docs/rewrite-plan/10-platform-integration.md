# Step 10: platform integration (optional polish)

**Goal:** Campfire behaves like a native document app: `.cho` and `.zip` files open in it, songs can be shared, files
can be dropped onto the window, and the iOS library is visible in the Files app. Each sub-section is independent;
do them in the order listed and stop when time runs out, the app is complete without them.

**Depends on:** 08 (import path) and 09 (editor) for the best experience.

## 1. iOS: expose the library in the Files app

- `app/ios/iosApp/iosApp/Info.plist`: add `UIFileSharingEnabled = true` and `LSSupportsOpeningDocumentsInPlace = true`.
  Since step 02 stores `library/` under `NSDocumentDirectory`, the songs and setlists become visible under
  "On My iPhone → Campfire" and editable in place.
- Because the folder can now change behind the app's back, call `LoadScreenDataUseCase(isRescan = true)` when the
  app returns to the foreground (`LifecycleEventEffect(ON_RESUME)` in `CampfireIosApp.kt`; do the same on Android and
  desktop, it is cheap and handles external edits there too).
- Set `libraryLocationHint` from step 07 on iOS.

## 2. "Open with" / file associations

- **Android** (`AndroidManifest.xml`): add an intent filter to `CampfireActivity` for `ACTION_VIEW` and `ACTION_SEND`
  / `ACTION_SEND_MULTIPLE` with `mimeType="*/*"` and `pathPattern` for `.*\\.cho`, `.*\\.chordpro`, `.*\\.zip`
  (several `<data>` lines; both `content` and `file` schemes). In `CampfireActivity.onCreate` and `onNewIntent`, read
  the URIs (`intent.data`, `EXTRA_STREAM`) into `ImportedFile`s and hand them to the view model through a
  `pendingImports` `MutableStateFlow` consumed by `CampfireAndroidApp`. Set `android:launchMode="singleTask"`.
- **iOS**: `Info.plist` gets `CFBundleDocumentTypes` (name "ChordPro song", role Editor, content types
  `com.pandulapeter.campfire.cho`, `public.zip-archive`) and `UTImportedTypeDeclarations` declaring
  `com.pandulapeter.campfire.cho` conforming to `public.plain-text` with extensions `cho`, `chordpro`, `chopro`,
  `crd`, `pro`. In `iOSApp.swift` handle `.onOpenURL { url in … }` and pass the bytes to Kotlin through a new
  top-level function `CampfireViewControllerKt.importFile(name, data)` that forwards to the same `pendingImports`.
- **Desktop**: `main(args)` treats every argument as a path to import on startup. File associations for the installers
  (`compose.desktop { nativeDistributions { macOS { … fileAssociation("cho", "ChordPro song", "text/plain") } } }`)
  exist in the Compose Gradle plugin; add for macOS and Windows if the API is present in the pinned version, otherwise
  note it.
- **Web**: nothing (browsers cannot register handlers without a service worker; out of scope).

## 3. Drag and drop

- **Desktop**: `Modifier.dragAndDropTarget` on the root `Box` of `CampfireDesktopApp`, accepting
  `DragData.FilesList`, mapped to `ImportedFile`s.
- **Web**: `dragover` / `drop` listeners on `document` in `CampfireWebApp.kt` (prevent default, read `dataTransfer.files`).
- **Android / iOS**: `Modifier.dragAndDropTarget` with `ClipData` URIs on Android (needs `DragAndDropPermissions`)
  is a nice-to-have; skip unless trivial.
- Dropped files go through `viewModel.importFiles`, with the same snackbar as step 08.

## 4. Share a song

- Add "Share" next to "Export" in the song context menu on Android (`Intent.ACTION_SEND` with a `FileProvider` URI of
  a temp copy; add the provider to the manifest and `res/xml/file_paths.xml`) and iOS (`UIActivityViewController`).
  Desktop and web use "Export" only (hide "Share"). Route it through `FilePicker`: add
  `suspend fun shareFile(name, mimeType, bytes)` with a default implementation that calls `saveFile`.

## 5. Strings

`song_share` ("Share"; hu "Megosztás"), `import_opened_files` reuse `import_result`.

## Verify

- iOS simulator: Files app → On My iPhone → Campfire → songs listed; edit one in a text editor on the simulator (or
  `xcrun simctl` the container path) and return to the app: the change shows after resume.
- Android: `adb shell am start -a android.intent.action.VIEW -d file:///sdcard/Download/simple.cho -t text/plain`
  (push the file first) opens Campfire and imports it; sharing from a file manager works.
- Desktop: `./gradlew :app:desktop:run --args="/path/simple.cho"` imports; dropping a zip onto the window imports.
- Web: dropping files on the page imports.

## Execution notes

- **Campfire registers for ChordPro files and nothing else.** The plan's `.zip` association is gone and plain text
  never gained one: an app that claims `public.zip-archive` or `text/plain` system wide is answering for every
  archive and every note on the device, which is not what Campfire is. Both are still *importable* - handed one
  through the picker, a share or a drop, it reads it. That split is the difference between
  `LibraryFiles.IMPORTABLE_EXTENSIONS` (what an import will look inside) and `LibraryFiles.SONG_EXTENSIONS` (what a
  song file is, and the only thing registered with an operating system).
- **The extension family is one list**, `LibraryFiles.SONG_EXTENSIONS` = `.cho`, `.chopro`, `.chordpro`, `.crd`,
  `.chord`, `.pro`. It is what the library scan accepts, what the import rules treat as a song and what the pickers
  offer. Campfire only ever *writes* `.cho`: an imported `other.chopro` lands in the library as `other.cho`, so the
  folder stays uniform however the file arrived.
  The three OS registrations cannot see Kotlin (an XML manifest, a plist and a Gradle file), so they repeat the list;
  each of the three carries a comment naming `LibraryFiles.SONG_EXTENSIONS` as the copy to keep in step with.
- **"Prefers `.cho`" is said differently on each platform**, because each has a different way of saying it - or none:
  iOS has `LSHandlerRank`, so `.cho` is `Default` and the rest of the family `Alternate`; the desktop's
  `fileAssociation` has no rank at all; Android has no ranking either, so all six patterns sit in one filter and the
  user's "always open with" is what decides. What is uniform is that `.cho` is the extension Campfire writes.
- **`libraryLocationHint: String?` (step 07) became `LibraryLocation`**, a sealed interface of `Folder(path)` and
  `FilesApp`. A `String?` was fine while only the desktop filled it in; iOS's answer is not a path but a sentence
  ("Visible in the Files app under Campfire"), and a sentence has to come from `strings.xml` in the user's language,
  not from a platform source set.
- **The rescan on resume is gated on `libraryLocation != null`**, i.e. it runs on iOS and desktop only. Those are the
  two platforms whose library is a folder somebody else can edit. Android's is app-private and the web's is OPFS,
  which no one outside the tab can touch - and on the web `ON_RESUME` fires every time the tab regains focus, so an
  ungated rescan would re-read the whole library each time the user came back from another tab. The Android rescan
  had been driven and worked; it is off because it cannot ever have anything to find, not because it failed.
- **Android's `VIEW` filter needs `mimeType="*/*"` for `pathPattern` to be consulted at all.** A `content://` URI has
  no extension in the eyes of the intent resolver unless a type is declared, so the wildcard type is what lets the
  six patterns do the actual deciding. The consequence is worth knowing: the filter matches on the file *name*, so a
  `.cho` handed over with any MIME type opens, and anything else does not.
- **`file://` intents do not import, by design of the platform.** `adb am start -d file:///sdcard/...` reaches the
  activity, but reading the URI throws for a modern target SDK. That is Android's rule for every app, not something
  to work around; real "open with" from a file manager or a browser download always arrives as `content://`.
- **The share filter is `text/plain`, not `*/*`.** The same reasoning as the associations: Campfire in the share
  sheet of every photo on the phone would be noise.
- **"Share" is not "Export" with a different word.** `FilePicker.shareFile` defaults to `saveFile`, and `canShare`
  says whether the platform has a real send-to (a share sheet on Android and iOS). Desktop and web report false and
  the song menu shows "Export" alone, rather than two entries doing the same thing.
- **Web drops hand their files over through a promise.** A Kotlin lambda cannot be passed into a `js(...)` block, so
  the listener queues each drop and `droppedFiles()` awaits `nextDrop()` in a loop. The listeners are attached once,
  on first collection, and both `dragover` and `drop` have their default prevented - without the `dragover` half the
  browser navigates away to the dropped file and the app is gone.
- **Skipped**: in-app drag and drop on Android and iOS, which the plan lists as a nice-to-have. Dragging a file onto
  a phone app is not a gesture anyone performs; the share sheet is that platform's answer and it is wired up.

### Verified

- All four platforms build; `:chordpro:desktopTest` (51) and `:data:source:local:implementation:desktopTest` (28)
  pass. Step 10 adds no tests: everything in it is platform glue, which is exactly what the test policy excludes.
- **Android** (emulator, phone width):
  - `pm query-activities` for a `.cho` and for a `.crd` offers Campfire; the same query for a `.zip` answers "No
    activities found" and for `text/plain` returns no Campfire match. The association is as narrow as intended.
  - Opening `simple.cho` as a `content://` VIEW intent from a cold start imports it ("Campfire Song" in the list);
    the same URI as `file://` imports nothing, as above.
  - With the app already running, a second file (`other.crd`) arrives through `onNewIntent` thanks to
    `singleTask` and shows up as "Opened Crd" without a restart.
  - "Share" on a song opens the system share sheet with `everything.cho` attached, through the FileProvider.
    Campfire itself appears among the targets, which is its own `SEND` filter answering.
- **Desktop**:
  - `./gradlew :app:desktop:run --args=".../other.chopro"` imports the file and the list shows "Opened Chopro" -
    the `.chopro` member of the family, written into the library as `.cho`.
  - Dropping `everything.cho` into the library folder while the window was not focused changes nothing; handing the
    focus to Finder and taking it back brings "Everything at Once" into the list. That is the resume rescan, on the
    one desktop platform where the folder is a real folder.
- **Web** (Chrome, dev server): a drop of `dropped.chopro` and `dropped.pro` on the page imports both ("Imported 2
  songs and 0 setlists · 0 skipped"), and OPFS afterwards holds `dropped.cho` and `dropped (2).cho` - the family
  recognised on the way in, `.cho` written on the way out, with the duplicate-name rule from step 05 applied. The
  drop was dispatched as a `DragEvent` carrying a real `DataTransfer` rather than performed by hand, so it exercises
  the listeners and everything behind them, but not the browser's own drag chrome.
- **iOS is the gap again**: the framework links and the plist is in place, but nothing there was driven. The Files
  app exposure, `.onOpenURL` and the `UIActivityViewController` share are unverified, for the same reason as in
  steps 08 and 09 - the simulator will not come to the foreground on this machine.
