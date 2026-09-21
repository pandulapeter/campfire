# 51 · iOS: files opened from other apps pile up in an "Inbox" folder next to the library, an unreadable one is ignored without a word, and the read blocks the UI

**Severity:** minor (ios; the leftovers every time a file arrives by AirDrop, Mail or Messages, the silence and the blocked UI only with an unreadable or a large file) · **Area:** `:app:ios` — `IosFileImport.kt`, `IosFilePicker.kt` (`readImportedFile`, `pickFiles`), `iosApp/iOSApp.swift`

## Symptom
1. Somebody AirDrops three songs to the phone, or the user taps a `.cho` attachment in Mail and chooses "Open in
   Campfire". The songs are imported. Then, in the Files app under **On My iPhone → Campfire**, next to `library`
   there is a folder called `Inbox` holding a copy of every file that ever arrived this way. It only grows; nothing
   in the app shows it or empties it.
2. The file cannot be read (the sender's iCloud copy was never downloaded, the attachment is gone by the time the
   app asks): the app comes to the front and nothing happens — no import, no message.
3. The file is large, or on storage that answers slowly, and the open was what started the app: the launch screen
   freezes until the read is over, since it happens on the main thread in the middle of the cold start. The
   document picker's files are read on the main thread the same way.

## Cause
`Info.plist` sets `LSSupportsOpeningDocumentsInPlace` and `UIFileSharingEnabled` (lines 38–41). A file opened from
the Files app is therefore handed over in place, as a security scoped URL to the original. One that comes from an
app that cannot share in place — AirDrop, Mail, Messages — is **copied by iOS into `Documents/Inbox/`** first, and
that copy is the app's to delete. `Documents` is what `UIFileSharingEnabled` exposes, so the folder is in plain
sight.

`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFileImport.kt:35-40`:

```kotlin
fun openUrl(url: NSURL) {
    val absoluteString = url.absoluteString.orEmpty()
    if (!isSyncRedirect(absoluteString)) {
        url.readImportedFile()?.let { pendingImports.trySend(listOf(it)) }
    }
}
```

- nothing deletes the copy;
- a `null` from `readImportedFile()` is dropped, and `CampfireViewModel.importFiles` never hears of the file;
- Swift calls this from `.onOpenURL` (`iosApp/iOSApp.swift:22`), that is on the main thread.

`IosFilePicker.kt:102-114` — `NSData.dataWithContentsOfURL` reports a failure by returning `nil`, not by throwing, so
the `catch` there never runs and nothing is ever logged:

```kotlin
return try {
    NSData.dataWithContentsOfURL(this)?.let { ImportedFile(name = lastPathComponent.orEmpty(), bytes = it.toByteArray()) }
} catch (exception: Exception) {
    println("Could not read \"$this\": ${exception.message}")
    null
}
```

and `pickFiles` (`:55`) maps the picked URLs through it inside the picker delegate's callback, on the main thread.

