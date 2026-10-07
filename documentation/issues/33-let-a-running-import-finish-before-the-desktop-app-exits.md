# Let a running import finish before the desktop process ends, and keep Escape from going back or offering to close behind the import's progress dialog

**Challenged:** amended — (b) breaks `SingleInstance.kt`'s invariant that `CLOSING_INSTANCE_WAIT_MILLIS` (30 s) exceeds the whole exit wait, so a second launch during a 30 + 15 + 2 s exit would start next to a process still writing the import: the plan now raises it to 60 s with its KDoc, the companion's KDoc and `app/desktop/CLAUDE.md`'s "30 s"; the leaving check's placement, the main-thread confinement, the conflicts question, the follow-up sync run, the macOS logout and the ordering against plan 32 are spelled out.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** desktop (the Escape half); desktop and the macOS quit (the exit half)
**Files:** `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireDesktopApp.kt`
(`handleKeyEvent`), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
(`settleSynchronizationBeforeExit`, the import queue's consumer, a new `EXIT_IMPORT_GRACE`), `app/desktop/CLAUDE.md`
(the paragraph on `leave` / `settleSynchronizationBeforeExit`, and the single instance paragraph's "30 s - longer than
the 15 + 2 s"), `app/desktop/src/main/java/com/pandulapeter/campfire/SingleInstance.kt` (`CLOSING_INSTANCE_WAIT_MILLIS`
and its KDoc), `app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt` (the comments on
`leave` and the quit handler only), `presentation/CLAUDE.md` (the `CampfireDesktopApp.kt` bullet, for the Escape rule)

**Lane order:** after plan 32, which rewrites the same paragraph of `app/desktop/CLAUDE.md` and the same
`handleKeyEvent` KDoc neighbourhood; apply this one on top of its wording.

## Problem

The import's progress dialog is not a `visibleDialog`. `dialogs/Dialogs.kt` hosts it apart from the others:

```kotlin
ImportProgressDialogHost(
    progress = importProgress.takeIf { importReport == null },
    canShow = visibleDialog == null,
    onCancel = viewModel::cancelImportPreparation,
)
```

and `ImportProgressDialog.kt` draws it as a Material `AlertDialog(onDismissRequest = {}, …)` saying
"Please keep Campfire open until the import finishes." (`import_progress_wait`).

**Escape reaches the window handler while it is up.** In Compose Desktop 1.12 (`ComposeSceneMediator.onKeyEvent`) a key
goes to the window's `onPreviewKeyEvent`, then the scene (the focused dialog layer, whose content — a Cancel
`TextButton` and text — does not consume Escape), then the window's `onKeyEvent`, and only then to
`navigationEventInput`, which is where a dialog's back handling lives. So `handleKeyEvent` sees the Escape, and its
only modal check is

```kotlin
if (visibleDialog.value != null || isAnyOverflowMenuOpen) return false
```

which is false here. On a screen deeper than the root it pops the back stack behind the modal progress dialog; on the
root screen it calls `confirmExit(onExit)`, `DialogType.ConfirmExit` becomes the `visibleDialog`, `canShow` turns false
and the progress dialog disappears under the question. Close (`exitConfirmed` → `requestExit` → the shell's `leave`)
hides the window and runs `settleSynchronizationBeforeExit`, which waits for a save, the waiting preferences and a sync
run — not for the import — and then `exitApplication` ends the process. With sync off that is immediate. The window's
close button and Cmd+Q / the macOS quit take the same `requestExit` path and always did.

**What a kill mid-import leaves.** Every write is atomic (`JvmFileStorage.writeAtomically`: a temporary file, `force`,
then `ATOMIC_MOVE`), so no file is corrupt, but `ImportFilesUseCaseImpl` writes "Songs first, setlists second", so the
library keeps some of the batch's songs and none of its setlists, and with *Replace* answered only some library files
are replaced. The import screen and its report are never shown. `applyImportPlan` itself says it should not happen:
it runs the writing `withContext(NonCancellable)` because "the view model going away … is no reason to leave an
archive half imported - its setlists come after all of its songs". Re-importing repairs it (identical songs are
disregarded), but nothing tells the user to. A sync run that starts during the exit wait may also carry the half
import to the cloud folder.

## Fix

Options:

- **(a) Escape treats a running import as a modal.** In `handleKeyEvent`, right after the `visibleDialog` /
  `isAnyOverflowMenuOpen` check, `if (importProgress.value != null && importReport.value == null) return true` —
  the same condition the dialog host shows it under — consuming the key so that it neither goes back nor asks to
  close (returning `false` would let Navigation 3 pop the back stack during the 400 ms before the dialog appears).
  Fixes the Escape paths only; the window's close button and the macOS quit still end the import.
