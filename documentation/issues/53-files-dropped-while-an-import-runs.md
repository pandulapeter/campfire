# 53 · Files handed over by the OS during an import, or while the conflicts question is up, are dropped silently

**Severity:** medium · **Area:** `:presentation` (`CampfireViewModel`)

`import()` (`CampfireViewModel.kt:948–949`) returns at `if (files.isEmpty() || _isImporting.value || pendingImportPlan != null) return`
with no message and no queue. The picker path disables its entry; the "open with" / share / drop path
(`importFiles(files: List<ImportedFile>)`, :875) has no such guard. Drop a 300-song archive on the desktop window,
then drop one more file while the bar runs: nothing happens.

## Fix

Queue rather than drop:

1. `private val importQueue = Channel<List<ImportedFile>>(Channel.UNLIMITED)`; `importFiles(files)` sends into it
   (and `importFiles(filePicker)` after the picker returns).
2. One consumer launched in `init`: `for (files in importQueue) { import(files); awaitImportSettled() }` where
   `awaitImportSettled()` is the `combine(_visibleDialog, _isImporting) { … }.first { !it }` already written in
   `importDemoLibrary` (extract it). This serializes imports and waits for the conflicts answer before the next one.
3. `import()` no longer needs the `_isImporting`/`pendingImportPlan` early return for queued callers; keep it as an
   assertion-style guard. `importDemoLibrary` and `plantDemoLibraryOnFirstRun` go through the queue too, so a demo
   import cannot race a dropped file.
4. The progress bar shows for the whole queue; no new strings needed.
