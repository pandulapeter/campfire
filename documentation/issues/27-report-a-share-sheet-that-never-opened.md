# 27 — Report a share sheet that never opened

## What the user sees

On iOS. The user exports a song, or connects Dropbox, and while that sheet is still sliding away they tap **Share**
in a song's menu. Nothing happens. No sheet, no message, no second chance — the action is spent, and the only clue
is a line in the device log that the user will never see: *"Attempt to present UIActivityViewController … which is
already presenting …"*. Tapping Share again works, so it reads as the app dropping a tap.

The same tap also leaves a copy of the exported file in the app's temporary directory, since the bytes are written
before the sheet is asked for (plan 29 is about the copies; this plan is about the sheet being refused without
anybody noticing).

Inside the app it is worse than a dropped tap: `shareFile` answers **true**, which by the `FilePicker` contract
means the file was handed over. Nothing acts on that answer today — the view model discards it for a share — so the
user-visible bug is only the silence. It is still a lie that the next caller would inherit, and the document
pickers next to it in the same file already refuse to tell it.

## Cause

`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt:87-99`, verified at HEAD `984861e4`:

```kotlin
    override suspend fun shareFile(file: ExportedFile): Boolean = suspendCancellableCoroutine { continuation ->
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + file.name)
        if (!file.bytes.toNSData().writeToURL(url, atomically = true)) {
            continuation.resume(false)
        } else {
            val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
            // An iPad presents this as a popover, which needs something to point at; the whole view will do.
            val host = viewController()
            controller.popoverPresentationController?.sourceView = host.view
            host.presentViewController(controller, animated = true, completion = null)
            continuation.resume(true)
        }
    }
```

`presentViewController` is called without asking whether the host is already presenting something, and `true` is
resumed whatever UIKit did with it. The document picker path in the same file has exactly the guard that is
missing, `IosFilePicker.kt:101-110`:

```kotlin
    private fun present(controller: UIDocumentPickerViewController, onFinished: (List<NSURL>) -> Unit) {
        val host = viewController()
        // UIKit refuses a second presentation with nothing but a log line, and the answer it would have given never
        // comes: the caller is told nothing was picked instead of waiting for good.
        if (host.presentedViewController != null) {
            onFinished(emptyList())
            return
        }
```

How the refused presentation is reachable at all, given that
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:1319-1322`
allows one file transfer at a time:

```kotlin
    private fun launchFileTransfer(block: suspend () -> Unit) {
        if (fileTransferJob?.isActive == true) return
        fileTransferJob = viewModelScope.launch { block() }
    }
```

— the guard is about two *transfers*, and the sheet that is in the way need not be one. A document picker resumes
its caller from the delegate callback, which fires before the dismissal animation has finished, so the job is over
while `presentedViewController` is still set; `ASWebAuthenticationSession` puts its own controller on the same host
while sync is being connected; and a system alert (the notification permission a sync run asks for) does the same.
Any of those, plus a tap, is the bug.

## The change

`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt` — the same guard the pickers have, with the
same reasoning, and `false` rather than `true`:

```kotlin
    override suspend fun shareFile(file: ExportedFile): Boolean = suspendCancellableCoroutine { continuation ->
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + file.name)
        if (!file.bytes.toNSData().writeToURL(url, atomically = true)) {
            continuation.resume(false)
        } else {
            val host = viewController()
            // UIKit refuses a second presentation with nothing but a log line, so a share asked for while another
            // sheet is up - or is still sliding away, which is where a picker's own callback leaves it - would
            // otherwise be reported as a share that happened.
            if (host.presentedViewController != null) {
                continuation.resume(false)
            } else {
                val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
                // An iPad presents this as a popover, which needs something to point at; the whole view will do.
                controller.popoverPresentationController?.sourceView = host.view
                host.presentViewController(controller, animated = true, completion = null)
                continuation.resume(true)
            }
        }
    }
```

Kotlin/Native interop notes: `presentedViewController` is a plain Obj-C property read, nothing to pin or free, and
this whole function runs on the caller's dispatcher, which is the main thread (`viewModelScope`); UIKit may only be
asked any of this there. See plan 28, which moves the *write* off that thread and leaves the presentation on it.

### Deliberately not resuming from `completionWithItemsHandler`

The obvious next step — resume with the handler's `completed` flag instead of with `true` — is **not** part of this
plan, and the reason belongs in the code as a comment:

- The boolean's only caller discards it for a share
  (`CampfireViewModel.kt:1573`: `isShare -> filePicker.shareFile(file)`), so a truthful "the user cancelled the
  sheet" would change nothing the user sees.
- A handler that is never called — and UIKit gives no promise about a controller that is torn down from underneath
  it — would leave the caller suspended for good, holding `fileTransferJob` and so blocking every later export,
  share and import. That is the failure the pickers' own `DocumentPickerDelegate` exists to avoid, and it is a
  worse bug than the one being fixed.
- Android's `shareFile` returns `true` unconditionally for the same reason: the chooser is fire and forget.

The handler is used in plan 29, for deleting the temporary file, where a call that never comes costs one file that
iOS purges on its own.

## Tests

None possible. `:app:ios` is a platform shell and untested by policy, and the behaviour under test is UIKit
refusing a presentation. The check is manual, below.

## Verification

```
./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64
```

Manual, **needs the iOS simulator** (a device is not required for this one):

1. Run the app from Xcode. Open a song, tap the menu, **Share**. The sheet appears — the ordinary path still works.
2. Cancel the sheet, and while it is animating away tap **Share** again immediately. Before the change: nothing
   happens. After: still nothing visible, but the log line is gone and the temporary file is not left behind (with
   plan 29 landed).
3. The case worth watching in the debugger: put a breakpoint on the new `continuation.resume(false)`, open the
   export picker, cancel it and tap Share within the dismissal animation. The breakpoint must be hit — that is the
   refused presentation, which used to be reported as a successful share.
4. Regression: export a song (**Export**, not Share) and confirm the document picker still opens and saves.
5. Regression on an iPad simulator: the share sheet still appears as a popover anchored to the app's view.

## Docs

`app/ios/CLAUDE.md:15` — the sentence names the two controllers and says nothing about what happens when one of
them cannot be presented:

> `IosFilePicker.kt` — the `FilePicker` actual: `UIDocumentPickerViewController` for importing,
> `UIActivityViewController` for saving and sharing.

Extend it with one clause: a presentation UIKit would refuse — anything already on the host, including a sheet that
is still dismissing — is answered as nothing picked and nothing shared, rather than reported as done.

`presentation/src/commonMain/.../ui/platform/FilePicker.kt` — the KDoc of `saveFile` says what `false` means, and
`shareFile` inherits it:

> False when the user dismissed it and nothing was saved.

Add that a platform which could not show anything at all also answers false. No behaviour of the caller changes;
this is the contract catching up with the implementations.

## Files touched

- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt`
- `app/ios/CLAUDE.md`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.kt` (KDoc only)

## Depends on

Nothing, but it touches the same function as plans 28 and 29. Land them in number order: 27 adds the guard, 28
restructures `saveFile`/`shareFile` around a suspending write, 29 adds the deletions. Whoever lands them out of
order rebases the sketches rather than reapplying them literally.

## Rules

- Load the `code-style` skill before the first edit: `//` comments inside statements, "why, not what", trailing
  commas. No new files, so no MPL header.
- No new strings.
- UIKit is touched on the main thread only.
- Unit tests are the command in the root `CLAUDE.md`; this plan adds none, and the shells are untested by policy.
- `app/ios/CLAUDE.md` is part of the change, not a follow-up.
