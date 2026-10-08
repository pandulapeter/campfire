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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.ChordProTempo
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.chordpro.ChordProMetadataFields
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.ImportProgress
import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.MetronomeSettings
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.normalizedTags
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.presentation.ui.chords.toChordInstrument
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.LabelsOnEverySong
import com.pandulapeter.campfire.presentation.ui.components.Placeholder
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.firstRun.FirstRunController
import com.pandulapeter.campfire.presentation.ui.fontScale.FontScaleController
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeController
import com.pandulapeter.campfire.presentation.ui.navigation.Navigator
import com.pandulapeter.campfire.presentation.ui.playing.Transpositions
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.models.SongFilter
import com.pandulapeter.campfire.domain.api.useCases.CancelSyncConnectionUseCase
import com.pandulapeter.campfire.domain.api.useCases.CancelSynchronizationUseCase
import com.pandulapeter.campfire.domain.api.useCases.ClearCoverArtCacheUseCase
import com.pandulapeter.campfire.domain.api.useCases.ConnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.useCases.ConvertChordProNotationUseCase
import com.pandulapeter.campfire.domain.api.useCases.ConvertChordProTextNotationUseCase
import com.pandulapeter.campfire.domain.api.useCases.CreateSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.CreateSongUseCase
import com.pandulapeter.campfire.domain.api.useCases.DeleteLibraryUseCase
import com.pandulapeter.campfire.domain.api.useCases.DeleteSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.DeleteSongUseCase
import com.pandulapeter.campfire.domain.api.useCases.DisconnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.useCases.EditSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.ExportLibraryUseCase
import com.pandulapeter.campfire.domain.api.useCases.ExportSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.ExportSongsUseCase
import com.pandulapeter.campfire.domain.api.useCases.ForgetSyncConnectionUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetCoverArtCacheSizeUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetEditorDraftUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentInvalidationsUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSyncProvidersUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSyncStateUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetUserPreferencesUseCase
import com.pandulapeter.campfire.domain.api.useCases.ImportFilesUseCase
import com.pandulapeter.campfire.domain.api.useCases.IsFirstRunUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeSearchTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.ParseChordProUseCase
import com.pandulapeter.campfire.domain.api.useCases.PrepareImportUseCase
import com.pandulapeter.campfire.domain.api.useCases.PrettifyChordProUseCase
import com.pandulapeter.campfire.domain.api.useCases.RememberDemoLibraryFilesUseCase
import com.pandulapeter.campfire.domain.api.useCases.RenameSongFileUseCase
import com.pandulapeter.campfire.domain.api.useCases.RestoreSyncUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveEditorDraftUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.SearchCoverArtUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProCoverArtUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProLanguagesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProLinksUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProMetadataUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProTagUseCase
import com.pandulapeter.campfire.domain.api.useCases.StartScheduledSynchronizationUseCase
import com.pandulapeter.campfire.domain.api.useCases.SynchronizeLibraryUseCase
import com.pandulapeter.campfire.domain.api.useCases.UpdateSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.metronome.api.Metronome
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import com.pandulapeter.campfire.presentation.ui.components.isAnyOverflowMenuOpen
import com.pandulapeter.campfire.presentation.ui.dialogs.SONG_METADATA_FIELDS
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeContext
import com.pandulapeter.campfire.presentation.ui.metronome.SongTiming
import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverrides
import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverridesSnapshot
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import com.pandulapeter.campfire.presentation.ui.playing.Tempos
import com.pandulapeter.campfire.presentation.ui.metronome.isMetronomeScreenLeft
import com.pandulapeter.campfire.presentation.ui.playing.withTempo
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.NavigationState
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LibraryPersistence
import com.pandulapeter.campfire.presentation.ui.platform.requestLibraryPersistence
import com.pandulapeter.campfire.presentation.ui.playing.CapoKey
import com.pandulapeter.campfire.presentation.ui.playing.Capos
import com.pandulapeter.campfire.presentation.ui.playing.TempoKey
import com.pandulapeter.campfire.presentation.ui.playing.effectiveCapo
import com.pandulapeter.campfire.presentation.ui.playing.withCapo
import com.pandulapeter.campfire.presentation.ui.screens.export.ExportController
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import com.pandulapeter.campfire.presentation.ui.screens.setlists.SetlistWithSongs
import com.pandulapeter.campfire.presentation.ui.screens.settings.DemoLibraryOffer
import com.pandulapeter.campfire.presentation.ui.screens.settings.LibrarySummary
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsTab
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab
import com.pandulapeter.campfire.presentation.ui.playing.SongOverrides
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.FONT_SCALE_STEP
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.FontScaleAccumulator
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.PINCH_SENSITIVITY
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.hasSongInfo
import com.pandulapeter.campfire.presentation.ui.fontScale.FontScaleAccumulator
import com.pandulapeter.campfire.presentation.ui.fontScale.PINCH_SENSITIVITY
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.EditorSession
import com.pandulapeter.campfire.presentation.ui.songInfo.hasSongInfo
import com.pandulapeter.campfire.presentation.ui.state.CoverArtSearchController
import com.pandulapeter.campfire.presentation.ui.state.DebouncedPreference
import com.pandulapeter.campfire.presentation.ui.state.ImportController
import com.pandulapeter.campfire.presentation.ui.state.ImportController.ImportRequest
import com.pandulapeter.campfire.presentation.ui.state.LibraryState
import com.pandulapeter.campfire.presentation.ui.state.PendingExit
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore.Companion.SONG_FILTER_KEY
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore.SavedSongFilter
import com.pandulapeter.campfire.presentation.ui.state.SetlistsController
import com.pandulapeter.campfire.presentation.ui.state.SongListState
import com.pandulapeter.campfire.presentation.ui.state.SongMetadataEditing
import com.pandulapeter.campfire.presentation.ui.state.SongPickerState
import com.pandulapeter.campfire.presentation.ui.state.SongTextStore
import com.pandulapeter.campfire.presentation.ui.state.SyncController
import com.pandulapeter.campfire.presentation.ui.state.asState
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.EditorTextEdit
import com.pandulapeter.campfire.presentation.ui.search.SearchableSong
import com.pandulapeter.campfire.presentation.ui.search.pickerFilterOptions
import com.pandulapeter.campfire.presentation.ui.search.toPickableSong
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import com.pandulapeter.campfire.presentation.ui.state.emptyPlaceholder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.koin.core.annotation.KoinViewModel

