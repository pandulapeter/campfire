# 30 — Read files opened in place with a file coordinator

## What the user sees

On iOS. The user keeps their songs in iCloud Drive. They open one with Campfire — from the Files app, or from
another app's share sheet, or by tapping a `.cho` attachment. iOS hands Campfire the original file where it is,
because the app says it can do that. If that file is not on the device right now — iCloud has evicted it to save
space, which it does to anything not opened in a while, and which the Files app shows as a little cloud — the read
comes back empty and the user is told the file was **skipped**. Nothing says why. Tapping the file in the Files app
first (which downloads it) and then opening it with Campfire works, which makes the failure look random.

The same shape of failure happens to a file another app is in the middle of writing: the read gets half a song, or
nothing.

## Cause

`app/ios/iosApp/iosApp/Info.plist:49-50`, verified at HEAD `984861e4`:

```xml
	<key>LSSupportsOpeningDocumentsInPlace</key>
	<true/>
```

which is what makes iOS hand over the user's own URL instead of a copy — deliberate, and documented in
`app/ios/CLAUDE.md:23`. The read that follows is uncoordinated,
`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt:133-153`:

```kotlin
@OptIn(ExperimentalForeignApi::class)
internal fun NSURL.readImportedFile(budget: ImportBudget): ImportedFile? {
    val isAccessible = startAccessingSecurityScopedResource()
    return try {
        val size = path?.let { NSFileManager.defaultManager.attributesOfItemAtPath(it, error = null) }?.get(NSFileSize) as? NSNumber
        budget.read(name = lastPathComponent.orEmpty(), size = size?.longLongValue) { limit ->
            // Mapped rather than loaded, so that a file whose attributes said nothing still gives its length away
            // before any of it is copied into the heap. Longer than the limit, it only has to say so, which the
            // budget takes one byte past the limit to mean.
            val data = NSData.dataWithContentsOfURL(this, options = NSDataReadingMappedIfSafe, error = null)
            if (data == null) {
                println("Could not read \"$this\".")
            }
            data?.let { if (it.length.toLong() > limit) ByteArray(limit.toInt() + 1) else it.toByteArray() }
        }
    } finally {
        if (isAccessible) {
            stopAccessingSecurityScopedResource()
        }
    }
}
```

`startAccessingSecurityScopedResource` buys the *right* to read the file. It does not make the file be there.
Apple's rule for a URL that belongs to another process's document store — which every iCloud Drive and File
Provider item is — is that reads and writes go through `NSFileCoordinator`: that is what materializes an evicted
file, and what waits for a writer that is mid-save. An uncoordinated `dataWithContentsOfURL` on an evicted item
answers nil, which this function turns into `null`, which the import reports as a skipped file
(`ImportedFile.unread` is not even reached here — the file is dropped by `mapNotNull`, and the open-in-place path
in `IosFileImport.kt:61` substitutes `ImportedFile.unread` so that the user is at least told something was
skipped).