## Fix
1. **`IosFileImport.kt`** — read on a background dispatcher, report a file that could not be read, delete the Inbox
   copy once it has been read. Replace `openUrl` and add what it needs; `pendingImports` and `filesToImport` stay:

   ```kotlin
   /**
    * One thing at a time, in the order it was asked for. The reads are off the main thread because a file that is
    * large, or not on the device yet, would otherwise hold up the very launch it caused; they are not side by side
    * because [cleanImportInbox] must not get ahead of a read that was asked for before it.
    */
   private val inboxScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

   /**
    * Called from Swift for every URL iOS hands the app, which in practice means a file opened with Campfire.
    *
    * A sync redirect is answered by the `ASWebAuthenticationSession` itself and never reaches this, but one is
    * recognised and ignored anyway: read as a song it would fail, and the user would be told a file could not be
    * imported that they never tried to import.
    *
    * A file that cannot be read is passed on empty rather than dropped, so that the import counts it among the
    * skipped ones: the user asked for something, and an app that comes to the front and says nothing has not
    * answered.
    */
   @Suppress("unused")
   fun openUrl(url: NSURL) {
       if (isSyncRedirect(url.absoluteString.orEmpty())) return
       inboxScope.launch {
           val file = url.readImportedFile()
           pendingImports.send(listOf(file ?: ImportedFile(name = url.lastPathComponent.orEmpty(), bytes = ByteArray(0))))
           if (file != null && url.isInboxCopy()) {
               NSFileManager.defaultManager.removeItemAtURL(url, error = null)
           }
       }
   }

   /**
    * Called from Swift when the app goes to the background. Removes what is left in the inbox: the copy of a file
    * that could not be read, or of one the app was killed before reading.
    *
    * Not done as the app starts, which would be the obvious moment, because the file a cold start was caused by is
    * already in the inbox by then and is only handed over afterwards - there is no telling it from a leftover. By
    * the time the app leaves the screen, everything it was handed is either read or queued ahead of this.
    */
   @Suppress("unused")
   fun cleanImportInbox() {
       inboxScope.launch {
           val fileManager = NSFileManager.defaultManager
           val inboxPath = inboxPath() ?: return@launch
           fileManager.contentsOfDirectoryAtPath(inboxPath, error = null)
               ?.filterIsInstance<String>()
               ?.forEach { fileManager.removeItemAtPath("$inboxPath/$it", error = null) }
       }
   }

   /**
    * Whether this is the copy iOS made for the app, which is the app's to delete, rather than the user's own file
    * opened in place - a song in the Files app, which may well be one in Campfire's own library folder.
    */
   private fun NSURL.isInboxCopy(): Boolean {
       val inboxPath = inboxPath() ?: return false
       val path = URLByResolvingSymlinksInPath?.path ?: return false
       return path.startsWith("$inboxPath/")
   }

   /** `Documents/Inbox`, where iOS puts a copy of a file that comes from an app that cannot share it in place. */
   private fun inboxPath() = NSFileManager.defaultManager
       .URLForDirectory(directory = NSDocumentDirectory, inDomain = NSUserDomainMask, appropriateForURL = null, create = false, error = null)
       ?.URLByAppendingPathComponent("Inbox")
       ?.URLByResolvingSymlinksInPath
       ?.path
   ```

   New imports: `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.IO`,
   `kotlinx.coroutines.SupervisorJob`, `kotlinx.coroutines.launch`, `platform.Foundation.NSDocumentDirectory`,
   `platform.Foundation.NSFileManager`, `platform.Foundation.NSUserDomainMask` (and
   `kotlinx.cinterop.ExperimentalForeignApi` opt-ins on the functions that pass `error = null`, as
   `FileStorage.ios.kt` does for the same calls).

   Points that are easy to get wrong:
   - **Only a path inside `Documents/Inbox/` is ever deleted.** With `LSSupportsOpeningDocumentsInPlace` the URL
     of a file tapped in the Files app is the user's original — possibly `Documents/library/songs/x.cho` itself.
     Both paths are resolved before they are compared because the container is reached through `/var` and
     `/private/var` alike.
   - The copy is deleted only after a **successful** read, and after the file has been handed to the channel. One
     that could not be read is left for `cleanImportInbox()`.
   - The security scope is taken inside `readImportedFile()`, on the background thread; that is allowed, and the
     scope is tied to the `NSURL` object, which the coroutine holds.
   - `send` rather than `trySend`: the coroutine can wait, and a buffer that happened to be full would otherwise
     lose an import.
   - The `Inbox` directory itself is left alone. iOS owns it (an app may read and delete files in it, not create
     them), it recreates it for the next file, and an empty folder is not what the finding is about.

2. **`iosApp/iOSApp.swift`** — tell the Kotlin side when the app leaves the screen. The deployment target is 15.3,
   so this is the one-parameter `onChange`:

   ```swift
   @main
   struct iOSApp: App {
       @Environment(\.scenePhase) private var scenePhase

       var body: some Scene {
           WindowGroup {
               ContentView()
                   // (the existing comment stays)
                   .onOpenURL { url in IosFileImportKt.openUrl(url: url) }
           }
           // A file that arrives from AirDrop or Mail is a copy iOS leaves in Documents/Inbox, which the Files app
           // shows next to the library. The ones that were read are deleted as they are read; this is for the rest.
           .onChange(of: scenePhase) { phase in
               if phase == .background {
                   IosFileImportKt.cleanImportInbox()
               }
           }
       }
   }
   ```

   `.onOpenURL` stays where it is and keeps calling `openUrl` synchronously — the hop off the main thread is
   Kotlin's, so that the URL is queued before Swift returns and the order of two URLs is kept.

3. **`IosFilePicker.kt`, `readImportedFile()`** — the `catch` is dead code; say what actually happens:

   ```kotlin
   /**
    * Null when the file cannot be read, so that one bad file does not lose the ones next to it. Foundation reports
    * that by handing back nothing rather than by throwing.
    *
    * The copies the picker hands over live in the app's own container, but a URL that arrives from another app is
    * security scoped, so the read happens inside the access it grants. Blocking, so not for the main thread.
    */
   internal fun NSURL.readImportedFile(): ImportedFile? {
       val isAccessible = startAccessingSecurityScopedResource()
       return try {
           val data = NSData.dataWithContentsOfURL(this)
           if (data == null) {
               println("Could not read \"$this\".")
           }
           data?.let { ImportedFile(name = lastPathComponent.orEmpty(), bytes = it.toByteArray()) }
       } finally {
           if (isAccessible) {
               stopAccessingSecurityScopedResource()
           }
       }
   }
   ```

