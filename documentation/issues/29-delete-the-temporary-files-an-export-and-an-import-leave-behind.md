# 29 — Delete the temporary files an export and an import leave behind

## What the user sees

On iOS, nothing — until they look. Every export, every share and every import through the picker leaves a full
copy of what was exported or imported in the app's temporary directory, and none of them is ever deleted. A user
who exports their library once a week is spending a copy of it per week; the storage is charged to Campfire under
Settings → iPhone Storage → Campfire → Documents & Data, and it is the user's own storage.

iOS purges the temporary directory when it feels like it — which in practice means when the device is short of
space, and not while the app is installed and used. A user who exports a 60 MB library archive twice has 120 MB of
nothing.

## Cause

Three places, all verified at HEAD `984861e4`.

**The export** — `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt:75-83`:

```kotlin
    override suspend fun saveFile(file: ExportedFile): Boolean = suspendCancellableCoroutine { continuation ->
        // The picker exports a file that already exists, so the bytes go to a temporary one first.
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + file.name)
        if (!file.bytes.toNSData().writeToURL(url, atomically = true)) {
            continuation.resume(false)
        } else {
            present(UIDocumentPickerViewController(forExportingURLs = listOf(url))) { urls -> continuation.resume(urls.isNotEmpty()) }
        }
    }
```

Nothing deletes `url`. (An export the user confirms is *moved* out by the picker, since `forExportingURLs:` without
`asCopy` moves rather than copies; an export the user cancels leaves the file.)

**The share** — `IosFilePicker.kt:87-99`:

```kotlin
    override suspend fun shareFile(file: ExportedFile): Boolean = suspendCancellableCoroutine { continuation ->
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + file.name)
        if (!file.bytes.toNSData().writeToURL(url, atomically = true)) {
            continuation.resume(false)
        } else {
            val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
```

The share sheet never takes the file away — every activity copies it — so this one always stays, and it is the one
that is a whole-library archive.

**The import** — `IosFilePicker.kt:61-73`:

```kotlin
            val controller = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData, UTTypeZIP), asCopy = true)
            controller.allowsMultipleSelection = true
            present(controller) { urls -> continuation.resume(urls) }
        }
        return withContext(Dispatchers.IO) {
            val budget = ImportBudget()
            urls.mapNotNull { it.readImportedFile(budget) }
        }
```

`asCopy = true` means iOS copies every picked file into the app's temporary directory and hands over the copy. The
copies are read and then forgotten.

The app already knows how to do this for the *other* kind of copy iOS makes — `Documents/Inbox`, for files handed
over by AirDrop, Mail or Messages — in
`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFileImport.kt:59-66`:

```kotlin
    inboxScope.launch {
        val file = url.readImportedFile(ImportBudget())
        pendingImports.send(listOf(file ?: ImportedFile.unread(url.lastPathComponent.orEmpty())))
        if (file != null && url.isInboxCopy()) {
            NSFileManager.defaultManager.removeItemAtURL(url, error = null)
        }
    }
```

with the distinction that has to be preserved, `IosFileImport.kt:88-96`:

```kotlin
/**
 * Whether this is the copy iOS made for the app, which is the app's to delete, rather than the user's own file
 * opened in place - a song in the Files app, which may well be one in Campfire's own library folder.
 */
private fun NSURL.isInboxCopy(): Boolean {
    val inboxPath = inboxPath() ?: return false
    val path = URLByResolvingSymlinksInPath?.path ?: return false
    return path.startsWith("$inboxPath/")
}
```

and the serialization that keeps the sweep from overtaking a read, `IosFileImport.kt:37-42`:

```kotlin
/**
 * One thing at a time, in the order it was asked for. … they are not side by side
 * because [cleanImportInbox] must not get ahead of a read that was asked for before it.
 */
private val inboxScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
```

## The change

`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt`. Each temporary file is deleted by whoever
made it, at the moment it is certainly finished with. No sweep, no timer.

### 1. Two small helpers, in the same voice as `isInboxCopy`

