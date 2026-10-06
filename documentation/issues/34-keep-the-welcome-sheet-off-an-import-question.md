# Keep the first run's welcome sheet off an import question, so its Open settings cannot cancel the import

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (Android/iOS "open with" and share, desktop/web drop or
"open with" on a first launch)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/WhatsNewGate.kt,
presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/WhatsNewGateTest.kt,
presentation/CLAUDE.md

## Problem

`CampfireViewModel.kt:2939-2953` (8ee010b36)
```kotlin
/** ... Only onto a screen with no other dialog on it - on a first run that is a question about a file the app was
 * opened with - since a welcome that replaced a question would leave it unanswered ... */
private suspend fun showWelcomeOnFirstRun() {
    if (!isFirstLaunch.await()) return
    isAppOnScreen.first { it }
    _visibleDialog.compareAndSet(null, DialogType.Welcome)
}
```
The KDoc's premise is that the import question is a dialog. It no longer is: it is `CampfireDestination.ImportReport`,
pushed by `showImportReport` (`:3195-3206`) as soon as no dialog is up. The import queue only waits for the demo
decision (`:1286`, `demoLibraryDecision.await()`), not for the launch screen, so on a first run opened with a file that
conflicts with a planted demo file (a demo song edited on another device and shared to the new one, a setlist zip holding
the demo setlist) the Review screen is pushed behind the launch screen. When `hasShownApp` sets `isAppOnScreen`, the
welcome's `compareAndSet(null, Welcome)` succeeds over it, because `_visibleDialog` is null.

The welcome's **Open settings** (`openSettingsFromWelcome`, `:3014-3017`) calls `selectTopLevelDestination(Settings)`,
which rebuilds the stack as `[Songs, Settings]`; `updateBackStack` sees the import screen gone and `onImportReportLeft`
(`:3234-3245`) answers the Review by cancelling it (`pendingImport = null; _importReport.value = null`). The file the app
was opened with is never imported and nothing says so. What's new is already gated on exactly this
(`canShowWhatsNew(hasDialog, isImporting, hasImportReport, queuedImportCount)` in `WhatsNewGate.kt`); the welcome is
not. Severity is low: it needs a first run opened with a conflicting file, and the file is still where it came from.

## Fix

Restore what the KDoc intends — a question in the way means no welcome — by also treating an import report as being in
the way. Do not wait for it (the KDoc explains why a welcome that waits would arrive in the middle of whatever the user
does next):

1. `WhatsNewGate.kt`: add
   ```kotlin
   /** Whether the first run's welcome may go up now: not over a dialog, and not over an import screen, which is a question too. */
   internal fun canShowWelcome(hasDialog: Boolean, hasImportReport: Boolean) = !hasDialog && !hasImportReport
   ```
2. `showWelcomeOnFirstRun`:
   ```kotlin
   isAppOnScreen.first { it }
   if (!canShowWelcome(hasDialog = _visibleDialog.value != null, hasImportReport = _importReport.value != null)) return
   _visibleDialog.compareAndSet(null, DialogType.Welcome)
   ```
   `_importReport` is set first thing in `showImportReport`, before the push, so it covers a report that is still
   waiting to be pushed as well as one on the stack. Everything here runs on the main thread, so the check and the
   `compareAndSet` cannot be split by the queue. An import still being *prepared* at this moment is not in the way: the
   welcome goes up, and the report it ends in waits for the welcome to close (`showImportReport` waits for no dialog),
   then lands over whatever Open settings opened — which is fine.
3. Rewrite the KDoc: the question about a file the app was opened with is the import screen (`ImportReport`), not a
   dialog, and the welcome is skipped rather than put over it.
4. presentation/CLAUDE.md, the welcome sheet paragraph ("and only onto a screen with no other dialog on it"): "…with no
   other dialog on it and no import screen under it".

## Tests

`WhatsNewGateTest`: `canShowWelcome(false, false)` is true; `canShowWelcome(true, false)` and `canShowWelcome(false,
true)` are false.

## Manual check

Fresh install (or cleared data) on Android: edit one of the demo songs on another device (same file name, different text), export it, and
open the `.cho` with Campfire before ever launching it. The import screen asks about the conflict and no welcome sheet
covers it; answering imports the file. A fresh install opened without a file still shows the welcome.
