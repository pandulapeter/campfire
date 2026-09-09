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

_(filled in by the executing agent)_