```kotlin
/** Best effort: a file that is already gone, or that the picker moved out, is the outcome this wanted anyway. */
private fun NSURL.deleteTemporaryFile() {
    NSFileManager.defaultManager.removeItemAtURL(this, error = null)
}

/**
 * Whether this is a copy iOS made for the app in the temporary directory - what the picker hands over when it is
 * asked for copies - rather than the user's own file somewhere else. `NSTemporaryDirectory` is reached through a
 * symbolic link (`/var` for `/private/var`), so both sides are resolved before they are compared, the way
 * `isInboxCopy` does it in `IosFileImport.kt`.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSURL.isTemporaryCopy(): Boolean {
    val temporaryPath = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByResolvingSymlinksInPath?.path ?: return false
    val path = URLByResolvingSymlinksInPath?.path ?: return false
    return path.startsWith("$temporaryPath/")
}
```

### 2. The import deletes each copy once it has been read

```kotlin
        return withContext(Dispatchers.IO) {
            val budget = ImportBudget()
            urls.mapNotNull { url ->
                url.readImportedFile(budget).also {
                    // The picker was asked for copies, so this file is the app's own and nobody else's. Deleted
                    // whether it could be read or not: nothing will come back for it. A file opened in place is
                    // never one of these - that path is IosFileImport's, and it makes the same distinction.
                    if (url.isTemporaryCopy()) url.deleteTemporaryFile()
                }
            }
        }
```

Ordering needs no dispatcher of its own here, unlike the inbox: the read and the deletion are two statements of one
coroutine, over a URL nothing else has.

### 3. The export deletes its file once the picker is done with it

```kotlin
    override suspend fun saveFile(file: ExportedFile): Boolean {
        val url = file.writeToTemporaryFile() ?: return false
        return try {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    present(UIDocumentPickerViewController(forExportingURLs = listOf(url))) { urls -> continuation.resume(urls.isNotEmpty()) }
                }
            }
        } finally {
            // After the picker has answered, never before: an export that the user confirmed is the picker moving
            // this very file to where they chose. What is left here is the cancelled case - and, where the move
            // happened, a file that is already gone, which removing is a no-op.
            withContext(NonCancellable + Dispatchers.IO) { url.deleteTemporaryFile() }
        }
    }
```

(The `writeToTemporaryFile` helper and the `withContext(Dispatchers.Main)` are plan 28's; without that plan landed
first, the `finally` wraps the existing `suspendCancellableCoroutine` instead.)

### 4. The share deletes its file from the activity controller's completion handler

```kotlin
                val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
                // An iPad presents this as a popover, which needs something to point at; the whole view will do.
                controller.popoverPresentationController?.sourceView = host.view
                // The sheet hands out copies and never takes the file away, so this is the only moment it is
                // certainly finished with: an activity that is still reading it has not returned yet. A handler
                // that never runs costs one file that iOS purges on its own, which is why the caller is still
                // answered from the presentation rather than from here (see plan 27).
                controller.completionWithItemsHandler = { _, _, _, _ -> url.deleteTemporaryFile() }
                host.presentViewController(controller, animated = true, completion = null)
                continuation.resume(true)
```

and the guard plan 27 adds — the branch where UIKit would refuse the presentation — deletes it too, since the
handler will never run there:

```kotlin
            if (host.presentedViewController != null) {
                url.deleteTemporaryFile()
                continuation.resume(false)
            } else {
```

Kotlin/Native interop for the handler:

- `completionWithItemsHandler` is the Obj-C block
  `(UIActivityType activityType, BOOL completed, NSArray *returnedItems, NSError *activityError)`, which Kotlin
  sees as `((String?, Boolean, List<*>?, NSError?) -> Unit)?`. All four are ignored here; if the compiler cannot
  infer the parameter types of the lambda, spell them out.
- It is called **on the main thread**, which is where this block already is. `removeItemAtURL` is one unlink of a
  file in the app's own container — a metadata operation, not a read of the file — so it is left inline rather than
  sent to a dispatcher and the handler kept trivial.
- The lambda captures `url` (an ordinary Obj-C object reference) and is retained by the controller, which UIKit
  releases when the sheet goes. No cycle: nothing in the lambda points at the controller.
