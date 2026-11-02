# Turn the view model's lifetime listeners into named, unit-tested policies and let holders start their own collectors

**Kind:** structure · **Severity:** medium · **Priority:** P1 · **Platforms:** all · **Lane:** P (after 12) · **Effort:** L (5 steps)
**Files:** `ui/CampfireViewModel.kt`, new `ui/metronome/SoundLifetime.kt`, new `ui/state/ModeEndings.kt`,
`ui/screens/export/ExportController.kt`, `ui/share/ShareLinkController.kt`, `ui/state/setlists/SetlistsController.kt`,
`ui/state/songs/SongSelection.kt`, `ui/tuner/TunerController.kt`, `ui/metronome/MetronomeController.kt`,
`ui/fontScale/FontScaleController.kt`, `ui/playing/PlayingOverrides.kt`, `ui/state/songText/SongTextStore.kt`,
`ui/state/importing/ImportController.kt`, `ui/state/importing/ImportReportState.kt`, `ui/firstRun/FirstRunController.kt`,
`ui/state/LibraryState.kt`, `ui/sync/SyncController.kt`, `ui/dialogs/DialogHost.kt`, `ui/dialogs/SavedDialogRestorer.kt`,
`ui/state/editor/EditorSession.kt` (`screens/songEditor/` before plan 11), `ui/navigation/Navigator.kt`, their tests,
`ui/CLAUDE.md`, `ui/metronome/CLAUDE.md`, `ui/tuner/CLAUDE.md`