The function is shared by both ways a file arrives: the picker's copies (`IosFilePicker.pickFiles`, which are the
app's own files in its own container, where coordination is cheap and unnecessary) and the file opened in place
(`IosFileImport.openUrl`, which is the user's original and where it is required).

## The change

`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt` — wrap the whole read, attributes included,
in one coordinated read. One code path for both callers: coordinating a local copy costs a function call.

```kotlin
/**
 * Null when the file cannot be read … (existing KDoc, plus:)
 *
 * Read through an `NSFileCoordinator`, because `LSSupportsOpeningDocumentsInPlace` means a file opened with
 * Campfire is the user's own where it lies - an iCloud Drive song that may not be on this device at all, or one
 * another app is in the middle of writing. The coordinated read is what downloads the first and waits for the
 * second; an uncoordinated one simply answers nothing, and the user is told their song was skipped. Blocking for
 * as long as that takes, so not for the main thread - doubly so, since a coordinated read asked for on the main
 * thread can deadlock against a file presenter that answers on it.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun NSURL.readImportedFile(budget: ImportBudget): ImportedFile? {
    val isAccessible = startAccessingSecurityScopedResource()
    return try {
        var file: ImportedFile? = null
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(
                url = this@readImportedFile,
                options = 0uL,
                error = error.ptr,
            ) { coordinated ->
                // The coordinator may hand over a different URL - a snapshot it made of a file that is being
                // written - and everything has to be read from that one, and before this block returns, since it
                // is only the app's for as long as the block runs.
                val url = coordinated ?: this@readImportedFile
                val size = url.path?.let { NSFileManager.defaultManager.attributesOfItemAtPath(it, error = null) }?.get(NSFileSize) as? NSNumber
                file = budget.read(name = lastPathComponent.orEmpty(), size = size?.longLongValue) { limit ->
                    // Mapped rather than loaded, so that a file whose attributes said nothing still gives its
                    // length away before any of it is copied into the heap. Longer than the limit, it only has to
                    // say so, which the budget takes one byte past the limit to mean.
                    val data = NSData.dataWithContentsOfURL(url, options = NSDataReadingMappedIfSafe, error = null)
                    if (data == null) {
                        println("Could not read \"$url\".")
                    }
                    data?.let { if (it.length.toLong() > limit) ByteArray(limit.toInt() + 1) else it.toByteArray() }
                }
            }
            // A coordination that was refused - a file that could not be downloaded, a writer that never finished -
            // says why, which is the one thing the old nil never did.
            error.value?.let { println("Could not read \"${this@readImportedFile}\": ${it.localizedDescription}") }
        }
        file
    } finally {
        if (isAccessible) {
            stopAccessingSecurityScopedResource()
        }
    }
}
```

Kotlin/Native interop, spelled out:

- **Imports**: `platform.Foundation.NSFileCoordinator`, `platform.Foundation.NSError`,
  `kotlinx.cinterop.ObjCObjectVar`, `kotlinx.cinterop.alloc`, `kotlinx.cinterop.memScoped`, `kotlinx.cinterop.ptr`,
  `kotlinx.cinterop.value`. The file already opts in to `ExperimentalForeignApi` on this function.
- **`NSFileCoordinator(filePresenter = null)`** — the designated initializer. Campfire registers no
  `NSFilePresenter`, so there is nothing to exclude from the coordination.
- **The accessor block is synchronous.** `coordinateReadingItemAtURL` calls it on the calling thread and returns
  after it. That is what makes writing to a captured `var` correct here: no thread hops, no freezing (the Kotlin/
  Native memory manager has none), and the value is there when the call returns. `budget.read` is an `inline`
  function, so it inlines into the block like any other lambda.
- **`options = 0uL`** — no reading options: not `WithoutChanges`, because pending changes by the file's owner are
  exactly what has to be waited for, and not `ImmediatelyAvailableMetadataOnly`, which is the opposite of
  downloading an evicted file. `NSFileCoordinatorReadingOptions` is an unsigned integer typealias; if `0uL` does not
  type-check on the toolchain's Darwin klib, `NSFileCoordinatorReadingOptions(0)` is the same value.
- **Bytes are copied inside the block.** `toByteArray()` (the file's own private extension) copies out of the
  `NSData`, so no mapping outlives the coordination — which is required, since the URL may be a snapshot.
- **The name stays the original's.** `lastPathComponent` is read from `this@readImportedFile`, not from the
  coordinated URL, so the import reports the file the user picked rather than a snapshot's name.
- **Privacy manifest**: unchanged. `NSFileCoordinator` is not a required-reason API, and the file size read that is
  already declared under 3B52.1 (`app/ios/CLAUDE.md:27`) is the same call as before.

What this costs: `openUrl` reads on `Dispatchers.IO.limitedParallelism(1)` (`IosFileImport.kt:42`), so a large
evicted file downloading holds up the files queued behind it. That is the right trade — those files are being
imported one after another anyway, and the alternative is what happens today, which is reporting them as skipped.

## Tests

None possible. `:app:ios` is a shell and untested by policy, and what is under test is iCloud's behaviour. This is
the plan in this lane whose verification carries the most weight, and it needs real hardware.

## Verification

```
./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64
```

Manual, **needs a real device signed into iCloud with iCloud Drive on** (the simulator does not evict files, so it
cannot reproduce the bug — it can only show the regression checks):

1. Put a `.cho` song in iCloud Drive from the Mac. On the iPhone, open the Files app, long-press the file and
   choose **Remove Download** so that it shows the cloud badge.
2. Long-press it again → **Share** → **Campfire** (or **Open in Campfire**). Before the change: "1 file was
   skipped". After: the song is imported and opened.
3. Repeat with a zip archive in iCloud Drive, evicted the same way.
4. The mid-write case, best effort: start writing a large `.cho` to iCloud Drive from the Mac and open it with
   Campfire while it is still syncing. It must either import the finished file or report a failure — never a
   truncated song. (This one is hard to hit on purpose; the coordinated read is the reason it is safe, not
   something the check can prove.)
5. Regression, on the device or the simulator: import two local files through **Import**; both arrive.
6. Regression: open a `.cho` from "On My iPhone → Campfire" — a file in Campfire's own library folder, opened in
   place — and confirm it still imports (as a duplicate that the import disregards, which is the correct outcome)
   and that the original is untouched.
7. Regression: AirDrop a song to the phone and open it with Campfire; it still arrives and its `Documents/Inbox`
   copy is still deleted.
8. Watch the Xcode console for `Could not read` lines: with the change, a failure now prints the coordinator's
   `localizedDescription` instead of nothing.

If step 2 still fails on a device, the next thing to try is
`NSFileManager.defaultManager.startDownloadingUbiquitousItemAtURL(url, error)` before the coordinated read, and
waiting for it — the coordinated read is the documented way and should be enough, so try it alone first.

## Docs

`app/ios/CLAUDE.md:16` — the sentence about files opened in place describes the path and not the way it is read:

> A file that came from AirDrop, Mail or Messages is a copy iOS leaves in `Documents/Inbox` — visible in the Files
> app because of `UIFileSharingEnabled` — and it is deleted once it has been read, and only there, since a file
> opened in place is the user's original (possibly one in the library itself).

Add: because it is the user's original, it is read through an `NSFileCoordinator` — an iCloud Drive song may not be
on the device at all, and a coordinated read is what fetches it instead of reporting it as skipped.

`app/ios/CLAUDE.md:23`, the paragraph on `LSSupportsOpeningDocumentsInPlace`, is where the obligation belongs in one
clause: claiming to open documents in place is claiming to read them the way their owner expects.

## Files touched

- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt`
- `app/ios/CLAUDE.md`

## Depends on

Nothing, but it rewrites `readImportedFile`, which plan 29 calls from a slightly different place (the deletion of
the picked copy is added around the call, not inside it), so the two touch neighbouring lines. Land in number
order or rebase.

## Rules

- Load the `code-style` skill before the first edit: the KDoc gains a paragraph rather than a rewrite, `//`
  comments inside the statements, "why, not what", trailing commas. No new files, so no MPL header.
- No new strings.
- `commonMain` is untouched; this is `iosMain` and may use Foundation freely.
- Unit tests are the command in the root `CLAUDE.md`; this plan adds none and says why.
- `app/ios/CLAUDE.md` is part of the change, not a follow-up.
