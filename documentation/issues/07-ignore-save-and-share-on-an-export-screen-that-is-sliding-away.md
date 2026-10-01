# Ignore Save, Share and option changes on an export screen that is already sliding away

**Challenged:** amended — named the two funnels to guard (`requestExport` and the `update` lambda, plus `onSelected`)
instead of "the option callbacks"; made the view-model guard the one that matters (a Save tapped while the pages were
being laid out is queued in `requestedExport` and fires from a `LaunchedEffect` once they are, which a UI-side guard on
the tap does not stop); recorded that `DialogType.PrintExport` is a data class and that equality, not identity, is the
intended comparison.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportScreen.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`

## Problem

`PrintExportHost` keeps the closed export composed while it slides away (`shown` is cleared only after
`transition.progress.animateTo(0f, spec)`), and the screen stays interactive the whole time. `setVisibleDialog` has
already flushed the settings and run `cancelPdfExport()` for it:

```kotlin
// However the export sheet goes - closed, Escape, the web's Back, another dialog put over it - it is removed from
...
// An export nobody is looking at any more would put its picker up over whatever is on screen by then.
cancelPdfExport()
```

Two ways a picker still comes up over whatever is on screen by then, the case that comment says is prevented:
- a tap on Save or Share during the ~300 ms slide reaches `requestExport`;
- more likely, a Save tapped while an option change was being laid out again is parked in `requestedExport`; the
  `LaunchedEffect(requestedExport, canSave, hasPages)` fires `viewModel.exportPdf(…)` as soon as `canSave` turns true,
  which can be after the screen was closed — `exportPdf` does not check that its dialog is still the visible one, and
  `cancelPdfExport()` only cancels a job that already exists.

An option changed during the slide also lands in `pendingPrintSettings` after the flush (the debounced collector then
saves it), which is a change the user made to a screen they had already left.

## Fix

1. `CampfireViewModel.exportPdf`: return at once when `_visibleDialog.value != dialog`, before `launchFileTransfer`.
   This is the guard that matters, since it covers the queued request too. `DialogType.PrintExport` is a `data class`,
   so this is equality, and that is intended: the same export closed and opened again during the slide is a new but
   equal instance, the host keeps showing the old one (`shown` is a structural `mutableStateOf`, so assigning the equal
   instance changes nothing), and that old instance has to count as open. Do not use `!==` here.
2. `PrintExportScreen`: collect `viewModel.visibleDialog` and derive `val isOpen = visibleDialog == dialog` (equality
   again, for the same reason). Make `requestExport` a no-op while `!isOpen`, and likewise the `update` lambda (the one
   funnel every option goes through, `onSettings = update`) and `onSelected`. Nothing else needs a guard; a pointer
   blocker over the content is unnecessary.

"One export replacing another is the same screen" (the host's comment) is unaffected: if the visible dialog changes
straight from one export to another, `shown` and `dialog` follow it, `isOpen` holds for the new one, and
`requestedExport` is `remember(dialog)`, so nothing of the old one is carried over; `setVisibleDialog` cancels the old
one's drawing. The `printExportSaved` → `close()` path is untouched (see plan 09 on why it must stay `dismissSheet`).

Leave the back handler as it is: `isBackEnabled = target != null` is already false during the slide, so a second Back
pops the screen underneath, which is an ordinary double Back.

## Tests

None: the guard is in a Compose screen and a view model method that needs a `FilePicker`.

## Manual check

Change the font size in the export and tap Save PDF at once, then press Back immediately: no picker appears. Close the
export screen with Back and tap where Save PDF was in the same instant: no picker appears either.
