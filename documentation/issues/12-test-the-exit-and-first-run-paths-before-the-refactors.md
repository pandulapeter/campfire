# Test the exit, pause, and first-run paths through the running app before the holders are refactored

**Kind:** tests · **Severity:** medium · **Priority:** P1 · **Platforms:** all (run on desktop) · **Lane:** P (after 11) · **Effort:** M
**Files:** `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/AppTest.kt` (harness),
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/state/AppExitTest.kt` (new),
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/firstRun/FirstRunTest.kt`,
`tools/screenshots/CLAUDE.md` (one line on the new harness members)

**Challenged:** amended — said on which thread the tests call the holders (setters inside `runOnIdle`, the suspending
exit with `runBlocking` outside it, so it never blocks the thread it resumes on), and that plan 01 edits `AppTest.kt`
too.

## Problem

Plans 13 and 16 restructure how the view model's holders start, listen, and write what is still waiting when the app
goes. The code they move has no test: `ui/state/AppExitController.kt` (exit questions, the desktop's settle before the
process ends, `onAppPaused`, `onCleared`'s detached write), `ui/firstRun/FirstRunController.kt` (only its helpers are
tested: `DemoLibraryTest`, `WhatsNewGateTest`, `ClipboardLinkOfferTest`), and the debounced writers in
`FontScaleController`, `MetronomeController`, `TunerController` (its `TunerControllerTest` does not reach the write),
`ExportController`, and `PlayingOverrides` (tempo and capo, which go to the preferences or to a setlist file). A
refactor that drops one of them loses a user's value on quit, silently: today only the hand-kept list in
`CampfireViewModel` says what is flushed:

```kotlin
waitingPreferences = listOf(
    fontScaleController.fontScalePreference,
    metronomeController.metronomeSettingsPreference,
    tunerController.tunerSettingsPreference,
    exportController.printSettingsPreference,
),
```

Unit tests of these holders would have to be rewritten by the very refactors they are meant to guard (their
constructors change). Tests through the running app (`tools/screenshots`, real repositories on real files, only the
platform faked) survive them unchanged, and check what matters: the bytes on disk.

## Fix

1. **Harness** (`AppTest.kt`): `runApp` passes `onViewModel = { viewModel = it }` instead of `{}`, and `AppTestScope`
   gains `val viewModel: CampfireViewModel` and `val dataDirectory: File` (`host.dataDirectory`), plus
   `fun storedPreferences(): JsonObject` (parses `preferences/preferences.json` with `Json.parseToJsonElement`) and
   `fun storedSetlist(fileName)` likewise. `tools/screenshots` is a friend of `:presentation`
   (`build.gradle.kts`, `-Xfriend-paths`), so the tests reach `viewModel.appExitController` and the other `internal`
   holders. Never call `ViewModelStore.clear()` mid-test; `withCampfireApp` does it at the end. Holder setters and
   `onAppPaused` / `requestExit` are called inside `runOnIdle { }` (the thread the view model's scope runs on);
   `settleSynchronizationBeforeExit` with `runBlocking` from the test body, never inside `runOnIdle`, since it resumes
   on that thread. Plan 01 also edits `AppTest.kt` (`captureWindow`); whichever lands second rebases.
2. **`AppExitTest`** (each a `runAppTest`; values set through the holders' own setters, the same calls the screens
   make, then the exit path called at once, before the 500 ms debounce):
   - `` `the exit writes the text size, metronome, tuner, and export settings that were still waiting` ``:
     `runBlocking { viewModel.settleSynchronizationBeforeExit() }`, then `storedPreferences()` holds all four.
   - `` `clearing the view model writes the values that were still waiting` ``: the same through
     `viewModel.appExitController.onCleared()`, waiting (`waitForLibrary`) for the file to say so, since that write is
     detached.
   - `` `the exit writes a tempo and a capo still waiting, a library song's to the preferences and a setlist song's to the setlist` ``.
   - `` `going to the background stores the editor's unsaved text and removes it once there is none` ``:
     `appExitController.onAppPaused()` with the editor open and typed into, then saved; read
     `preferences/editor-draft.json`.
   - `` `an exit with unsaved editor text asks first and runs only after Discard` ``, and
     `` `staying, or a second exit request, cancels the exit that asked` `` (`requestExit(onExit, onCancelled)`
     counting calls; the `UnsavedChanges` dialog found by its strings).
3. **`FirstRunTest`** additions (`runAppTest(isFirstRun = true)`):
   - `` `the onboarding stays owed until it is finished or skipped` ``: `isOnboardingPending` true in
     `storedPreferences()` while it is open, false once skipped.
   - `` `What's new is recorded as seen on a first run without being shown` ``.
4. Keep the new tests in the UI test set (`./gradlew :tools:screenshots:test`), not `desktopTest`; note the two new
   harness members in `tools/screenshots/CLAUDE.md`.

A test that fails on today's code has found a real defect: fix it in its own commit before the test's commit (code-review
skill, section 2), and stop if it cannot be fixed.

## Tests

This plan is the tests. Run `./gradlew :tools:screenshots:test --tests '*AppExitTest*' --tests '*FirstRunTest*'`.
