# 28 — Write exported files off the main thread

## What the user sees

On iOS, exporting or sharing freezes the app for as long as the file takes to write. For one song it is
imperceptible; for **Export library** on a library of a few hundred songs it is a frozen screen — no scrolling, no
animation, a tap that does nothing — and then the picker appears. On a phone with a slow or nearly full flash
device, a big enough archive risks the watchdog: an app that does not return to the run loop for long enough is
killed, and the user sees Campfire quit while backing up their library.

Nothing in the UI says a thing is happening, because nothing can be drawn while the main thread is inside the
write.

## Cause

`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt:75-99`, verified at HEAD `984861e4`:

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

    override val canShare = true

    override suspend fun shareFile(file: ExportedFile): Boolean = suspendCancellableCoroutine { continuation ->
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + file.name)
        if (!file.bytes.toNSData().writeToURL(url, atomically = true)) {
            continuation.resume(false)
        } else {
```

`suspendCancellableCoroutine` does **not** change dispatcher: the block runs on whatever the caller is on. The
caller is `CampfireViewModel.save` (`presentation/src/commonMain/.../CampfireViewModel.kt:1569-1576`), reached
through `launchFileTransfer` → `viewModelScope.launch`, which on iOS is the main dispatcher. So both
`file.bytes.toNSData()` — which copies the whole array into a fresh `NSData` — and `writeToURL(…, atomically =
true)` — which writes a temporary file and renames it — happen on the main thread.

The import half of the same class already does it properly, `IosFilePicker.kt:69-72`:

```kotlin
        return withContext(Dispatchers.IO) {
            val budget = ImportBudget()
            urls.mapNotNull { it.readImportedFile(budget) }
        }
```

and `readImportedFile`'s KDoc says the rule out loud (`IosFilePicker.kt:131`): *"Blocking, so not for the main
thread."* Only the two writes never got it.

## The change

`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt`. One private suspending helper writes the
file on the IO dispatcher, and both entry points present their controller on the main thread afterwards.

```kotlin
    override suspend fun saveFile(file: ExportedFile): Boolean {
        // The picker exports a file that already exists, so the bytes go to a temporary one first.
        val url = file.writeToTemporaryFile() ?: return false
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                present(UIDocumentPickerViewController(forExportingURLs = listOf(url))) { urls -> continuation.resume(urls.isNotEmpty()) }
            }
        }
    }

    override val canShare = true

    override suspend fun shareFile(file: ExportedFile): Boolean {
        val url = file.writeToTemporaryFile() ?: return false
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val host = viewController()
                // (the guard from plan 27 goes here)
                val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
                // An iPad presents this as a popover, which needs something to point at; the whole view will do.
                controller.popoverPresentationController?.sourceView = host.view
                host.presentViewController(controller, animated = true, completion = null)
                continuation.resume(true)
            }
        }
    }
```

and, next to the file's other private helpers:

```kotlin
/**
 * The bytes on disk, which is what both the picker and the share sheet take: neither takes bytes. Null where the
 * write failed, which the caller reports as an export that did not come out.
 *
 * On [Dispatchers.IO] because a whole-library archive is megabytes and both callers are called on the main
 * dispatcher, where copying and writing it froze the app until it was done.
 */
private suspend fun ExportedFile.writeToTemporaryFile(): NSURL? = withContext(Dispatchers.IO) {
    val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + name)
    if (bytes.toNSData().writeToURL(url, atomically = true)) url else null
}
```

Interop and threading, spelled out:

- **`import kotlinx.coroutines.IO`** — the root `CLAUDE.md` rule, and already imported at the top of this file
  (`IosFilePicker.kt:21`). `Dispatchers.IO` on Kotlin/Native is a thread pool, so this is genuinely off the main
  thread.
- **`Dispatchers.Main` exists on iOS** and is the main queue; the precedent is
  `data/source/remote/implementation/src/iosMain/.../auth/SyncAuthenticator.ios.kt:56-58`, which wraps its
  presentation the same way with the KDoc *"The session has to be created and started on the main thread"*. Add the
  `kotlinx.coroutines.Dispatchers` import's `Main` usage (the `Dispatchers` import is already there).
  `withContext(Dispatchers.Main)` is what makes the presentation correct even if a future caller is not on the main
  thread; today's caller already is, so it costs nothing.
- **Memory.** `ByteArray.toNSData()` (`IosFilePicker.kt:186-191`) pins the array for the length of
  `NSData.create(bytes:length:)`, which copies; the pin is released when `usePinned` returns, and the `NSData` is an
  ordinary Obj-C object the Kotlin runtime releases. Moving the call to another thread changes none of that — the
  new memory manager has no freezing and `ExportedFile` is immutable data.
- **Cancellation.** The write itself cannot be interrupted, and `withContext` checks for cancellation on the way in
  and the way out, so a cancelled export finishes writing a temporary file and then stops — which plan 29's
  deletion cleans up. `suspendCancellableCoroutine` still covers the part where the user is looking at a picker.
- **Nothing else moves.** `viewController()`, `presentedViewController`, `presentViewController` and the delegate
  all stay on the main thread.

## Tests

None possible: `:app:ios` is a shell and untested by policy, and what is being changed is which thread a write
happens on. The manual check below is the whole of it.

## Verification

```
./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64
```

Manual, **needs the iOS simulator, and a device is worth it** (the simulator's disk is the Mac's and hides the
freeze):

1. Build a library big enough to matter: import a zip of a few hundred songs, or duplicate the demo songs until
   **Export library** produces something over ten megabytes.
2. Settings → **Export library**, and keep a finger moving on the list behind it, or watch a running animation.
   Before the change the app is frozen until the picker appears; after it, the UI keeps drawing.
3. In Xcode, the Main Thread Checker and the time profiler are the honest proof: record a trace over the export and
   confirm `writeToURL` no longer appears on thread 1.
4. Regression: the file that comes out still opens — save it to Files, then import it back into Campfire and
   confirm every song arrives.
5. Regression: **Share** a single song; the sheet still appears and the file still reaches Mail or Files.
6. Regression: cancel the export picker and confirm the app is not left waiting (the `false` path).

## Docs

`app/ios/CLAUDE.md:15` gains the rule this makes true, next to the sentence naming the two controllers:

> `IosFilePicker.kt` — the `FilePicker` actual: `UIDocumentPickerViewController` for importing,
> `UIActivityViewController` for saving and sharing.

— add: the bytes of an export are written on the IO dispatcher and only the presentation happens on the main
thread, because a whole-library archive is megabytes and the caller is the view model's main-thread scope.

Nothing else states the opposite, so nothing else becomes untrue.

## Files touched

- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt`
- `app/ios/CLAUDE.md`

## Depends on

Plan 27 if it lands first (the guard sits inside the block this plan restructures) and plan 29 if it lands after
(it deletes the file this plan's helper writes). Land in number order: 27, 28, 29. Nothing outside `:app:ios`.

## Rules

- Load the `code-style` skill before the first edit: KDoc on the new private helper, "why, not what" for the
  comments, trailing commas. No new files, so no MPL header.
- No new strings.
- `import kotlinx.coroutines.IO` for `Dispatchers.IO` — already the import in this file, and the rule in the root
  `CLAUDE.md`.
- UIKit is only ever touched on the main thread, and that is made explicit rather than assumed.
- Unit tests are the command in the root `CLAUDE.md`; this plan adds none.
- `app/ios/CLAUDE.md` is part of the change, not a follow-up.
