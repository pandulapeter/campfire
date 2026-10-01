# Keep the song selection and the exporting state across rotation and layout changes

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** Android mostly
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
**Challenged:** amended — the "transfer active" flag is cleared by the job that set it (a late completion handler of an earlier job must not clear a later one's), and an existing `isImporting` flow is reused rather than duplicated.

## Problem

- `var selected by remember(dialog) { mutableStateOf<Set<Int>?>(null) }` — rotating the phone (the view model keeps
  the dialog, the composition is rebuilt) replaces a hand-picked subset of a setlist with all songs.
- `var exporting by remember { mutableStateOf(false) }` — after a rotation mid-export the button is enabled again; a
  second tap silently does nothing because `exportPdf` returns when `fileTransferJob` is active. The same silent
  nothing happens when an import or another export is running.

## Fix

- `selected`: `rememberSaveable(dialog)` with a `listSaver` of ints (`Set<Int>?` saved as a list, null as absent).
- Exporting belongs to the view model: `val isExportingPdf: StateFlow<Boolean>` set around the job in `exportPdf`
  (drop the `onFinished` callback), collected by the sheet. Plan 27 replaces it with its nullable progress.
- Expose whether any file transfer is active (`isFileTransferActive`, a `StateFlow`) and disable Save while it is, so
  the button never accepts a tap it will ignore. `launchFileTransfer` sets it true before launching and clears it with
  `job.invokeOnCompletion { if (fileTransferJob === job) _isFileTransferActive.value = false }`: a plain
  `finally` is skipped by a job cancelled before it starts, and an unconditional clear from the completion handler of
  a previous, cancelling job can land after the next one has begun and leave the flag false during a live transfer.
  `isImporting` already exists for the import's own placeholders and stays as it is.

## Tests

None.

## Manual check

Android: untick three songs, rotate — still unticked. Start a long export, rotate — the button still shows progress.
Start an import and open the sheet: Save is disabled until the import ends.
