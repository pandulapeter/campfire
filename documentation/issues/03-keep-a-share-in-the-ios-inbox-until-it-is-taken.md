# Keep a share in the iOS inbox until the app has taken it, not only read it

**Challenged:** amended — picked option A and specified it: "taken" is the import request's `settled` (with plan 01
merged, a text's form settles when it is closed, so no separate "form shown" signal and a form lost to process death is
offered again); the drain must hold a `Mutex`, since `inboxScope`'s `limitedParallelism(1)` does not serialize across
suspension and a second `.active` would hand the waiting share over twice; the contract into `:presentation` is spelled
out (return values plus one iOS-only flow), so it is no longer a stop-and-report.

**Kind:** bug (data loss)  ·  **Severity:** medium  ·  **Platforms:** iOS
**Depends on:** plan 01 (merged first, lane S): it makes a text request settle only once its New song sheet has left.
**Files:** `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosShareInbox.kt`,
`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/CampfireViewController.kt`,
`presentation/src/iosMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireIosApp.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportController.kt` (`importFiles(files)`),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/PastedSongController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (the `enqueueImport` lambda),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/PastedSongControllerTest.kt` (its fake),
`app/ios/CLAUDE.md` (Share extension), `presentation/src/iosMain/CLAUDE.md` if it describes `CampfireIosApp`'s parameters

## Problem

`importShare` (`IosShareInbox.kt`) at HEAD (`51d66ea99`; unchanged since `4bde19afe`) deletes a share as soon as its
bytes are in a channel:

```kotlin
if (files.isNotEmpty()) pendingImports.send(files)
if (texts.isNotEmpty()) pendingSharedTexts.send(SharedTextPayload(texts = texts, subject = manifest.subject))
fileManager.removeItemAtPath(folderPath, error = null)
```

and `drainShareInbox` does this for every waiting share in one pass. The import behind those channels waits for the
user (every file import is reviewed; a text becomes the New song sheet; several shares queue one after another). The
extension told the user "Saved to Campfire" for each. Scenario: three PDFs shared over the day, Campfire opened, the
first review shows; the user switches away and iOS jettisons the process (or they swipe it away). Every share not yet
answered is gone from Campfire and the user has no reason to share again.

## Fix

Drain one share at a time and delete each only once the app has taken it. "Taken" is the `settled` deferred
`ImportController.enqueueImport` already returns: completed when the batch is reviewed and written, abandoned,
cancelled, failed, skipped because the desktop is leaving, or — after plan 01 — when its New song sheet has left.
A share answered with a snackbar alone (links, blank, too large) is taken at once.

**`:presentation` (return values, no new behaviour elsewhere):**
- `ImportController.importFiles(files: List<ImportedFile>)` returns `enqueueImport(...)`'s `CompletableDeferred<Unit>`.
  `CampfireApp`'s `filesToImport.collect(viewModel.importController::importFiles)` keeps compiling (a reference to a
  non-Unit function is adapted); use a lambda if not.
- `PastedSongController`: the `enqueueImport` constructor parameter returns `Deferred<Unit>` (the view model's lambda
  already ends in `importController.enqueueImport(...)`); `newSongFromText` and `importSharedText` return `Deferred<Unit>`,
  `CompletableDeferred(Unit)` for every branch that only sends a message. `newSongFromClipboard` ignores it. Update the
  test fake to return `CompletableDeferred(Unit)`.
- `presentation/src/iosMain/.../CampfireIosApp.kt`: replace the `sharedTexts` parameter with an iOS-only
  `inboxShares: Flow<InboxShare>`, a small class in the same package: `files: List<ImportedFile>`,
  `texts: SharedTextPayload?`, `taken: CompletableDeferred<Unit>` (the extension writes either files or texts, never
  both: "files beat text in one share"). Collect it in a `LaunchedEffect` and, per share, call
  `viewModel.importController.importFiles(files)` or `viewModel.pastedSongController.importSharedText(texts)` and
  complete `taken` from the result with `invokeOnCompletion { share.taken.complete(Unit) }` — not by awaiting in the
  effect, whose scope can go with the composition while the queue (view model) carries on. Pass `sharedTexts = emptyFlow()`
  (or nothing) to `CampfireApp`; Android keeps its `sharedTexts`.

**`:app:ios`:**
- `IosShareInbox.kt`: one `Channel<InboxShare>` replaces `pendingSharedTexts` (shares stop going into `pendingImports`,
  which stays `openUrl`'s). `importShare` sends the share with a fresh `CompletableDeferred`, awaits it, then removes the
  folder; a share with neither files nor texts is removed at once, as today; a manifest it cannot read is removed, one
  from a newer version left, as today.
- Wrap the body of `drainShareInbox`'s coroutine in a top-level `Mutex().withLock { … }`. `inboxScope` is
  `Dispatchers.IO.limitedParallelism(1)`, which limits threads, not coroutines: while one drain is suspended in
  `taken.await()`, the next `.active` or Darwin notification starts another that lists the same, not yet deleted,
  folder and hands it over a second time. Under the lock the later drain re-lists once the first is done and finds only
  what is still there. (Coalescing the queued drains is optional.)
- `CampfireViewController.kt`: pass the new flow as `inboxShares`.
- Update `drainShareInbox`'s KDoc ("hands every finished share … and deletes it" → one at a time, deleted once taken)
  and its ordering claim: an "Open with" arriving while a share waits for its answer now goes ahead of the shares not
  yet handed over. `app/ios/CLAUDE.md`'s Share extension bullet ("Files go to `pendingImports` … texts to
  `sharedTextsToImport`"): a share is handed over one at a time and deleted once the app has taken it, so a process
  ended before that offers it again.

Accepted consequences: between one share settling and the next being read, `queuedImportCount` is briefly zero, so What's
new may come up between two shares (the next one's review or form then waits for it). A process ended between a write
and the folder's removal offers the share again; its import plans it against what was written (an identical file is
skipped; a text whose form was answered with other values opens the form again) — a narrow window, preferred to loss.
Shares stay in the inbox longer, so the extension's bound (20 shares, 64 MB) is reached sooner while a review is
unanswered.

## Tests

`:app:ios` has no test source set and the inbox is file system plus Darwin notifications. `PastedSongControllerTest`:
`importSharedText` of links only returns a completed deferred; of one song returns the fake's deferred.

## Manual check

On the simulator: share two PDFs through the extension, open Campfire, kill it from the switcher while the first review
shows, reopen: the first is offered again, then the second; answer both, reopen: nothing is offered. Share two texts,
open Campfire, kill it with the first New song sheet up, reopen: the sheet comes back.
