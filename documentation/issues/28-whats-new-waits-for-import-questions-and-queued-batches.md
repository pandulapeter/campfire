# Keep "What's new" from opening over an import question or before a queued import has started

**Challenged:** amended — the counter is changed with `update { }` (enqueueImport is public-facing and may be called off the main thread by a platform shell), its decrement is pinned to the consumer's existing `finally` so a cancelled preparation (27-let-the-user-cancel-an-import-while-it-is-being-prepared.md) or a failed import cannot leak it, and presentation/CLAUDE.md is named.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`, `presentation/CLAUDE.md`
(the `CampfireViewModel.kt` bullet, only if it describes what What's new waits for), a new
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/WhatsNewGate.kt`, a new
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/WhatsNewGateTest.kt`

## Problem
Root `CLAUDE.md`: What's new opens "after the app is on screen and startup import questions have finished".
`showWhatsNewOnVersionChange()` waits only for
```kotlin
combine(_visibleDialog, _isImporting) { dialog, isImporting -> dialog == null && !isImporting }.first { it }
if (!_visibleDialog.compareAndSet(null, DialogType.WhatsNew)) return
```
`_isImporting` is false while an `ImportReport.Review` question is open (`import()` sets `_isImporting.value = false`
right after `showImportReport(...)`), and `_importReport` is not looked at at all. `showImportReport` itself only puts
the import screen on the back stack once `_visibleDialog == null`, so the sequence "file opened with the app at launch,
conflicts found, report set but not yet pushed, What's new claims the dialog slot" is possible, and the question is
then pushed behind/after the dialog. A batch that is still in `importQueue` (opened with the app a moment ago, not yet
taken by the consumer, so `_isImporting` is still false) is not seen either.

## Fix
1. Add `private val _queuedImportCount = MutableStateFlow(0)`: `_queuedImportCount.update { it + 1 }` in
   `enqueueImport` just before `importQueue.trySend(request)` (only for a non-empty batch, which is the only kind
   queued), and `_queuedImportCount.update { it - 1 }` in the consumer's existing
   `finally { request.settled.complete(Unit) }`. The `finally` is what keeps the count honest whatever `import()` did:
   returned after a Cancel of the preparation, failed, or left a question that was answered or abandoned. The first-run
   demo import calls `import()` directly and is not counted, which is right: What's new never opens on a first run.
2. Extract a pure function in `WhatsNewGate.kt`:
   ```kotlin
   internal fun canShowWhatsNew(hasDialog: Boolean, isImporting: Boolean, hasImportReport: Boolean, queuedImportCount: Int) =
       !hasDialog && !isImporting && !hasImportReport && queuedImportCount == 0
   ```
3. Use it, in `showWhatsNewOnVersionChange()`, with a four-way `combine(_visibleDialog, _isImporting, _importReport, _queuedImportCount) { ... -> canShowWhatsNew(dialog != null, isImporting, report != null, count) }.first { it }`;
   keep the `compareAndSet`. A normal launch with nothing queued sees all four clear at once and opens it as today; an
   import screen showing a finished result (from a startup batch or a snackbar's Details) also holds it back until left.
4. Residual race (a file the system has not yet handed to the view model when the check passes) is inherent and not worth
   more; do not add timers.

## Tests
`WhatsNewGateTest`: true only when all four are clear; false for each of the four on its own (a Review question is
`hasImportReport = true, isImporting = false`).

## Manual check
Set the installed version to a new one with a non-empty `whats_new_message`, put a song into the library, then launch
the app by opening a *different* file with the same name via "open with": the conflicts question appears and What's
new waits until the import screen has been left.
