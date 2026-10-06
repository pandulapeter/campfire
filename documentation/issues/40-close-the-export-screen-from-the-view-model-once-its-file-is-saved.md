# Close the export screen from the view model once its file is saved, rather than by an event nobody may hear

**Kind:** ux  ·  **Severity:** low  ·  **Platforms:** Android (in practice), all by construction
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ExportScreen.kt,
presentation/CLAUDE.md

## Problem

8ee010b36:
```kotlin
private val _exportSaved = MutableSharedFlow<DialogType.Export>(extraBufferCapacity = 1)   // CampfireViewModel.kt:2776
...
onSaved = { _exportSaved.tryEmit(dialog) },                                                // :3308 (PDF) and :3350 (files)
...
LaunchedEffect(dialog) { viewModel.exportSaved.collect { if (it == dialog) close() } }    // ExportScreen.kt:548
```
A `MutableSharedFlow` with no replay drops a `tryEmit` made while it has no subscriber (`extraBufferCapacity` only
buffers for subscribers that exist). The only subscriber is the `ExportScreen` composable, and it is not always there
when the save completes. On Android, rotating the phone while the system "save as" screen is up recreates the Campfire
activity behind it. In the new composition `ExportHost.shown` starts as `null` (`ExportScreen.kt:299`, a plain
`remember`) and is only set by its `snapshotFlow` collector after the first frame; `ExportScreen` and its collector start
a frame after that. Meanwhile the result reaches the singleton `AndroidFilePicker` as soon as the new composition
registers its launcher, the write runs on IO, and `onSaved` fires. For a small file (a song's `.cho`, a one-page PDF) the
write can finish before the subscription: the "saved" snackbar shows (it is a separate message) but the screen stays
open, against presentation/CLAUDE.md ("A saved file emits `exportSaved` for that screen, which closes it").

Relied on: `SharedFlow` semantics (no replay, no subscriber → value dropped). The timing on a device was not reproduced;
the fix removes the dependency on it either way.

## Fix

`close()` is `viewModel.dismissSheet(dialog)`, which already does nothing unless `dialog` is still the visible dialog —
the same guard the event carries. So the view model can close it directly:

1. `CampfireViewModel`: in both `save(…)` calls, `onSaved = { dismissSheet(dialog) }`. Delete `_exportSaved` and
   `exportSaved` (`:2776-2777`).
2. `ExportScreen.kt`: delete the `LaunchedEffect(dialog) { viewModel.exportSaved.collect { … } }` line; keep `close` for
   its other uses.
3. presentation/CLAUDE.md (`:454`): "A saved file closes that screen (the view model dismisses it, whether or not the
   screen is composed at that moment, e.g. after an Activity recreated under the picker); a share leaves it open."

`onSaved` is invoked from `save(…)` inside `launchFileTransfer` on `viewModelScope` (main thread), where `dismissSheet`
belongs. Closing the export dialog through `setVisibleDialog` also flushes its pending print settings and cancels a
drawing that is no longer needed — both harmless after a save.

## Tests

None: view model wiring.

## Manual check

Android: export a song as ChordPro, tap Save, rotate the phone while the system save screen is up, confirm the save:
the export screen closes behind it and the saved message shows. Same for a one-page PDF. Share still leaves the screen
open.
