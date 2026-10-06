# Settle the stored editor draft before the desktop process ends, so a discarded text cannot come back

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** desktop (Windows, Linux, macOS)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt,
presentation/CLAUDE.md

## Problem

On the desktop the window losing focus is `ON_PAUSE`, so switching to another window while editing stores the unsaved
text as `preferences/editor-draft.json` (`onAppPaused`, `CampfireViewModel.kt:2140-2149`). Removing it later is
asynchronous: the collector at `:1326` (`hasUnsavedEditorChanges.collect { if (!it && !hasUnsavedEditorText())
storeEditorDraft(null) }`) runs once `hasUnsavedEditorChanges` (a `combine(...).asState(false)`) has caught up.

Quitting with **Discard** (8ee010b36):
```kotlin
fun leaveEditorWithoutSaving() {                       // :2229
    val exit = takePendingExit()
    leaveEditor()                                      // _editorDraft = null; the stored draft is removed only later
    exit?.let { requestExit(onExit = it.exit, onCancelled = it.onCancelled) }
}
```
`requestExit` (`:1754`) calls `onExit` as soon as nothing unsaved is on top; the desktop's `leave`
(`app/desktop/.../CampfireDesktopApplication.kt:107-117`) runs `viewModel.settleSynchronizationBeforeExit()` and then
`exitApplication`, and Compose's `application(exitProcessOnExit = true)` ends in `exitProcess(0)`
(`Application.desktop.kt`, ui-desktop 1.12.1). `settleSynchronizationBeforeExit` (`:1778`) returns at once when no sync
run is owed, and nothing on that path waits for the draft's removal, which is a hop through the `combine`, a hop to the
collector, then an IO write on a daemon thread. If the process ends first, the next launch's `recoverEditorDraft` finds
a draft that differs from the file, reopens the editor on it and says the unsaved changes were restored — the text the
user explicitly threw away. (Save is safe: the file then equals the draft and recovery drops it.)

This is a race and was not reproduced; it is filed because nothing orders the two, and the fix is to make the order
explicit on the one path where the process is about to end.

## Fix

At the start of `settleSynchronizationBeforeExit` (the desktop's only pre-exit hook, already awaited before `end()`),
bring the stored draft in line with the editor before anything else:
```kotlin
suspend fun settleSynchronizationBeforeExit() {
    // The stored draft is removed by a collector a few hops after the editor lets its text go, which a process that
    // ends now would not wait for: a Discard answered on the way out would come back as "unsaved changes restored".
    if (!_isEditorDraftRecoveryPending.value) storeEditorDraft(currentEditorDraftToStore())
    metronome.stop()
    ...
}
```
with `currentEditorDraftToStore()` extracted from `onAppPaused` so both use the same expression:
```kotlin
/** The unsaved text as the file would hold it, or null when nothing is unsaved. */
private fun currentEditorDraftToStore() = _editorDraft.value?.takeIf { hasUnsavedEditorText() }?.let { it.copy(text = fileTextOf(it.text)) }
```
`storeEditorDraft` is already `NonCancellable`, serialized by `editorDraftStoreMutex`, and a no-op when the stored draft
is already that value, so the extra call costs nothing when there is nothing to do. Rename is not needed, but extend the
function's KDoc with one sentence saying it also settles the stored editor draft.

presentation/CLAUDE.md: where the stored draft is described (`editor-draft.json`, "gone once it is saved or
discarded"), add that a desktop quit writes or removes it before the process ends.

## Tests

None: lifecycle and file IO; no pure logic.

## Manual check

Desktop: open a song in the editor, type something, click another application's window (the draft is stored), come
back, close the window → Discard. Relaunch: the editor does not reopen and nothing says unsaved changes were restored.
Repeat with Cmd+Q on macOS.