- No `NSError` is asked for: a file that is already gone and a file that cannot be removed lead to the same
  nothing, and the log line would be noise.

### Deliberately not a sweep of the temporary directory

`cleanImportInbox()` could be extended to empty `NSTemporaryDirectory()` when the app goes to the background, and
it must not be: **sharing to another app backgrounds Campfire while that app is still reading the shared file.**
Share a song to Mail and the sweep would delete the attachment out from under it. The inbox sweep is safe precisely
because those copies are made *for* Campfire and handed over before it is backgrounded; the temporary directory
holds files other apps are reading.

What that leaves unhandled is a process killed while a share sheet is open, whose file stays until iOS purges the
directory. That is one file, it is what the directory is for, and it is the price of not deleting a file somebody
else is reading.

## Tests

None possible: `:app:ios` is a shell and untested by policy. The checks are manual and they are file-system
observations, which is the honest way to check this.

## Verification

```
./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64
```

Manual, **needs the iOS simulator** (a device does it too; the simulator makes the directory readable from the Mac,
which is what makes this checkable at all):

1. Find the container:
   `xcrun simctl get_app_container booted com.pandulapeter.campfire data` (the bundle id is in
   `app/ios/iosApp/Configuration/Config.xcconfig`), and watch `<container>/tmp` with `ls -la` between steps.
2. **Import**: tap Import, pick two `.cho` files from Files. After the import, `tmp` must hold no copies of them.
   Before the change there are two.
3. **Export a song**, and cancel the picker. `tmp` must be empty afterwards. Repeat, confirming the save: the file
   lands where it was asked for and `tmp` is empty.
4. **Export library** to a zip, confirm the save, and check `tmp` again.
5. **Share** a song to Files or Mail, complete the share, and confirm `tmp` empties once the sheet closes. Repeat,
   dismissing the sheet without sharing: it must empty then too (the handler runs with `completed = false`).
6. **Share to Mail and actually send it**: the attachment must be intact — this is the check that the deletion is
   not too early.
7. Regression: a file opened with Campfire from the Files app (open in place) must still be there afterwards. Open
   a `.cho` from iCloud Drive with Campfire, then confirm the original is still in iCloud Drive — nothing in this
   plan may reach a file the app did not make.
8. Regression: a file sent by AirDrop or Mail still lands in `Documents/Inbox` and is still removed from there
   after it is read.

## Docs

`app/ios/CLAUDE.md:16` describes the inbox rule in full and is right; it is now half of the story:

> A file that came from AirDrop, Mail or Messages is a copy iOS leaves in `Documents/Inbox` — visible in the Files
> app because of `UIFileSharingEnabled` — and it is deleted once it has been read, and only there, since a file
> opened in place is the user's original (possibly one in the library itself).

Add the other half, to `app/ios/CLAUDE.md:15`'s `IosFilePicker.kt` sentence: the picker is asked for copies, so
every picked file is a copy in the temporary directory and is deleted once it has been read; an export's and a
share's temporary file is deleted by whoever made it — the export after the picker has answered, the share from the
activity controller's completion handler — and the temporary directory is never swept, because sharing hands a file
to an app that is still reading it while Campfire is in the background.

## Files touched

- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt`
- `app/ios/CLAUDE.md`

## Depends on

Plans 27 and 28 touch the same two functions. Land in number order — 27 (the guard), 28 (the write off the main
thread), 29 (the deletions) — or rebase the sketches by hand. Nothing outside `:app:ios`.

## Rules

- Load the `code-style` skill before the first edit: KDoc on the two new private helpers, `//` comments inside the
  statements, "why, not what", trailing commas. No new files, so no MPL header.
- No new strings.
- `import kotlinx.coroutines.IO` for `Dispatchers.IO`, which this file already does.
- The deletion is ordered by where it sits in the code — after the read, after the picker's answer, in the
  completion handler — never by a delay or a sweep on a schedule.
- Unit tests are the command in the root `CLAUDE.md`; this plan adds none.
- `app/ios/CLAUDE.md` is part of the change, not a follow-up.
