# Split `CampfireViewModel` into cohesive internal state holders behind a thin view model facade

**Kind:** architecture  ·  **Severity:** high  ·  **Effort:** L  ·  **Risk:** medium  ·  **Platforms:** all
**Challenged:** amended — step 0 is done by the package-move pass (verify and skip); holders go into the feature packages
that pass created; rule 1 resolves the "first property vs. everything it reads" conflict and names the eager starts
whose relative order must hold; rule 2 allows one `start…()` per collector (a holder's collectors are interleaved with
other holders' in `init`); new rule 6 on holder cycles (Navigator ↔ EditorSession/SetlistsController/DialogHost) and
rule 7 on the shared `asState`; `writeWaitingPreferences` moves to `AppExitController` (it reads the pending values of
holders 6–8, which are built after `PreferencesController`); `PreferencesController` takes the persistence request
(plan 09 step 2); overlaps with plans 04, 06, 10 spelled out.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (the whole
class, its nested types and its `companion object`); new files under `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/`
(one per holder, see the table); every screen, dialog and component that calls `viewModel.*` (only where a step changes
a name, which the facade avoids); `presentation/src/desktopMain/.../ui/CampfireDesktopApp.kt` (`CampfireViewModel.handleKeyEvent`),
`presentation/src/wasmJsMain/.../ui/CampfireWebApp.kt`, `presentation/src/wasmJsMain/.../ui/navigation/BrowserHistory.kt`,
`BrowserRoutes.kt`, `presentation/src/androidMain/.../ui/CampfireAndroidApp.kt`, `presentation/src/iosMain/.../ui/CampfireIosApp.kt`;
callers outside the module that must keep compiling unchanged: `app/desktop/src/main/java/com/pandulapeter/campfire/{CampfireDesktopApplication,TitleBar,AppIcon,SingleInstance}.kt`,
`tools/screenshots/src/main/kotlin/com/pandulapeter/campfire/screenshots/{Render,Shot,Website}.kt`; new tests in
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/`; `presentation/CLAUDE.md` (every
paragraph that says "`CampfireViewModel.x`" for a member that moves keeps working through the facade, but the
`ui/CampfireViewModel.kt` paragraph is rewritten to describe the holders); `app/android/src/main/generated/baselineProfiles/*`
(regenerate after the last step, see root `CLAUDE.md`)
**Depends on:** none to start. Individual steps below name the separate plans that should land before them
(02 before the PlayingOverrides step, 04 before the SongRenderer step, 06 before the FontScale/Metronome/Export
steps, 07 before the SongMetadataEditing step, 09 before the DialogHost step). Each of those plans lands on its own
without this one.

## Problem

`CampfireViewModel` (at 2940b0e0a: 4627 lines, one class) is the whole presentation state of the app:

- 53 constructor parameters (51 use cases plus `Metronome` and `SavedStateHandle`, `CampfireViewModel.kt:210-263`);
- about 152 non-private functions/properties and about 57 private functions (`grep -cE "^    (fun|internal fun|suspend fun)"`
  gives 152, `^    private (suspend )?fun` gives 57);
- one `init` block (`:1267-1461`) that launches about 25 collectors which belong to a dozen unrelated features
  (sync restore, demo planting, welcome, What's new, song text invalidation, the import queue, dialog-closes-with-song,
  editor draft recovery, five saved-state persisters, three debounced preference writers, two tempo/capo settle
  collectors, the metronome pattern collector, the metronome stop-reason collector, the font scale echo and settle);
- ~40 nested types (`DialogType`, `Message`, `Placeholder`, `SetlistWithSongs`, `Transpositions`, `SongGroups`, …)
  referenced from everywhere as `CampfireViewModel.DialogType` (80 references) and `CampfireViewModel.Message` (20).

Cross-feature side effects are hidden inside two "funnel" functions, so nobody can tell what a back stack or dialog
change does without reading all of them:

```kotlin
private fun updateBackStack(isPredictiveBackCompleted: Boolean = false, update: SnapshotStateList<CampfireDestination>.() -> Unit) {
    if (isNavigationTransitionRunning && !isPredictiveBackCompleted) navigationGeneration++
    val previousTop = backStack.lastOrNull()
    backStack.update()
    if (backStack.lastOrNull() != CampfireDestination.Setlists) reorderingSetlistFileName = null     // setlists
    if (isMetronomeScreenLeft(previousTop = previousTop, top = backStack.lastOrNull())) metronome.stop() // metronome
    if (backStack.none { it is CampfireDestination.SongEditor }) retainedEditorField = null          // editor
    backStack.mapNotNullTo(mutableSetOf()) { (it as? CampfireDestination.SongDetails)?.id }.let { ids ->
        songDetailsCurrentSongs.keys.retainAll(ids)                                                    // navigation
        songDetailsTargetSongs.keys.retainAll(ids)                                                     // metronome
        songDetailsTargetTimings.keys.retainAll(ids)                                                   // metronome
    }
    val hadImportReport = isImportReportOnBackStack
    isImportReportOnBackStack = backStack.any { it == CampfireDestination.ImportReport }
    if (hadImportReport && !isImportReportOnBackStack) onImportReportLeft()                           // import
    persistBackStack()
}
```

and `setVisibleDialog` (`:4036-4079`) ends the setlist reorder mode, cancels a parked exit, clears the confirmed exit,
clears the cover art search, computes `_underlyingSongInfo`, flushes the print settings and cancels a PDF export,
stops the metronome, and starts a cover art search. There are no tests of any of it: the class cannot be built in a
test without 53 fakes and a `SavedStateHandle`, and every behaviour is interleaved with every other.

## Fix

Step 1 of the finding: **holders are plain `internal` classes constructed inside the view model** (no DI change), each
taking a `CoroutineScope` (the view model passes `viewModelScope`), the use cases it needs, and the other holders it
reads. The view model keeps every public/internal member it has today as a one-line delegation (the facade), so
screens, the platform shells, `app/desktop` and `tools/screenshots` compile unchanged at every step; plan 03 then moves
components and screens onto holders directly. Every step below is one commit that compiles and passes
`./gradlew :presentation:desktopTest`, and moves code without changing it except for the listener plumbing it names.

### Rules that hold for every step

1. **Construction order is behaviour.** The view model's properties are initialised top to bottom and every
   `asState` is `stateIn(viewModelScope, SharingStarted.Eagerly, …)`, so a holder must be declared after everything
   it reads **as a value at construction** (a constructor parameter that is still null at that point is a
   `NullPointerException` at launch, not a compile error) and its flows start where the moved properties started.
   Declare each holder as a `private val` at the position of the first property it replaces (not in table order:
   `PlayingOverrides`, for one, reads `setlists` and `userPreferences`, which must already exist), and pass it the
   flows it reads as constructor parameters, so a holder extracted early can read a property that still lives in the
   view model. Where the two conflict — a holder's first member sits above something else it reads (`transpositions`
   at the top of the class vs. `songsByFileName`, which `effectiveTempoOf`/`effectiveCapoOf` read, ~100 lines lower) —
   pass the later dependency as a lambda (`songsByFileName = { songsByFileName.value }`) rather than moving the holder
   down. Moving a pure derived `stateIn` over StateFlows a few properties later is harmless, but these starts must keep
   their relative order, since they begin I/O or run synchronously on `Main.immediate`: `screenData`
   (`getScreenData(_songFilter).stateIn`), `userPreferencesState` (`getUserPreferences().asState`), `isFirstLaunch`
   (`viewModelScope.async { isFirstRun() }`, declared just above `init`) and `libraryPersistence` (a `flow { emit(requestLibraryPersistence()) }`).
   `FirstRunController`'s first replaced member is `isDemoLibraryPending` (~line 500), far above `isFirstLaunch`: keep
   `isFirstLaunch` created at its old position (the view model creates the `Deferred` and hands it to
   `FirstRunController` and `SyncController`), rather than starting `isFirstRun()` before the preferences read.
2. **Collector start order is behaviour.** `viewModelScope` runs on `Dispatchers.Main.immediate`, so each
   `viewModelScope.launch` in `init` runs synchronously up to its first suspension, in source order. A holder's
   collectors are not contiguous in `init` (the font scale writer, then the print and metronome writers, the two
   settle collectors, the pattern and stop-reason collectors, and only then the font scale echo and settle), so a
   single `start()` per holder would reorder them: give a holder one `start…()` per collector or per contiguous run of
   them (`FontScaleController.startWriter()`, `startEcho()`, `startSettle()`), and keep the view model's `init`
   calling them **in the order the collectors are launched at 2940b0e0a** (copy the order from the `init` block).
   The first launch in `init`, `loadScreenData(false)`, belongs to `LibraryState` and stays first. Known dependencies
   that must survive:
   - `editorDraftRecovery` is completed by the editor-draft collector and **awaited by `navigateOnLaunch`**;
     `_isEditorDraftRecoveryPending` gates `hasLibraryToShow`.
   - `demoLibraryDecision` is completed by `plantDemoLibraryOnFirstRun` and **awaited by the import-queue collector**
     before it takes the first batch.
   - `isFirstLaunch.await()` precedes `forgetSyncConnection()` and `restoreSync()` in the sync-restore coroutine.
   - The font scale echo collector (`userPreferences…fontScale…collect { if (unsavedFontScale.value == null) … }`)
     must start after the debounced font-scale writer, so the writer's own echo compares equal.
   - The metronome pattern collector treats its first value as "only remembered"; it must keep being the first
     collection of that flow.
3. **Side effects become explicit listener lists, invoked in the original order.** `Navigator` exposes
   `addOnBackStackChanged(listener: (previousTop: CampfireDestination?, stack: List<CampfireDestination>) -> Unit)` and
   `DialogHost` exposes `addBeforeDialogChange((previous: DialogType?, next: DialogType?) -> Unit)` and
   `addAfterDialogChange(...)`. Listeners are registered in the view model, after construction, in the order of the
   statements they replace (for `updateBackStack`: setlists reorder reset, metronome stop, retained editor field,
   song-details maps, import report; then `persistBackStack`). Do not let a holder register itself from its
   constructor, or the order becomes the declaration order.
4. **Every move is mechanical.** No renaming of the moved members' bodies, no "while I'm here" fixes. KDoc moves with
   the declaration (see the `code-style` skill: invoke it before editing any `.kt`).
5. Holders are `internal`, named for what they hold (no `Impl`, they implement nothing), one file each. Put a holder in
   the feature package the package-move pass created where one fits — `PlayingOverrides` in `ui.playing`,
   `FirstRunController` in `ui.firstRun`, `ExportController` in `ui.screens.export` (or beside it), the search state in
   `ui.search` — and the rest in a new `ui.state`. Do not name any pre-move package (`ui.metronome.SongTempo`,
   `screens.songDetails.SongCapo`, nested `CampfireViewModel.X`) in new code: look the declarations up by name.
6. **Holders that need each other take a function, not the holder.** Several pairs are cycles if both sides take the
   other as a constructor parameter: `Navigator.navigateBack` reads `SetlistsController.isSetlistReordering` and
   `EditorSession.hasUnsavedEditorChanges` and calls `DialogHost.showDialog`, while `EditorSession.openEditor`,
   `SetlistsController` and `ImportController` push onto `Navigator`'s back stack; `DialogHost`'s listeners are
   registered by the holders it notifies. The holder extracted **earlier** takes a narrow lambda (e.g.
   `updateBackStack: (SnapshotStateList<CampfireDestination>.() -> Unit) -> Unit`, `showDialog: (DialogType) -> Unit`)
   that the view model fills with a reference to whatever owns the member at that step (first itself, later the
   holder); a lambda that reads a view model property declared further down is fine, since it is only invoked after
   construction. The holder extracted **later** may take the earlier one directly.
7. **One `asState`.** The view model's private `Flow<T>.asState(initialValue)` is `distinctUntilChanged().stateIn(viewModelScope,
   Eagerly, initialValue)`; move it to an internal top-level `Flow<T>.asState(scope, initialValue)` in the first step and
   have every holder use it, so the `distinctUntilChanged` is not lost in a moved flow.

### Holders and the members they own (names at 2940b0e0a)

| # | Holder | Owns |
|---|---|---|
| 1 | `MessageSink` | `_messageQueue`, `messageQueue`, `messageCount`, `sendMessage`, `onMessageShown`; also `launchLibraryChange` (it turns a failed write into `Message.OperationFailed`), taking the scope |
| 2 | `SavedStateStore` | `restore`/`persist` helpers, every `*_KEY` constant, `restoreSearch`, `SavedSearch`, `SavedSongFilter`, the five persisting collectors (song filter, picker tags, picker languages, the two searches) |
| 3 | `PreferencesController` | `userPreferencesState`, `userPreferences`, `arePreferencesLoaded`, `isPerformanceModeEnabled`, `topLevelDestinations`, `libraryPersistence` (taking `requestPersistence: suspend () -> LibraryPersistence`, which the view model fills with `::requestLibraryPersistence` — plan 09 step 2), `changeUserPreferences`, every `set*` preference toggle (`setPerformanceModeEnabled` … `setNotation`, `setSortingMode`, `setTagSortingMode`, `setLanguageSortingMode`, `setCoverArtEnabled`, `setSectionNumberingEnabled`, …), `toggleSectionFold`, `toggleChordSectionFold`, `setChordVoicing` (not `writeWaitingPreferences`: see #22) |
| 4 | `DialogHost` | `_visibleDialog`, `visibleDialog`, `_underlyingSongInfo`, `underlyingSongInfo`, `setVisibleDialog`, `showDialog`, `dismissDialog`, `dismissSheet`, `DialogType.songFileName`, the "dialog about a song closes when the song goes" collector, and (after plan 09) the overlay/menu count |
| 5 | `PlayingOverrides` | `transpositions`, `storedTempos`, `pendingTempos`, `tempoWriteJobs`, `tempos`, `storedCapos`, `pendingCapos`, `capoWriteJobs`, `capos`, `effectiveTempoOf`, `stepTempo`, `setTempo`, `resetTempo`, `changeTempo`, `writeTempo`, `effectiveCapoOf`, `stepCapo`, `resetCapo`, `changeCapo`, `writeCapo`, `stepTransposition`, `resetTransposition`, `changeTransposition`, `takeWaitingOverrideWrites`, both settle collectors (simpler after plan 02) |
| 6 | `FontScaleController` | `liveFontScale`, `fontScale`, `unsavedFontScale`, `settledFontScaleState`, `settledFontScale`, `setFontScale`, `adjustFontScale`, `settleFontScale`, `isSongTextZoomable`, `zoomSongText`, `touchpadFontScale`, `_printPreviewMagnifications`, `printPreviewMagnifications`, `magnifyByTouchpad`, the debounced writer, the echo collector, the settle collector |
| 7 | `MetronomeController` | `_pendingMetronomeSettings`, `metronomeSettings`, `metronomePlayback`, `metronomeBeats`, `songDetailsTargetSongs`, `songDetailsTargetTimings`, `metronomeRenames`, `metronomeContext`, `onSongDetailsPageChanged`, `currentMetronomePattern`, `toggleMetronomeByKey`, `toggleMetronome`, `toggleMetronomePanel`, `showMetronomePanel`, `stopMetronome`, `previewMetronomeSound`, `updateMetronomeSettings`, `silentClickStopJob` + the silent-click part of `onAppStopped`/`onAppStarted`, the pattern collector, the stop-reason collector, the settings writer; registers a back-stack listener (stop on leaving + retain the two maps) and a before-dialog listener (stop on `DialogType.Export`) |
| 8 | `ExportController` | `fileTransferJob`, `_isFileTransferActive`, `isFileTransferActive`, `launchFileTransfer` (also used by the import's `importFiles(filePicker)`, so `ImportController` takes this holder), `_pdfExportProgress`, `pdfExportProgress`, `pdfExportJob`, `preparePrintSource`, `_pendingPrintSettings`, `pendingPrintSettings`, `setPrintSettings`, `savePrintSettings`, `exportPdf`, `cancelPdfExport`, `exportFiles`, `exportLibrary`, `onExportFailed`, `onLinkNotOpened`, `save`; registers the before-dialog listener that flushes print settings and cancels the PDF export when the export screen goes |
| 9 | `CoverArtSearchController` | `_coverArtSearch`, `coverArtSearch`, `coverArtSearchJob`, `coverArtQueryOf`, `searchCoverArt`, `clearCoverArtSearch`, `coverArtCacheSize`, `clearCoverArtCache`; registers before-dialog (clear unless `CoverArtSearch`) and after-dialog (search on open) listeners |
| 10 | `SyncController` | `syncState`, `isSyncing`, `syncProviders`, `syncConnectionJob`, `connectSyncProvider`, `cancelSyncConnection`, `disconnectSyncProvider`, `synchronizeLibrary`, `cancelSynchronization`, `openSyncSettingsAfterConsent` (via a callback into `Navigator`), the sync-restore coroutine |
| 11 | `LibraryState` | `screenData`, `isLoading`, `LoadingLatch`, `allSongs`, `setlists`, `songFileNamesInSetlists`, `librarySummary`, `songSearchIndex`, `indexedSongs`, `IndexedSongInput`, `IndexedSongs`, `songsByFileName`, `labelsOnEverySong`, `tags`, `languages`, `failedSongFileNames`, `refresh`, `refreshIfStale`, `lastRescanAt` |
| 12 | `SongListState` | `_songFilter`, `songFilter`, `songsSearch`, `songGroups`, `songsPlaceholder`, `hasSongFilters`, `isSongFilterActive`, `toggleTagFilter`, `clearTagFilter`, `setTagMatchMode`, `setLanguageMatchMode`, `toggleLanguageFilter`, `clearLanguageFilter`, `clearSongFilter`, `songsScrollPosition` (`_songFilter` is needed by `getScreenData(_songFilter)` in `LibraryState`, so `SongListState` is built first and hands its filter flow to `LibraryState`, or the filter flow is created by the view model and passed to both — pick whichever keeps the original declaration order) |
| 13 | `SongPickerState` | `_songPickerSelectedTags`, `songPickerSelectedTags`, `_songPickerSelectedLanguages`, `songPickerSelectedLanguages`, `toggleSongPickerTag`, `toggleSongPickerLanguage`, `pickerSongs`, `songPickerFilters` |
| 14 | `SetlistsController` | `setlistsSearch`, `setlistsScrollPosition`, `reorderingSetlistFileName`, `isSetlistReordering`, `shouldShowArchivedSetlists`, `visibleSetlists`, `setlistsWithSongs`, `setlistsPlaceholder`, `Setlist.matchesSearch`, `createSetlist`, `createSetlistWithSong`, `addSongToSetlist`, `setSetlistSong`, `editSetlist`, `duplicateSetlist`, `setSetlistArchived`, `deleteSetlist`, `removeSongFromSetlist`, `reorderSetlist`, `updateEditableSetlist`, `setShouldShowArchivedSetlists`, `setSetlistSortingMode`; registers a back-stack listener (reorder reset) and a before-dialog listener (reorder reset for Export/Duplicate/Edit) |
| 15 | `SongTextStore` | `_songTexts`, `songTexts`, `_songsBeingRenamed`, `songsBeingRenamed`, `songWriteMutex`, `pruneSongTexts`, `rereadSongTexts`, `loadSongContent`, `editSongText`, `writeSongContent`, `updateSongFileName`, `deleteSong`, `createSong`, the invalidations collector |
| 16 | `SongMetadataEditing` | `setSongTags`, `setSongLanguages`, `showSongTagsDialog`, `showSongLanguagesDialog`, `songForLabelEditing`, `showSongMetadataDialog`, `setSongMetadata`, `showSongLinksDialog`, `songMetadataOf`, `hasSongInfo`, `setSongLinks`, `showSongCoverArtDialog`, `setSongCoverArt`, `songTextOf`, `editSong`, `showSongPlayingDialog`, `setSongPlaying`, `fileKeyOf` (after plan 07 these take a `SongEditTarget`) |
| 17 | `SongRenderer` | `renderSong`, `transposedSong`, `notatedSong`, both `renderKey`, `normalize`, `normalizeForSearch`, `languageCode` — see plan 04, which extracts it on its own **and also takes `prettifyText`, `transposeText`, `editorTextOf`/`lastEditorText`/`EditorText`, `editorSummaryCache`, `editorKeyOf`, `fileTextOf` out of #18**; after plan 04, #18 keeps only `editorNotation` and the calls that pass it |
| 18 | `EditorSession` | `_editorDraft`, `storedEditorDraft`, `editorDraftStoreMutex`, `retainedEditorField`, `_editorRevertRequests`, `_editorTextEdits`, `hasUnsavedEditorChanges`, `_isEditorDraftRecoveryPending`, `editorDraftRecovery`, `currentSaveJob`, `editorLeaveJob`, `_isSavingSong`, `openEditor`, `onEditorTextChanged`, `onEditorClosed`, `retainEditorField`, `retainedEditorField()`, `onEditorDraftLost`, `currentEditorDraftToStore`, `storeEditorDraft`, `recoverEditorDraft`, `saveEditorChangesAndLeave`, `leaveEditorWithoutSaving`, `revertEditorChanges`, `leaveEditor`, `saveSongContent`, `writeEditorText`, `hasUnsavedEditorText`, `editorNotation`, `editorTextOf`, `lastEditorText`, `EditorText`, `editorSummaryCache`, `editorKeyOf`, `fileTextOf`, `transposeText`, `prettifyText`, the draft-recovery collector; registers a back-stack listener (drop retained field) |
| 19 | `ImportController` | `_isImporting`, `isImporting`, `isDeletingLibrary`, `_importProgress`, `importProgress`, `_importReport`, `importReport`, `isImportReportOnBackStack`, `importReportRequest`, `pendingImport`, `preparation`, `isPreparationCancelled`, `importQueue`, `_queuedImportCount`, `importReportSearch`, `ImportRequest`, `PendingImport`, both `importFiles`, `enqueueImport`, `awaitImportSettled`, `import`, `cancelImportPreparation`, `resolveImport`, `applyImportPlan`, `showImportReport`, `openImportReport`, `followReportedFileNames`, `closeImportReport`, `onImportReportLeft`, `deleteLibrary`, `ImportResult.isClean`, `ImportPlan.entryCount`, the queue collector; registers a back-stack listener (report left) |
| 20 | `FirstRunController` | `isFirstLaunch`, `isDemoLibraryPending`, `demoLibraryDecision`, `isAddingDemoLibrary`, `demoLibraryOffer`, `importDemoLibrary`, `plantDemoLibraryOnFirstRun`, `readDemoLibrary`, `showWelcomeOnFirstRun`, `openSettingsFromWelcome`, `showWhatsNewOnVersionChange`, `onWhatsNewShown`, `recordWhatsNewVersion`, `_isLaunchNavigationPending`, `isLaunchNavigationPending`, `hasNavigatedOnLaunch`, `navigateOnLaunch`, `hasLibraryToShow`, `hasShownApp`, `_isAppOnScreen`, `isAppOnScreen` |
| 21 | `Navigator` | `backStack`, `navigationGeneration`, `isNavigationTransitionRunning`, `setNavigationTransitionRunning` (calls `SongTextStore.pruneSongTexts` through a listener), `updateBackStack`, `persistBackStack`, `metronomeScrollPosition`, `settingsScrollPositions`, `settingsTab`, `isSettingsBackToGeneral`, `songDetailsCurrentSongs`, `onSongDetailsPageSettled`, `currentSongFileName`, `navigationState`, `restoreNavigationState`, `selectTopLevelDestination`, `openSong`, `openSongInSetlist`, `openSongDetails`, `openImportedSong`, `openReportedSong`, `navigateBack`, `popBackStack`, `_scrollToTopRequests`, `scrollToTopRequests`, `currentSearch`, `openCurrentSearch` |
| 22 | `AppExitController` | `pendingExit`, `PendingExit`, `takePendingExit`, `confirmedExit`, `requestExit`, `confirmExit`, `exitConfirmed`, `isLeaving`, `settleSynchronizationBeforeExit`, `onAppPaused`, the non-metronome parts of `onAppStopped`/`onAppStarted`, `onCleared`'s write-on-a-detached-scope body, and `writeWaitingPreferences` (its only callers are `settleSynchronizationBeforeExit` and `onCleared`, and it reads the pending values of #6, #7 and #8 — after plan 06 it is `DebouncedPreference.flushAll` over the three holders' preferences); registers the before-dialog listener for the parked exit and the confirmed exit. Until this step lands, `writeWaitingPreferences` stays in the view model and reads the three holders |

What stays in the view model: the constructor, the holder properties in the order above, `init { holder.start…() … }`
in the original collector order, the listener registrations, `onCleared()` delegating to `AppExitController`, and
the facade delegations. The nested types are already top-level declarations by the time this plan starts (the
package-move pass, see step 0); leave them where that pass put them. Types used outside the module must stay public:
`DialogType` (and everything it carries, e.g. plan 07's `SongEditTarget`) and `SongGroups` are named by
`tools/screenshots`, and `settleSynchronizationBeforeExit`/`EXIT_*_GRACE` by `app/desktop`.

### Landing order (each line one commit)

0. **Done by the package-move pass; verify and skip.** That pass moves the nested types (`DialogType`, `Message`,
   `Placeholder`, `SetlistWithSongs`, `Transpositions`, …) out of the class into their packages (`ui.playing`,
   `ui.search`, `ui.screens.export`, `ui.components.sheet`, `ui.songLayout`, `ui.firstRun`, `ui.update`, …) and
   updates `tools/screenshots` (`Shot.kt`, `Website.kt`). Check: `grep -rn "CampfireViewModel\.[A-Z]" presentation tools app`
   finds only `CampfireViewModel.EXIT_*` constants (companion members), then go on.
1. `MessageSink` (+ `launchLibraryChange`) and the shared `asState` (rule 7). Everything else takes it.
2. `SavedStateStore`.
3. `PreferencesController`.
4. `DialogHost` with the before/after listener lists (after plan 09 if that has landed, otherwise keep reading
   `isAnyOverflowMenuOpen`).
5. `PlayingOverrides` (after plan 02).
6. `FontScaleController` (after plan 06; its three collectors are started from three separate `init` positions, rule 2).
7. `MetronomeController`. If plan 10 has landed, its `setStartable` collector is the **last** launch in `init`; give it
   its own `startStartableRule()` called last rather than folding it into the controller's other starts.
8. `ExportController`.
9. `CoverArtSearchController`, `SyncController`.
10. `LibraryState`, `SongListState`, `SongPickerState` (one commit each).
11. `SetlistsController`.
12. `SongTextStore`, then `SongMetadataEditing` (after plan 07), then `SongRenderer` if plan 04 has not landed.
13. `ImportController`, then `FirstRunController`.
14. `EditorSession`.
15. `Navigator` with the back-stack listener list, then `AppExitController`.
16. Facade clean-up: delete delegations that no caller uses any more (plan 03 moves components onto state and
    callbacks but screens keep calling the view model, so expect few), rewrite the `ui/CampfireViewModel.kt` paragraph of `presentation/CLAUDE.md` to list the holders, and
    regenerate the Android Baseline Profile (`./gradlew :app:android:generateBaselineProfile`), whose method names all
    moved. Keep on the view model every member `app/desktop` and `tools/screenshots` call
    (`selectTopLevelDestination`, `toggleLanguageFilter`, `songsScrollPosition`, `songGroups`, `showDialog`,
    `settingsTab`, `openEditor`, `userPreferences`, `toggleMetronome`, `openSong`, `settleSynchronizationBeforeExit`,
    `handleKeyEvent`'s receivers, `EXIT_*_GRACE`), or update those callers in the same commit.

Optional step 2 of the finding (holders as Koin `@Factory`s) is **not recommended**: every holder needs the view
model's scope and several other holders, which would mean `@InjectedParam` plumbing for no gain — tests construct
holders directly with fake use cases (the use cases are single-method interfaces) and a `TestScope`. See Decision.

## Tests

The point of the split is that holders become testable; add `commonTest` suites (with `kotlinx-coroutines-test`,
already a `commonTest` dependency of `:presentation`) for the pure, high-value parts as each holder lands:

- `NavigatorTest`: back-stack listeners are called with the right previous top, in registration order; `navigateBack`
  ends reorder mode first, asks `UnsavedChanges` over an editor with unsaved text, goes to General from another
  settings tab, else pops; `selectTopLevelDestination` rebuilds `[Songs, tab]`, refuses a disabled tab and refuses
  over unsaved editor text; `persistBackStack` truncates past `MAX_SAVED_BACK_STACK_LENGTH`.
- `DialogHostTest`: before/after listeners run in order around the state change; `dismissSheet` ignores a dialog that
  is no longer on screen; `_underlyingSongInfo` is kept only for the song-edit dialogs over the same song.
- `MessageSinkTest`: identical messages in a row get distinct indices; `onMessageShown` removes only that index.
- `ImportControllerTest`: the queue does not start a batch before `demoLibraryDecision` completes.
- `EditorSessionTest`: `editorTextOf` caching and notation round trip (`fileTextOf(editorTextOf(x)) == x` for
  standard-notation text).

Existing suites that must stay green at every step: all of `:presentation:desktopTest` (notably
`navigation/FeatureDestinationsTest`, `navigation/SongDetailsPagesTest`, `metronome/MetronomeContextTest`,
`metronome/SongTempoTest`, `screens/songDetails/SongCapoTest`, `WhatsNewGateTest`, `SearchIndexTest`,
`SongPickerIndexTest`, `SetlistSongToggleTest`), and the screenshot tool must still render
(`./gradlew :tools:screenshots:run`), since it drives the view model through its public API.

## Manual check

After each landing step that moves a collector or a funnel (steps 4, 6, 7, 13, 14, 15), on the desktop app and one
phone: cold start on an empty data directory (demo planted, welcome sheet, no flash of the empty state); open a
deep web address (`/song/<name>/edit`) on the web build; open a song from a setlist, start the click, page to the next
song (click follows from beat one), open the editor (click stops); pinch the text size, kill the app within half a
second and relaunch (size kept); step a tempo and a capo in a setlist, leave immediately, relaunch (both kept); type
in the editor, background the app, kill it, relaunch (draft restored with snackbar); drop two archives while the first
one's conflict question is open (second waits); export a setlist PDF and press Back mid-render (export cancelled,
options kept); desktop Escape over a menu, a dialog, an open search, and with nothing open (exit question).

## Decision

Whether to make the holders Koin definitions after the split:
1. **Keep them constructed inside the view model (recommended).** Nothing outside the view model needs them, tests
   build them directly, and the Koin compiler plugin's graph check stays exactly as it is.
2. Make them `@Factory` with `@InjectedParam scope: CoroutineScope`. More wiring, and the inter-holder dependencies
   (e.g. `ImportController` needs `ExportController`, `Navigator`, `DialogHost`) would have to be Koin definitions too.