**Challenged:** amended — the two policies are built before `DialogHost` and `Navigator` (lambdas into later holders,
as today's listeners) so every listener exists before any holder's `init` runs a collector; step 4 puts each holder's
starts in an `init` after all its properties (Kotlin initializes in textual order), checks that no start navigates or
shows a dialog before its first suspension, and names the collectors that stay in the view model's `init`; the two online sheets' searches gain the missing tests
of their work ending with the sheet (plan 19 dropped).

`ui/` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.

## Problem

`CampfireViewModel.kt` (936 lines) takes 53 use cases plus `SongRenderer`, `Metronome`, `Tuner`, and
`SavedStateHandle`, hand-builds about 35 holders, and is where the app's lifetime rules live, as anonymous lambdas
registered in construction order: six `dialogHost.addBeforeDialogChange`, one `addOpenDialogsChange`, eight
`navigator.addOnBackStackChanged` in the property initializer and two more in `init`. Rules such as "a click never
outlives its screen" and "the tuner listens only while its tab or sheet is up" (root `CLAUDE.md`, Feature invariants)
exist only there, untested, interleaved with construction:

```kotlin
addOnBackStackChanged { previousTop, stack ->
    if (isMetronomeScreenLeft(previousTop = previousTop, top = stack.lastOrNull())) metronome.stop()
}
addOnBackStackChanged { previousTop, stack ->
    if (previousTop?.contentKey != stack.lastOrNull()?.contentKey) {
        tunerController.stopTuner()
        dialogHost.dismissSheet(DialogType.Tuner)
    }
}
```

`init` then calls 23 `start…()` methods (`libraryState.startLoading()`, `songTextStore.startFollowingFiles()`,
`importController.startQueue(…)`, `fontScaleController.startWriter()`, … `tunerController.startStartableRule(…)`) and
launches three `firstRunController` jobs; a start that is forgotten compiles and silently disables a feature. And
`ui/CLAUDE.md` has to warn "**Construction order is behaviour**", because `Navigator` and `DialogHost` are built
first with lambdas into holders declared later (`songsSearch = { songListState.songsSearch }`,
`isLeaving = { isLeaving }` into `ImportController` before `appExitController` exists,
`awaitAppOnScreen = { launchState.isAppOnScreen.first { it } }` before `launchState`).

No unit test builds the view model; only the UI tests in `tools/screenshots` run it (every app start there does).

## Fix

**Decision taken here: no `AppSession` class and no move into Koin.** An `AppSession` would take the same 57
dependencies and be as hard to build in a unit test as the view model; Koin cannot build holders that need
`viewModelScope`. What makes this testable is small policy classes tested on their own and holders that cannot be
left unstarted. `CampfireViewModel` stays the composition root, shrunk to: build the holders (in an order where nothing
points forward), build the policies, and the members other modules call.

### Step 1 — `SoundLifetime` (`ui/metronome/SoundLifetime.kt`)

One class owning "sound and listening end with what they were started on". It takes `metronome: Metronome`,
`stopTuner: () -> Unit`, `stopScale: () -> Unit`, `dismissTunerSheet: () -> Unit`, and exposes
`fun onBackStackChanged(previousTop: CampfireDestination?, stack: List<CampfireDestination>)` and
`fun onBeforeDialogChange(previousDialog: DialogType?, dialogType: DialogType?)`, whose bodies are today's lambdas
verbatim, in today's order:

- back stack: `isMetronomeScreenLeft(…)` → `metronome.stop()`; then a changed `contentKey` → `stopTuner()` and
  `dismissTunerSheet()`;
- dialogs: `Export` or `Tuner` shown → `metronome.stop()`; `Tuner` left → `stopTuner()`; `Scale` left → `stopScale()`.

The view model builds it before `DialogHost` (its lambdas point at the tuner controller and the dialog host as today's
listeners do, called only on a change) and registers its two methods where the five lambdas were registered, so their
position among the other listeners is unchanged. Comments move with the code.

### Step 2 — `ModeEndings` (`ui/state/ModeEndings.kt`) and the export screen's own listener

`ModeEndings` takes `endSetlistReordering: () -> Unit` and `endSongSelection: () -> Unit` and holds "a mode ends with
the screen it belongs to": leaving `Setlists` ends reordering; leaving `Songs` ends the selection; showing `Export`,
`DuplicateSetlist`, or `EditSetlist` ends reordering; performance mode switched on ends the selection (today a
`viewModelScope.launch { preferencesController.isPerformanceModeEnabled.collect { … } }` in `init`; it becomes
`fun start(scope, isPerformanceModeEnabled)` called from the same place, or a collector in `ModeEndings`' `init` once
step 4 makes that the rule).

The export listener (`flushPrintSettings`, `forgetCreation`, `cancelPdfExport` when an `Export` dialog leaves) becomes
`ExportController.onBeforeDialogChange(previousDialog, dialogType, forgetShareCreation: (ShareTarget) -> Unit)`;
`AppExitController.onBeforeDialogChange` already exists. `ModeEndings` is built before `DialogHost` too. Register all
of them in `DialogHost`'s and `Navigator`'s `apply`, in today's order, so that every cross-holder listener is in place
before any holder built later can start a collector in its `init` (step 4).

**Size note (`check_sizes.py`):** `ExportController.kt` is 499 lines, so the new `onBeforeDialogChange` must be paid for by lines moved out of it.

### Step 3 — holder-owned listeners register themselves

These listeners only call one holder and are independent of the others (each prunes its own state):
`editorSession.onBackStackChanged()`, `metronomeController.onBackStackChanged(stack)`,
`importController.onBackStackChanged()`, `firstRunController.onBackStackChanged(stack)`, the song text read-ahead
(`songTextStore.readAhead(top, …)`, in `init` today), `randomPicksDraw.catchUp()`, and the open-dialogs trio
(`savedDialogRestorer`, `coverArtSearchController`, `songDetailsLookupController`). Each holder takes the
`Navigator` / `DialogHost` it already takes (or the narrow `addOnBackStackChanged` function) and registers in its own
`init`. Their order relative to each other changes to construction order; none reads another's state, which the
executor confirms by reading each body before moving it (the four are quoted in plan-time `HEAD`: they retain ids, drop
fields, or note a report's screen leaving).

**Size note (`check_sizes.py`):** none of `EditorSession` (19 parameters), `ImportController` (19), `FirstRunController` (15), or `MetronomeController` (12) takes the `Navigator` today, so a new parameter fails for each; and `ImportController.kt` (920 lines) may not grow — register those through an existing parameter or leave their listener in the view model.

### Step 4 — starts move into each holder's `init`

This step carries the lane's most ordering risk and only removes the "forgotten start" hazard; steps 1–3 hold the
plan's value. Move one holder per commit; if a holder's start turns out to need a reorder or a reference the spots
below do not list, leave that holder's start in the view model, note it in the lane report, and go on.

Each holder's `start…()` calls move into its `init`, in the order the view model calls them today
(`FontScaleController`: `startWriter`, `startEcho(userPreferences)`, `startSettle`; `MetronomeController`:
`startSettingsWriter`, `startFollowingPattern`, `startReportingStops`, `startStartableRule`; `TunerController`:
`startSettingsWriter`, `startFollowingSettings`, `startTrackingTunedNotes`, `startStartableRule`; and so on for
`LibraryState`, `SongTextStore`, `PlayingOverrides`, `ExportController`, `EditorSession`, `SavedDialogRestorer`,
`SongSelection`, `ImportReportState`, `DialogHost.startClosingWithSong`, `SyncController.startRestoring`,
`ImportController.startQueue`, and the three `FirstRunController` launches). The `start…` functions become private.

Each holder gets one `init` block, at the end of its class body: Kotlin runs property initializers and `init` blocks
in textual order, so a collector launched from an `init` above a property it reads finds it null.

On Android, iOS, and desktop `viewModelScope` is `Dispatchers.Main.immediate`, so a collector launched in `init` runs
until its first suspension *during construction*. Read each moved start for a back stack or dialog change before its
first suspension (`LaunchState`'s `restoreNavigationState`, `SavedDialogRestorer`, `SyncController.startRestoring`'s
consent answer, the three `FirstRunController` launches): a holder registering a listener in its own `init` (step 3)
and built after such a holder would miss it. Today all of them wait on the library or the preferences first; one that
does not keeps its start in the view model's `init`, after every holder.

The view model's `init` keeps only what spans holders: `logBreadcrumbs`, `savedStateStore.startPersisting(…)`, and the
`followFeatureSwitches` collector, which navigates and stays last. Before moving a holder's starts, check that nothing its collectors
call before suspending is a lambda into a holder built after it; where one is, build the target first or pass the
shared object instead of a lambda:

- `Navigator`: build the three `SearchState`s, `chordLibrary`, and `scaleLibrary` before it and pass them, not
  lambdas into `songListState`, `setlistsController`, `importReportState`.
- `ImportController.startQueue(firstRunController.demoLibraryDecision)`: create the `CompletableDeferred` in the view
  model and pass it to both; `isLeaving = { isLeaving }` stays a lambda (only read when a batch starts, after the
  queue's first suspension).
- `FirstRunController`'s `awaitAppOnScreen`: build `LaunchState` first if its inputs allow, else keep the lambda (it
  is only awaited inside a launched job).

**Size note (`check_sizes.py`):** a start argument turned into a constructor parameter (the `CompletableDeferred` above) fails `ImportController` (19) and `FirstRunController` (15), and an `init` added to `ImportController.kt` (920 lines) must be offset by lines removed in the same commit; otherwise that holder's start stays in the view model.

The relative order of different holders' starts becomes construction order. That is safe for collectors of
`StateFlow`s (conflated: a later subscriber sees the latest value). It is not for `SharedFlow`/`Channel` events: grep
each moved collector for `emit`/`tryEmit`/`send` and for collectors of a `SharedFlow`, and keep any producer–consumer
pair in today's order. One commit per holder group, so a regression bisects to one holder.

### Step 5 — docs

`ui/CLAUDE.md`: replace "Construction order is behaviour …" and "Cross-holder reactions are listeners registered in
order …" with: holders start their own collectors in `init` and are built so that nothing points at a holder built
later; cross-holder lifetime rules are the policies (`SoundLifetime`, `ModeEndings`), registered in the view model in
one place. Point `ui/metronome/CLAUDE.md` and `ui/tuner/CLAUDE.md` at `SoundLifetime` for "a click never outlives its
screen" and "nothing of the tuner outlives its screen".

## Tests

- `SoundLifetimeTest` (`commonTest`, `ui/metronome/`): a real `Navigator` and `DialogHost` (built as `NavigatorTest`
  does), a fake `Metronome` (a small class in the test recording `stop()` and `playback`), counters for the lambdas:
  `` `leaving the song a click was started on stops it` ``, `` `a sheet over the song leaves the click going` ``,
  `` `opening the export screen or the tuner sheet stops the click` ``,
  `` `another song arriving on top stops the tuner and closes its sheet` ``,
  `` `covering the scale sheet with a chord's sheet stops the scale` ``.
- `ModeEndingsTest` (`ui/state/`): `` `leaving the setlists ends reordering` ``,
  `` `editing a setlist ends reordering` ``, `` `leaving the songs ends the selection` ``,
  `` `switching performance mode on ends the selection` ``.
- The two online sheets, whose work ending with the sheet has no test (plan 19, dropped, would have given each entry a
  scope for it): `CoverArtSearchControllerTest` (new) and `SongDetailsLookupControllerTest` gain
  `` `closing the sheet cancels the search it started` ``, through `onOpenDialogsChanged` as step 3 registers it.
- `ExportControllerTest`, if absent, gains `` `the export screen going flushes its options and cancels its PDF` ``.
- Every step: `./gradlew desktopTest :tools:screenshots:test` (the UI tests build the real view model on every app
  start, so a forward reference touched during construction fails them) and plan 12's tests unchanged.