4. **`IosFilePicker.kt`, `pickFiles()`** — same read, same thread problem; split the picking from the reading:

   ```kotlin
   override suspend fun pickFiles(): List<ImportedFile> {
       val urls = suspendCancellableCoroutine<List<NSURL>> { continuation ->
           // Everything, not just plain text: ".cho" is not a type iOS knows, so a narrower list would grey the songs
           // out in the picker. What is not a song is skipped by the import and reported afterwards.
           val controller = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData, UTTypeZIP), asCopy = true)
           controller.allowsMultipleSelection = true
           present(controller) { urls -> continuation.resume(urls) }
       }
       return withContext(Dispatchers.IO) { urls.mapNotNull { it.readImportedFile() } }
   }
   ```

   (imports `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.IO`, `kotlinx.coroutines.withContext`). The
   picker's `asCopy` copies go to the app's `tmp`, which the system purges and the Files app does not show; they are
   not this plan's business. A picked file that cannot be read stays dropped here, as today — the user is looking
   at the result of a batch they chose, and plan 15 is where the picker paths learn to report what they skipped.

5. **What the user sees for an unreadable opened file.** Nothing new is needed: an `ImportedFile` with no bytes is
   sorted by its extension in `PrepareImportUseCaseImpl`, a song with no text splits into no parts and lands in
   `skippedFileNames`, and the snackbar reads "Imported 0 songs and 0 setlists · 0 already there · 1 skipped"
   (`import_result`). No new string, no new `Message`.

Do **not** set `LSSupportsOpeningDocumentsInPlace` to `false` to make every file a copy (it is what puts the library
into the Files app), and do not wrap the read in `NSFileCoordinator` as part of this: a coordinated read would also
fetch a file iCloud has not downloaded, which is a feature of its own.

## Tests
None (`:app:ios` is a shell; the UI and the shells are untested).

## Verify
Simulator or device; the simulator's Files app works for steps 1–3 if the file is dragged into it, AirDrop needs a
device.
1. AirDrop a `.cho` to the device, or share one to Campfire from Mail or Messages (`xcrun simctl openurl` hands
   the URL over as it is and copies nothing, so it does not exercise this). The song is imported; Files → On My
   iPhone → Campfire → `Inbox` is empty (or absent).
2. In the Files app, open On My iPhone → Campfire → library → songs and tap a song: the app says "… 1 already
   there", and **the song is still in the library afterwards**.
3. Put a file into the Inbox that cannot be read (on a simulator: drop a file into the app container's
   `Documents/Inbox` and `chmod 000` it, then `simctl openurl` its `file://` URL): the snackbar says "1 skipped".
   Send the app to the background: the file is gone.
4. AirDrop a 50 MB zip while the app is closed: the launch screen animates instead of freezing, the import runs.
5. Import → pick several files through the picker: unchanged behaviour.
6. `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64`, then build the Xcode project (the Swift change is not
   covered by Gradle).

## Docs
`app/ios/CLAUDE.md`, the `IosFileImport.kt` bullet — add: the URL is read on a background dispatcher, one at a
time; a file that cannot be read is passed on empty so the import reports it as skipped; a file that came from
AirDrop, Mail or Messages is a copy iOS leaves in `Documents/Inbox` — visible in the Files app because of
`UIFileSharingEnabled` — which is deleted once read, and only there, since a file opened in place is the user's
original; `cleanImportInbox()`, called from Swift when the scene goes to the background, removes the rest, and why
not at start. The `iosApp.xcodeproj` bullet's description of `iOSApp.swift`/`ContentView.swift` gains the
`scenePhase` observer.

## Touches
- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFileImport.kt`
- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt`
- `app/ios/iosApp/iosApp/iOSApp.swift`
- `app/ios/CLAUDE.md`

## Depends on
Nothing. Plan 15 (size caps) changes `readImportedFile()` and `pickFiles()` in `IosFilePicker.kt` as well — it
checks the size before the read that step 3 rewrites — so the two are landed one after the other; whichever comes
second keeps both the size check and the `nil`-instead-of-`catch` shape.