@OptIn(FlowPreview::class)
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
    private val createSong: CreateSongUseCase,
    private val deleteSong: DeleteSongUseCase,
    private val deleteLibrary: DeleteLibraryUseCase,
    private val prepareImport: PrepareImportUseCase,
    private val importFiles: ImportFilesUseCase,
    private val exportSongs: ExportSongsUseCase,
    private val exportSetlist: ExportSetlistUseCase,
    private val exportLibrary: ExportLibraryUseCase,
    private val createSetlist: CreateSetlistUseCase,
    private val saveSetlist: SaveSetlistUseCase,
    private val editSetlist: EditSetlistUseCase,
    private val updateSetlist: UpdateSetlistUseCase,
    private val renameSongFile: RenameSongFileUseCase,
    private val deleteSetlist: DeleteSetlistUseCase,
    private val saveSongContent: SaveSongContentUseCase,
    private val updateUserPreferences: UpdateUserPreferencesUseCase,
    private val setChordProCoverArt: SetChordProCoverArtUseCase,
    private val setChordProLanguages: SetChordProLanguagesUseCase,
    private val setChordProTag: SetChordProTagUseCase,
    private val setChordProLinks: SetChordProLinksUseCase,
    private val setChordProMetadata: SetChordProMetadataUseCase,
    private val connectSyncProvider: ConnectSyncProviderUseCase,
    private val disconnectSyncProvider: DisconnectSyncProviderUseCase,
    private val cancelSyncConnection: CancelSyncConnectionUseCase,
    private val forgetSyncConnection: ForgetSyncConnectionUseCase,
    private val rememberDemoLibraryFiles: RememberDemoLibraryFilesUseCase,
    private val cancelSynchronization: CancelSynchronizationUseCase,
    private val restoreSync: RestoreSyncUseCase,
    private val synchronizeLibrary: SynchronizeLibraryUseCase,
    private val startScheduledSynchronization: StartScheduledSynchronizationUseCase,
    private val normalizeSearchText: NormalizeSearchTextUseCase,
    private val parseChordPro: ParseChordProUseCase,
    private val searchCoverArt: SearchCoverArtUseCase,
    getCoverArtCacheSize: GetCoverArtCacheSizeUseCase,
    private val clearCoverArtCache: ClearCoverArtCacheUseCase,
    /** What the screens draw a song, a key and a search with, see [SongRenderer]. */
    internal val songRenderer: SongRenderer,
    private val metronome: Metronome,
    /** What survives the system killing the process while the app is in the background, see [SavedStateStore]. */
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val savedStateStore: SavedStateStore = SavedStateStore(savedStateHandle, viewModelScope)

    /** Read by holders built before the snackbar's own members, so built first; it starts nothing. */
    private val messageSink: MessageSink = MessageSink(viewModelScope)

    /**
     * The tags and languages the song list is narrowed to. Held here and in [savedStateStore] only, so it lasts
     * exactly as long as the app does - a process killed in the background and restored is still the same run of the
     * app as far as the user can tell: a filter is a question asked of the library for the moment, and one that came
     * back on the next launch would read as songs having gone missing. Declared before [screenData], which is built
     * from it.
     */
    private val _songFilter = MutableStateFlow(
        restore<SavedSongFilter>(SONG_FILTER_KEY)
            ?.let { SongFilter(selectedTags = it.selectedTags.toSet(), selectedLanguages = it.selectedLanguages.toSet()) }
            ?: SongFilter()
    )

    private val libraryState: LibraryState = LibraryState(
        scope = viewModelScope,
        getScreenData = getScreenData,
        songFilter = _songFilter,
        loadScreenData = loadScreenData,
        normalizeSearchText = normalizeSearchText,
    )

    /** See [LibraryState.screenData]. */
    private val screenData get() = libraryState.screenData

    /** See [LibraryState.isLoading]. */
    val isLoading: StateFlow<Boolean> get() = libraryState.isLoading

    /** See [LibraryState.setlists]. */
    val setlists: StateFlow<List<Setlist>> get() = libraryState.setlists

    /** See [LibraryState.songFileNamesInSetlists]. */
    val songFileNamesInSetlists: StateFlow<Set<String>> get() = libraryState.songFileNamesInSetlists

    /** See [LibraryState.allSongs]. */
    val allSongs: StateFlow<List<Song>> get() = libraryState.allSongs

    /** See [LibraryState.labelsOnEverySong]. */
    val labelsOnEverySong get() = libraryState.labelsOnEverySong

    /** See [LibraryState.tags]. */
    val tags get() = libraryState.tags

    /** See [LibraryState.languages]. */
    val languages get() = libraryState.languages

    private val indexedSongs get() = libraryState.indexedSongs

    /** See [LibraryState.songsByFileName]. */
    val songsByFileName: StateFlow<Map<String, Song>> get() = libraryState.songsByFileName

    /** See [LibraryState.librarySummary]. */
    val librarySummary get() = libraryState.librarySummary

    private val dialogHost: DialogHost = DialogHost(viewModelScope).apply {
        addBeforeDialogChange { _, dialogType ->
            // Editing can rename the setlist's file, which is what the mode is keyed by, so it ends the mode as the other
            // ways of leaving the rows do rather than losing it to a name that has stopped existing.
            if (dialogType is DialogType.Export || dialogType is DialogType.DuplicateSetlist || dialogType is DialogType.EditSetlist) {
                reorderingSetlistFileName = null
            }
        }
        addBeforeDialogChange { _, dialogType ->
            // An exit the question was asked for and that is not being run is an exit that was cancelled: its caller
            // may be waiting to hear so (the macOS quit request is).
            if (dialogType != DialogType.UnsavedChanges) takePendingExit()?.onCancelled?.invoke()
            if (dialogType != DialogType.ConfirmExit) confirmedExit = null
        }
        addBeforeDialogChange { _, dialogType ->
            // Nothing but the sheet reads it, and a search nobody is waiting for any more still counts against the
            // service's one request a second.
            if (dialogType !is DialogType.CoverArtSearch) clearCoverArtSearch()
        }
        addBeforeDialogChange { previousDialog, dialogType ->
            // However the export screen goes - closed, Escape, the web's Back, another dialog put over it - it stays drawn
            // while it slides away, so its own disposal would be too late: its options are saved and its drawing cancelled
            // here, a screen opened again in the next moment finds the options it left, and its Save, Share and options do
            // nothing once it is no longer the dialog on screen.
            if (previousDialog is DialogType.Export && dialogType != previousDialog) {
                exportController.flushPrintSettings()
                // An export nobody is looking at any more would put its picker up over whatever is on screen by then.
                cancelPdfExport()
            }
        }
        addBeforeDialogChange { _, dialogType ->
            // The export screen covers the song the click is played from as a screen of its own would, and leaves no way
            // to stop it, so it stops a click the way pushing a destination does (see updateBackStack).
            if (dialogType is DialogType.Export) metronome.stop()
        }
        addAfterDialogChange { previousDialog, dialogType ->
            // Asked as the sheet is put up rather than by the sheet once it is composed, so that its first frame already
            // says that the search is running instead of crossfading from the hint to it while it slides up.
            if (dialogType is DialogType.CoverArtSearch && previousDialog !is DialogType.CoverArtSearch) searchCoverArt(coverArtQueryOf(song = dialogType.song, target = dialogType.target))
        }
    }

    // Navigation
    private val navigator: Navigator = Navigator(
        savedStateStore = savedStateStore,
        dialogHost = dialogHost,
        songsSearch = { songsSearch },
        setlistsSearch = { setlistsSearch },
        importReportSearch = { importReportSearch },
        topLevelDestinations = { topLevelDestinations.value },
        userPreferences = { userPreferencesState.value.data },
        hasUnsavedEditorText = { hasUnsavedEditorText() },
        hasUnsavedEditorChanges = { hasUnsavedEditorChanges.value },
        isSetlistReordering = { isSetlistReordering },
        endSetlistReordering = { reorderingSetlistFileName = null },
        onTransitionEnded = { pruneSongTexts() },
    ).apply {
        addOnBackStackChanged { _, stack ->
            if (stack.lastOrNull() != CampfireDestination.Setlists) reorderingSetlistFileName = null
        }
        addOnBackStackChanged { previousTop, stack ->
            // The two screens that hold a metronome are the two it can be stopped from, so a click never outlives the one
            // it was started on: the editor opened over a song, a song closed, a tab selected, all stop it - and so does a
            // different screen arriving on top, a song opened over the Metronome tab or over another song, which holds a
            // metronome but not the one that was started. The panel the click was played from is a preference and stays
            // where the user put it, so the next song is read to a click without asking for the instrument again.
            if (isMetronomeScreenLeft(previousTop = previousTop, top = stack.lastOrNull())) metronome.stop()
        }
        addOnBackStackChanged { _, _ -> editorSession.onBackStackChanged() }
        addOnBackStackChanged { _, stack ->
            stack.mapNotNullTo(mutableSetOf()) { (it as? CampfireDestination.SongDetails)?.id }.let { ids ->
                songDetailsCurrentSongs.keys.retainAll(ids)
                songDetailsTargetSongs.keys.retainAll(ids)
                songDetailsTargetTimings.keys.retainAll(ids)
            }
        }
        addOnBackStackChanged { _, _ -> importController.onBackStackChanged() }
    }

    /** See [Navigator.backStack]. */
    val backStack: SnapshotStateList<CampfireDestination> get() = navigator.backStack

    /** See [Navigator.navigationGeneration]. */
    val navigationGeneration: Int get() = navigator.navigationGeneration

    /** See [Navigator.metronomeScrollPosition]. */
    internal val metronomeScrollPosition get() = navigator.metronomeScrollPosition

    /** See [Navigator.settingsScrollPositions]. */
    internal val settingsScrollPositions get() = navigator.settingsScrollPositions

    /** See [Navigator.settingsTab]. */
    internal var settingsTab: SettingsTab
        get() = navigator.settingsTab
        set(value) {
            navigator.settingsTab = value
        }

    /** See [Navigator.isSettingsBackToGeneral]. */
    internal val isSettingsBackToGeneral get() = navigator.isSettingsBackToGeneral

    private val songDetailsCurrentSongs get() = navigator.songDetailsCurrentSongs

    /** See [Navigator.currentSearch]. */
    internal val currentSearch: SearchState? get() = navigator.currentSearch

    /** See [Navigator.scrollToTopRequests]. */
    val scrollToTopRequests get() = navigator.scrollToTopRequests

    /** See [DialogHost.overlayState]. */
    internal val overlayState get() = dialogHost.overlayState

    internal fun openCurrentSearch() = navigator.openCurrentSearch()

    private val fontScaleController: FontScaleController = FontScaleController(
        scope = viewModelScope,
        backStack = backStack,
        dialogHost = dialogHost,
        updateUserPreferences = updateUserPreferences,
        writeDelayMillis = PREFERENCE_WRITE_DEBOUNCE_MILLIS,
    )

    /** See [FontScaleController.isSongTextZoomable]. */
    internal val isSongTextZoomable get() = fontScaleController.isSongTextZoomable

    /** See [FontScaleController.zoomSongText]. */
    internal fun zoomSongText(steps: Int?) = fontScaleController.zoomSongText(steps)

    /** See [FontScaleController.printPreviewMagnifications]. */
    internal val printPreviewMagnifications: SharedFlow<Float> get() = fontScaleController.printPreviewMagnifications

    /** See [FontScaleController.magnifyByTouchpad]. */
    fun magnifyByTouchpad(factor: Float) = fontScaleController.magnifyByTouchpad(factor)

    /** See [FontScaleController.fontScale]. */
    val fontScale: Float get() = fontScaleController.fontScale

    /** See [FontScaleController.settledFontScale]. */
    val settledFontScale: Float get() = fontScaleController.settledFontScale

    private val preferencesController: PreferencesController = PreferencesController(
        scope = viewModelScope,
        getUserPreferences = getUserPreferences,
        updateUserPreferences = updateUserPreferences,
        requestPersistence = ::requestLibraryPersistence,
    )

    /** See [PreferencesController.userPreferencesState]. */
    private val userPreferencesState get() = preferencesController.userPreferencesState

    /** See [PreferencesController.userPreferences]. */
    val userPreferences: StateFlow<UserPreferences?> get() = preferencesController.userPreferences

    /** See [PreferencesController.arePreferencesLoaded]. */
    val arePreferencesLoaded: StateFlow<Boolean> get() = preferencesController.arePreferencesLoaded

    /** See [PreferencesController.isPerformanceModeEnabled]. */
    val isPerformanceModeEnabled: StateFlow<Boolean> get() = preferencesController.isPerformanceModeEnabled

    /** See [PreferencesController.topLevelDestinations]. */
    internal val topLevelDestinations: StateFlow<List<CampfireDestination.TopLevel>> get() = preferencesController.topLevelDestinations

    /** See [PreferencesController.libraryPersistence]. */
    internal val libraryPersistence: StateFlow<LibraryPersistence?> get() = preferencesController.libraryPersistence

    private val syncController: SyncController = SyncController(
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
        cancelSynchronization = cancelSynchronization,
    )

    /** See [SyncController.syncState]. */
    val syncState get() = syncController.syncState

    /** See [SyncController.isSyncing]. */
    val isSyncing get() = syncController.isSyncing

    /** See [SyncController.syncProviders]. */
    val syncProviders get() = syncController.syncProviders

    private val songTextStore: SongTextStore = SongTextStore(
        scope = viewModelScope,
        backStack = backStack,
        editorDraftFileName = { editorSession.editorDraft.value?.fileName },
        messageSink = messageSink,
        getSongContent = getSongContent,
        saveSongContent = saveSongContent,
        getSongContentInvalidations = getSongContentInvalidations,
    )

    private val songMetadataEditing: SongMetadataEditing = SongMetadataEditing(
        dialogHost = dialogHost,
        messageSink = messageSink,
        songTextStore = songTextStore,
        songRenderer = songRenderer,
        editorNotation = { editorNotation },
        retainedEditorField = { retainedEditorField(it) },
        editorDraft = { editorSession.editorDraft.value },
        emitEditorTextEdit = { editorSession.emitEditorTextEdit(it) },
        parseChordPro = parseChordPro,
        setChordProCoverArt = setChordProCoverArt,
        setChordProLanguages = setChordProLanguages,
        setChordProTag = setChordProTag,
        setChordProLinks = setChordProLinks,
        setChordProMetadata = setChordProMetadata,
    )

    /** See [SongTextStore.songTexts]. */
    val songTexts: StateFlow<Map<String, String>> get() = songTextStore.songTexts

    /** See [SongTextStore.songsBeingRenamed]. */
    val songsBeingRenamed: StateFlow<Map<String, Song>> get() = songTextStore.songsBeingRenamed

    private val songWriteMutex get() = songTextStore.songWriteMutex

    /** See [SongTextStore.failedSongFileNames]. */
    val failedSongFileNames: StateFlow<Set<String>> get() = songTextStore.failedSongFileNames

    private val editorSession: EditorSession = EditorSession(
        scope = viewModelScope,
        dialogHost = dialogHost,
        messageSink = messageSink,
        songTextStore = songTextStore,
        songRenderer = songRenderer,
        backStack = backStack,
        userPreferences = userPreferences,
        arePreferencesLoaded = arePreferencesLoaded,
        getSongContent = getSongContent,
        getEditorDraft = getEditorDraft,
        saveEditorDraft = saveEditorDraft,
        updateBackStack = { updateBackStack(update = it) },
        popBackStack = { popBackStack() },
        takePendingExit = { takePendingExit() },
        requestExit = { onExit, onCancelled -> requestExit(onExit = onExit, onCancelled = onCancelled) },
    )

    /** See [EditorSession.editorRevertRequests]. */
    val editorRevertRequests get() = editorSession.editorRevertRequests

    /** See [EditorSession.editorTextEdits]. */
    val editorTextEdits get() = editorSession.editorTextEdits

    /** See [EditorSession.hasUnsavedEditorChanges]. */
    val hasUnsavedEditorChanges: StateFlow<Boolean> get() = editorSession.hasUnsavedEditorChanges

    /** See [EditorSession.isSavingSong]. */
    val isSavingSong: StateFlow<Boolean> get() = editorSession.isSavingSong

    /** See [EditorSession.editorNotation]. */
    val editorNotation get() = editorSession.editorNotation

    private val overrides: PlayingOverrides = PlayingOverrides(
        scope = viewModelScope,
        userPreferences = userPreferences,
        setlists = setlists,
        songsByFileName = { songsByFileName.value },
        updateUserPreferences = updateUserPreferences,
        updateEditableSetlist = { fileName, transform -> updateEditableSetlist(fileName, transform) },
        messageSink = messageSink,
        writeDelayMillis = PREFERENCE_WRITE_DEBOUNCE_MILLIS,
    )

    /** See [PlayingOverrides.transpositions]. */
    internal val transpositions: StateFlow<Transpositions> get() = overrides.transpositions

    /** See [PlayingOverrides.tempos]. */
    internal val tempos: StateFlow<Tempos> get() = overrides.tempos

    /** See [PlayingOverrides.capos]. */
    internal val capos: StateFlow<Capos> get() = overrides.capos

    /** See [PlayingOverrides.playingOverrides]. */
    internal val playingOverrides: StateFlow<PlayingOverridesSnapshot> get() = overrides.playingOverrides

    // Metronome

    private val metronomeController: MetronomeController = MetronomeController(
        scope = viewModelScope,
        metronome = metronome,
        backStack = backStack,
        currentSongFileName = { currentSongFileName(it) },
        dialogHost = dialogHost,
        userPreferences = userPreferences,
        tempos = tempos,
        songsByFileName = songsByFileName,
        songsBeingRenamed = songsBeingRenamed,
        messageSink = messageSink,
        updateUserPreferences = updateUserPreferences,
        writeDelayMillis = PREFERENCE_WRITE_DEBOUNCE_MILLIS,
    )

    /** See [MetronomeController.metronomeSettings]. */
    val metronomeSettings: StateFlow<MetronomeSettings> get() = metronomeController.metronomeSettings

    /** See [MetronomeController.metronomePlayback]. */
    val metronomePlayback get() = metronomeController.metronomePlayback

    /** See [MetronomeController.metronomeBeats]. */
    val metronomeBeats get() = metronomeController.metronomeBeats

    private val songDetailsTargetSongs get() = metronomeController.songDetailsTargetSongs

    private val songDetailsTargetTimings get() = metronomeController.songDetailsTargetTimings

    private val metronomeRenames get() = metronomeController.metronomeRenames

    /** See [MetronomeController.metronomeContext]. */
    internal val metronomeContext get() = metronomeController.metronomeContext

    private val songPickerState: SongPickerState = SongPickerState(
        scope = viewModelScope,
        savedStateStore = savedStateStore,
        indexedSongs = indexedSongs,
        allSongs = allSongs,
    )

    /** See [SongPickerState.songPickerSelectedTags]. */
    internal val songPickerSelectedTags get() = songPickerState.songPickerSelectedTags

    /** See [SongPickerState.songPickerSelectedLanguages]. */
    internal val songPickerSelectedLanguages get() = songPickerState.songPickerSelectedLanguages

    /** See [SongPickerState.pickerSongs]. */
    internal val pickerSongs get() = songPickerState.pickerSongs

    /** See [SongPickerState.songPickerFilters]. */
    internal val songPickerFilters get() = songPickerState.songPickerFilters

    private val coverArtSearchController: CoverArtSearchController = CoverArtSearchController(
        scope = viewModelScope,
        searchCoverArt = searchCoverArt,
        getCoverArtCacheSize = getCoverArtCacheSize,
        clearCoverArtCache = clearCoverArtCache,
        parseChordPro = parseChordPro,
        songTextOf = { songTextOf(it) },
    )

    /** See [CoverArtSearchController.coverArtCacheSize]. */
    val coverArtCacheSize get() = coverArtSearchController.coverArtCacheSize

    private val importController: ImportController = ImportController(
        scope = viewModelScope,
        dialogHost = dialogHost,
        messageSink = messageSink,
        backStack = backStack,
        updateBackStack = { updateBackStack(update = it) },
        songTexts = songTexts,
        editorDraft = editorSession.editorDraft,
        hasUnsavedEditorText = { hasUnsavedEditorText() },
        openImportedSong = { openImportedSong(it) },
        launchFileTransfer = { launchFileTransfer(it) },
        isLeaving = { isLeaving },
        prepareImport = prepareImport,
        importFiles = importFiles,
        rememberDemoLibraryFiles = rememberDemoLibraryFiles,
        deleteLibrary = deleteLibrary,
    )

    /** See [ImportController.isImporting]. */
    val isImporting: StateFlow<Boolean> get() = importController.isImporting

    /** See [ImportController.importProgress]. */
    val importProgress: StateFlow<ImportProgress?> get() = importController.importProgress

    /** See [ImportController.importReport]. */
    val importReport: StateFlow<ImportReport?> get() = importController.importReport

    /** See [ImportController.importReportSearch]. */
    internal val importReportSearch get() = importController.importReportSearch

    private val songListState: SongListState = SongListState(
        scope = viewModelScope,
        savedStateStore = savedStateStore,
        mutableSongFilter = _songFilter,
        screenData = screenData,
        indexedSongs = indexedSongs,
        tags = tags,
        languages = languages,
        isImporting = isImporting,
        normalizeSearchText = normalizeSearchText,
        changeUserPreferences = { changeUserPreferences(it) },
    )

    /** See [SongListState.songsScrollPosition]. */
    internal val songsScrollPosition get() = songListState.songsScrollPosition

    /** See [SongListState.songsSearch]. */
    internal val songsSearch get() = songListState.songsSearch

    /** See [SongListState.songFilter]. */
    val songFilter get() = songListState.songFilter

    /** See [SongListState.hasSongFilters]. */
    val hasSongFilters get() = songListState.hasSongFilters

    /** See [SongListState.isSongFilterActive]. */
    val isSongFilterActive get() = songListState.isSongFilterActive

    /** See [SongListState.songGroups]. */
    val songGroups get() = songListState.songGroups

    /** See [SongListState.songsPlaceholder]. */
    val songsPlaceholder get() = songListState.songsPlaceholder

    private val setlistsController: SetlistsController = SetlistsController(
        scope = viewModelScope,
        savedStateStore = savedStateStore,
        backStack = backStack,
        userPreferences = userPreferences,
        setlists = setlists,
        allSongs = allSongs,
        indexedSongs = indexedSongs,
        screenData = screenData,
        isImporting = isImporting,
        dialogHost = dialogHost,
        messageSink = messageSink,
        createSetlist = createSetlist,
        saveSetlist = saveSetlist,
        editSetlist = editSetlist,
        updateSetlist = updateSetlist,
        deleteSetlist = deleteSetlist,
        normalizeSearchText = normalizeSearchText,
        followReportedFileNames = { followReportedFileNames(it) },
        changeUserPreferences = { changeUserPreferences(it) },
    )

    /** See [SetlistsController.setlistsScrollPosition]. */
    internal val setlistsScrollPosition get() = setlistsController.setlistsScrollPosition

    /** See [SetlistsController.setlistsSearch]. */
    internal val setlistsSearch get() = setlistsController.setlistsSearch

    /** See [SetlistsController.reorderingSetlistFileName]. */
    internal var reorderingSetlistFileName: String?
        get() = setlistsController.reorderingSetlistFileName
        set(value) {
            setlistsController.reorderingSetlistFileName = value
        }

    /** See [SetlistsController.isSetlistReordering]. */
    internal val isSetlistReordering: Boolean get() = setlistsController.isSetlistReordering

    /** See [SetlistsController.setlistsWithSongs]. */
    val setlistsWithSongs get() = setlistsController.setlistsWithSongs

    /** See [SetlistsController.setlistsPlaceholder]. */
    val setlistsPlaceholder get() = setlistsController.setlistsPlaceholder

    private val exportController: ExportController = ExportController(
        scope = viewModelScope,
        dialogHost = dialogHost,
        messageSink = messageSink,
        screenData = screenData,
        userPreferencesState = userPreferencesState,
        transpositions = transpositions,
        effectiveTempoOf = { songFileName, setlistFileName -> effectiveTempoOf(songFileName, setlistFileName) },
        effectiveCapoOf = { songFileName, setlistFileName -> effectiveCapoOf(songFileName, setlistFileName) },
        songRenderer = songRenderer,
        getSongContent = getSongContent,
        exportSongs = exportSongs,
        exportSetlist = exportSetlist,
        exportLibrary = exportLibrary,
        updateUserPreferences = updateUserPreferences,
        writeDelayMillis = PREFERENCE_WRITE_DEBOUNCE_MILLIS,
    )

    /** See [ExportController.pendingPrintSettings]. */
    val pendingPrintSettings get() = exportController.pendingPrintSettings

    /** See [ExportController.isFileTransferActive]. */
    val isFileTransferActive get() = exportController.isFileTransferActive

    /** See [ExportController.pdfExportProgress]. */
    val pdfExportProgress get() = exportController.pdfExportProgress

    /** See [ExportController.launchFileTransfer]. */
    private fun launchFileTransfer(block: suspend () -> Unit) = exportController.launchFileTransfer(block)

    /**
     * Set once the desktop process is on its way out ([settleSynchronizationBeforeExit]) and never cleared, so that the
     * import queue's consumer starts no batch the exit would cut off halfway. Touched only on the main thread, like
     * [preparation].
     */
    private var isLeaving = false

    /**
     * The exit that asked the `UnsavedChanges` question, run once it is answered with Save or Discard. Any other way
     * the dialog goes away is staying, and that is reported too: on macOS the exit may be the system's own quit
     * request, which has to be answered either way (see the desktop app module).
     */
    private var pendingExit: PendingExit? = null

    /**
     * Taken rather than read, so that exactly one of an exit's two callbacks can ever run: the branch that is about to
     * run the exit holds it before [leaveEditor] dismisses the dialog, which would otherwise report it as cancelled.
     */
    private fun takePendingExit() = pendingExit.also { pendingExit = null }

    /** See [MessageSink.messageQueue]. */
    val messageQueue: StateFlow<List<IndexedValue<Message>>> get() = messageSink.messageQueue

    private fun sendMessage(message: Message) = messageSink.sendMessage(message)

    /** Called by the snackbar host once [message] has been on screen for its whole duration, or dismissed. */
    fun onMessageShown(message: IndexedValue<Message>) = messageSink.onMessageShown(message)

    // Dialogs

    /** See [CoverArtSearchController.coverArtSearch]. */
    val coverArtSearch get() = coverArtSearchController.coverArtSearch

    /** See [DialogHost.visibleDialog]. */
    val visibleDialog: StateFlow<DialogType?> get() = dialogHost.visibleDialog

    /** See [DialogHost.underlyingSongInfo]. */
    val underlyingSongInfo: StateFlow<DialogType.SongInfo?> get() = dialogHost.underlyingSongInfo

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

    private val firstRunController: FirstRunController = FirstRunController(
        scope = viewModelScope,
        isFirstLaunch = isFirstLaunch,
        dialogHost = dialogHost,
        messageSink = messageSink,
        importController = importController,
        backStack = backStack,
        screenData = screenData,
        userPreferencesState = userPreferencesState,
        isEditorDraftRecoveryPending = editorSession.isEditorDraftRecoveryPending,
        editorDraftRecovery = editorSession.editorDraftRecovery,
        syncProviders = syncProviders,
        songFilter = _songFilter,
        getScreenData = getScreenData,
        updateUserPreferences = updateUserPreferences,
        restoreNavigationState = { restoreNavigationState(it) },
        selectTopLevelDestination = { selectTopLevelDestination(it) },
        selectSettingsTab = { settingsTab = it },
    )

    /** See [FirstRunController.isLaunchNavigationPending]. */
    internal val isLaunchNavigationPending get() = firstRunController.isLaunchNavigationPending

    /** See [FirstRunController.hasLibraryToShow]. */
    val hasLibraryToShow get() = firstRunController.hasLibraryToShow

    /** See [FirstRunController.hasShownApp]. */
    internal var hasShownApp: Boolean
        get() = firstRunController.hasShownApp
        set(value) {
            firstRunController.hasShownApp = value
        }

    /** See [FirstRunController.isAppOnScreen]. */
    internal val isAppOnScreen get() = firstRunController.isAppOnScreen

    /** See [FirstRunController.demoLibraryOffer]. */
    val demoLibraryOffer get() = firstRunController.demoLibraryOffer

    init {
        libraryState.startLoading()
        viewModelScope.launch { firstRunController.plantDemoLibraryOnFirstRun() }
        viewModelScope.launch { firstRunController.showWelcomeOnFirstRun() }
        viewModelScope.launch { firstRunController.showWhatsNewOnVersionChange() }
        syncController.startRestoring(isFirstLaunch = isFirstLaunch, onConsentAnswered = ::openSyncSettingsAfterConsent)
        songTextStore.startFollowingFiles()
        importController.startQueue(firstRunController.demoLibraryDecision)
        dialogHost.startClosingWithSong(allSongs = allSongs, isLoading = isLoading, songsBeingRenamed = songsBeingRenamed)
        editorSession.startRecovery()
        savedStateStore.startPersisting(
            songFilter = _songFilter,
            songPickerSelectedTags = songPickerSelectedTags,
            songPickerSelectedLanguages = songPickerSelectedLanguages,
            songsSearch = songsSearch,
            setlistsSearch = setlistsSearch,
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
    }

    // Navigation

    fun setNavigationTransitionRunning(isRunning: Boolean) = navigator.setNavigationTransitionRunning(isRunning)

    private fun pruneSongTexts() = songTextStore.pruneSongTexts()

    private fun updateBackStack(
        isPredictiveBackCompleted: Boolean = false,
        update: SnapshotStateList<CampfireDestination>.() -> Unit,
    ) = navigator.updateBackStack(isPredictiveBackCompleted, update)

    private fun persistBackStack() = navigator.persistBackStack()

    internal fun onSongDetailsPageSettled(destination: CampfireDestination.SongDetails, songFileName: String) =
        navigator.onSongDetailsPageSettled(destination, songFileName)

    internal fun currentSongFileName(destination: CampfireDestination.SongDetails) = navigator.currentSongFileName(destination)

    internal val navigationState: NavigationState get() = navigator.navigationState

    internal fun restoreNavigationState(state: NavigationState) = navigator.restoreNavigationState(state)

    internal fun navigateOnLaunch(resolve: (songs: List<Song>, setlists: List<Setlist>, isPerformanceModeEnabled: Boolean) -> NavigationState?) =
        firstRunController.navigateOnLaunch(resolve)

    /**
     * Shows the answer to a consent page the app was sent away to, which is where the user was when they left: Settings,
     * on the tab holding the sync section. Only onto a stack nobody has built on since the app started - the songs the
     * app opens on, or the settings screen Android brings back after reclaiming the process behind the browser. The
     * code exchange this follows can take a minute on a bad network, and a user who has gone somewhere else meanwhile
     * has moved on: the settings screen says the same thing whenever they get there, and an editor they opened in the
     * meantime holds text that nothing but its own ways out may take away.
     */
    private fun openSyncSettingsAfterConsent() {
        val stack = backStack.toList()
        if (stack != listOf(CampfireDestination.Songs) && stack != listOf(CampfireDestination.Songs, CampfireDestination.Settings)) return
        // Not through the re-selection of an open settings screen, which would send it back to General a moment after.
        if (backStack.lastOrNull() != CampfireDestination.Settings) selectTopLevelDestination(CampfireDestination.Settings)
        settingsTab = SettingsTab.LIBRARY
    }

    fun selectTopLevelDestination(destination: CampfireDestination.TopLevel) = navigator.selectTopLevelDestination(destination)

    fun openSong(song: Song) = navigator.openSong(song)

    fun openSongInSetlist(setlistWithSongs: SetlistWithSongs, song: Song) = navigator.openSongInSetlist(setlistWithSongs, song)

    internal fun openImportedSong(fileName: String) = navigator.openImportedSong(fileName)

    internal fun openReportedSong(songFileNames: List<String>, index: Int) = navigator.openReportedSong(songFileNames, index)

    fun navigateBack(isPredictiveBackCompleted: Boolean = false) = navigator.navigateBack(isPredictiveBackCompleted)

    /**
     * Closing the application, which is a way out of the editor like any other. A save that is still being written
     * is waited for first, since the process ends with [onExit] - and since only then is it known whether it worked:
     * with unsaved text in the editor, which is also what a save that failed leaves behind, the `UnsavedChanges`
     * question is asked, and [onExit] only runs once that has been answered with something other than staying.
     *
     * @param onCancelled Called instead of [onExit] when the user answers the `UnsavedChanges` question by staying -
     *   dismissing it, or a save that failed. The macOS quit handler needs it: a quit request that is not answered
     *   one way or the other leaves the system waiting.
     */
    fun requestExit(onExit: () -> Unit, onCancelled: () -> Unit = {}) {
        viewModelScope.launch {
            editorSession.currentSaveJob?.join()
            // A second request - Cmd+Q pressed again while the question is up - answers the first one, which is
            // still waiting for something.
            takePendingExit()?.onCancelled?.invoke()
            if (hasUnsavedEditorText() && backStack.lastOrNull() is CampfireDestination.SongEditor) {
                pendingExit = PendingExit(exit = onExit, onCancelled = onCancelled)
                showDialog(DialogType.UnsavedChanges)
            } else {
                onExit()
            }
        }
    }

    /**
     * Closing the application with the key that is otherwise only ever a step back - the desktop's Escape, pressed
     * once more than there were screens to leave. That is a habit of the hand rather than a decision, and on a music
     * stand it ends a song and a click mid-performance, so it asks first; the window's close button and the system's
     * quit are deliberate and go straight to [requestExit]. A second press while the question is up dismisses it.
     */
    fun confirmExit(onExit: () -> Unit) {
        confirmedExit = onExit
        showDialog(DialogType.ConfirmExit)
    }

    /** The answer to [DialogType.ConfirmExit] that leaves. */
    fun exitConfirmed() {
        val exit = confirmedExit ?: return
        confirmedExit = null
        dismissDialog()
        requestExit(exit)
    }

    /** The exit [DialogType.ConfirmExit] is asking about, kept only while that question is the one on screen. */
    private var confirmedExit: (() -> Unit)? = null

    /**
     * Lets the sync runs the library still owes the cloud folder happen before a desktop process ends, which is where
     * a quit leads once [requestExit] has let it through - the shell hides the window first, so the quit looks as
     * immediate as it is. An import that is being written is let finish first, for up to [EXIT_IMPORT_GRACE], since
     * its songs are written before its setlists and the progress dialog asked for Campfire to be kept open; one that is
     * still being read is cancelled, having written nothing, and no further batch is started. Waited for before the
     * sync run, so that the run carries the whole import. The automatic run that is waiting for the library to settle
     * is started next: dropped, the change made just before quitting would reach the other devices only the next time
     * this computer opens Campfire.
     * A run that is going is waited for, and so is one chained behind it, the metronome having been stopped first. Past
     * [EXIT_SYNC_GRACE] the run is stopped instead and its winding down waited for, briefly, since a stopped run writes its index and clears the marker
     * that would otherwise have the next launch report it as interrupted and start no run of its own. Before any of
     * that, the stored editor draft is brought in line with the editor, written or removed, and what is still waiting
     * for its debounce (the text size, the metronome settings, the export options and the tempo and capo overrides)
     * is written, since the process ends right after.
     */
    suspend fun settleSynchronizationBeforeExit() {
        isLeaving = true
        cancelImportPreparation()
        // A conflicts question that is up has written nothing and has already let the import flag go, so it is dropped
        // here rather than waited for.
        withTimeoutOrNull(EXIT_IMPORT_GRACE) { isImporting.first { !it } }
        // The stored draft is removed by a collector a few hops after the editor lets its text go, which a process that
        // ends now would not wait for: a Discard answered on the way out would come back as "unsaved changes restored".
        if (!editorSession.isEditorDraftRecoveryPending.value) storeEditorDraft(currentEditorDraftToStore())
        // The window is already hidden, so a click still sounding while the run is waited for would come from nowhere.
        metronome.stop()
        // The process ends right after this, and onCleared's detached write would race it - or, on a macOS Quit, never
        // run. Before the sync wait, so that an override written now is in the library a run that is still to start carries.
        writeWaitingPreferences()
        takeWaitingOverrideWrites()()
        val isSyncing = { state: SyncState -> state is SyncState.Connected && state.isSyncing }
        val hasSettled = withTimeoutOrNull(EXIT_SYNC_GRACE) {
            // The state is the repository's own rather than syncState, which only follows it a hop to the main thread
            // later and would still say nothing is going for a run that has just been started.
            while (startScheduledSynchronization() != null) {
                getSyncState().first { !isSyncing(it) }
            }
        } != null
        if (!hasSettled) {
            cancelSynchronization()
            withTimeoutOrNull(EXIT_SYNC_STOP_GRACE) { getSyncState().first { !isSyncing(it) } }
        }
    }

    private fun hasUnsavedEditorText() = editorSession.hasUnsavedEditorText()

    private fun popBackStack(isPredictiveBackCompleted: Boolean = false) = navigator.popBackStack(isPredictiveBackCompleted)

    // Songs

    fun refresh() = libraryState.refresh()

    fun refreshIfStale() = libraryState.refreshIfStale()

    /** Creates the file and opens it in the editor, which is the only useful thing to do with an empty song. */
    fun createSong(values: Map<ChordProMetadataFields.Field, String>) = launchLibraryChange {
        val song = createSong.invoke(
            title = values[ChordProMetadataFields.Field.TITLE].orEmpty(),
            artist = values[ChordProMetadataFields.Field.ARTIST].orEmpty(),
            metadata = values,
        )
        openEditor(fileName = song.fileName, shouldStartInsideFirstSection = true)
    }

    /**
     * Renames the song's file to the one its own metadata gives it, offered by the song's menu wherever the two have
     * drifted apart (`Song.canUpdateFileName`). The use case follows the references that are on disk - the setlists
     * holding the song, its saved transposition - and what is left here is the two places the old name lives in
     * memory: the text read from the file, and the screens that were opened on it. Where a setlist or the
     * transposition could not follow, the rename still stands and the screens still follow it; that is said
     * afterwards (`Message.SongFileRenamedPartly`).
     */
    fun updateSongFileName(song: Song) = launchLibraryChange {
        // Before the file moves, so that the moment the library drops the old name is already covered.
        songTextStore.updateSongsBeingRenamed { it + (song.fileName to song) }
        try {
            val rename = renameSongFile(song) ?: return@launchLibraryChange
            val fileName = rename.fileName
            // Only while the screen being rewritten is the one on top, which is what makes sure the context moves off
            // the old name and the entry is taken out again.
            if ((metronomeContext as? MetronomeContext.Song)?.songFileName == song.fileName) metronomeRenames[song.fileName] = fileName
            songTextStore.updateSongTexts { texts -> texts[song.fileName]?.let { texts - song.fileName + (fileName to it) } ?: texts }
            // The details screen is named after the songs it pages through, so the entry showing this one is rewritten
            // rather than popped: the action can be taken from that screen, and a song that has just been renamed is
            // still the song being read.
            backStack.forEachIndexed { index, destination ->
                when {
                    destination is CampfireDestination.SongDetails && song.fileName in destination.songFileNames -> {
                        // A destination opened on a setlist that named both files - the old name and the one the song is
                        // moving to, whose file this device did not have - would otherwise name the same song twice, and
                        // the pager keys its pages by that name.
                        val songFileNames = destination.songFileNames.map { if (it == song.fileName) fileName else it }.distinct()
                        backStack[index] = destination.copy(
                            songFileNames = songFileNames,
                            // The page the reader is on, so that a rename leaves them looking at the song they renamed.
                            initialIndex = songFileNames.indexOf(fileName),
                        )
                    }

                    destination is CampfireDestination.SongEditor && destination.fileName == song.fileName -> {
                        backStack[index] = destination.copy(fileName = fileName)
                    }
                }
            }
            songDetailsCurrentSongs.entries.filter { it.value == song.fileName }.forEach { songDetailsCurrentSongs[it.key] = fileName }
            songDetailsTargetSongs.entries.filter { it.value == song.fileName }.forEach { songDetailsTargetSongs[it.key] = fileName }
            followReportedFileNames { if (it == song.fileName) fileName else it }
            persistBackStack()
            // Said after the screens have followed the file, which has moved whatever else could not be rewritten.
            if (!rename.haveReferencesFollowed) sendMessage(Message.SongFileRenamedPartly)
        } finally {
            // Cleared once the screens name the new file - and on the paths that never got that far: a rename that
            // found nothing to do, one that threw, and a view model going away mid-rename.
            songTextStore.updateSongsBeingRenamed { it - song.fileName }
        }
    }

    fun deleteSong(fileName: String) = launchLibraryChange {
        val haveReferencesBeenRemoved = deleteSong.invoke(fileName)
        // Nobody is asked to save a file that has just been deleted, so the draft goes before the screens holding it.
        editorSession.clearEditorDraft()
        // A screen showing the file that has just gone is closed first, or it would sit there on nothing. The editor
        // goes before the details screen underneath it, so both have to be checked rather than only the top one.
        while (backStack.lastOrNull().let { it is CampfireDestination.SongEditor && it.fileName == fileName || it is CampfireDestination.SongDetails && fileName in it.songFileNames }) {
            popBackStack()
        }
        songTextStore.updateSongTexts { it - fileName }
        followReportedFileNames { it.takeUnless { it == fileName } }
        // Said once the screens have let go of the file, which is gone whatever else could not be rewritten.
        if (!haveReferencesBeenRemoved) sendMessage(Message.SongDeletedPartly)
    }

    fun setSongTags(target: SongEditTarget, tags: List<String>, offeredTags: List<String>) = songMetadataEditing.setSongTags(target, tags, offeredTags)

    fun setSongLanguages(target: SongEditTarget, codes: List<String>) = songMetadataEditing.setSongLanguages(target, codes)

    fun showSongTagsDialog(song: Song, target: SongEditTarget) = songMetadataEditing.showSongTagsDialog(song, target)

    fun showSongLanguagesDialog(song: Song, target: SongEditTarget) = songMetadataEditing.showSongLanguagesDialog(song, target)

    fun showSongMetadataDialog(song: Song, target: SongEditTarget) = songMetadataEditing.showSongMetadataDialog(song, target)

    fun setSongMetadata(
        target: SongEditTarget,
        values: Map<ChordProMetadataFields.Field, String>,
        offeredValues: Map<ChordProMetadataFields.Field, String>,
    ) = songMetadataEditing.setSongMetadata(target, values, offeredValues)

    fun showSongLinksDialog(song: Song, target: SongEditTarget) = songMetadataEditing.showSongLinksDialog(song, target)

    fun songMetadataOf(text: String) = songMetadataEditing.songMetadataOf(text)

    fun hasSongInfo(text: String) = songMetadataEditing.hasSongInfo(text)

    fun setSongLinks(target: SongEditTarget, links: List<ChordProLink>, offeredLinks: List<ChordProLink>) = songMetadataEditing.setSongLinks(target, links, offeredLinks)

    fun showSongCoverArtDialog(song: Song, target: SongEditTarget) = songMetadataEditing.showSongCoverArtDialog(song, target)

    fun setSongCoverArt(target: SongEditTarget, url: String?) = songMetadataEditing.setSongCoverArt(target, url)

    fun coverArtQueryOf(song: Song, target: SongEditTarget) = coverArtSearchController.coverArtQueryOf(song, target)

    fun searchCoverArt(query: CoverArtQuery) = coverArtSearchController.searchCoverArt(query)

    private fun clearCoverArtSearch() = coverArtSearchController.clearCoverArtSearch()

    private fun songTextOf(target: SongEditTarget) = songMetadataEditing.songTextOf(target)

    private suspend fun editSongText(fileName: String, edit: (String) -> String) = songTextStore.editSongText(fileName, edit)

    // The editor

    fun openEditor(fileName: String, shouldStartInsideFirstSection: Boolean = false) = editorSession.openEditor(fileName, shouldStartInsideFirstSection)

    fun onEditorTextChanged(fileName: String, text: String) = editorSession.onEditorTextChanged(fileName, text)

    fun onEditorClosed(fileName: String) = editorSession.onEditorClosed(fileName)

    fun retainEditorField(fileName: String, textFieldState: TextFieldState) = editorSession.retainEditorField(fileName, textFieldState)

    fun retainedEditorField(fileName: String) = editorSession.retainedEditorField(fileName)

    fun onEditorDraftLost() = editorSession.onEditorDraftLost()

    /**
     * Called by the app whenever it stops being the one in front (ON_PAUSE), which is the last moment it is certainly
     * running: iOS ends a process in the background without a word, and so does Android to a task swiped away and a
     * mobile browser to a tab it wants the memory of. Stores the unsaved text, or removes what was stored when there
     * is none. Nothing before the draft a previous run left has been read, or it would be written over.
     *
     * An automatic sync run that is still waiting for the library to settle starts now as well: the phones keep a run
     * alive in the background only once the app has told them about it, which it has to do before this callback is
     * over - the composition may not get another frame. So the progress of the run that is going is returned for the
     * caller to hand over there and then, rather than left to arrive through [syncState], which it would only do
     * after a hop to the main thread this callback is holding.
     */
    fun onAppPaused(): SyncProgress? {
        val syncProgress = startScheduledSynchronization()
        if (!editorSession.isEditorDraftRecoveryPending.value) {
            val draft = currentEditorDraftToStore()
            viewModelScope.launch { storeEditorDraft(draft) }
        }
        return syncProgress
    }

    fun onAppStopped(areBeatsFeltInBackground: Boolean) = metronomeController.onAppStopped(areBeatsFeltInBackground)

    fun onAppStarted() = metronomeController.onAppStarted()

    private fun currentEditorDraftToStore() = editorSession.currentEditorDraftToStore()

    private suspend fun storeEditorDraft(draft: SongContent?) = editorSession.storeEditorDraft(draft)

    fun saveEditorChangesAndLeave() = editorSession.saveEditorChangesAndLeave()

    fun leaveEditorWithoutSaving() = editorSession.leaveEditorWithoutSaving()

    fun revertEditorChanges() = editorSession.revertEditorChanges()

    fun saveSongContent(fileName: String, text: String) = editorSession.saveSongContent(fileName, text)

    private suspend fun writeSongContent(fileName: String, text: String, expectedText: String? = null) =
        songTextStore.writeSongContent(fileName, text, expectedText)

    fun loadSongContent(fileName: String) = songTextStore.loadSongContent(fileName)

    fun stepTransposition(songFileName: String, setlistFileName: String?, semitones: Int) = overrides.stepTransposition(songFileName, setlistFileName, semitones)

    fun resetTransposition(songFileName: String, setlistFileName: String?) = overrides.resetTransposition(songFileName, setlistFileName)

    internal fun onSongDetailsPageChanged(destination: CampfireDestination.SongDetails, songFileName: String, timing: SongTiming?) = metronomeController.onSongDetailsPageChanged(destination, songFileName, timing)

    internal fun toggleMetronomeByKey(isSpace: Boolean) = metronomeController.toggleMetronomeByKey(isSpace)

    fun toggleMetronome() = metronomeController.toggleMetronome()

    internal fun toggleMetronomePanel() = metronomeController.toggleMetronomePanel()

    fun stopMetronome() = metronomeController.stopMetronome()

    fun previewMetronomeSound(sound: MetronomeSound) = metronomeController.previewMetronomeSound(sound)

    fun updateMetronomeSettings(change: MetronomeSettings.() -> MetronomeSettings) = metronomeController.updateMetronomeSettings(change)

    internal fun effectiveTempoOf(songFileName: String, setlistFileName: String?) = overrides.effectiveTempoOf(songFileName, setlistFileName)

    fun stepTempo(songFileName: String, setlistFileName: String?, delta: Int) = overrides.stepTempo(songFileName, setlistFileName, delta)

    fun setTempo(songFileName: String, setlistFileName: String?, bpm: Int) = overrides.setTempo(songFileName, setlistFileName, bpm)

    fun resetTempo(songFileName: String, setlistFileName: String?) = overrides.resetTempo(songFileName, setlistFileName)

    internal fun effectiveCapoOf(songFileName: String, setlistFileName: String?) = overrides.effectiveCapoOf(songFileName, setlistFileName)

    fun stepCapo(songFileName: String, setlistFileName: String?, delta: Int) = overrides.stepCapo(songFileName, setlistFileName, delta)

    fun resetCapo(songFileName: String, setlistFileName: String?) = overrides.resetCapo(songFileName, setlistFileName)

    fun showSongPlayingDialog(song: Song, setlistFileName: String?, target: SongEditTarget) = songMetadataEditing.showSongPlayingDialog(song, setlistFileName, target)

    fun setSongPlaying(
        target: SongEditTarget,
        values: Map<ChordProMetadataFields.Field, String>,
        offeredValues: Map<ChordProMetadataFields.Field, String>,
    ) = songMetadataEditing.setSongPlaying(target, values, offeredValues)

    /**
     * The view model going (the activity finished, or the desktop window disposed) does not take what is still waiting
     * for its debounce with it - the text size, the metronome settings, the export options, a tempo or a capo: those
     * are written on a scope of their own, since this one is being cancelled.
     *
     * It does take the click, though. This is the app being left rather than being sent to the background - the one is
     * a finished Activity, the other a paused one - and the click is kept alive across the background for a phone on a
     * music stand with its screen off, not for an app the user has closed; the metronome is a singleton that would
     * otherwise go on clicking, with its notification, under a process nobody is looking at any more.
     */
    override fun onCleared() {
        metronome.stop()
        val writeWaitingOverrides = takeWaitingOverrideWrites()
        // Written on a scope of their own, since this one is being cancelled: each was waiting for its debounce, which
        // this scope's cancellation would otherwise drop.
        CoroutineScope(Dispatchers.Default + NonCancellable).launch {
            try {
                writeWaitingPreferences()
                writeWaitingOverrides()
            } catch (exception: Exception) {
                // A bare scope has no handler, and an exception escaping it would end the process.
                println("Could not write the waiting values: ${exception.message}")
            }
        }
    }

    /**
     * Writes the text size, the metronome settings and the export options that are still waiting for their debounce, in
     * one read-modify-write of the preferences, for a view model or a process that is about to go. Each is let go of
     * only if nothing newer arrived meanwhile, as the collectors do.
     */
    private suspend fun writeWaitingPreferences() = DebouncedPreference.flushAll(
        fontScaleController.fontScalePreference,
        metronomeController.metronomeSettingsPreference,
        exportController.printSettingsPreference,
        write = updateUserPreferences::invoke,
    )

    /** See [PlayingOverrides.takeWaitingOverrideWrites]. */
    private fun takeWaitingOverrideWrites() = overrides.takeWaitingOverrideWrites()

    // Import and export

    fun importFiles(filePicker: FilePicker) = importController.importFiles(filePicker)

    fun importFiles(files: List<ImportedFile>) = importController.importFiles(files)

    private fun enqueueImport(
        files: List<ImportedFile>,
        shouldAnnounceResult: Boolean = true,
        shouldOpenSong: Boolean = false,
        isDemoLibrary: Boolean = false,
    ) = importController.enqueueImport(files, shouldAnnounceResult, shouldOpenSong, isDemoLibrary)

    private suspend fun awaitImportSettled() = importController.awaitImportSettled()

    fun importDemoLibrary() = firstRunController.importDemoLibrary()

    fun onWhatsNewShown() = firstRunController.onWhatsNewShown()

    fun openSettingsFromWelcome() = firstRunController.openSettingsFromWelcome()

    private suspend fun import(request: ImportRequest) = importController.import(request)

    fun cancelImportPreparation() = importController.cancelImportPreparation()

    fun resolveImport(resolution: ImportConflictResolution) = importController.resolveImport(resolution)

    internal fun openImportReport(result: ImportResult) = importController.openImportReport(result)

    private fun followReportedFileNames(fileName: (String) -> String?) = importController.followReportedFileNames(fileName)

    internal suspend fun preparePrintSource(dialog: DialogType.Export) = exportController.preparePrintSource(dialog)

    fun setPrintSettings(value: PrintSettings) = exportController.setPrintSettings(value)

    /** See [ExportController.exportPdf]. */
    internal fun exportPdf(
        filePicker: FilePicker,
        fileName: String,
        dialog: DialogType.Export,
        pageCount: Int,
        isShare: Boolean,
        create: suspend (onPage: (done: Int) -> Unit) -> ByteArray,
    ) = exportController.exportPdf(filePicker, fileName, dialog, pageCount, isShare, create)

    fun cancelPdfExport() = exportController.cancelPdfExport()

    fun exportFiles(filePicker: FilePicker, dialog: DialogType.Export, songFileNames: Set<String>?, isShare: Boolean) =
        exportController.exportFiles(filePicker, dialog, songFileNames, isShare)

    fun exportLibrary(filePicker: FilePicker) = exportController.exportLibrary(filePicker)

    fun onExportFailed() = exportController.onExportFailed()

    fun onLinkNotOpened(url: String) = exportController.onLinkNotOpened(url)

    // Setlists

    fun createSetlist(title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = setlistsController.createSetlist(title, description, date, isCountdownShown)

    fun createSetlistWithSong(title: String, description: String, date: LocalDate, isCountdownShown: Boolean, songFileName: String) = setlistsController.createSetlistWithSong(title, description, date, isCountdownShown, songFileName)

    fun addSongToSetlist(songFileName: String, setlistFileName: String) = setlistsController.addSongToSetlist(songFileName, setlistFileName)

    fun setSetlistSong(setlistFileName: String, songFileName: String, isTicked: Boolean) = setlistsController.setSetlistSong(setlistFileName, songFileName, isTicked)

    fun editSetlist(offered: Setlist, title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = setlistsController.editSetlist(offered, title, description, date, isCountdownShown)

    fun duplicateSetlist(setlist: Setlist, title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = setlistsController.duplicateSetlist(setlist, title, description, date, isCountdownShown)

    fun setSetlistArchived(setlist: Setlist, isArchived: Boolean) = setlistsController.setSetlistArchived(setlist, isArchived)

    fun deleteSetlist(setlistFileName: String) = setlistsController.deleteSetlist(setlistFileName)

    fun deleteLibrary() = importController.deleteLibrary()

    fun clearCoverArtCache() = coverArtSearchController.clearCoverArtCache()

    fun removeSongFromSetlist(songFileName: String, setlistFileName: String) = setlistsController.removeSongFromSetlist(songFileName, setlistFileName)

    fun reorderSetlist(setlistFileName: String, songFileNames: List<String>, onNotWritten: () -> Unit = {}) = setlistsController.reorderSetlist(setlistFileName, songFileNames, onNotWritten)

    private suspend fun updateEditableSetlist(fileName: String, transform: (Setlist) -> Setlist): Setlist? =
        setlistsController.updateEditableSetlist(fileName, transform)

    // User preferences

    /** See [FontScaleController.setFontScale]. */
    fun setFontScale(value: Float) = fontScaleController.setFontScale(value)

    /** See [FontScaleController.adjustFontScale]. */
    fun adjustFontScale(steps: Int) = fontScaleController.adjustFontScale(steps)

    fun setShouldShowArchivedSetlists(value: Boolean) = setlistsController.setShouldShowArchivedSetlists(value)

    fun setPerformanceModeEnabled(value: Boolean) = preferencesController.setPerformanceModeEnabled(value)

    fun setChordsEnabled(value: Boolean) = preferencesController.setChordsEnabled(value)

    fun setChordDiagramsEnabled(value: Boolean) = preferencesController.setChordDiagramsEnabled(value)

    fun setChordInstrument(value: UserPreferences.ChordInstrument) = preferencesController.setChordInstrument(value)

    fun toggleChordSectionFold() = preferencesController.toggleChordSectionFold()

    fun setChordVoicing(instrument: UserPreferences.ChordInstrument, chordId: String, shape: String?) = preferencesController.setChordVoicing(instrument, chordId, shape)

    fun setSetlistsEnabled(value: Boolean) = preferencesController.setSetlistsEnabled(value)

    fun setMetronomeEnabled(value: Boolean) = preferencesController.setMetronomeEnabled(value)

    fun toggleSectionFold(songFileName: String, key: String) = preferencesController.toggleSectionFold(songFileName, key)

    fun setSortingMode(value: UserPreferences.SortingMode) = preferencesController.setSortingMode(value)

    fun setSetlistSortingMode(value: UserPreferences.SetlistSortingMode) = setlistsController.setSetlistSortingMode(value)

    fun toggleTagFilter(tag: String) = songListState.toggleTagFilter(tag)

    internal fun toggleSongPickerTag(tag: String) = songPickerState.toggleSongPickerTag(tag)

    internal fun toggleSongPickerLanguage(code: String) = songPickerState.toggleSongPickerLanguage(code)

    fun clearTagFilter() = songListState.clearTagFilter()

    fun setTagMatchMode(value: UserPreferences.MatchMode) = songListState.setTagMatchMode(value)

    fun setLanguageMatchMode(value: UserPreferences.MatchMode) = songListState.setLanguageMatchMode(value)

    fun setTagSortingMode(value: UserPreferences.LabelSortingMode) = preferencesController.setTagSortingMode(value)

    fun setLanguageSortingMode(value: UserPreferences.LabelSortingMode) = preferencesController.setLanguageSortingMode(value)

    fun toggleLanguageFilter(code: String) = songListState.toggleLanguageFilter(code)

    fun clearLanguageFilter() = songListState.clearLanguageFilter()

    fun clearSongFilter() = songListState.clearSongFilter()

    fun setUiMode(value: UserPreferences.UiMode) = preferencesController.setUiMode(value)

    fun setThemeColor(value: UserPreferences.ThemeColor) = preferencesController.setThemeColor(value)

    fun setBackgroundWarmth(value: Int) = preferencesController.setBackgroundWarmth(value)

    fun setAppIconThemed(value: Boolean) = preferencesController.setAppIconThemed(value)

    fun setCoverArtEnabled(value: Boolean) = preferencesController.setCoverArtEnabled(value)

    fun setSectionNumberingEnabled(value: Boolean) = preferencesController.setSectionNumberingEnabled(value)

    fun setLanguage(value: UserPreferences.Language) = preferencesController.setLanguage(value)

    fun setAccidentals(value: UserPreferences.Accidentals) = preferencesController.setAccidentals(value)

    fun setNotation(notation: UserPreferences.Notation) = preferencesController.setNotation(notation)

    private fun changeUserPreferences(change: UserPreferences.() -> UserPreferences) = preferencesController.changeUserPreferences(change)

    // Sync

    fun connectSyncProvider(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage) =
        syncController.connectSyncProvider(providerId, completionPage)

    fun cancelSyncConnection() = syncController.cancelSyncConnection()

    fun disconnectSyncProvider() = syncController.disconnectSyncProvider()

    fun synchronizeLibrary(deletionPolicy: SyncDeletionPolicy = SyncDeletionPolicy.ASK) = syncController.synchronizeLibrary(deletionPolicy)

    fun cancelSynchronization() = syncController.cancelSynchronization()

    // Dialogs

    /** See [DialogHost.setVisibleDialog]. */
    private fun setVisibleDialog(dialogType: DialogType?) = dialogHost.setVisibleDialog(dialogType)

    fun showDialog(dialogType: DialogType) = dialogHost.showDialog(dialogType)

    fun dismissDialog() = dialogHost.dismissDialog()

    /** See [DialogHost.dismissSheet]. */
    fun dismissSheet(dialogType: DialogType) = dialogHost.dismissSheet(dialogType)

    // Helpers

    private fun restoreSearch(key: String) = savedStateStore.restoreSearch(key)

    private inline fun <reified T> restore(key: String): T? = savedStateStore.restore(key)

    /** See [MessageSink.launchLibraryChange]. */
    private fun launchLibraryChange(block: suspend () -> Unit) = messageSink.launchLibraryChange(block)

    /** See the top-level [asState], in [viewModelScope]. */
    private fun <T> Flow<T>.asState(initialValue: T) = asState(viewModelScope, initialValue)

    companion object {
        private const val PREFERENCE_WRITE_DEBOUNCE_MILLIS = 500L
        /**
         * Long enough for the run an edit asks for, short enough to never look hung. The desktop's `SingleInstance.kt`
         * waits `CLOSING_INSTANCE_WAIT_MILLIS` for a closing process, which has to stay above this, [EXIT_SYNC_STOP_GRACE]
         * and [EXIT_IMPORT_GRACE] together, so raising any of them means raising that too.
         */
        private val EXIT_SYNC_GRACE = 15.seconds
        private val EXIT_SYNC_STOP_GRACE = 2.seconds

        /** Long enough for a few hundred songs to be written; only a stalled disk reaches it. */
        private val EXIT_IMPORT_GRACE = 30.seconds
    }
}