- **(b) The exit waits for the import, bounded.** At the start of `settleSynchronizationBeforeExit` (before
  `writeWaitingPreferences` and the sync wait, so the run that follows carries the whole import): stop the import
  queue's consumer from taking another batch, call `cancelImportPreparation()` — nothing has been written while the
  preparation runs — and then `withTimeoutOrNull(EXIT_IMPORT_GRACE) { isImporting.first { !it } }` with
  `EXIT_IMPORT_GRACE = 30.seconds`. Covers every way out, the window hidden meanwhile as for sync. The details:
  - **The leaving flag** is a private `var isLeaving = false` in the view model, set first thing in
    `settleSynchronizationBeforeExit` and never cleared (the shell's `leave` only calls it once the decision is final).
    The consumer checks it **after** the `isDeletingLibrary` wait and immediately before `import(request)`, and on
    `true` skips the import (`continue`, so the existing `finally` still counts the batch down and completes
    `settled`); checked any earlier, a batch that waited out a library deletion would start after the exit began.
    `import` sets `_isImporting` synchronously before its first suspension, so there is no gap between the check and
    the flag the wait reads. The demo library's import (`importDemoLibrary`, outside the queue) claims `_isImporting`
    the same way and is waited for like any other.
  - **Threading:** `leave` runs this in the application's `rememberCoroutineScope`, on the Swing event thread, which is
    the same thread `viewModelScope`'s `Dispatchers.Main` is on the desktop, so `preparation` and
    `isPreparationCancelled` stay confined to the main thread as their KDoc requires, and a Cancel that lands after the
    preparation finished is still caught by the existing post-`await` check.
  - **A conflicts question that is up** (`pendingImport`, `ImportReport.Review`) has written nothing, and `import` sets
    `_isImporting` to false as it shows it, so the wait returns at once rather than waiting thirty seconds for an
    answer nobody will give; the question is simply dropped. An answer already given (`resolveImport`) claims
    `_isImporting` before the question goes and is waited for.
  - **The sync run the import asks for** (every written file schedules one ten seconds later) is not waited out:
    the sync half that follows starts the waiting run at once (`startScheduledSynchronization`), which is why the import
    is waited for first. The worst case is 30 + 15 + 2 = 47 s with the window hidden; the user sees nothing in that
    time, as for sync today, and a few hundred songs write in a few seconds, so the bound is only ever reached by a
    stalled disk.
  - **The second instance:** `SingleInstance.kt`'s `CLOSING_INSTANCE_WAIT_MILLIS` (30 s) is documented to exceed
    everything a quit may still take after the window is gone; past it a newcomer starts *next to* the closing process
    ("starting next to it"), which would read the library while this one still writes the import — the very thing the
    lock is for. Raise it to `60_000L` and name `EXIT_IMPORT_GRACE` in its KDoc next to `EXIT_SYNC_GRACE` and
    `EXIT_SYNC_STOP_GRACE`; extend the companion's KDoc on `EXIT_SYNC_GRACE` ("has to stay above this and
    [EXIT_SYNC_STOP_GRACE] together") to the three graces; and change `app/desktop/CLAUDE.md`'s single instance
    paragraph to "waits up to 60 s - longer than the 30 + 15 + 2 s a quit gives a running import and a sync run in
    `settleSynchronizationBeforeExit` -". The newcomer prints "Waiting for the closing instance to exit." meanwhile.
  - **Only the desktop is touched:** `settleSynchronizationBeforeExit` is in `commonMain` but only `:app:desktop`'s
    `leave` calls it, and the leaving flag is set nowhere else, so Android, iOS and the web behave as before.
  - **OS logout:** on macOS a logout, restart or shut down reaches the quit handler, whose `performQuit` now comes up to
    47 s later instead of 17 s; update the quit handler's comment ("tolerates the few seconds") accordingly, and check
    it by hand (below). A Windows or Linux session ending does not go through `onCloseRequest` or `leave` (the JVM is
    ended by the system), so nothing changes there.
- **(c) Leave it**: closing during an import is rare, deliberate for the close button and quit, and repaired by
  importing again.

**Recommended: (a) and (b) together.** They cover different paths: (a) is the only one that stops Escape popping
screens behind the progress dialog, and it makes the accidental route — an Escape too many, which the confirmation
exists for — not reach the exit at all; (b) is the only one that keeps the deliberate exits from breaking the
promise the dialog makes and the invariant `applyImportPlan` documents. Both are a few lines. Update
`presentation/CLAUDE.md`'s `CampfireDesktopApp.kt` bullet ("What is open on top is `visibleDialog` plus
`isAnyOverflowMenuOpen` …" gains the running import), `app/desktop/CLAUDE.md`'s account of `leave` (a running
import is let finish, up to thirty seconds, before the sync wait) and its single instance paragraph (60 s, see above).

(a)'s guard returns `true` only while the dialog host would show the progress dialog: not under the import screen
(`importReport != null`, where Escape leaves the screen and the import carries on to a snackbar, as today), not while
a question is up (progress is null then), and not for an import that announces nothing (the first run's demo library,
which has no dialog). It sits after the `visibleDialog` / `isAnyOverflowMenuOpen` check, so a dialog over the progress
dialog still gets its Escape. Ctrl / Cmd + F, the zoom keys and Space / M are handled earlier in `handleKeyEvent` and
are left as they are (out of scope; Ctrl / Cmd + F behind the progress dialog is a separate, cosmetic matter).

## Tests

None in `commonTest`: both changes are view-model and key-handler wiring around coroutines and Compose state, with no
pure helper worth extracting.

## Manual check

On the desktop build, with a large import (a SongbookPro backup or a zip of a few hundred songs) so that the progress
dialog stays up:

1. On the Songs screen press Escape while the dialog shows: nothing happens, the dialog stays. Open a song, start the
   import by dropping the file onto the window, press Escape: the song stays open.
2. Close the window during the "Importing" phase: the window goes at once, and on the next launch every song and
   every setlist of the batch is in the library.
3. Close it during "Reading": on the next launch nothing of the batch is there, and nothing is reported.
4. On macOS, Cmd+Q during "Importing" behaves as 2.
5. Start a second Campfire while the first is still closing after step 2: it waits ("Waiting for the closing instance
   to exit." in its console) and opens once the first is gone, with the whole batch in the library.
6. On macOS, log out during "Importing": the logout completes without macOS reporting that Campfire interrupted it,
   and the next launch has the whole batch.
7. Answer nothing to a conflicts question and close the window: the process ends at once (no thirty-second wait).
