# Show the export's progress, let it be cancelled, close the sheet when it is saved, and report every failure

**Kind:** usability / bug  ·  **Severity:** high  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRenderer.kt` (`pdf`: a trailing `onPage: (done: Int) -> Unit = {}` parameter), both `strings.xml`
**Challenged:** amended — cancel and flush hook `setVisibleDialog`, not `dismissSheet`; Cancel exists only while pages are rendered (cancelling under a live Android picker orphans it and the file is written anyway); the saved event names its sheet and follows the app's `MutableSharedFlow(extraBufferCapacity = 1)` pattern; wasm cannot catch an out-of-memory trap; `onPage` is worded to survive lane A plan 20 and plans 17–19.

Runs after plan 26.

## Problem

- A long export (web: an estimated 10–30 s for 100 pages) shows a 20 dp spinner in a text button and nothing else.
- It cannot be cancelled. Dismissing the sheet leaves the job running in `viewModelScope`; the system's save dialog
  then appears over whatever screen the user has moved to.
- After a successful save the sheet stays open with no confirmation — did it save?
- `save()` catches `Exception`; an `OutOfMemoryError` from `create()` (plausible for a large setlist on a small-heap
  phone until plans 17–19 land, and still possible after) is not one, escapes `viewModelScope.launch` and crashes the
  app instead of sending `Message.ExportFailed`.

## Fix

- `launchFileTransfer` returns the `Job` it started (today `Unit`); `exportPdf` keeps it as `pdfExportJob`.
  `pdfExportProgress: StateFlow<PdfExportProgress?>` (`done`, `total` pages; null = not rendering) **replaces plan
  26's `isExportingPdf`** (`!= null` is the same fact); it is set to `done = 0` when the job starts and set to null as
  soon as `create()` has returned, before `save` calls the picker, and in a `finally`. `PrintRenderer.pdf` gains a
  trailing defaulted `onPage` parameter called with the running count after each page is added — after whatever
  title parameter lane A plan 20 gave it and inside whatever loop plans 17–19 leave, so describe the call by intent
  rather than by line. The sheet's call passes `onPage = { progress.value = ... }` and keeps plan 20's title argument.
- The sheet shows a determinate `LinearProgressIndicator` with "Page %1$d of %2$d" (reuse `print_page`) above the
  action row, and the Save button becomes **Cancel** (existing `cancel` string) **only while `pdfExportProgress !=
  null`**. After the bytes are ready the system picker is up, Save stays disabled by plan 26's `isFileTransferActive`,
  and there is no Cancel: on Android, cancelling the coroutine under a showing picker leaves the picker up and its
  late result is treated as an orphan (`onSaveLocationPicked` with no continuation writes the kept copy to the chosen
  location anyway), so a Cancel there would not cancel.
- Cancelling and the flush of plan 22 hook **`setVisibleDialog`**: when `previousDialog is DialogType.PrintExport` and
  the new dialog is not that one, cancel `pdfExportJob` if `pdfExportProgress != null`. `dismissSheet` reaches it via
  `dismissDialog`, but the web's Back, Escape, `navigateBack` and another dialog replacing the sheet do not pass
  through `dismissSheet`. Once the picker is up the job is left to finish.
- On a successful save signal the sheet to close. The app's one-shot pattern is a
  `MutableSharedFlow(extraBufferCapacity = 1)` exposed `asSharedFlow()` (`scrollToTopRequests`,
  `editorRevertRequests`, `editorTextEdits` in `CampfireViewModel`); add `printExportSaved` of
  `DialogType.PrintExport`, emitted with `tryEmit` from `save`'s `onSaved`, and carrying the dialog the export was
  started for. The sheet collects it in a `LaunchedEffect` **inside the `CampfireBottomSheet` content lambda** (where
  `close()` exists; it is not reachable from `actions` or from outside the sheet) and calls `close()` only when the
  event's dialog equals its own, so a late save from a sheet that was dismissed cannot close the next one opened. An
  event dropped while the Activity is being recreated just leaves the sheet open, which is harmless. `onSaved` is
  not called for a share (plan 28) or for a picker the user cancelled, so those leave the sheet open.
- Around `create()`: `catch (throwable: Throwable)` → rethrow `CancellationException`, otherwise log and
  `sendMessage(Message.ExportFailed)` (the Sync and cover-art code catch `Throwable` the same way, with the reason
  in their KDoc). This covers `OutOfMemoryError` on Android and desktop. It does not cover Kotlin/Wasm, where running
  out of memory is a trap that no handler sees (`Inflater.kt` says the same of the import); do not promise more in the
  KDoc.

## Tests

None.

## Manual check

Export a 60-song setlist: progress advances, Cancel stops it promptly, closing the sheet mid-way (button, Escape,
web Back) shows no save dialog afterwards, a finished save closes the sheet. Android: the picker cancelled by Back
leaves the sheet open, with Save enabled again.
