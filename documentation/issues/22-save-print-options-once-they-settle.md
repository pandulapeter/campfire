# Save the print options once they settle, not on every step

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all, felt on web
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
**Challenged:** amended — the flush hooks `setVisibleDialog`, not `dismissSheet` (most ways a dialog goes never reach `dismissSheet`); the claimed write count is overstated (the repository already coalesces writes); a recreated sheet starts from the pending value.

## Problem

```kotlin
if (normalized != settings) { settings = normalized; viewModel.setPrintSettings(normalized) }
```

Each step of the size or margin control goes through `changeUserPreferences` → `updateUserPreferences`: the new
preferences are published at once, re-emitting `userPreferences` to every collector (every screen, and this sheet
itself) once per step, and a write follows. `BaseLocalDataRepository.persistLatest` already folds writes that queue up
behind one in flight, so a drag is fewer than the 13–16 whole-document writes first estimated; what remains is a
re-emission per step and, on a fast disk, a write per step. The viewer's text size already avoids this
(`unsavedFontScale … debounce(FONT_SCALE_SAVE_DELAY_MILLIS)`).

## Fix

Keep `settings` local as today and persist with the same pattern: a `MutableStateFlow<PrintSettings?>`
(`pendingPrintSettings`) in the view model, debounced by `FONT_SCALE_SAVE_DELAY_MILLIS`, cleared with
`compareAndSet` after saving as the font scale's collector does.

- **Flush in `setVisibleDialog`**: when `previousDialog is DialogType.PrintExport` and the new dialog is not that same
  one, save `pendingPrintSettings` at once (in `viewModelScope`). `dismissSheet` reaches this through `dismissDialog`,
  but so do the web's Back, the desktop's Escape, `navigateBack` and another dialog replacing the sheet, and none of
  those call `dismissSheet`; the sheet is also removed from composition at once on those paths, so nothing in the
  sheet can do the flush. A flush in `dismissSheet` alone would leave a reopen within 500 ms reading the old values.
- The sheet's initial `settings` is `viewModel.pendingPrintSettings.value ?: initialSettings`, so a rotation (the
  composition is rebuilt and `remember(dialog)` re-reads) inside the debounce window does not go back a step.
- No flush when an export starts: the export uses the sheet's local `settings`, not the preferences.
- A process killed inside the 500 ms loses the last step, exactly as the font scale does; that is accepted.

## Tests

None (the view model is untested by convention).

## Manual check

Change the size several steps, close the sheet at once (close button, then again with Escape on desktop and Back on
the web), reopen: the last value is there.
