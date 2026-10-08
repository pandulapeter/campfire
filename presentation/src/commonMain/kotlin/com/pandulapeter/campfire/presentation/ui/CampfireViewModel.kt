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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.ChordProTempo
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.chordpro.ChordProMetadataFields
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.data.model.DataState
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
import com.pandulapeter.campfire.presentation.ui.firstRun.DemoLibrary
import com.pandulapeter.campfire.presentation.ui.firstRun.canShowWelcome
import com.pandulapeter.campfire.presentation.ui.firstRun.canShowWhatsNew
import com.pandulapeter.campfire.presentation.ui.fontScale.FontScaleController
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeController
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
import com.pandulapeter.campfire.presentation.CAMPFIRE_VERSION_NAME
import com.pandulapeter.campfire.presentation.localization.LocalizedStrings
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.whats_new_message
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
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
import com.pandulapeter.campfire.presentation.ui.navigation.reportedSongPages
import com.pandulapeter.campfire.presentation.ui.navigation.withoutDisabledFeatures
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
import com.pandulapeter.campfire.presentation.ui.songInfo.hasSongInfo
import com.pandulapeter.campfire.presentation.ui.state.CoverArtSearchController
import com.pandulapeter.campfire.presentation.ui.state.DebouncedPreference
import com.pandulapeter.campfire.presentation.ui.state.ImportController
import com.pandulapeter.campfire.presentation.ui.state.ImportController.ImportRequest
import com.pandulapeter.campfire.presentation.ui.state.LibraryState
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore.Companion.BACK_STACK_KEY
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    // Navigation
    /** Written into [savedStateStore] on every change, see [persistBackStack], and read back from it here. */
    val backStack: SnapshotStateList<CampfireDestination> = mutableStateListOf<CampfireDestination>().apply {
        // The import screen shows what an import running in this process is doing or did, so a new process has
        // nothing to put on it and comes back on the screen under it.
        addAll(
            restore<List<CampfireDestination>>(BACK_STACK_KEY)
                ?.filterNot { it == CampfireDestination.ImportReport }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(CampfireDestination.Songs),
        )
    }

    /**
     * Bumped whenever the back stack changes while a navigation transition is still running. The UI puts it into
     * the metadata of every entry, which makes the new scene differ from the one the running transition started
     * from: Navigation 3 then retargets the running animation instead of taking its "predictive back cancelled"
     * path, which cannot handle an interrupted animation and leaves the UI stuck halfway.
     *
     * Not bumped by the pop that completes a predictive back gesture, although the transition it seeked is running
     * then: that path is the one Navigation 3 finishes such a gesture with, and only while the new scene is the very one
     * the gesture seeked towards. A different one starts a second animation towards a scene of the same key, whose
     * screens go from visible to visible - and whatever they animate on their own enter transition, the scrim of the
     * screen being returned to among them, stays where the gesture left it.
     */
    var navigationGeneration by mutableIntStateOf(0)
        private set
    private var isNavigationTransitionRunning = false

    /**
     * Where each of the three top level screens is scrolled to - the settings screen once per tab, since each of its
     * tabs scrolls on its own - kept here because a tab that is left is taken off the back stack and loses everything
     * it remembered with it, see [ScrollPosition].
     */
    internal val metronomeScrollPosition = ScrollPosition()
    internal val settingsScrollPositions = SettingsTab.entries.associateWith { ScrollPosition() }

    /**
     * Which tab of the settings screen is open, kept here so that the screen's own recompositions and the sync consent
     * can reach it. Like the other two screens' scroll positions it lasts for the session; unlike them, the tabs' scroll
     * is thrown away when Settings is selected from another top level screen. The screen only reads it as it is composed, but it is a state because the web build's address names the tab, and follows it.
     */
    internal var settingsTab by mutableStateOf(SettingsTab.GENERAL)

    /**
     * Whether a way back out of the settings screen goes to its General tab rather than leaving it: the tabs are the
     * first thing on the screen, and General is the one it opens on, so a Back from any other one is taken as a step back
     * through them before it is a step back to the songs.
     */
    internal val isSettingsBackToGeneral
        get() = backStack.lastOrNull() == CampfireDestination.Settings && settingsTab != SettingsTab.GENERAL

    /**
     * The song each song details screen on the back stack has settled on, by [CampfireDestination.SongDetails.id]: the
     * pager is the screen's own, and its page is the one thing about where the user is that the destination does not
     * say. Reported by the screen, see [onSongDetailsPageSettled], and read by [navigationState].
     */
    private val songDetailsCurrentSongs = mutableStateMapOf<String, String>()

    /**
     * The search a back gesture is about, which is the one belonging to the screen that is on top. Only the two list
     * screens have one that opens and closes, and a search left open on a list screen is no business of the song that
     * was opened from it: there, back is back. The import screen's field is always there, so there is nothing for a
     * back gesture to close before the screen.
     *
     * The screens answer their own back gesture with a navigation event handler registered inside them, which is
     * composed after the navigation's own and therefore wins on its own; this is here for the desktop, whose window
     * key handler decides what Escape means from outside the composition entirely.
     */
    internal val currentSearch: SearchState?
        get() = when (backStack.lastOrNull()) {
            CampfireDestination.Songs -> songsSearch
            CampfireDestination.Setlists -> setlistsSearch
            else -> null
        }

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

    /** See [DialogHost.overlayState]. */
    internal val overlayState get() = dialogHost.overlayState

    /**
     * Answers Ctrl / Cmd + F, which the desktop window and the web page both hear before anything in the composition
     * does: nothing on a list screen is focused while its search is closed, and a key event only travels along the
     * focus path. It opens the search of the list screen that is on top, or brings the caret back into it if it is
     * open already - the import screen's field, which is always there, being given the caret the same way - and
     * answers whether it did, so that the key is left to whoever else wants it everywhere else - the
     * browser's own find bar among them. A dialog, a sheet or an overflow menu keeps it from reaching the screen under
     * it, the way it keeps Escape from reaching it.
     */
    internal fun openCurrentSearch(): Boolean {
        if (visibleDialog.value != null || overlayState.isAnyMenuOpen || isSetlistReordering) return false
        val search = currentSearch
            ?: importReportSearch.takeIf { backStack.lastOrNull() == CampfireDestination.ImportReport }
            ?: return false
        search.openOrFocus()
        return true
    }

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

    /**
     * True until the app has settled whether it is planting the demo library, and until it has finished if it is,
     * see [plantDemoLibraryOnFirstRun]. It starts out true rather than being set when the planting begins, because
     * that decision takes a read of its own and the scan of the library can finish first: an empty library would
     * then be announced as the answer, a moment before the songs on their way into it arrived.
     */
    private val isDemoLibraryPending = MutableStateFlow(true)

    /**
     * Completed once the first run has planted the demo library or found no reason to, which the consumer of
     * [importQueue] waits for before it takes its first batch: a file opened with the app as it starts for the first
     * time is queued long before that decision, and it belongs in a library that already has the demo in it rather
     * than the other way round - where the file has a demo song's name, the question about it is then asked of the
     * file the user opened, and not of songs they never asked for.
     */
    private val demoLibraryDecision = CompletableDeferred<Unit>()

    /**
     * True while a place the app was asked to open on is waiting for the library to be read, see [navigateOnLaunch].
     * The launch screen waits for it too ([hasLibraryToShow]), so that the app is uncovered on the screen it was
     * asked for rather than on the song list, a moment before that screen slides in over it.
     */
    private val _isLaunchNavigationPending = MutableStateFlow(false)
    internal val isLaunchNavigationPending: StateFlow<Boolean> = _isLaunchNavigationPending.asStateFlow()
    private var hasNavigatedOnLaunch = false

    /**
     * True until the draft a previous run left behind has been read and, where there was one, the editor reopened on
     * it (see [recoverEditorDraft]). The launch screen waits for it, so that the app is uncovered on the editor rather
     * than on the songs a moment before the editor slides in over them.
     */
    private val _isEditorDraftRecoveryPending = MutableStateFlow(true)

    /** Completed with whether the editor was reopened on a stored draft, which the other start-up navigations defer to. */
    private val editorDraftRecovery = CompletableDeferred<Boolean>()

    /**
     * False for as long as the song list would have nothing on it but a loading indicator, which is what the launch
     * screen stays up in place of. Either the read has put songs together - the first batch of one that publishes as
     * it goes counts, so a slow read still fills the list in front of the user rather than behind the launch screen -
     * or it has finished with none, which is an answer to show as much as a library is. On the one run where a
     * library that has finished empty is about to be filled anyway ([isDemoLibraryPending]), neither is true yet, and
     * neither is it while the screen the app was asked to open on is still being looked for ([isLaunchNavigationPending]),
     * nor while a draft a previous run left is being reopened ([_isEditorDraftRecoveryPending]).
     *
     * Latched like [arePreferencesLoaded]: a rescan reads the library again and says so, and none of that is a reason
     * to put the launch screen back up over an app the user is already using.
     */
    val hasLibraryToShow = combine(
        screenData,
        isDemoLibraryPending,
        _isLaunchNavigationPending,
        _isEditorDraftRecoveryPending,
    ) { state, isDemoLibraryPending, isLaunchNavigationPending, isEditorDraftRecoveryPending ->
        !isDemoLibraryPending && !isLaunchNavigationPending && !isEditorDraftRecoveryPending &&
                (state !is DataState.Loading || state.data?.songs?.isNotEmpty() == true)
    }
        .runningFold(false) { hasHadSomethingToShow, hasSomethingToShow -> hasHadSomethingToShow || hasSomethingToShow }
        .asState(false)

    /**
     * Whether the launch screen has already been taken away once. A plain flag rather than a state, since it is only
     * read as the root composition starts: Android recreates its activity, and with it the whole composition, on the
     * configuration changes it does not handle itself (the language and the dark mode, see the manifest) and when the
     * system reclaims it, and a composition that started from nothing would put the launch screen back over an app the
     * user is already using. Worse, it would hold the new activity's first frame back until that screen had faded,
     * which is a frozen window and a few hundred milliseconds of lost taps on every recreation. A process that is
     * started again gets a new view model, which is the start the launch screen is for.
     */
    internal var hasShownApp = false
        set(value) {
            field = value
            if (value) _isAppOnScreen.value = true
        }

    /**
     * [hasShownApp] as something to wait for, which the welcome sheet does, see [showWelcomeOnFirstRun], and as
     * something to watch, which the store's update check does, see `rememberAppUpdateController`.
     */
    private val _isAppOnScreen = MutableStateFlow(false)
    internal val isAppOnScreen: StateFlow<Boolean> = _isAppOnScreen.asStateFlow()

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
        editorDraftFileName = { _editorDraft.value?.fileName },
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
        editorDraft = { _editorDraft.value },
        emitEditorTextEdit = { _editorTextEdits.tryEmit(it) },
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

    /**
     * What the open editor currently has in it, reported by the screen as it is typed but never written until the
     * user asks for it. The text itself still lives in the field's own state; this copy exists so that leaving the
     * screen can be stopped ([navigateBack]) and the save finished from the confirmation dialog without the screen
     * that holds the field being there any more. Null whenever no editor is open.
     */
    private val _editorDraft = MutableStateFlow<SongContent?>(null)

    /**
     * The draft as it was last put on disk, so that a pause that finds the same text writes nothing. Only read or written
     * under [editorDraftStoreMutex], and only once [recoverEditorDraft] has read what a previous run left.
     */
    private var storedEditorDraft: SongContent? = null
    private val editorDraftStoreMutex = Mutex()

    /**
     * The open editor's field as its screen last saved it, by file name. The screen's saved state only holds the text,
     * and not even that for a long document (see the editor's saver); this holds the field itself, for the
     * restorations this object lives through - a rotation, the system changing its theme or language - where the
     * undo history and a draft of any length can simply be handed back.
     */
    private var retainedEditorField: Pair<String, TextFieldState>? = null

    /** Emitted when the item of the top level screen that is already open is pressed; that screen scrolls to its top. */
    private val _scrollToTopRequests = MutableSharedFlow<CampfireDestination.TopLevel>(extraBufferCapacity = 1)
    val scrollToTopRequests = _scrollToTopRequests.asSharedFlow()

    /** Asked for by the confirmation dialog and answered by the editor screen, see [revertEditorChanges]. */
    private val _editorRevertRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val editorRevertRequests = _editorRevertRequests.asSharedFlow()

    /**
     * The metadata dialogs' changes to the editor's text, opened from its own overflow menu. Answered by the screen,
     * like [editorRevertRequests], since the field is the screen's and a change made through its own editing is one
     * more step of its undo history that Save writes with everything else typed.
     */
    private val _editorTextEdits = MutableSharedFlow<EditorTextEdit>(extraBufferCapacity = 1)
    val editorTextEdits = _editorTextEdits.asSharedFlow()

    /** True while the editor's text differs from what is on disk, which is what [navigateBack] asks before it leaves. */
    val hasUnsavedEditorChanges = combine(_editorDraft, songTexts, userPreferences) { draft, songTexts, _ ->
        draft != null && draft.text != songTexts[draft.fileName]?.let(::editorTextOf)
    }.asState(false)

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
        editorDraft = _editorDraft,
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

    /** True from the moment the demo library is asked for until the offer has caught up with the outcome, see [importDemoLibrary]. */
    private val isAddingDemoLibrary = MutableStateFlow(false)

    /**
     * The settings screen's offer to add the demo library, null for as long as there is nothing to offer: while the
     * library holds all of it, and until the library has been read, like [librarySummary] and for the same reason -
     * the offer must not appear for a moment over a library that turns out to hold it.
     *
     * Whether it is there is answered by what is on disk rather than by anything the app remembers doing, so deleting
     * one of the demo songs brings the offer back - which is what makes it a way to restore them as well as a way to
     * get them.
     *
     * The offer and whether it can be taken are one value on purpose. As two, the import finishing and the library it
     * wrote reach the screen as separate updates, and the row would be enabled again for as long as the second one
     * takes, which is exactly while it is fading out of the list.
     */
    val demoLibraryOffer = combine(screenData, isAddingDemoLibrary, isImporting) { state, isAddingDemoLibrary, isImporting ->
        when (state.data?.takeIf { it.isWholeLibrary }?.let { DemoLibrary.isPresentIn(songs = it.unfilteredSongs, setlists = it.setlists) }) {
            false -> if (isAddingDemoLibrary || isImporting) DemoLibraryOffer.UNAVAILABLE else DemoLibraryOffer.AVAILABLE
            true, null -> null
        }
    }.asState(null)

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
     * The last write of the editor's text, from its Save action or from the `UnsavedChanges` dialog, which closing the
     * application waits for, see [requestExit].
     */
    private var currentSaveJob: Job? = null

    /** The save the `UnsavedChanges` dialog is waiting for, so that a second press of its Save does not start another. */
    private var editorLeaveJob: Job? = null

    /**
     * The exit that asked the `UnsavedChanges` question, run once it is answered with Save or Discard. Any other way
     * the dialog goes away is staying, and that is reported too: on macOS the exit may be the system's own quit
     * request, which has to be answered either way (see the desktop app module).
     */
    private var pendingExit: PendingExit? = null

    /** An exit that is waiting for an answer, and what to tell the caller if the answer is staying. */
    private class PendingExit(
        val exit: () -> Unit,
        val onCancelled: () -> Unit,
    )

    /**
     * Taken rather than read, so that exactly one of an exit's two callbacks can ever run: the branch that is about to
     * run the exit holds it before [leaveEditor] dismisses the dialog, which would otherwise report it as cancelled.
     */
    private fun takePendingExit() = pendingExit.also { pendingExit = null }

    /** True while the editor's text is being written, which it shows in place of its "Saved" label. */
    private val _isSavingSong = MutableStateFlow(false)
    val isSavingSong: StateFlow<Boolean> = _isSavingSong.asStateFlow()

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

    init {
        libraryState.startLoading()
        viewModelScope.launch { plantDemoLibraryOnFirstRun() }
        viewModelScope.launch { showWelcomeOnFirstRun() }
        viewModelScope.launch { showWhatsNewOnVersionChange() }
        syncController.startRestoring(isFirstLaunch = isFirstLaunch, onConsentAnswered = ::openSyncSettingsAfterConsent)
        songTextStore.startFollowingFiles()
        importController.startQueue(demoLibraryDecision)
        dialogHost.startClosingWithSong(allSongs = allSongs, isLoading = isLoading, songsBeingRenamed = songsBeingRenamed)
        // The editor's unsaved text as a previous run left it, when the process ended in the background (see
        // onAppPaused). Once that is settled, whatever leaves the editor with nothing unsaved takes the stored
        // draft with it - a save, a discard, a revert, the song deleted - so a crash after a save never brings back
        // text that was saved. Asked of the draft itself rather than of hasUnsavedEditorChanges alone, which may not
        // have caught up yet with a draft this has just reopened.
        viewModelScope.launch {
            try {
                editorDraftRecovery.complete(recoverEditorDraft())
            } finally {
                editorDraftRecovery.complete(false)
                _isEditorDraftRecoveryPending.value = false
            }
            hasUnsavedEditorChanges.collect { if (!it && !hasUnsavedEditorText()) storeEditorDraft(null) }
        }
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

    /** Reported by the UI whenever the state of the navigation transition changes, see [navigationGeneration]. */
    fun setNavigationTransitionRunning(isRunning: Boolean) {
        val hasTransitionEnded = isNavigationTransitionRunning && !isRunning
        isNavigationTransitionRunning = isRunning
        if (hasTransitionEnded) pruneSongTexts()
    }

    private fun pruneSongTexts() = songTextStore.pruneSongTexts()

    private fun updateBackStack(
        isPredictiveBackCompleted: Boolean = false,
        update: SnapshotStateList<CampfireDestination>.() -> Unit,
    ) {
        if (isNavigationTransitionRunning && !isPredictiveBackCompleted) navigationGeneration++
        val previousTop = backStack.lastOrNull()
        backStack.update()
        if (backStack.lastOrNull() != CampfireDestination.Setlists) reorderingSetlistFileName = null
        // The two screens that hold a metronome are the two it can be stopped from, so a click never outlives the one
        // it was started on: the editor opened over a song, a song closed, a tab selected, all stop it - and so does a
        // different screen arriving on top, a song opened over the Metronome tab or over another song, which holds a
        // metronome but not the one that was started. The panel the click was played from is a preference and stays
        // where the user put it, so the next song is read to a click without asking for the instrument again.
        if (isMetronomeScreenLeft(previousTop = previousTop, top = backStack.lastOrNull())) metronome.stop()
        if (backStack.none { it is CampfireDestination.SongEditor }) retainedEditorField = null
        backStack.mapNotNullTo(mutableSetOf()) { (it as? CampfireDestination.SongDetails)?.id }.let { ids ->
            songDetailsCurrentSongs.keys.retainAll(ids)
            songDetailsTargetSongs.keys.retainAll(ids)
            songDetailsTargetTimings.keys.retainAll(ids)
        }
        importController.onBackStackChanged()
        persistBackStack()
    }

    /**
     * Called after every change to [backStack]. The state Navigation 3 saves for each entry (the editor's text, a
     * scroll position) is saved with the Activity regardless, but it is only ever handed back to an entry with the
     * same content key, so a stack that restarted on the Songs screen would leave all of it behind unclaimed.
     *
     * A stack whose JSON would not fit [MAX_SAVED_BACK_STACK_LENGTH] is saved only up to the screen that makes it too
     * long - in practice a song opened from a setlist of thousands, which names every one of them. A restored process
     * then comes back one screen short, which beats one that crashes as it is sent to the background: the saved state
     * crosses to the system in a single transaction of at most a megabyte.
     */
    private fun persistBackStack() {
        val stack = backStack.toList()
        // The stack is a few screens deep at most, so encoding it once per screen is nothing, and the first one fits
        // in every case but the pathological one.
        savedStateStore.persistJson(
            BACK_STACK_KEY,
            (stack.size downTo 1).asSequence()
                .map { Json.encodeToString<List<CampfireDestination>>(stack.subList(0, it)) }
                .firstOrNull { it.length <= MAX_SAVED_BACK_STACK_LENGTH }
                ?: Json.encodeToString<List<CampfireDestination>>(listOf(CampfireDestination.Songs)),
        )
    }

    /** Reported by the song details screen whenever its pager comes to rest, see [songDetailsCurrentSongs]. */
    internal fun onSongDetailsPageSettled(destination: CampfireDestination.SongDetails, songFileName: String) {
        if (backStack.any { it is CampfireDestination.SongDetails && it.id == destination.id }) {
            songDetailsCurrentSongs[destination.id] = songFileName
        }
    }

    /** The file name of the song the given details screen is showing, which is where it was opened until it is paged. */
    internal fun currentSongFileName(destination: CampfireDestination.SongDetails) =
        songDetailsCurrentSongs[destination.id] ?: destination.songFileNames.getOrNull(destination.initialIndex) ?: destination.songFileNames.firstOrNull()

    /**
     * Where the user is right now, see [NavigationState]. Its snapshot states can be observed with snapshotFlow, and the
     * two searches, which are flows, have to be combined in by whoever observes it.
     */
    internal val navigationState: NavigationState
        get() = NavigationState(
            backStack = backStack.map { destination ->
                if (destination is CampfireDestination.SongDetails) {
                    destination.copy(initialIndex = destination.songFileNames.indexOf(currentSongFileName(destination)).takeIf { it >= 0 } ?: destination.initialIndex)
                } else {
                    destination
                }
            },
            isSongsSearchOpen = songsSearch.isOpen.value,
            isSetlistsSearchOpen = setlistsSearch.isOpen.value,
            settingsTab = settingsTab,
        )

    /**
     * Takes the user to [state] in one step, which is how the web build follows an address it was opened on or the
     * browser's Forward button. Refused, returning false, while the editor holds unsaved text: nothing but the
     * editor's own ways out may take that text off the screen, and those ask first. A search it opens is reopened on the
     * text its field still holds, since this is stepping back to a place rather than asking anything new. A screen of a
     * feature switched off is cut off with everything above it ([withoutDisabledFeatures]), so an address naming one
     * opens what is under it, and the browser's address is then written over with that.
     */
    internal fun restoreNavigationState(state: NavigationState): Boolean {
        if (hasUnsavedEditorText()) return false
        // Read from the repository's own state, which the launch has waited for, rather than from userPreferences,
        // which may not have caught up with the read yet.
        val preferences = userPreferencesState.value.data
        val allowedState = state.withoutDisabledFeatures(
            areSetlistsEnabled = preferences?.areSetlistsEnabled != false,
            isMetronomeEnabled = preferences?.isMetronomeEnabled != false,
        )
        if (allowedState.backStack.isEmpty()) return false
        settingsTab = allowedState.settingsTab
        listOf(songsSearch to allowedState.isSongsSearchOpen, setlistsSearch to allowedState.isSetlistsSearchOpen).forEach { (search, isOpen) ->
            if (isOpen != search.isOpen.value) if (isOpen) search.reopen() else search.close()
        }
        if (backStack.toList() != allowedState.backStack) {
            updateBackStack {
                clear()
                addAll(allowedState.backStack)
            }
        }
        return true
    }

    /**
     * Opens the app on the place [resolve] picks from the library, once the library has been read - the web build's
     * address, which can name a song or a setlist that only the library can say is still there. The launch screen is
     * held until then, see [isLaunchNavigationPending]. Only the first call counts, since only one start up can be
     * answered, and the answer is only taken while the app is still where every start opens: coming back from a
     * consent page opens Settings on its own, and that is the screen the user is waiting for. [resolve] is told whether
     * performance mode is on as the preferences were read rather than as [isPerformanceModeEnabled] says, since that
     * state may not have caught up with the read yet, and the mode decides whether an editor may be opened at all.
     *
     * Called by the shell while it is first composed, which is before the read it waits for can possibly have ended:
     * that read resumes on the main thread, and the composition is holding it.
     */
    internal fun navigateOnLaunch(resolve: (songs: List<Song>, setlists: List<Setlist>, isPerformanceModeEnabled: Boolean) -> NavigationState?) {
        if (hasNavigatedOnLaunch) return
        hasNavigatedOnLaunch = true
        _isLaunchNavigationPending.value = true
        viewModelScope.launch {
            try {
                val state = combine(screenData, isDemoLibraryPending) { state, isDemoLibraryPending -> state.takeUnless { isDemoLibraryPending } }
                    .first { it != null && it !is DataState.Loading }
                    ?.data
                val isPerformanceModeEnabled = userPreferencesState.first { it !is DataState.Loading }.data?.isPerformanceModeEnabled == true
                val destination = resolve(state?.unfilteredSongs.orEmpty(), state?.setlists.orEmpty(), isPerformanceModeEnabled)
                // A draft reopened on launch is unsaved text on screen, which an address does not get to replace.
                editorDraftRecovery.await()
                if (destination != null && backStack.toList() == listOf(CampfireDestination.Songs)) {
                    restoreNavigationState(destination)
                }
            } finally {
                _isLaunchNavigationPending.value = false
            }
        }
    }

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

    /**
     * Rebuilds the stack around a top level screen. Refused while an editor on the stack holds unsaved text: the
     * navigation chrome that calls this is hidden over the editor, and nothing else may take that text off the screen
     * without asking, see [navigateBack]. Refused too for a screen whose feature is switched off: its item stays on
     * screen, and tappable, for as long as it takes to shrink away.
     */
    fun selectTopLevelDestination(destination: CampfireDestination.TopLevel) {
        if (destination !in topLevelDestinations.value) return
        if (backStack.lastOrNull() == destination) {
            // Pressing the item of the screen that is already open takes that screen back to its resting state.
            if (destination != CampfireDestination.Settings) currentSearch?.close()
            _scrollToTopRequests.tryEmit(destination)
            return
        }
        if (hasUnsavedEditorText() && backStack.any { it is CampfireDestination.SongEditor }) return
        // Settings keeps its tab for the session but is always scrolled to the top of it on arrival; the other two screens
        // keep where they were left.
        if (destination == CampfireDestination.Settings) {
            settingsScrollPositions.values.forEach { it.offset = 0 }
        }
        updateBackStack {
            clear()
            add(CampfireDestination.Songs)
            if (destination != CampfireDestination.Songs) {
                add(destination)
            }
        }
    }

    fun openSong(song: Song) = openSongDetails(
        CampfireDestination.SongDetails(songFileNames = listOf(song.fileName), setlistFileName = null, initialIndex = 0)
    )

    /**
     * The pager can only page through the songs that are actually there, so the index is taken from those rather
     * than from the position of the row in the setlist, which also counts the entries whose file is missing.
     */
    fun openSongInSetlist(setlistWithSongs: SetlistWithSongs, song: Song) = openSongDetails(
        CampfireDestination.SongDetails(
            songFileNames = setlistWithSongs.songs.map { it.fileName },
            setlistFileName = setlistWithSongs.setlist.fileName,
            initialIndex = setlistWithSongs.songs.indexOfFirst { it.fileName == song.fileName }.coerceAtLeast(0),
        )
    )

    private fun openSongDetails(destination: CampfireDestination.SongDetails) {
        if (backStack.lastOrNull() !is CampfireDestination.SongDetails) {
            updateBackStack { add(destination) }
        }
    }

    /**
     * Where a file opened with the app lands. It is put on top of whatever is on screen, so that Back returns there,
     * except over a song that is already open, which it takes the place of rather than stacking a second pager on.
     * An editor is covered like any other screen as long as everything in it is saved, and Back returns to it. One
     * holding unsaved text is left alone, as [selectTopLevelDestination] leaves it: the song's arrival is announced all
     * the same, and the unsaved changes question is only ever asked of an editor on top, so the text would be one
     * closed window away from being lost without it.
     */
    internal fun openImportedSong(fileName: String) {
        if (hasUnsavedEditorText() && backStack.any { it is CampfireDestination.SongEditor }) return
        val songFileNames = listOf(fileName)
        val current = backStack.lastOrNull()
        if (current is CampfireDestination.SongDetails && current.setlistFileName == null && current.songFileNames == songFileNames) return
        updateBackStack {
            if (current is CampfireDestination.SongDetails) removeAt(lastIndex)
            add(CampfireDestination.SongDetails(songFileNames = songFileNames, setlistFileName = null, initialIndex = 0))
        }
    }

    /**
     * A song of the import screen's list, opened in a pager over the songs of the group it was listed in, so that what an
     * import brought can be read through one after the other and Back returns to the list.
     */
    internal fun openReportedSong(songFileNames: List<String>, index: Int) {
        val (pages, initialIndex) = reportedSongPages(songFileNames, index)
        openSongDetails(CampfireDestination.SongDetails(songFileNames = pages, setlistFileName = null, initialIndex = initialIndex))
    }

    /**
     * Every way out of a screen ends up here - the app bar's button, the system's back gesture and the desktop
     * window's Escape key - which is why this is where the editor's unsaved text is caught: nothing the user typed
     * is thrown away without being asked about it first, and why a settings tab other than General goes back to that
     * one before the screen is left ([isSettingsBackToGeneral]).
     *
     * @param isPredictiveBackCompleted Whether this is the pop a predictive back gesture ends in, see
     *   [navigationGeneration].
     */
    fun navigateBack(isPredictiveBackCompleted: Boolean = false) {
        when {
            isSetlistReordering -> reorderingSetlistFileName = null
            hasUnsavedEditorChanges.value && backStack.lastOrNull() is CampfireDestination.SongEditor -> {
                showDialog(DialogType.UnsavedChanges)
            }
            isSettingsBackToGeneral -> settingsTab = SettingsTab.GENERAL
            else -> popBackStack(isPredictiveBackCompleted)
        }
    }

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
            currentSaveJob?.join()
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
        if (!_isEditorDraftRecoveryPending.value) storeEditorDraft(currentEditorDraftToStore())
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

    /** [hasUnsavedEditorChanges] as of this moment, for a decision taken right after a write rather than drawn. */
    private fun hasUnsavedEditorText() = _editorDraft.value?.let { it.text != songTexts.value[it.fileName]?.let(::editorTextOf) } == true

    private fun popBackStack(isPredictiveBackCompleted: Boolean = false) {
        if (backStack.size > 1) {
            updateBackStack(isPredictiveBackCompleted = isPredictiveBackCompleted) { removeAt(lastIndex) }
        }
    }

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
        _editorDraft.update { null }
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

    fun openEditor(fileName: String, shouldStartInsideFirstSection: Boolean = false) {
        if (backStack.lastOrNull() !is CampfireDestination.SongEditor) {
            updateBackStack { add(CampfireDestination.SongEditor(fileName = fileName, shouldStartInsideFirstSection = shouldStartInsideFirstSection)) }
        }
    }

    /** Reported by the editor on every change, see [_editorDraft]. Nothing is written here. */
    fun onEditorTextChanged(fileName: String, text: String) = _editorDraft.update { SongContent(fileName = fileName, text = text) }

    /**
     * Reported by the editor once it is gone, whatever became of the text it had. An editor that is still on the
     * stack is only being composed again - a rotation, the system changing its theme - and its draft stands until the
     * new composition reports it: dropped in between, the moment would read as "nothing unsaved" to everything that
     * asks, the update gate and the way out of the editor included.
     */
    fun onEditorClosed(fileName: String) {
        if (backStack.none { it is CampfireDestination.SongEditor && it.fileName == fileName }) {
            _editorDraft.update { draft -> draft?.takeUnless { it.fileName == fileName } }
        }
    }

    /**
     * Called by the editor whenever its state is saved. That includes one last time as the screen leaves for good,
     * by which time the stack has let go of it - and then there is nothing to keep the field for.
     */
    fun retainEditorField(fileName: String, textFieldState: TextFieldState) {
        if (backStack.any { it is CampfireDestination.SongEditor && it.fileName == fileName }) {
            retainedEditorField = fileName to textFieldState
        }
    }

    fun retainedEditorField(fileName: String) = retainedEditorField?.takeIf { it.first == fileName }?.second

    /** Reported by the editor when it came back from a saved state that could not hold its unsaved text. */
    fun onEditorDraftLost() {
        sendMessage(Message.EditorDraftLost)
    }

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
        if (!_isEditorDraftRecoveryPending.value) {
            val draft = currentEditorDraftToStore()
            viewModelScope.launch { storeEditorDraft(draft) }
        }
        return syncProgress
    }

    fun onAppStopped(areBeatsFeltInBackground: Boolean) = metronomeController.onAppStopped(areBeatsFeltInBackground)

    fun onAppStarted() = metronomeController.onAppStarted()

    /**
     * The unsaved text as the file would hold it, or null when nothing is unsaved. Stored as the file would hold it
     * rather than as the field shows it, so that it means the same chords whatever the notation is by the time it is
     * reopened.
     */
    private fun currentEditorDraftToStore() = _editorDraft.value?.takeIf { hasUnsavedEditorText() }?.let { it.copy(text = fileTextOf(it.text)) }

    /** Not cancellable once started: a pause is often the last thing the process does. A write that fails is only a copy lost. */
    private suspend fun storeEditorDraft(draft: SongContent?) = withContext(NonCancellable) {
        editorDraftStoreMutex.withLock {
            if (draft == storedEditorDraft) return@withLock
            try {
                saveEditorDraft(draft)
                storedEditorDraft = draft
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                println("Could not store the editor's draft: ${exception::class.simpleName}")
            }
        }
    }

    /**
     * Reopens the editor on the draft a previous run left, answering whether it did. Not over a stack that already has
     * an editor - an Android process restored on the editor has its text from the saved state - and not for a draft the
     * file already holds. A file that is gone is reopened all the same: the draft is all there is, and saving puts the
     * file back, which is what an editor whose file goes while it is open does too.
     */
    private suspend fun recoverEditorDraft(): Boolean {
        val draft = try {
            getEditorDraft()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not read the editor's draft: ${exception::class.simpleName}")
            null
        }
        editorDraftStoreMutex.withLock { storedEditorDraft = draft }
        if (draft == null || backStack.any { it is CampfireDestination.SongEditor }) return false
        val content = getSongContent(draft.fileName)
        // The draft is in the file's notation and the field shows the reader's, which only the preferences can say.
        arePreferencesLoaded.first { it }
        val text = editorTextOf(draft.text)
        if (content != null && editorTextOf(content.text) == text) {
            storeEditorDraft(null)
            return false
        }
        content?.let { songTextStore.updateSongTexts { texts -> texts + (it.fileName to it.text) } }
        // The draft is the editor's before the editor exists, so that nothing asking whether there is unsaved text in
        // the moments before it composes - a pause, the update gate - hears "no".
        onEditorTextChanged(fileName = draft.fileName, text = text)
        updateBackStack { add(CampfireDestination.SongEditor(fileName = draft.fileName)) }
        // Taken by the editor as its field, see LoadedSongEditor, the same way a field it retained across a rotation is.
        retainedEditorField = draft.fileName to TextFieldState(initialText = text)
        sendMessage(Message.EditorDraftRestored)
        if (content == null) sendMessage(Message.EditedSongFileGone)
        return true
    }

    /**
     * The "Save" answer of the unsaved changes dialog. The editor holds the only copy of the text until the file
     * does, so leaving - and, where the dialog was asked by [requestExit], ending the process - is what a write that
     * succeeded earns, not what follows one that was started. The dialog stays up while the file is written. A write
     * that fails takes the dialog away and leaves the editor as it was, which is what the same failure does behind
     * the editor's own Save button; a dialog dismissed in the meantime still means staying, with the text saved.
     */
    fun saveEditorChangesAndLeave() {
        if (editorLeaveJob?.isActive == true) return
        val draft = _editorDraft.value ?: return leaveEditorWithoutSaving()
        editorLeaveJob = viewModelScope.launch {
            val isSaved = writeEditorText(fileName = draft.fileName, text = draft.text)
            when {
                !isSaved -> dismissDialog()
                visibleDialog.value == DialogType.UnsavedChanges -> {
                    // Taken before leaving, which dismisses the dialog and would otherwise report this exit as
                    // cancelled.
                    val exit = takePendingExit()
                    leaveEditor()
                    exit?.exit?.invoke()
                }
            }
        }.also { currentSaveJob = it }
    }

    /** The "Discard" answer of the unsaved changes dialog, and the only way typed text is ever thrown away. */
    fun leaveEditorWithoutSaving() {
        val exit = takePendingExit()
        leaveEditor()
        exit?.let { requestExit(onExit = it.exit, onCancelled = it.onCancelled) }
    }

    /**
     * The confirmed "Revert" action of the editor. Answered by the screen rather than here, because the text field
     * belongs to it and putting the saved text back has to go through the field's own editing (and its undo
     * history); what the text goes back to is [songTexts], which is the file as it was last read or written.
     */
    fun revertEditorChanges() {
        dismissDialog()
        _editorRevertRequests.tryEmit(Unit)
    }

    private fun leaveEditor() {
        // The draft goes first: with it still there, popping the editor would only ask the same question again.
        _editorDraft.update { null }
        dismissDialog()
        popBackStack()
    }

    /** The editor's Save action. Fire and forget: the outcome reaches the user as the editor's own state. */
    fun saveSongContent(fileName: String, text: String) = viewModelScope.launch {
        writeEditorText(fileName = fileName, text = text)
    }.also { currentSaveJob = it }

    /**
     * Writes the edited text, keeps the copy the viewer renders from in step, and answers whether the file now holds
     * it. A failure is reported from here as [Message.SaveFailed], so that every way of saving says the same thing.
     * Runs on [NonCancellable] because the last save of an editing session can be started as the screen is going
     * away, which cancels its scope.
     */
    private suspend fun writeEditorText(fileName: String, text: String) = try {
        _isSavingSong.update { true }
        val fileText = fileTextOf(text)
        withContext(NonCancellable) {
            songWriteMutex.withLock { writeSongContent(fileName = fileName, text = fileText) }
        }.also { isWritten ->
            // A chord typed in a spelling the notation has a second one for (a German Bb, a ♭) is written the one way
            // the file holds it, so the field is brought to what was written, or it would never stop being unsaved.
            val writtenText = editorTextOf(fileText)
            if (isWritten && writtenText != text) {
                _editorTextEdits.tryEmit(EditorTextEdit(fileName = fileName) { current -> if (current == text) writtenText else current })
            }
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not save the song \"$fileName\": ${exception.message}")
        sendMessage(Message.SaveFailed)
        false
    } finally {
        _isSavingSong.update { false }
    }

    private suspend fun writeSongContent(fileName: String, text: String, expectedText: String? = null) =
        songTextStore.writeSongContent(fileName, text, expectedText)

    fun loadSongContent(fileName: String) = songTextStore.loadSongContent(fileName)

    /**
     * The notation the editor's field is written in, the reader's own: the file is converted out of the standard one
     * as it is opened ([SongRenderer.editorTextOf]) and back into it as it is saved ([SongRenderer.fileTextOf]). It never changes under an open
     * editor, since Settings is only reached by selecting a top level screen, which takes the editor off the stack.
     *
     * Under a numbering it is the standard notation, and only the preview is in numbers: a field in numbers would make
     * correcting a wrong `{key}` move every chord of the song at Save, the numbers staying where they are, and a key
     * half typed would leave every number with nothing to count from (see [UserPreferences.Notation.forTyping]).
     */
    val editorNotation get() = (userPreferences.value?.chordSpelling?.notation ?: UserPreferences.Notation.STANDARD).forTyping

    /** The text of a file as the editor shows it, see [SongRenderer.editorTextOf]. */
    private fun editorTextOf(fileText: String) = songRenderer.editorTextOf(fileText, editorNotation)

    /** The editor's text as the file is to hold it, in the standard notation. */
    private fun fileTextOf(editorText: String) = songRenderer.fileTextOf(editorText, editorNotation)

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

    /**
     * The songs the app is shipped with, put into the library the way any other batch of files is. The settings
     * screen offers this for as long as they are not all there, so the same action both plants them and puts back
     * the ones that have been deleted.
     */
    fun importDemoLibrary() = viewModelScope.launch {
        if (isImporting.value || !isAddingDemoLibrary.compareAndSet(expect = false, update = true)) return@launch
        val files = readDemoLibrary()
        if (files == null) {
            sendMessage(Message.ImportFailed)
        } else {
            enqueueImport(files, isDemoLibrary = true).await()
        }
        // Asked of a fresh read of the library rather than of the offer, which can still be a step behind the import
        // that just finished. Where the demo is now all there, the offer is waited for until it has left the screen,
        // so the row fades out disabled instead of coming back for the moments in between.
        val library = getScreenData(_songFilter).first { it !is DataState.Loading }.data
        if (library != null && DemoLibrary.isPresentIn(songs = library.unfilteredSongs, setlists = library.setlists)) {
            demoLibraryOffer.first { it == null }
        }
        isAddingDemoLibrary.update { false }
    }

    /**
     * The one thing the app ever puts into the library without being asked, and it only happens to an installation
     * that has nothing of its own: a first run whose scan of the library came back empty. Somebody who has been
     * using Campfire and simply never changed a setting reads as a first run too, which is why the library has to
     * be empty as well - and if it is, then they are a new user by every measure that matters here.
     *
     * Nothing is reported, not even a failure: none of this was asked for, and an empty library is what the user
     * was going to be shown anyway. The preferences are written at the end instead, on every first run whether
     * anything was planted or not, which is what makes the next launch not a first run: without it, deleting every
     * demo song would be undone by the next launch, and so would a library the user filled on the first day and
     * emptied later without ever changing a setting.
     */
    private suspend fun plantDemoLibraryOnFirstRun() {
        try {
            if (isFirstLaunch.await()) {
                // Waited for rather than raced: what is being asked is whether the library is empty, and every
                // library looks empty while it is still being read. Nothing else writes to it meanwhile, since the
                // import queue waits for this.
                val library = screenData.first { it !is DataState.Loading }.data
                // An unreadable songs or setlists folder stands in empty so that the rest can be shown, and is not an
                // empty library to plant into.
                if (library != null && library.isWholeLibrary && library.unfilteredSongs.isEmpty() && library.setlists.isEmpty()) {
                    // Imported here rather than through importQueue, where a file opened with the app is already
                    // waiting; see demoLibraryDecision. An empty library has no names for it to collide with, so
                    // this never asks anything.
                    readDemoLibrary()?.let { files ->
                        import(
                            ImportRequest(
                                files = files,
                                shouldAnnounceResult = false,
                                shouldOpenSong = false,
                                isDemoLibrary = true,
                            ),
                        )
                        awaitImportSettled()
                    }
                }
                // Before the preferences are written rather than after: neither the queue nor the launch screen has any
                // reason to wait for those.
                demoLibraryDecision.complete(Unit)
                isDemoLibraryPending.update { false }
                // Read and written by the repository rather than copied from userPreferencesState, which can lag it by a
                // dispatch and would write the demo record the import just took back out. With nothing read there is
                // nothing to write either, and the next start is a first run again - which plants nothing into a library
                // that has songs in it.
                updateUserPreferences { it.copy(seenWhatsNewVersions = it.seenWhatsNewVersions + CAMPFIRE_VERSION_NAME) }
            }
        } finally {
            // In a finally rather than at the end: whatever went wrong, the app is no longer waiting for this, and
            // the launch screen is over the whole of it - and neither is the queue of imports.
            demoLibraryDecision.complete(Unit)
            isDemoLibraryPending.update { false }
        }
    }

    /**
     * Puts [DialogType.Welcome] over the app on the first run, once the launch screen has gone: the sheet is a window of
     * its own on Android and would otherwise slide up over the mark rather than over the library it introduces, which
     * is also what lets the colors picked in it be seen taking hold of the app behind it. Only onto a screen with no
     * other dialog on it and no import screen ([ImportReport]) under it - on a first run that is the question about a
     * file the app was opened with, which is a screen of its own rather than a dialog - since a welcome over a question
     * would leave it unanswered, its Open settings taking the import screen off the stack and the import with it. It is
     * skipped rather than put up later: one that waited for the answer would arrive in the middle of whatever the user
     * went on to do next. Showing it is the only time it is shown: the first run's preferences
     * are written as the demo library is settled, before this, so a process that ends with the sheet still up starts
     * the next time without it.
     */
    private suspend fun showWelcomeOnFirstRun() {
        if (!isFirstLaunch.await()) return
        isAppOnScreen.first { it }
        // A report is set before it is pushed, so this also covers one still waiting for its push.
        if (!canShowWelcome(hasDialog = visibleDialog.value != null, hasImportReport = importReport.value != null)) return
        dialogHost.showIfNoneIsShown(DialogType.Welcome)
    }

    /**
     * The first installed version belongs to the welcome, so it is recorded by [plantDemoLibraryOnFirstRun] instead.
     * Later versions wait for the app and for every import queued, running or reported on before opening (see
     * [canShowWhatsNew]), and are recorded by [onWhatsNewShown] once the dialog has stayed uncovered for a few seconds,
     * or here as it closes, rather than as it is asked for or on its first frame: on Android it is not composed while
     * the update required screen covers the app, and Play's answer right after an update can still be the update it
     * just installed - arriving after the dialog opened, too - which puts that screen up and starts its flow, which
     * ends this process; a version recorded before anybody saw it would never be introduced. Recording it once it has
     * stayed up rather than only as it closes still keeps a process ended with the dialog up after that from
     * introducing it again.
     * Keeping every introduced version also makes rolling back and returning to a version silent.
     * An empty release message is recorded at once, so a small release introduces nothing.
     */
    private suspend fun showWhatsNewOnVersionChange() {
        if (isFirstLaunch.await()) return
        val preferences = userPreferencesState.first { it !is DataState.Loading }.data ?: return
        if (CAMPFIRE_VERSION_NAME in preferences.seenWhatsNewVersions) return
        isAppOnScreen.first { it }
        if (LocalizedStrings.get(Res.string.whats_new_message).isNotBlank()) {
            combine(visibleDialog, isImporting, importReport, importController.queuedImportCount) { dialog, isImporting, report, queuedImportCount ->
                canShowWhatsNew(
                    hasDialog = dialog != null,
                    isImporting = isImporting,
                    hasImportReport = report != null,
                    queuedImportCount = queuedImportCount,
                )
            }.first { it }
            if (dialogHost.showIfNoneIsShown(DialogType.WhatsNew)) {
                // A close before the dialog's own delay ran out; being covered leaves the dialog where it is.
                visibleDialog.first { it != DialogType.WhatsNew }
                recordWhatsNewVersion()
            }
        } else {
            recordWhatsNewVersion()
        }
    }

    /** What [DialogType.WhatsNew] calls once it has stayed on screen for a moment, see [showWhatsNewOnVersionChange]. */
    fun onWhatsNewShown() {
        viewModelScope.launch { recordWhatsNewVersion() }
    }

    private suspend fun recordWhatsNewVersion() {
        try {
            withContext(NonCancellable) {
                updateUserPreferences { it.copy(seenWhatsNewVersions = it.seenWhatsNewVersions + CAMPFIRE_VERSION_NAME) }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not remember the introduced version: ${exception.message}")
        }
    }

    /**
     * The welcome sheet's way on to the rest of the settings, on the tab holding sync where this build has any: that
     * is the one thing the sheet points at that nothing else in the app would lead a new user to. The sheet closes
     * itself, since only it can do that with the animation it closes with everywhere else.
     */
    fun openSettingsFromWelcome() {
        if (backStack.lastOrNull() != CampfireDestination.Settings) selectTopLevelDestination(CampfireDestination.Settings)
        settingsTab = if (syncProviders.isEmpty()) SettingsTab.GENERAL else SettingsTab.LIBRARY
    }

    /**
     * Null where the bundled files could not be read, which each caller then says as much about as it should. On the
     * web they are requests to the site, and one that neither answers nor fails would otherwise hold whatever waits for
     * it for good - on a first run, the launch screen, which only goes once the demo has been planted or given up on.
     */
    private suspend fun readDemoLibrary() = try {
        withTimeoutOrNull(DEMO_LIBRARY_READ_TIMEOUT_MILLIS) { DemoLibrary.read() }
            .also { if (it == null) println("Could not read the demo library in time.") }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not read the demo library: ${exception.message}")
        null
    }

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
        private const val DEMO_LIBRARY_READ_TIMEOUT_MILLIS = 10_000L // Past the drawables' five seconds: it cuts short a first impression, not a frame.
        private const val MAX_SAVED_BACK_STACK_LENGTH = 100_000 // Characters of JSON, about 200 KB as the UTF-16 a Bundle writes.
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
