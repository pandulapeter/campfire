/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import com.pandulapeter.campfire.presentation.ui.chrome.toolsItemDestination
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.sync.SyncProgress
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.sync.AuthorizationCompletionPage
import com.pandulapeter.campfire.presentation.ui.components.labels.TagColor
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.firstRun.FirstRunController
import com.pandulapeter.campfire.presentation.ui.launch.LaunchState
import com.pandulapeter.campfire.presentation.ui.fontScale.FontScaleController
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeController
import com.pandulapeter.campfire.presentation.ui.navigation.Navigator
import com.pandulapeter.campfire.presentation.ui.playing.Transpositions
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.domain.api.library.SongFilter
import com.pandulapeter.campfire.domain.api.sync.CancelSyncConnectionUseCase
import com.pandulapeter.campfire.domain.api.sync.CancelSynchronizationUseCase
import com.pandulapeter.campfire.domain.api.catalogue.ClearCoverArtCacheUseCase
import com.pandulapeter.campfire.domain.api.sync.ConnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.setlists.CreateSetlistUseCase
import com.pandulapeter.campfire.domain.api.songs.CreateSongFromTextUseCase
import com.pandulapeter.campfire.domain.api.songs.CreateSongUseCase
import com.pandulapeter.campfire.domain.api.songs.DraftSongFromTextUseCase
import com.pandulapeter.campfire.domain.api.library.DeleteLibraryUseCase
import com.pandulapeter.campfire.domain.api.importing.DownloadImportFileUseCase
import com.pandulapeter.campfire.domain.api.setlists.DeleteSetlistUseCase
import com.pandulapeter.campfire.domain.api.songs.DeleteSongUseCase
import com.pandulapeter.campfire.domain.api.songs.DuplicateSongUseCase
import com.pandulapeter.campfire.domain.api.sync.DisconnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.setlists.EditSetlistUseCase
import com.pandulapeter.campfire.domain.api.songs.EditSongsUseCase
import com.pandulapeter.campfire.domain.api.export.ExportLibraryUseCase
import com.pandulapeter.campfire.domain.api.export.ExportSetlistUseCase
import com.pandulapeter.campfire.domain.api.export.ExportSongsUseCase
import com.pandulapeter.campfire.domain.api.sync.ForgetSyncConnectionUseCase
import com.pandulapeter.campfire.domain.api.catalogue.GetCoverArtCacheSizeUseCase
import com.pandulapeter.campfire.domain.api.songs.GetEditorDraftUseCase
import com.pandulapeter.campfire.domain.api.library.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.songs.GetSongContentInvalidationsUseCase
import com.pandulapeter.campfire.domain.api.songs.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.sync.GetSyncProvidersUseCase
import com.pandulapeter.campfire.domain.api.sync.GetSyncStateUseCase
import com.pandulapeter.campfire.domain.api.preferences.GetUserPreferencesUseCase
import com.pandulapeter.campfire.domain.api.importing.ImportFilesUseCase
import com.pandulapeter.campfire.domain.api.library.IsFirstRunUseCase
import com.pandulapeter.campfire.domain.api.library.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.api.catalogue.LoadSongCreditsUseCase
import com.pandulapeter.campfire.domain.api.text.NormalizeSearchTextUseCase
import com.pandulapeter.campfire.domain.api.importing.PrepareImportUseCase
import com.pandulapeter.campfire.domain.api.library.RememberDemoLibraryFilesUseCase
import com.pandulapeter.campfire.domain.api.songs.RenameSongFileUseCase
import com.pandulapeter.campfire.domain.api.sync.RestoreSyncUseCase
import com.pandulapeter.campfire.domain.api.songs.SaveEditorDraftUseCase
import com.pandulapeter.campfire.domain.api.setlists.SaveSetlistUseCase
import com.pandulapeter.campfire.domain.api.songs.SaveSongContentUseCase
import com.pandulapeter.campfire.domain.api.catalogue.SearchCoverArtUseCase
import com.pandulapeter.campfire.domain.api.catalogue.SearchSongDetailsUseCase
import com.pandulapeter.campfire.domain.api.songs.SetChordProTagUseCase
import com.pandulapeter.campfire.domain.api.sync.StartScheduledSynchronizationUseCase
import com.pandulapeter.campfire.domain.api.sync.SynchronizeLibraryUseCase
import com.pandulapeter.campfire.domain.api.sync.SynchronizeOnReturnUseCase
import com.pandulapeter.campfire.domain.api.sync.WatchRemoteChangesUseCase
import com.pandulapeter.campfire.domain.api.setlists.UpdateSetlistUseCase
import com.pandulapeter.campfire.domain.api.preferences.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.metronome.api.Metronome
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.ui.tuner.TunerController
import com.pandulapeter.campfire.tuner.api.Tuner
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import com.pandulapeter.campfire.tuner.api.model.TunerConfig
import com.pandulapeter.campfire.presentation.ui.dialogs.lookup.filledForm
import com.pandulapeter.campfire.presentation.ui.metronome.SongTiming
import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverrides
import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverridesSnapshot
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import com.pandulapeter.campfire.presentation.ui.playing.Tempos
import com.pandulapeter.campfire.presentation.ui.metronome.isMetronomeScreenLeft
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.dialogs.SavedDialogRestorer
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.NavigationState
import com.pandulapeter.campfire.presentation.ui.navigation.closeScreensShowing
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches
import com.pandulapeter.campfire.presentation.ui.platform.LibraryPersistence
import com.pandulapeter.campfire.presentation.ui.platform.requestLibraryPersistence
import com.pandulapeter.campfire.presentation.ui.platform.takeSyncConsentOrigin
import com.pandulapeter.campfire.presentation.ui.sync.rememberSyncConsentOrigin
import com.pandulapeter.campfire.presentation.ui.sync.showSyncConsentAnswer
import com.pandulapeter.campfire.presentation.ui.playing.Capos
import com.pandulapeter.campfire.presentation.ui.screens.chords.ChordLibraryState
import com.pandulapeter.campfire.presentation.ui.screens.scales.ScaleLibraryState
import com.pandulapeter.campfire.presentation.ui.screens.export.ExportController
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import com.pandulapeter.campfire.presentation.ui.screens.onboarding.OnboardingStep
import com.pandulapeter.campfire.presentation.ui.screens.onboarding.onboardingSteps
import com.pandulapeter.campfire.presentation.ui.screens.setlists.SetlistWithSongs
import com.pandulapeter.campfire.presentation.ui.state.SongLookup
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.EditorSession
import com.pandulapeter.campfire.presentation.ui.songInfo.hasSongInfo
import com.pandulapeter.campfire.presentation.ui.state.AppExitController
import com.pandulapeter.campfire.presentation.ui.state.online.CoverArtSearchController
import com.pandulapeter.campfire.presentation.ui.state.online.SongDetailsLookupController
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportController
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportReportState
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportSource
import com.pandulapeter.campfire.presentation.ui.state.importing.PastedSongController
import com.pandulapeter.campfire.presentation.ui.state.LibraryState
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import com.pandulapeter.campfire.presentation.ui.state.songs.SelectionEditing
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore.Companion.SONG_FILTER_KEY
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore.SavedSongFilter
import com.pandulapeter.campfire.presentation.ui.state.setlists.SetlistsController
import com.pandulapeter.campfire.presentation.ui.state.songs.SongListState
import com.pandulapeter.campfire.presentation.ui.state.songText.SongMetadataEditing
import com.pandulapeter.campfire.presentation.ui.state.songs.SongFilesController
import com.pandulapeter.campfire.presentation.ui.state.songs.SongPickerState
import com.pandulapeter.campfire.presentation.ui.state.songs.SongSelection
import com.pandulapeter.campfire.presentation.ui.state.songText.SongTextStore
import com.pandulapeter.campfire.presentation.ui.sync.SyncController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class CampfireViewModel(
    private val getScreenData: GetScreenDataUseCase,
    getUserPreferences: GetUserPreferencesUseCase,
    private val getSyncState: GetSyncStateUseCase,
    getSyncProviders: GetSyncProvidersUseCase,
    private val loadScreenData: LoadScreenDataUseCase,
    private val isFirstRun: IsFirstRunUseCase,
    private val getSongContent: GetSongContentUseCase,
    private val getEditorDraft: GetEditorDraftUseCase,
    private val saveEditorDraft: SaveEditorDraftUseCase,
    getSongContentInvalidations: GetSongContentInvalidationsUseCase,
    createSong: CreateSongUseCase,
    draftSongFromText: DraftSongFromTextUseCase,
    createSongFromText: CreateSongFromTextUseCase,
    duplicateSong: DuplicateSongUseCase,
    private val editSongs: EditSongsUseCase,
    deleteSong: DeleteSongUseCase,
    private val deleteLibrary: DeleteLibraryUseCase,
    downloadImportFile: DownloadImportFileUseCase,
    private val prepareImport: PrepareImportUseCase,
    private val importFiles: ImportFilesUseCase,
    private val exportSongs: ExportSongsUseCase,
    private val exportSetlist: ExportSetlistUseCase,
    private val exportLibrary: ExportLibraryUseCase,
    private val createSetlist: CreateSetlistUseCase,
    private val saveSetlist: SaveSetlistUseCase,
    private val editSetlist: EditSetlistUseCase,
    private val updateSetlist: UpdateSetlistUseCase,
    renameSongFile: RenameSongFileUseCase,
    private val deleteSetlist: DeleteSetlistUseCase,
    private val saveSongContent: SaveSongContentUseCase,
    private val updateUserPreferences: UpdateUserPreferencesUseCase,
    private val setChordProTag: SetChordProTagUseCase,
    private val connectSyncProvider: ConnectSyncProviderUseCase,
    private val disconnectSyncProvider: DisconnectSyncProviderUseCase,
    private val cancelSyncConnection: CancelSyncConnectionUseCase,
    private val forgetSyncConnection: ForgetSyncConnectionUseCase,
    private val rememberDemoLibraryFiles: RememberDemoLibraryFilesUseCase,
    private val cancelSynchronization: CancelSynchronizationUseCase,
    private val restoreSync: RestoreSyncUseCase,
    private val synchronizeLibrary: SynchronizeLibraryUseCase,
    private val synchronizeOnReturn: SynchronizeOnReturnUseCase,
    private val watchRemoteChanges: WatchRemoteChangesUseCase,
    private val startScheduledSynchronization: StartScheduledSynchronizationUseCase,
    private val normalizeSearchText: NormalizeSearchTextUseCase,
    private val searchCoverArt: SearchCoverArtUseCase,
    getCoverArtCacheSize: GetCoverArtCacheSizeUseCase,
    private val clearCoverArtCache: ClearCoverArtCacheUseCase,
    private val searchSongDetails: SearchSongDetailsUseCase,
    private val loadSongCredits: LoadSongCreditsUseCase,
    /** What the screens draw a song, a key and a search with, see [SongRenderer]. */
    internal val songRenderer: SongRenderer,
    private val metronome: Metronome,
    private val tuner: Tuner,
    /** What survives the system killing the process while the app is in the background, see [SavedStateStore]. */
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val savedStateStore: SavedStateStore = SavedStateStore(savedStateHandle, viewModelScope)

    /** Read by holders built before the snackbar's own members, so built first; it starts nothing. */
    internal val messageSink: MessageSink = MessageSink(viewModelScope)

    /**
     * The tags and libraryState.languages the song list is narrowed to. Held here and in [savedStateStore] only, so it lasts
     * exactly as long as the app does - a process killed in the background and restored is still the same run of the
     * app as far as the user can tell: a filter is a question asked of the library for the moment, and one that came
     * back on the next launch would read as songs having gone missing. Declared before [libraryState.screenData], which is built
     * from it.
     */
    private val _songFilter = MutableStateFlow(
        restore<SavedSongFilter>(SONG_FILTER_KEY)
            ?.let { SongFilter(selectedTags = it.selectedTags.toSet(), selectedLanguages = it.selectedLanguages.toSet()) }
            ?: SongFilter()
    )

    internal val libraryState: LibraryState = LibraryState(
        scope = viewModelScope,
        getScreenData = getScreenData,
        songFilter = _songFilter,
        loadScreenData = loadScreenData,
        normalizeSearchText = normalizeSearchText,
    )

    internal val dialogHost: DialogHost = DialogHost(viewModelScope).apply {
        addBeforeDialogChange { _, dialogType ->
            // Editing can rename the setlist's file, which is what the mode is keyed by, so it ends the mode as the other
            // ways of leaving the rows do rather than losing it to a name that has stopped existing.
            if (dialogType is DialogType.Export || dialogType is DialogType.DuplicateSetlist || dialogType is DialogType.EditSetlist) {
                setlistsController.reorderingSetlistFileName = null
            }
        }
        addBeforeDialogChange { _, dialogType ->
            appExitController.onBeforeDialogChange(dialogType)
        }
        addBeforeDialogChange { previousDialog, dialogType ->
            // However the export screen goes - closed, Escape, the web's Back, another dialog put over it - it stays drawn
            // while it slides away, so its own disposal would be too late: its options are saved and its drawing cancelled
            // here, a screen opened again in the next moment finds the options it left, and its Save, Share and options do
            // nothing once it is no longer the dialog on screen.
            if (previousDialog is DialogType.Export && dialogType != previousDialog) {
                exportController.flushPrintSettings()
                // An export nobody is looking at any more would put its picker up over whatever is on screen by then.
                exportController.cancelPdfExport()
            }
        }
        addBeforeDialogChange { _, dialogType ->
            // The export screen covers the song the click is played from as a screen of its own would, and leaves no way
            // to stop it, so it stops a click the way pushing a destination does (see navigator.updateBackStack). The tuner sheet
            // listens to the room through the audio session the click plays in, and the two never hold the audio together.
            if (dialogType is DialogType.Export || dialogType is DialogType.Tuner) metronome.stop()
        }
        addBeforeDialogChange { previousDialog, dialogType ->
            // The tuner sheet listens for as long as it is up and not a moment longer, as the Tuner tab does.
            if (previousDialog is DialogType.Tuner && dialogType !is DialogType.Tuner) tunerController.stopTuner()
        }
        addBeforeDialogChange { previousDialog, dialogType ->
            // A scale plays only under the sheet that shows it: closed, or covered by a chord's sheet, it stops.
            if (previousDialog is DialogType.Scale && dialogType != previousDialog) tunerController.stopScale()
        }
        addOpenDialogsChange { before, after ->
            savedDialogRestorer.onOpenDialogsChanged(after)
            coverArtSearchController.onOpenDialogsChanged(before = before, after = after)
            songDetailsLookupController.onOpenDialogsChanged(before = before, after = after)
        }
    }

    // Navigation
    internal val navigator: Navigator = Navigator(
        savedStateStore = savedStateStore,
        dialogHost = dialogHost,
        songsSearch = { songListState.songsSearch },
        setlistsSearch = { setlistsController.setlistsSearch },
        importReportSearch = { importReportState.search },
        chordLibrary = { chordLibrary },
        scaleLibrary = { scaleLibrary },
        // The view model's copy of the preferences, which is what the onboarding's pages draw their dots from.
        onboardingSteps = { onboardingSteps(userPreferences.value) },
        topLevelDestinations = { preferencesController.topLevelDestinations.value },
        userPreferences = { preferencesController.userPreferencesState.value.data },
        hasUnsavedEditorText = { editorSession.hasUnsavedEditorText() },
        hasUnsavedEditorChanges = { editorSession.hasUnsavedEditorChanges.value },
        isSetlistReordering = { setlistsController.isSetlistReordering },
        endSetlistReordering = { setlistsController.reorderingSetlistFileName = null },
        isSongSelecting = { songSelection.isSongSelecting },
        endSongSelection = { songSelection.end() },
        onTransitionEnded = { songTextStore.pruneSongTexts() },
    ).apply {
        addOnBackStackChanged { _, stack ->
            if (stack.lastOrNull() != CampfireDestination.Setlists) setlistsController.reorderingSetlistFileName = null
        }
        addOnBackStackChanged { _, stack ->
            // A tab tapped, a song opened by an import, the import screen: none of them may stand over a mode nobody can
            // see, so leaving the songs drops the selection, as the reorder mode is dropped with its screen.
            if (stack.lastOrNull() != CampfireDestination.Songs) songSelection.end()
        }
        addOnBackStackChanged { previousTop, stack ->
            // The two screens that hold a metronome are the two it can be stopped from, so a click never outlives the one
            // it was started on: the editor opened over a song, a song closed, a tab selected, all stop it - and so does a
            // different screen arriving on top, a song opened over the Metronome tab or over another song, which holds a
            // metronome but not the one that was started. The panel the click was played from is a preference and stays
            // where the user put it, so the next song is read to a click without asking for the instrument again.
            if (isMetronomeScreenLeft(previousTop = previousTop, top = stack.lastOrNull())) metronome.stop()
        }
        addOnBackStackChanged { previousTop, stack ->
            // Nothing of the tuner outlives the screen it is used on: the tab, or the song its sheet is opened over. The
            // screen starts it again once it is composed, and only that screen does - which a sheet left over another screen
            // never is, so the sheet goes with the song it was opened over.
            if (previousTop?.contentKey != stack.lastOrNull()?.contentKey) {
                tunerController.stopTuner()
                dialogHost.dismissSheet(DialogType.Tuner)
            }
        }
        addOnBackStackChanged { _, _ -> editorSession.onBackStackChanged() }
        addOnBackStackChanged { _, stack -> metronomeController.onBackStackChanged(stack) }
        addOnBackStackChanged { _, _ -> importController.onBackStackChanged() }
    }

    internal val songSelection: SongSelection = SongSelection(scope = viewModelScope, backStack = navigator.backStack)

    /** The key the Chords and the Scales tabs both list, see [ChordLibraryState.keyIndex]. */
    private val libraryKeyIndex = mutableIntStateOf(0)

    /** Where the Chords tab was left, see [ChordLibraryState]. */
    internal val chordLibrary = ChordLibraryState(keyIndex = libraryKeyIndex)

    /** Where the Scales tab was left, and how the scale sheet labels its notes, see [ScaleLibraryState]. */
    internal val scaleLibrary = ScaleLibraryState(keyIndex = libraryKeyIndex)

    internal val fontScaleController: FontScaleController = FontScaleController(
        scope = viewModelScope,
        backStack = navigator.backStack,
        dialogHost = dialogHost,
        updateUserPreferences = updateUserPreferences,
        writeDelayMillis = PREFERENCE_WRITE_DEBOUNCE_MILLIS,
    )

    /** See [FontScaleController.magnifyByTouchpad]. */
    fun magnifyByTouchpad(factor: Float) = fontScaleController.magnifyByTouchpad(factor)

    internal val preferencesController: PreferencesController = PreferencesController(
        scope = viewModelScope,
        getUserPreferences = getUserPreferences,
        updateUserPreferences = updateUserPreferences,
        requestPersistence = ::requestLibraryPersistence,
    )

    /** See [PreferencesController.userPreferences]. */
    val userPreferences: StateFlow<UserPreferences?> get() = preferencesController.userPreferences

    internal val syncController: SyncController = SyncController(
        scope = viewModelScope,
        messageSink = messageSink,
        getSyncState = getSyncState,
        getSyncProviders = getSyncProviders,
        connectSyncProvider = connectSyncProvider,
        disconnectSyncProvider = disconnectSyncProvider,
        cancelSyncConnection = cancelSyncConnection,
        forgetSyncConnection = forgetSyncConnection,
        restoreSync = restoreSync,
        synchronizeLibrary = synchronizeLibrary,
        synchronizeOnReturn = synchronizeOnReturn,
        watchRemoteChanges = watchRemoteChanges,
        cancelSynchronization = cancelSynchronization,
    )

    internal val songTextStore: SongTextStore = SongTextStore(
        scope = viewModelScope,
        backStack = navigator.backStack,
        editorDraftFileName = { editorSession.editorDraft.value?.fileName },
        messageSink = messageSink,
        getSongContent = getSongContent,
        saveSongContent = saveSongContent,
        getSongContentInvalidations = getSongContentInvalidations,
    )

    internal val songMetadataEditing: SongMetadataEditing = SongMetadataEditing(
        dialogHost = dialogHost,
        messageSink = messageSink,
        songTextStore = songTextStore,
        songRenderer = songRenderer,
        editorNotation = { editorSession.editorNotation },
        retainedEditorField = { editorSession.retainedEditorField(it) },
        editorDraft = { editorSession.editorDraft.value },
        emitEditorTextEdit = { editorSession.emitEditorTextEdit(it) },
        setChordProTag = setChordProTag,
    )

    internal val selectionEditing: SelectionEditing = SelectionEditing(
        scope = viewModelScope,
        messageSink = messageSink,
        editSongs = editSongs,
        setChordProTag = setChordProTag,
    )

    internal val editorSession: EditorSession = EditorSession(
        scope = viewModelScope,
        dialogHost = dialogHost,
        messageSink = messageSink,
        songTextStore = songTextStore,
        songRenderer = songRenderer,
        backStack = navigator.backStack,
        userPreferences = userPreferences,
        arePreferencesLoaded = preferencesController.arePreferencesLoaded,
        getSongContent = getSongContent,
        getEditorDraft = getEditorDraft,
        saveEditorDraft = saveEditorDraft,
        draftSongFromText = draftSongFromText,
        createSongFromText = createSongFromText,
        updateBackStack = { navigator.updateBackStack(update = it) },
        isSongShownOnTop = navigator::isSongShownOnTop,
        popBackStack = { navigator.popBackStack() },
        takePendingExit = { appExitController.takePendingExit() },
        requestExit = { onExit, onCancelled -> requestExit(onExit = onExit, onCancelled = onCancelled) },
        followNewSongCreated = navigator::followNewSongCreated,
    )

    internal val overrides: PlayingOverrides = PlayingOverrides(
        scope = viewModelScope,
        userPreferences = userPreferences,
        setlists = libraryState.setlists,
        songsByFileName = { libraryState.songsByFileName.value },
        updateUserPreferences = updateUserPreferences,
        updateEditableSetlist = { fileName, transform -> updateEditableSetlist(fileName, transform) },
        messageSink = messageSink,
        writeDelayMillis = PREFERENCE_WRITE_DEBOUNCE_MILLIS,
    )

    // Metronome

    internal val metronomeController: MetronomeController = MetronomeController(
        scope = viewModelScope,
        metronome = metronome,
        backStack = navigator.backStack,
        currentSongFileName = { navigator.currentSongFileName(it) },
        dialogHost = dialogHost,
        userPreferences = userPreferences,
        tempos = overrides.tempos,
        songsByFileName = libraryState.songsByFileName,
        songsBeingRenamed = songTextStore.songsBeingRenamed,
        messageSink = messageSink,
        updateUserPreferences = updateUserPreferences,
        writeDelayMillis = PREFERENCE_WRITE_DEBOUNCE_MILLIS,
    )

    internal val songPickerState: SongPickerState = SongPickerState(
        scope = viewModelScope,
        savedStateStore = savedStateStore,
        indexedSongs = libraryState.indexedSongs,
        screenData = libraryState.screenData,
    )

    internal val coverArtSearchController: CoverArtSearchController = CoverArtSearchController(
        scope = viewModelScope,
        searchCoverArt = searchCoverArt,
        getCoverArtCacheSize = getCoverArtCacheSize,
        clearCoverArtCache = clearCoverArtCache,
        songTextOf = { songMetadataEditing.songTextOf(it) },
    )

    internal val songDetailsLookupController: SongDetailsLookupController = SongDetailsLookupController(
        scope = viewModelScope,
        searchSongDetails = searchSongDetails,
        loadSongCredits = loadSongCredits,
    )

    internal val importReportState: ImportReportState = ImportReportState(
        scope = viewModelScope,
        dialogHost = dialogHost,
        backStack = navigator.backStack,
        updateBackStack = { navigator.updateBackStack(update = it) },
        songTexts = songTextStore.songTexts,
        editorDraft = editorSession.editorDraft,
        hasUnsavedEditorText = { editorSession.hasUnsavedEditorText() },
    )

    internal val importController: ImportController = ImportController(
        scope = viewModelScope,
        dialogHost = dialogHost,
        messageSink = messageSink,
        backStack = navigator.backStack,
        reportState = importReportState,
        openImportedSong = { navigator.openImportedSong(it) },
        openNewSongEditor = { text, suggestion -> editorSession.openNewSongEditor(text, suggestion) },
        areSongsEnabled = { preferencesController.areSongsEnabled.value },
        songLookup = libraryState.songLookup,
        launchFileTransfer = { exportController.launchFileTransfer(it) },
        isLeaving = { isLeaving },
        downloadImportFile = downloadImportFile,
        prepareImport = prepareImport,
        importFiles = importFiles,
        rememberDemoLibraryFiles = rememberDemoLibraryFiles,
        deleteLibrary = deleteLibrary,
    )

    internal val pastedSongController: PastedSongController = PastedSongController(
        scope = viewModelScope,
        messageSink = messageSink,
        dialogHost = dialogHost,
        backStack = navigator.backStack,
        isImporting = importController.isImporting,
        isPerformanceModeEnabled = preferencesController.isPerformanceModeEnabled,
        enqueueImport = { files, isNewSongFromText, suggestion ->
            importController.enqueueImport(
                source = ImportSource.Files(files),
                shouldOpenSong = !isNewSongFromText,
                isNewSongFromText = isNewSongFromText,
                suggestion = suggestion,
            )
        },
        importSharedLink = { importController.importSharedLink(it) },
    )

    internal val songFilesController: SongFilesController = SongFilesController(
        messageSink = messageSink,
        songTextStore = songTextStore,
        showDialog = { dialogHost.showDialog(it) },
        duplicateSongDialog = songMetadataEditing::duplicateSongDialog,
        openEditor = { fileName, shouldStartInsideFirstSection -> editorSession.openEditor(fileName, shouldStartInsideFirstSection) },
        clearEditorDraft = editorSession::clearEditorDraft,
        followMetronomeRename = metronomeController::followSongRename,
        followScreensRename = navigator::followSongRename,
        closeScreensShowing = { navigator.closeScreensShowing(it) },
        followReportedFileNames = importReportState::followReportedFileNames,
        getSongContent = getSongContent,
        createSongFile = createSong,
        duplicateSongFile = duplicateSong,
        renameSongFile = renameSongFile,
        deleteSongFile = deleteSong,
    )

    private val savedDialogRestorer: SavedDialogRestorer = SavedDialogRestorer(
        scope = viewModelScope,
        savedStateStore = savedStateStore,
        dialogHost = dialogHost,
        backStack = navigator.backStack,
        songLookup = libraryState.songLookup,
        setlists = libraryState.setlists,
        editorDraft = editorSession.editorDraft,
        songTexts = songTextStore.songTexts,
        loadSongContent = { songTextStore.loadSongContent(it) },
        duplicateSongDialogOf = { songFilesController.duplicateSongDialogOf(it) },
        showSavedSongEdit = songMetadataEditing::showSavedSongEdit,
    )

    internal val songListState: SongListState = SongListState(
        scope = viewModelScope,
        savedStateStore = savedStateStore,
        mutableSongFilter = _songFilter,
        screenData = libraryState.screenData,
        indexedSongs = libraryState.indexedSongs,
        tags = libraryState.tags,
        languages = libraryState.languages,
        isImporting = importController.isImporting,
        normalizeSearchText = normalizeSearchText,
        changeUserPreferences = { preferencesController.changeUserPreferences(it) },
    )

    internal val setlistsController: SetlistsController = SetlistsController(
        scope = viewModelScope,
        savedStateStore = savedStateStore,
        backStack = navigator.backStack,
        userPreferences = userPreferences,
        setlists = libraryState.setlists,
        allSongs = libraryState.allSongs,
        indexedSongs = libraryState.indexedSongs,
        screenData = libraryState.screenData,
        isImporting = importController.isImporting,
        dialogHost = dialogHost,
        messageSink = messageSink,
        createSetlist = createSetlist,
        saveSetlist = saveSetlist,
        editSetlist = editSetlist,
        updateSetlist = updateSetlist,
        deleteSetlist = deleteSetlist,
        normalizeSearchText = normalizeSearchText,
        followReportedFileNames = { importReportState.followReportedFileNames(it) },
        changeUserPreferences = { preferencesController.changeUserPreferences(it) },
    )

    internal val exportController: ExportController = ExportController(
        scope = viewModelScope,
        dialogHost = dialogHost,
        messageSink = messageSink,
        screenData = libraryState.screenData,
        userPreferencesState = preferencesController.userPreferencesState,
        transpositions = overrides.transpositions,
        effectiveTempoOf = { songFileName, setlistFileName -> overrides.effectiveTempoOf(songFileName, setlistFileName) },
        effectiveCapoOf = { songFileName, setlistFileName -> overrides.effectiveCapoOf(songFileName, setlistFileName) },
        songRenderer = songRenderer,
        getSongContent = getSongContent,
        exportSongs = exportSongs,
        exportSetlist = exportSetlist,
        exportLibrary = exportLibrary,
        updateUserPreferences = updateUserPreferences,
        writeDelayMillis = PREFERENCE_WRITE_DEBOUNCE_MILLIS,
    )

    // Tuner

    internal val tunerController: TunerController = TunerController(
        scope = viewModelScope,
        tuner = tuner,
        userPreferences = userPreferences,
        updateUserPreferences = updateUserPreferences,
        writeDelayMillis = PREFERENCE_WRITE_DEBOUNCE_MILLIS,
    )

    internal val appExitController: AppExitController = AppExitController(
        scope = viewModelScope,
        metronome = metronome,
        tuner = tuner,
        dialogHost = dialogHost,
        backStack = navigator.backStack,
        editorSession = editorSession,
        importController = importController,
        syncController = syncController,
        overrides = overrides,
        waitingPreferences = listOf(
            fontScaleController.fontScalePreference,
            metronomeController.metronomeSettingsPreference,
            tunerController.tunerSettingsPreference,
            exportController.printSettingsPreference,
        ),
        updateUserPreferences = updateUserPreferences,
        getSyncState = getSyncState,
        startScheduledSynchronization = startScheduledSynchronization,
        stopSilentClick = metronomeController::stopSilentClick,
    )

    private val isLeaving get() = appExitController.isLeaving

    // Dialogs

    /**
     * Whether this is the first launch of this installation, asked once and shared. It stops being true the moment
     * the preferences are written, which is the last thing the demo library planting does, so a second caller
     * asking the question again would be answered by whichever coroutine got there first. Two of them need it: the
     * demo library, and the sync connection a reinstalled app must not inherit (see [forgetSyncConnection]).
     *
     * An answer that could not be read is false: neither caller may act on a guess.
     */
    private val isFirstLaunch = viewModelScope.async {
        try {
            isFirstRun()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not tell whether this is a first run: ${exception.message}")
            false
        }
    }

    internal val firstRunController: FirstRunController = FirstRunController(
        scope = viewModelScope,
        isFirstLaunch = isFirstLaunch,
        dialogHost = dialogHost,
        messageSink = messageSink,
        importController = importController,
        importReport = importReportState.importReport,
        backStack = navigator.backStack,
        screenData = libraryState.screenData,
        userPreferencesState = preferencesController.userPreferencesState,
        songFilter = _songFilter,
        getScreenData = getScreenData,
        updateUserPreferences = updateUserPreferences,
        openOnboarding = { navigator.openOnboarding(isOpenedFromSettings = false) },
        awaitAppOnScreen = { launchState.isAppOnScreen.first { it } },
    )

    internal val launchState: LaunchState = LaunchState(
        scope = viewModelScope,
        screenData = libraryState.screenData,
        isDemoLibraryPending = firstRunController.isDemoLibraryPending,
        isEditorDraftRecoveryPending = editorSession.isEditorDraftRecoveryPending,
        editorDraftRecovery = editorSession.editorDraftRecovery,
        userPreferencesState = preferencesController.userPreferencesState,
        backStack = navigator.backStack,
        restoreNavigationState = { navigator.restoreNavigationState(it) },
    )

    init {
        libraryState.startLoading()
        viewModelScope.launch { firstRunController.plantDemoLibraryOnFirstRun() }
        viewModelScope.launch { firstRunController.showWhatsNewOnVersionChange() }
        // The origin is taken whatever happens next, so that it cannot be mistaken later for where another consent was asked from.
        syncController.startRestoring(isFirstLaunch = isFirstLaunch, onConsentAnswered = { navigator.showSyncConsentAnswer(takeSyncConsentOrigin()) })
        songTextStore.startFollowingFiles()
        importController.startQueue(firstRunController.demoLibraryDecision)
        importReportState.startReportingInterruption(savedStateStore, messageSink)
        navigator.addOnBackStackChanged { _, stack -> firstRunController.onBackStackChanged(stack) }
        dialogHost.startClosingWithSong(
            allSongs = libraryState.allSongs,
            isLoading = libraryState.isLoading,
            songsBeingRenamed = songTextStore.songsBeingRenamed,
        )
        editorSession.startRecovery()
        savedDialogRestorer.start()
        songSelection.startPruning(libraryState.screenData)
        viewModelScope.launch {
            // Performance mode leaves no row menu to start the mode from, and no way to act on what is ticked.
            preferencesController.isPerformanceModeEnabled.collect { if (it) songSelection.end() }
        }
        viewModelScope.launch {
            // From the first read on, so that a start with the songs switched off opens on the first tool rather than
            // on a songs screen that is not there.
            preferencesController.userPreferences.map { FeatureSwitches.of(it) }.distinctUntilChanged().collect { navigator.followFeatureSwitches(it) }
        }
        savedStateStore.startPersisting(
            songFilter = _songFilter,
            songPickerSelectedTags = songPickerState.songPickerSelectedTags,
            songPickerSelectedLanguages = songPickerState.songPickerSelectedLanguages,
            songsSearch = songListState.songsSearch,
            setlistsSearch = setlistsController.setlistsSearch,
        )
        fontScaleController.startWriter()
        exportController.startPrintSettingsWriter()
        metronomeController.startSettingsWriter()
        overrides.startSettling()
        metronomeController.startFollowingPattern()
        metronomeController.startReportingStops()
        fontScaleController.startEcho(userPreferences)
        fontScaleController.startSettle()
        metronomeController.startStartableRule()
        tunerController.startSettingsWriter()
        tunerController.startFollowingSettings()
        tunerController.startTrackingTunedNotes()
        tunerController.startStartableRule(backStack = navigator.backStack, visibleDialog = dialogHost.visibleDialog)
    }

    // Navigation

    fun selectTopLevelDestination(destination: CampfireDestination.TopLevel) = selectTopLevelDestination(destination, destinationWhenAtTop = null)

    /**
     * The chrome's Tools item (the navigation bar's, or a rail's too short for every item), opening [destination] (see
     * `toolsItemDestination`): pressed again on a tool at its top, it goes on to the first tool switched on, as the Settings
     * item goes on to General.
     */
    fun selectToolsItem(destination: CampfireDestination.TopLevel) = selectTopLevelDestination(
        destination = destination,
        destinationWhenAtTop = CampfireDestination.TopLevel.tools.firstOrNull { it in preferencesController.topLevelDestinations.value },
    )

    private fun selectTopLevelDestination(destination: CampfireDestination.TopLevel, destinationWhenAtTop: CampfireDestination.TopLevel?) {
        val wasTunerShown = navigator.backStack.lastOrNull() == CampfireDestination.Tuner
        navigator.selectTopLevelDestination(destination, destinationWhenAtTop)
        // Only where the tab did open: a refused navigation would otherwise leave the mark for a tuner opened later without a tap.
        if (!wasTunerShown && navigator.backStack.lastOrNull() == CampfireDestination.Tuner) tunerController.markOpenedByTap()
    }

    /** See [Navigator.openOnboarding]; the settings screen's way into it, a first run's being [FirstRunController]'s. */
    fun openOnboarding() = navigator.openOnboarding(isOpenedFromSettings = true)

    /** The pages the onboarding shows with the features as they are now, see [onboardingSteps]. */
    internal fun onboardingSteps(preferences: UserPreferences?) = onboardingSteps(
        canConnectDropbox = SyncProviderId.DROPBOX in syncController.syncProviders,
        areChordsEnabled = preferences?.areChordsEnabled != false,
    )

    fun requestExit(onExit: () -> Unit, onCancelled: () -> Unit = {}) = appExitController.requestExit(onExit, onCancelled)

    /** See [AppExitController.settleSynchronizationBeforeExit]. */
    suspend fun settleSynchronizationBeforeExit() = appExitController.settleSynchronizationBeforeExit()

    // Songs

    /** See [SongSelection.showSetlists]. */
    internal fun showSelectionSetlists() = songSelection.showSetlists(sortedSongs(), dialogHost::showDialog)

    internal fun showSelectionTags() = songSelection.showTags(sortedSongs(), dialogHost::showDialog)

    internal fun showSelectionLanguages() = songSelection.showLanguages(sortedSongs(), dialogHost::showDialog)

    /** See [SongSelection.export]. */
    internal fun exportSelection() = songSelection.export(sortedSongs(), libraryState.songsByFileName.value, dialogHost::showDialog)

    private fun sortedSongs() = libraryState.screenData.value.data?.sortedSongs.orEmpty()

    /** Puts the song details lookup up over [dialog], the metadata form, which holds [draft] at the moment. */
    fun showSongDetailsLookup(dialog: DialogType.SongMetadata, draft: Map<ChordProMetadataFields.Field, String>) = dialogHost.showDialog(
        DialogType.SongDetailsLookup(song = dialog.song, target = dialog.target, values = dialog.values, draft = draft, formFillCount = dialog.fillCount),
    )

    /**
     * Fills [draft] into the metadata form [dialog] was put up over, which is still open under it and is updated where
     * it is, see [DialogHost.updateUnderlyingDialog]; the lookup then closes as any sheet does, uncovering it.
     */
    fun fillSongMetadata(dialog: DialogType.SongDetailsLookup, draft: Map<ChordProMetadataFields.Field, String>) =
        dialogHost.updateUnderlyingDialog(dialog.filledForm(draft))

    /** Opens the tuner sheet over the song from its tuner button or menu entry, see [TunerController.markOpenedByTap]. */
    fun openTunerSheet() {
        dialogHost.showDialog(DialogType.Tuner)
        if (dialogHost.visibleDialog.value is DialogType.Tuner) tunerController.markOpenedByTap()
    }

    /** See [AppExitController.onCleared]. */
    override fun onCleared() = appExitController.onCleared()

    private suspend fun updateEditableSetlist(fileName: String, transform: (Setlist) -> Setlist): Setlist? =
        setlistsController.updateEditableSetlist(fileName, transform)

    // User preferences

    internal fun setTagColor(tag: String, color: TagColor?) = preferencesController.setTagColor(tag, color?.id)

    // Sync

    fun connectSyncProvider(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage) {
        navigator.rememberSyncConsentOrigin()
        syncController.connectSyncProvider(providerId, completionPage)
    }

    // Helpers

    private inline fun <reified T> restore(key: String): T? = savedStateStore.restore(key)

    companion object {
        private const val PREFERENCE_WRITE_DEBOUNCE_MILLIS = 500L
    }
}
