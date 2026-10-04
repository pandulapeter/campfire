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
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.ChordProMetadataFields
import com.pandulapeter.campfire.chordpro.ChordProSummaryCache
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.data.model.domain.CoverArtSearchResults
import com.pandulapeter.campfire.data.model.domain.ExportedFile
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.ImportProgress
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.normalizedToNfc
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncProgress
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import com.pandulapeter.campfire.presentation.ui.print.PrintSong
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.models.SongFilter
import com.pandulapeter.campfire.domain.api.models.SongSection
import com.pandulapeter.campfire.domain.api.useCases.CancelSyncConnectionUseCase
import com.pandulapeter.campfire.domain.api.useCases.CancelSynchronizationUseCase
import com.pandulapeter.campfire.domain.api.useCases.ClearCoverArtCacheUseCase
import com.pandulapeter.campfire.domain.api.useCases.ConnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.useCases.ConvertChordProNotationUseCase
import com.pandulapeter.campfire.domain.api.useCases.PrettifyChordProUseCase
import com.pandulapeter.campfire.domain.api.useCases.ConvertChordProTextNotationUseCase
import com.pandulapeter.campfire.domain.api.useCases.CreateSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.CreateSongUseCase
import com.pandulapeter.campfire.domain.api.useCases.DeleteLibraryUseCase
import com.pandulapeter.campfire.domain.api.useCases.DeleteSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.DeleteSongUseCase
import com.pandulapeter.campfire.domain.api.useCases.DisconnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.useCases.ForgetSyncConnectionUseCase
import com.pandulapeter.campfire.domain.api.useCases.EditSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.ExportLibraryUseCase
import com.pandulapeter.campfire.domain.api.useCases.ExportSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.ExportSongsUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentInvalidationsUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetCoverArtCacheSizeUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetEditorDraftUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSyncProvidersUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSyncStateUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetUserPreferencesUseCase
import com.pandulapeter.campfire.domain.api.useCases.ImportFilesUseCase
import com.pandulapeter.campfire.domain.api.useCases.IsFirstRunUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeLanguageCodeUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeSearchTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.ParseChordProUseCase
import com.pandulapeter.campfire.domain.api.useCases.PrepareImportUseCase
import com.pandulapeter.campfire.domain.api.useCases.RestoreSyncUseCase
import com.pandulapeter.campfire.domain.api.useCases.RenameSongFileUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveEditorDraftUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveUserPreferencesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SearchCoverArtUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProCoverArtUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProLanguagesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProLinksUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProMetadataUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProTagUseCase
import com.pandulapeter.campfire.domain.api.useCases.StartScheduledSynchronizationUseCase
import com.pandulapeter.campfire.domain.api.useCases.SynchronizeLibraryUseCase
import com.pandulapeter.campfire.domain.api.useCases.TransposeChordProTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.TransposeChordProUseCase
import com.pandulapeter.campfire.domain.api.useCases.UpdateSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.presentation.CAMPFIRE_VERSION_NAME
import com.pandulapeter.campfire.presentation.localization.LocalizedStrings
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.whats_new_message
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.presentation.ui.components.isAnyOverflowMenuOpen
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.NavigationState
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LibraryPersistence
import com.pandulapeter.campfire.presentation.ui.platform.requestLibraryPersistence
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsTab
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.FontScaleAccumulator
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.PINCH_SENSITIVITY
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
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
    private val saveUserPreferences: SaveUserPreferencesUseCase,
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
    private val cancelSynchronization: CancelSynchronizationUseCase,
    private val restoreSync: RestoreSyncUseCase,
    private val synchronizeLibrary: SynchronizeLibraryUseCase,
    private val startScheduledSynchronization: StartScheduledSynchronizationUseCase,
    private val normalizeLanguageCode: NormalizeLanguageCodeUseCase,
    private val normalizeText: NormalizeTextUseCase,
    private val normalizeSearchText: NormalizeSearchTextUseCase,
    private val parseChordPro: ParseChordProUseCase,
    private val searchCoverArt: SearchCoverArtUseCase,
    getCoverArtCacheSize: GetCoverArtCacheSizeUseCase,
    private val clearCoverArtCache: ClearCoverArtCacheUseCase,
    private val transposeChordPro: TransposeChordProUseCase,
    private val transposeChordProText: TransposeChordProTextUseCase,
    private val convertChordProNotation: ConvertChordProNotationUseCase,
    private val convertChordProTextNotation: ConvertChordProTextNotationUseCase,
    private val prettifyChordPro: PrettifyChordProUseCase,
    /**
     * What survives the system killing the process while the app is in the background, which Android does whenever it
     * needs the memory: the back stack, the song filter and the two searches. Empty on every real start, and on the
     * platforms that have no such thing as a process being restored.
     */
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * The tags and languages the song list is narrowed to. Held here and in [savedStateHandle] only, so it lasts
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
    val songFilter = _songFilter.asStateFlow()

    /** Assignment-sheet tags survive reopening the sheet, independently of the main Songs filter. Same run lifetime. */
    private val _songPickerSelectedTags = MutableStateFlow<Set<String>>(
        restore<List<String>>(SONG_PICKER_TAGS_KEY).orEmpty().mapTo(mutableSetOf()) { it.lowercase() }
    )
    internal val songPickerSelectedTags = _songPickerSelectedTags.asStateFlow()

    /** Assignment-sheet languages have the same independent session lifetime as its tags. */
    private val _songPickerSelectedLanguages = MutableStateFlow<Set<String>>(
        restore<List<String>>(SONG_PICKER_LANGUAGES_KEY).orEmpty().toSet()
    )
    internal val songPickerSelectedLanguages = _songPickerSelectedLanguages.asStateFlow()

    /**
     * The single subscription to the domain layer: every state below maps over this instead of over
     * [GetScreenDataUseCase] directly, which would re-run the whole repository combine once per state. Started
     * eagerly so that the data is loaded into memory as the app starts, rather than when a screen first asks for it.
     */
    private val screenData: StateFlow<DataState<ScreenData>> = getScreenData(_songFilter).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        // Loading, not Failure: nothing has been asked for yet, which is not something to show an error for.
        initialValue = DataState.Loading(null),
    )

    // Navigation
    /** Written into [savedStateHandle] on every change, see [persistBackStack], and read back from it here. */
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
     */
    var navigationGeneration by mutableIntStateOf(0)
        private set
    private var isNavigationTransitionRunning = false

    /**
     * Where each of the three top level screens is scrolled to - the settings screen once per tab, since each of its
     * tabs scrolls on its own - kept here because a tab that is left is taken off the back stack and loses everything
     * it remembered with it, see [ScrollPosition].
     */
    internal val songsScrollPosition = ScrollPosition()
    internal val setlistsScrollPosition = ScrollPosition()
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
     * The search of each of the two list screens, held here for the same reason their scroll positions are, see
     * [SearchState]. They are separate because the two lists are searched for different things.
     */
    internal val songsSearch = restoreSearch(SONGS_SEARCH_KEY)
    internal val setlistsSearch = restoreSearch(SETLISTS_SEARCH_KEY)

    /** Transient mode shared with desktop Escape and browser history; never restored after leaving the screen. */
    internal var reorderingSetlistFileName by mutableStateOf<String?>(null)

    internal val isSetlistReordering: Boolean
        get() = backStack.lastOrNull() == CampfireDestination.Setlists && reorderingSetlistFileName != null

    /**
     * Open for good, since its field is part of the import screen's list rather than something the screen opens, and
     * emptied whenever that screen is left. Not restored, since the screen it belongs to never is, see [backStack].
     */
    internal val importReportSearch = SearchState(isInitiallyOpen = true)

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
        if (visibleDialog.value != null || isAnyOverflowMenuOpen || isSetlistReordering) return false
        val search = currentSearch
            ?: importReportSearch.takeIf { backStack.lastOrNull() == CampfireDestination.ImportReport }
            ?: return false
        search.openOrFocus()
        return true
    }

    /**
     * Whether the song details screen is on top with nothing over it, which is where Ctrl / Cmd + plus, minus and zero
     * change the text size and where the web page keeps the browser from zooming itself instead. It is asked from the
     * desktop window and the web page rather than from a key handler on the screen for the reason [openCurrentSearch]
     * is: the browser acts on a key pressed anywhere but the canvas before Compose hears of it, and a zoomed page is
     * not something Compose can undo. A dialog, a sheet or an overflow menu keeps the shortcuts from the screen under it.
     */
    internal val isSongTextZoomable
        get() = backStack.lastOrNull() is CampfireDestination.SongDetails && visibleDialog.value == null && !isAnyOverflowMenuOpen

    /**
     * Answers the zoom shortcuts the way the browser answers them for a page: [steps] of [FONT_SCALE_STEP] in or out,
     * or back to [DEFAULT_FONT_SCALE] for null. Answers whether it did, so that everywhere but the song details screen
     * the key is left to whoever else wants it.
     */
    internal fun zoomSongText(steps: Int?): Boolean {
        if (!isSongTextZoomable) return false
        if (steps == null) {
            setFontScale(DEFAULT_FONT_SCALE)
            settleFontScale()
        } else {
            adjustFontScale(steps)
        }
        return true
    }

    /** Where the pinches [magnifyByTouchpad] is given add up, which each arrive as too small a step to be kept on their own. */
    private val touchpadFontScale = FontScaleAccumulator()

    private val _printPreviewMagnifications = MutableSharedFlow<Float>(extraBufferCapacity = 64)

    /** The touchpad pinches [magnifyByTouchpad] hands the export screen's preview, which zooms its page by each ratio. */
    internal val printPreviewMagnifications = _printPreviewMagnifications.asSharedFlow()

    /**
     * Answers a pinch on a touchpad the way a touchscreen pinch is answered where it lands: on the song details screen
     * with the text size, [factor] being how much farther apart the fingers are than at the last report, damped by the
     * same [PINCH_SENSITIVITY], and on the export screen with the zoom of the page on preview, by [factor] itself as a
     * touchscreen pinch zooms it. Only a platform that tells a touchpad pinch apart from a scroll calls it - the macOS
     * desktop app, which reports the gesture itself, and Chrome, Edge and Firefox, which report a Ctrl + scroll the user
     * is not holding Ctrl for; Safari reports a gesture of its own that nothing listens for, so there a pinch zooms the
     * page - and it is asked from the window rather than from the screen, since none of these ever reach Compose as a
     * pinch. Neither screen answers one with anything drawn over it, and it answers whether one did, so that anywhere
     * else the gesture is left to whoever else wants it.
     */
    fun magnifyByTouchpad(factor: Float): Boolean {
        val isUsable = factor > 0f && factor.isFinite()
        return when {
            isSongTextZoomable -> {
                if (isUsable) setFontScale(touchpadFontScale.next(fontScale) { it * factor.pow(PINCH_SENSITIVITY) })
                true
            }
            visibleDialog.value is DialogType.Export && !isAnyOverflowMenuOpen -> {
                if (isUsable) _printPreviewMagnifications.tryEmit(factor)
                true
            }
            else -> false
        }
    }

    // Data
    /**
     * True while the library is being read for the first time, its partial batches included. A rescan of a library
     * that has been read once is not a loading state: it publishes no partial data, so what is on screen meanwhile is
     * the previous, complete library, and flipping this would only recompose every screen twice for nothing. A first
     * read that failed has not been read, so the retry after it still shows loading.
     */
    val isLoading = screenData
        .runningFold(LoadingLatch(isLoading = true, hasBeenRead = false)) { latch, state ->
            val hasBeenRead = latch.hasBeenRead || state is DataState.Idle
            LoadingLatch(isLoading = state is DataState.Loading && !hasBeenRead, hasBeenRead = hasBeenRead)
        }
        .map { it.isLoading }
        .asState(true)

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
            if (value) isAppOnScreen.value = true
        }

    /** [hasShownApp] as something to wait for, which the welcome sheet does, see [showWelcomeOnFirstRun]. */
    private val isAppOnScreen = MutableStateFlow(false)

    /**
     * Read straight from its own repository rather than out of [screenData], which only has anything once every
     * source has been read: the theme and the language come from here, and waiting for a scan of the whole song
     * library would leave the app in the system's theme and language for as long as that takes. Both states below
     * are derived from this one, so that they can never disagree about whether the read has happened.
     */
    private val userPreferencesState = getUserPreferences().asState(DataState.Loading(null))

    val userPreferences = userPreferencesState.map { it.data }.asState(null)

    /**
     * False only for as long as the preferences have not been read yet, which is what the app waits for before it
     * draws anything: they decide the palette it is drawn in and the language it is written in, and a frame drawn
     * before they arrive is a frame of the system's guess at both.
     *
     * Read rather than read *successfully*: a read that failed has no answer left to wait for, and the app has to
     * open in the defaults rather than not at all.
     *
     * It only ever turns on, because what it gates is the whole of `CampfireContent`: every remembered thing under
     * it — the scroll position of each list, the state Navigation 3 saved for each entry, the text being edited —
     * is gone the moment this goes false, and the app comes back with the user somewhere they never navigated to.
     * Nothing that happens after the first read is a reason to put the launch screen back up.
     */
    val arePreferencesLoaded = userPreferencesState
        .runningFold(false) { hasBeenRead, state -> hasBeenRead || state !is DataState.Loading }
        .asState(false)

    /**
     * The one preference enough screens ask about to be worth a state of its own: every list, menu, sheet and app
     * bar in the app has something it takes away.
     */
    val isPerformanceModeEnabled = userPreferences.map { it?.isPerformanceModeEnabled == true }.asState(false)
    /**
     * Read straight from its own repository, like the preferences and for the same reason: sync runs on its own
     * schedule, and a settings screen must not wait for a scan of the library to say whether an account is on.
     */
    val syncState = getSyncState().asState(SyncState.Disconnected)

    /** True while a sync run is going. Acted on rather than drawn: a restart the app offers by itself waits for it. */
    val isSyncing = syncState.map { it is SyncState.Connected && it.isSyncing }.asState(false)

    /**
     * Whether the platform has promised to keep the library, null until it has answered. Asked for here, as early as
     * there is anything to ask from, and never insisted on: on the web this is what stops the browser from evicting
     * the library when the device runs short of space, and everywhere else the answer is a foregone conclusion.
     *
     * A state of the view model rather than something the settings screen asks for as it opens, for the reason every
     * other state here is eager: an answer that arrives a frame after the screen does is a row appearing in the
     * middle of the transition the screen is entering with.
     */
    internal val libraryPersistence = flow<LibraryPersistence?> { emit(requestLibraryPersistence()) }.asState(null)

    /** Fixed for the life of the build, so it is a value rather than a flow. Empty means sync is not configured. */
    val syncProviders: List<SyncProviderId> = getSyncProviders()

    val setlists = screenData.map { it.data?.setlists.orEmpty() }.asState(emptyList())

    /**
     * The file names of every song that is in at least one setlist, which is what decides whether the "add to
     * setlist" action is drawn as a filled star or an outlined one. Worked out once per change to the setlists
     * rather than per song shown, since every row of the song list asks the same question.
     */
    val songFileNamesInSetlists = setlists
        .map { setlists -> setlists.flatMapTo(mutableSetOf()) { setlist -> setlist.entries.map { it.songFileName } } }
        .asState(emptySet())

    /**
     * The text of the songs the back stack can reach, by file name, read one file at a time as they are opened. Kept in
     * step with the files by [GetSongContentInvalidationsUseCase], see the collector in `init`, and left with only what
     * the screens on the back stack name once a navigation transition has ended, see [pruneSongTexts].
     */
    private val _songTexts = MutableStateFlow(emptyMap<String, String>())
    val songTexts: StateFlow<Map<String, String>> = _songTexts.asStateFlow()

    /**
     * The songs whose files are being renamed right now, under the names they had when it started.
     *
     * A rename moves the file - and with it the song in the library - before the screens that were opened on the old
     * name have been rewritten, since every setlist naming the song and its saved transposition are written in
     * between (see [updateSongFileName]). For those few writes the details screen's destination names a song the
     * library no longer holds, and a screen that resolved that as "gone" closed itself, or dropped a page and left a
     * setlist on the next song. Resolved through rather than waited out: the song is here from before the file moves
     * until after the back stack names it by its new name, so there is no window to miss and no delay to tune.
     */
    private val _songsBeingRenamed = MutableStateFlow(emptyMap<String, Song>())
    val songsBeingRenamed: StateFlow<Map<String, Song>> = _songsBeingRenamed.asStateFlow()

    /**
     * Held by every write to a song file, from the read the write is built on until [songTexts] has the result. The
     * header's edits are tapped in quick succession on the same file, and two of them working from the same text would
     * each write back the tag the other took off; two saves from the editor would land in whatever order they finished.
     * One lock for every file rather than one per file: the writes are rare and short.
     */
    private val songWriteMutex = Mutex()

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
    val hasUnsavedEditorChanges = combine(_editorDraft, _songTexts, userPreferences) { draft, songTexts, _ ->
        draft != null && draft.text != songTexts[draft.fileName]?.let(::editorTextOf)
    }.asState(false)

    /**
     * Where a song's transposition is kept depends on how it was opened, so both places are folded into one lookup:
     * a song opened from a setlist reads the setlist's entry, one opened from the library reads the preferences.
     */
    val transpositions = combine(userPreferences, setlists) { userPreferences, setlists ->
        Transpositions(
            library = userPreferences?.transpositions.orEmpty(),
            bySetlist = setlists.associate { setlist ->
                setlist.fileName to setlist.entries.filter { it.transposition != 0 }.associate { it.songFileName to it.transposition }
            },
        )
    }.asState(Transpositions())

    /**
     * The whole library, whatever the filters hide, which is what everything that looks a song up by its file name
     * reads: a setlist lists what somebody wrote down rather than what the song list is currently narrowed to, and
     * the details screen it opens has to find every one of those songs.
     */
    val allSongs = screenData.map { it.data?.unfilteredSongs.orEmpty() }.asState(emptyList())

    /**
     * The labels every song in the library carries, which the song rows leave off: a tag that is on every song tells
     * one song from no other, and a library that sings in one language has nothing to mark a song with. Counted over
     * the whole library rather than over what the filters leave, since a tag filter narrows the list to songs that
     * all carry that tag, and the rows would then lose the very label the reader narrowed them by. Tags are folded
     * to lowercase the way the filters count them, so two spellings of one word are one tag here too.
     */
    val labelsOnEverySong = allSongs.map { songs ->
        // Folded one song at a time, stopping at the first song that leaves both empty, which in most libraries is the
        // second one.
        var tags: Set<String>? = null
        var languages: Set<String>? = null
        for (song in songs) {
            if (tags?.isEmpty() != true) song.tags.mapTo(HashSet()) { it.lowercase() }.let { tags = tags?.intersect(it) ?: it }
            if (languages?.isEmpty() != true) song.languages.toSet().let { languages = languages?.intersect(it) ?: it }
            if (tags?.isEmpty() == true && languages?.isEmpty() == true) break
        }
        LabelsOnEverySong(tags = tags.orEmpty(), languages = languages.orEmpty())
    }.flowOn(Dispatchers.Default).asState(LabelsOnEverySong())

    /**
     * Every tag the library uses, most used first, as both the filter controls and the suggestions of the tag
     * dialog offer them.
     */
    val tags = screenData.map { it.data?.tags.orEmpty() }.asState(emptyList())

    /**
     * Every language the library sings in, most used first and the songs that declare none last, as the filter
     * controls offer them. Empty, or a single entry, is a library with nothing to filter by.
     */
    val languages = screenData.map { it.data?.languages.orEmpty() }.asState(emptyList())

    /**
     * Whether the filter controls have anything to offer: a tag, or a choice between two languages. Without either,
     * the songs screen leaves out both the controls and the action that opens them rather than showing an empty sheet.
     */
    val hasSongFilters = combine(tags, languages) { tags, languages -> tags.isNotEmpty() || languages.size > 1 }.asState(false)

    /**
     * Whether the song list is narrowed by anything the filter controls show as selected, which is what the badge on
     * their app bar action says while they are out of sight. It asks the chips rather than [songFilter]: a selected
     * tag the library no longer has is kept but narrows nothing, and the language group is only offered at all once
     * there are two languages to choose between, so a badge counting either would point at a filter that cannot be
     * found in the controls it opens.
     */
    val isSongFilterActive = combine(songFilter, tags, languages) { filter, tags, languages ->
        val selectedTags = filter.selectedTags.mapTo(mutableSetOf()) { it.lowercase() }
        tags.any { it.name.lowercase() in selectedTags } || (languages.size > 1 && languages.any { it.code in filter.selectedLanguages })
    }.asState(false)

    /**
     * The library as the song list shows it and as the search reads it, taken from one [screenData] value: the sections
     * as the domain layer cut them, the filtered songs with their title, artist and tags normalized for searching, and
     * the whole library by file name, which is what the setlists and the song details screen read - a setlist names its
     * songs whatever the song filters hide. Built from one value so that a library change can never pair a new filtered
     * list with an old lookup, and normalized once per library rather than once per keystroke, a song whose searchable
     * text did not change keeping what it was folded to.
     *
     * Distinct, because [screenData] also emits for every write to a setlist with the songs exactly as they were, and
     * each of those would otherwise have the whole library indexed again for nothing. Built on [Dispatchers.Default],
     * as the domain layer builds [screenData], since a whole library is too much to fold between two frames.
     */
    private val songSearchIndex = SongSearchIndex { normalizeSearchText(it) }
    private val indexedSongs = screenData.map { state ->
        val data = state.data
        IndexedSongInput(
            all = data?.unfilteredSongs.orEmpty(),
            filtered = data?.songs.orEmpty(),
            sections = data?.songSections.orEmpty(),
            filterKey = data?.let {
                "${it.sortingMode.name}|${it.songFilter.selectedTags.sorted()}|${it.tagMatchMode.name}|" +
                    "${it.songFilter.selectedLanguages.sorted()}|${it.languageMatchMode.name}"
            }.orEmpty(),
        )
    }.distinctUntilChanged().map { input ->
        IndexedSongs(input.sections, songSearchIndex.update(input.all, input.filtered), input.filterKey)
    }.flowOn(Dispatchers.Default).asState(IndexedSongs(emptyList(), SongSearchSnapshot.Empty, ""))

    /** Shared file-name lookup for screens that resolve songs from a destination or a setlist. */
    val songsByFileName = indexedSongs.map { it.search.songsByFileName }.asState(emptyMap())

    /**
     * Every song of the library in the order the songs screen is sorted by, with its search and filter keys, as the song
     * picker lists and searches it. Built here rather than as the sheet opens, where the first frame of the sheet would
     * wait for a whole library to be sorted and folded, and sorted by keys folded once per song rather than on both
     * sides of every comparison.
     */
    internal val pickerSongs = combine(
        indexedSongs,
        userPreferences.map { it?.sortingMode ?: UserPreferences.SortingMode.BY_ARTIST }.distinctUntilChanged(),
    ) { indexed, sortingMode ->
        val list = indexed.search.byFileName.values
            .map { song ->
                val title = normalizeText(song.song.title)
                val artist = normalizeText(song.song.artist)
                if (sortingMode == UserPreferences.SortingMode.BY_ARTIST) Triple(song, artist, title) else Triple(song, title, artist)
            }
            .sortedWith(compareBy({ it.second }, { it.third }, { it.first.song.fileName }))
            .map { it.first.toPickableSong() }
        PickerSongs(list = list, byFileName = list.associateBy { it.song.fileName })
    }
        .flowOn(Dispatchers.Default)
        .asState(PickerSongs.Empty)

    /** The song picker's filter chips, counted over the whole library for the same reason [pickerSongs] is sorted here. */
    internal val songPickerFilters = allSongs
        .map(::pickerFilterOptions)
        .flowOn(Dispatchers.Default)
        .asState(PickerFilterOptions.Empty)

    /**
     * Null until the library has actually been read, so that the settings screen never flashes a count of zero. The size
     * is added up from what the scan read off every file, so it costs no listing of its own and arrives in the same value
     * as the counts.
     */
    val librarySummary = screenData
        .map { state ->
            // A library with an unreadable part standing in empty is not one to count.
            state.data?.takeIf { it.isWholeLibrary }?.let { data ->
                LibrarySummary(
                    songCount = data.unfilteredSongs.size,
                    setlistCount = data.setlists.size,
                    size = data.unfilteredSongs.sumOf { it.size } + data.setlists.sumOf { it.size },
                )
            }
        }
        .asState(null)

    /**
     * The bytes the copies of the covers take up, null until they have been listed. Eager like every other state, so
     * that the settings screen opens on the number rather than fading it in; the repository lists the folder once per
     * change at most, and only one listing at a time.
     */
    val coverArtCacheSize = getCoverArtCacheSize().asState(null)

    // The sections arrive cut, from the same pass that sorted them. Cutting them here would take the sorting mode
    // from the preferences, which change before the list sorted by them arrives.
    val songGroups = combine(indexedSongs, songsSearch.activeQuery) { indexed, query ->
        val normalizedQuery = normalizeSearchText(query)
        SongGroups(
            filterKey = "$normalizedQuery|${indexed.filterKey}",
            groups = songGroupsFor(sections = indexed.sections, filtered = indexed.search.filtered, normalizedQuery = normalizedQuery),
        )
    }.flowOn(Dispatchers.Default).asState(SongGroups(filterKey = "", groups = emptyList()))

    /** True while an import is running, which the screens that can start one show as a progress bar. */
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()
    /**
     * The phase of an import the user asked for and how far into it it is, shown by a dialog while nothing else is
     * reporting on it, and by the import screen while that is open.
     */
    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    /**
     * What [CampfireDestination.ImportReport] shows, which is everything about an import that went anywhere but the
     * one way that needs no more than a snackbar: a question about names that are taken, the import it decides on
     * being written, and what an import that left something out came to. Null while no import has anything to report,
     * and an import that has one keeps the next batch waiting until the screen is left, see [awaitImportSettled].
     */
    private val _importReport = MutableStateFlow<ImportReport?>(null)
    val importReport: StateFlow<ImportReport?> = _importReport.asStateFlow()

    /** Whether the last change to [backStack] left [CampfireDestination.ImportReport] on it, see [onImportReportLeft]. */
    private var isImportReportOnBackStack = false

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
    val demoLibraryOffer = combine(screenData, isAddingDemoLibrary, _isImporting) { state, isAddingDemoLibrary, isImporting ->
        when (state.data?.takeIf { it.isWholeLibrary }?.let { DemoLibrary.isPresentIn(songs = it.unfilteredSongs, setlists = it.setlists) }) {
            false -> if (isAddingDemoLibrary || isImporting) DemoLibraryOffer.UNAVAILABLE else DemoLibraryOffer.AVAILABLE
            true, null -> null
        }
    }.asState(null)

    /**
     * What the song list has to show instead of songs, null while it has songs. A library that is empty because
     * the search matched nothing is told apart from one that is empty because the load has not finished (or has
     * failed) here, so that a list without data never sits on a loading indicator that nothing will ever replace.
     */
    val songsPlaceholder = combine(screenData, songGroups, _isImporting) { screenData, songGroups, isImporting ->
        val data = screenData.data
        when {
            songGroups.groups.isNotEmpty() -> null
            // The library itself, not the filtered list: a library that only holds songs the filters hide is not an
            // empty one, and offering to create a first song there would be answering a question nobody asked.
            data == null || data.unfilteredSongs.isEmpty() -> screenData.emptyPlaceholder(Placeholder.NO_SONGS, isImporting)
            data.songs.isEmpty() -> Placeholder.ALL_SONGS_HIDDEN
            else -> Placeholder.NO_MATCHING_SONGS
        }
    }.asState(Placeholder.LOADING)

    private val shouldShowArchivedSetlists = userPreferences.map { it?.shouldShowArchivedSetlists == true }.distinctUntilChanged()

    /**
     * The setlists the screen would list if nothing had been typed into its search, with every song they name: the
     * song filters are about the song list and a setlist is answerable to nobody but whoever wrote it down, so a
     * setlist shows what it holds whether or not the library screen next door is narrowed to something else. The
     * archived ones are the one thing left out, and only until the user asks for them, see
     * [UserPreferences.shouldShowArchivedSetlists].
     *
     * Kept apart from the search below so that the placeholder can tell a setlist list emptied by the archive filter
     * from one emptied by the search, the way the song list tells its own two empty states apart - and a state
     * rather than a plain flow because both the placeholder and the search below read it.
     */
    private val visibleSetlists = combine(setlists, indexedSongs, shouldShowArchivedSetlists) { setlists, indexed, shouldShowArchivedSetlists ->
        val songsByFileName = indexed.search.byFileName
        setlists.filter { shouldShowArchivedSetlists || !it.isArchived }.map { setlist ->
            SetlistWithSongs(
                setlist = setlist,
                entries = setlist.entries.mapIndexed { index, entry ->
                    when (val song = songsByFileName[entry.songFileName]) {
                        null -> SetlistWithSongs.Entry.Missing(index = index, songFileName = entry.songFileName)
                        else -> SetlistWithSongs.Entry.Present(index = index, song = song.song)
                    }
                },
            )
        }
    }.flowOn(Dispatchers.Default).asState(emptyList())

    /**
     * The setlists as the screen lists them, narrowed by its search: a setlist answers it by what it says about
     * itself - its title and its description - or by holding a song that does.
     *
     * A setlist that answers is shown **whole**. The search finds setlists rather than songs inside them: a setlist
     * is the list somebody wrote down, and three of its twelve songs is not that list.
     */
    val setlistsWithSongs = combine(visibleSetlists, indexedSongs, setlistsSearch.activeQuery) { setlists, indexed, query ->
        // Branched on the folded query, as the song list is: one of punctuation or symbols alone is no search.
        val normalizedQuery = normalizeSearchText(query)
        if (normalizedQuery.isEmpty()) {
            setlists
        } else {
            setlists.filter { it.setlist.matchesSearch(normalizedQuery = normalizedQuery, songs = indexed.search.byFileName) }
        }
    }.flowOn(Dispatchers.Default).asState(emptyList())

    /**
     * What the setlists screen shows instead of setlists, null while it has some. Same reasoning as
     * [songsPlaceholder]: without it a load in progress is indistinguishable from a user who has no setlists, and
     * the screen claims there are none for as long as reading the library takes. A library whose every setlist is
     * archived is told apart from one with no setlists at all, and from one whose setlists the search did not
     * match, for the same reason the song list tells its empty states apart: each of the three is answered by
     * something different, and only one of them by making a setlist.
     */
    val setlistsPlaceholder = combine(screenData, setlistsWithSongs, visibleSetlists, _isImporting) { screenData, setlistsWithSongs, visibleSetlists, isImporting ->
        when {
            setlistsWithSongs.isNotEmpty() -> null
            screenData.data?.setlists.isNullOrEmpty() -> screenData.emptyPlaceholder(Placeholder.NO_SETLISTS, isImporting)
            visibleSetlists.isEmpty() -> Placeholder.ALL_SETLISTS_HIDDEN
            else -> Placeholder.NO_MATCHING_SETLISTS
        }
    }.asState(Placeholder.LOADING)

    /** The file names of the songs whose text could not be read. */
    private val _failedSongFileNames = MutableStateFlow(emptySet<String>())
    val failedSongFileNames: StateFlow<Set<String>> = _failedSongFileNames.asStateFlow()

    /**
     * The text size multiplier of the song details screen. A pinch gesture changes it on every frame, so the latest
     * value is kept here and only written to the user preferences once the changes have settled.
     */
    private val liveFontScale = mutableFloatStateOf(DEFAULT_FONT_SCALE)

    /**
     * Snapshot state rather than a flow, so that it is read where the text is laid out: a pinch changes it on every
     * frame, and a flow collected at the root of the screen would recompose the whole screen every time, a frame late.
     */
    val fontScale: Float get() = liveFontScale.floatValue

    /**
     * The value set on this device and not yet written to the preferences. For as long as there is one it wins over
     * the stored value; once it is saved, whatever the preferences hold wins again.
     */
    private val unsavedFontScale = MutableStateFlow<Float?>(null)

    /**
     * The export screen's options as it last set them and not saved yet. A step of its size or its margins is a new
     * value, and saving each one would publish the preferences to every screen once a step, so they are saved the way
     * [unsavedFontScale] is: once they have held still, or at once when the screen goes (see [setVisibleDialog]). A
     * screen composed again within that moment, as a rotation does, starts from this rather than a step back.
     */
    private val _pendingPrintSettings = MutableStateFlow<PrintSettings?>(null)
    val pendingPrintSettings = _pendingPrintSettings.asStateFlow()

    private val settledFontScaleState = mutableFloatStateOf(DEFAULT_FONT_SCALE)

    /**
     * [fontScale] once it has held still for a moment: what the songs that are not on screen are laid out at, so that a
     * pinch lays out the one song being read rather than the pages beside it as well. A step of the stepper or of a
     * shortcut is not a continuous change, so it reaches them at once.
     */
    val settledFontScale: Float get() = settledFontScaleState.floatValue

    /**
     * The import that has been worked out but not carried out, waiting for the user to answer the question
     * [ImportReport.Review] asks. Not part of the screen, which holds only what it draws: this is the work, and it has
     * to outlive whichever screen the import was started from. It never outlives the question: see [onImportReportLeft].
     */
    private var pendingImport: PendingImport? = null

    /**
     * The reading and comparing half of the import being run, which [cancelImportPreparation] can end. Touched only on the
     * main thread, like [isPreparationCancelled]: [import] runs in [viewModelScope] and the dialog's click calls in there.
     */
    private var preparation: Deferred<ImportPlan>? = null

    /**
     * Whether the user asked for [preparation] to stop, which a cancellation of the view model itself is told apart from.
     */
    private var isPreparationCancelled = false

    /**
     * Every batch of files waiting to be imported, taken one at a time by the consumer launched in `init`. Files are
     * handed over whenever the system or the user feels like it - a second archive dropped while the first is still
     * being written, a file opened with the app while the conflicts question is up - and an import can only run while
     * no other one is, so what arrives in the meantime waits here rather than being dropped.
     */
    private val importQueue = Channel<ImportRequest>(Channel.UNLIMITED)

    /**
     * How many batches have been put into [importQueue] and not yet settled, so that What's new can tell a file opened
     * with the app a moment ago, which no import has taken yet, from a launch with nothing to import.
     */
    private val _queuedImportCount = MutableStateFlow(0)

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

    /**
     * Messages waiting for the snackbar, oldest first, each with a number of its own so that two identical results in
     * a row are still two messages. Held here rather than by the screen that shows them: Android recreates that screen
     * whenever it recreates its activity, and a message it had already taken would go with it unshown. A message
     * leaves the queue once it has been shown, see [onMessageShown].
     */
    private val _messageQueue = MutableStateFlow(emptyList<IndexedValue<Message>>())
    val messageQueue: StateFlow<List<IndexedValue<Message>>> = _messageQueue.asStateFlow()
    private var messageCount = 0

    private fun sendMessage(message: Message) {
        val indexedMessage = IndexedValue(messageCount++, message)
        _messageQueue.update { it + indexedMessage }
    }

    /** Called by the snackbar host once [message] has been on screen for its whole duration, or dismissed. */
    fun onMessageShown(message: IndexedValue<Message>) = _messageQueue.update { queue -> queue.filterNot { it.index == message.index } }

    // Dialogs

    /**
     * The state of the cover search sheet ([DialogType.CoverArtSearch]). Held here rather than by the sheet so that a
     * search survives the Android activity being recreated under it, and cleared with its search cancelled whenever
     * the sheet stops being the dialog on screen, see [setVisibleDialog].
     */
    private val _coverArtSearch = MutableStateFlow<CoverArtSearchState>(CoverArtSearchState.Idle)
    val coverArtSearch = _coverArtSearch.asStateFlow()
    private var coverArtSearchJob: Job? = null

    private val _visibleDialog = MutableStateFlow<DialogType?>(null)
    val visibleDialog: StateFlow<DialogType?> = _visibleDialog.asStateFlow()

    // Kept mounted beneath an editor so its sheet and scroll position survive opening and closing that editor.
    private val _underlyingSongInfo = MutableStateFlow<DialogType.SongInfo?>(null)
    val underlyingSongInfo = _underlyingSongInfo.asStateFlow()

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
        viewModelScope.launch { loadScreenData(false) }
        viewModelScope.launch { plantDemoLibraryOnFirstRun() }
        viewModelScope.launch { showWelcomeOnFirstRun() }
        viewModelScope.launch { showWhatsNewOnVersionChange() }
        // Picks a connected account back up, finishes a consent the app was closed in the middle of, and runs a
        // first sync. Its own coroutine, so that a slow network never holds up the library appearing on screen.
        viewModelScope.launch {
            // On the web the consent page replaces the app, so this start up is the second half of a tap on
            // Settings: whether it ended up connected or not, that is the screen the answer is on. The page load forgot
            // which tab the tap was made on, so the one holding the sync section is opened rather than the first.
            try {
                // A reinstall is the one case where credentials outlive the library: iOS leaves the Keychain item
                // behind while everything else goes, so a fresh installation would find itself connected to an
                // account nobody connected here, and its first run would upload the demo library into the user's
                // folder. In this coroutine rather than beside it, because it is the one thing that must happen
                // before restore() reads those credentials.
                if (isFirstLaunch.await()) forgetSyncConnection()
                if (restoreSync()) openSyncSettingsAfterConsent()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // Sync is something the app does on the side: nothing about it may keep the library from appearing.
                println("Could not restore the sync connection: ${exception.message}")
            }
        }
        // A sync run, a rescan or a save replaces files underneath the texts held here, and the details header builds
        // its writes on them: without this, toggling a tag would write the text that was open over the version sync
        // had just brought in. The texts are read again rather than only dropped, so a song that is on screen changes
        // in place instead of flashing a loading indicator. An editor with nothing typed in it follows its file (see
        // the editor's FollowFileWhileUntouched); one with text of its own now has unsaved changes, which is what asks
        // the user before their draft replaces the new version — an editor whose file is gone included, where the
        // draft is all there is.
        // Every text held here is read again, not only the one that was named: the invalidations are a state, so that
        // none of them can be lost while this is busy reading, and that state names no file. The texts are few (the
        // screens on the back stack), and those that did not change come out of the repository's cache.
        viewModelScope.launch {
            getSongContentInvalidations().drop(1).collect { rereadSongTexts() }
        }
        viewModelScope.launch {
            demoLibraryDecision.await()
            for (request in importQueue) {
                try {
                    import(request)
                    // The next batch waits for this one's conflicts question too: it would have nowhere to be asked.
                    awaitImportSettled()
                } finally {
                    // Here rather than in import, so that the count is honest whatever that did: returned after the
                    // preparation was cancelled, failed, or left a question that was answered or abandoned.
                    _queuedImportCount.update { it - 1 }
                    request.settled.complete(Unit)
                }
            }
        }
        // A sheet or a dialog about one song goes when the song does - deleted or renamed by a sync run, or taken out
        // of the folder behind the app's back - whichever screen opened it: the details screen underneath closes
        // itself, but the dialogs are not its own, and a setlist picker left behind would write the name of a file that
        // is not there into every setlist ticked in it. Only against a library that has been read, and never for a song
        // this app is renaming, which is missing from the library for a few writes on purpose (songsBeingRenamed).
        viewModelScope.launch {
            combine(_visibleDialog, allSongs, isLoading, _songsBeingRenamed) { dialog, songs, isLoading, songsBeingRenamed ->
                val fileName = dialog?.songFileName
                dialog?.takeIf { fileName != null && !isLoading && fileName !in songsBeingRenamed && songs.none { it.fileName == fileName } }
            }.filterNotNull().collect { dialog ->
                // The song disappearing closes both the editor and the sheet underneath it.
                if (_visibleDialog.value == dialog) setVisibleDialog(null)
            }
        }
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
        viewModelScope.launch {
            _songFilter.collect { persist(SONG_FILTER_KEY, SavedSongFilter(selectedTags = it.selectedTags.toList(), selectedLanguages = it.selectedLanguages.toList())) }
        }
        viewModelScope.launch {
            _songPickerSelectedTags.collect { persist(SONG_PICKER_TAGS_KEY, it.toList()) }
        }
        viewModelScope.launch {
            _songPickerSelectedLanguages.collect { persist(SONG_PICKER_LANGUAGES_KEY, it.toList()) }
        }
        listOf(SONGS_SEARCH_KEY to songsSearch, SETLISTS_SEARCH_KEY to setlistsSearch).forEach { (key, search) ->
            viewModelScope.launch {
                combine(search.isOpen, snapshotFlow { search.textFieldState.text.toString() }) { isOpen, query -> SavedSearch(isOpen = isOpen, query = query) }
                    .collect { persist(key, it) }
            }
        }
        viewModelScope.launch {
            unsavedFontScale.filterNotNull().debounce(FONT_SCALE_SAVE_DELAY_MILLIS).collect { fontScale ->
                updateUserPreferences { it.copy(fontScale = fontScale) }
                // Only if nothing newer arrived while this one was being saved, or that one would never be.
                unsavedFontScale.compareAndSet(fontScale, null)
            }
        }
        viewModelScope.launch {
            _pendingPrintSettings.filterNotNull().debounce(FONT_SCALE_SAVE_DELAY_MILLIS).collect { savePrintSettings(it) }
        }
        viewModelScope.launch {
            // The first read at launch, a read again, a restore, a sync run: whatever wrote the preference wins
            // whenever nothing set here is still waiting to be saved. The echo of our own save equals the live value.
            userPreferences.filterNotNull().map { it.fontScale }.distinctUntilChanged().collect { stored ->
                if (unsavedFontScale.value == null) {
                    liveFontScale.floatValue = stored
                    settleFontScale()
                }
            }
        }
        viewModelScope.launch {
            snapshotFlow { fontScale }.debounce(FONT_SCALE_SETTLE_MILLIS).collect { settledFontScaleState.floatValue = it }
        }
    }

    // Navigation

    /** Reported by the UI whenever the state of the navigation transition changes, see [navigationGeneration]. */
    fun setNavigationTransitionRunning(isRunning: Boolean) {
        val hasTransitionEnded = isNavigationTransitionRunning && !isRunning
        isNavigationTransitionRunning = isRunning
        if (hasTransitionEnded) pruneSongTexts()
    }

    /**
     * Lets go of the texts no screen on the back stack names, which would otherwise pile up for as long as the process
     * lives - one per page of every setlist paged through - and be read again by every rescan and sync run. Kept: every
     * file a song details screen names (the pages of a setlist next to the current one are read ahead), the editor's
     * file and the draft's, since [hasUnsavedEditorChanges] compares against them and a missing one reads as unsaved.
     *
     * Once the transition has ended rather than as the back stack changes, because a screen that has been popped is
     * still composed while it slides away, and its page losing its text would put a loading indicator in its place
     * halfway out.
     */
    private fun pruneSongTexts() {
        val reachable = buildSet {
            backStack.forEach { destination ->
                when (destination) {
                    is CampfireDestination.SongDetails -> addAll(destination.songFileNames)
                    is CampfireDestination.SongEditor -> add(destination.fileName)
                    else -> Unit
                }
            }
            _editorDraft.value?.fileName?.let(::add)
        }
        _songTexts.update { texts -> if (texts.keys.all { it in reachable }) texts else texts.filterKeys { it in reachable } }
    }

    /**
     * Reads the held texts of [fileNames] - every held one for null - back from the files, and applies them in one
     * update, so that the screens reading [songTexts] recompose once rather than once per file. Only to texts that are
     * still held when the reads are done, so that one pruned meanwhile is not brought back.
     */
    private suspend fun rereadSongTexts() {
        val affected = _songTexts.value.keys
        if (affected.isEmpty()) return
        val results = affected.map { name -> name to getSongContent(name)?.text }
        _songTexts.update { texts ->
            results.fold(texts) { updated, (name, text) ->
                when {
                    name !in updated -> updated
                    text == null -> updated - name
                    else -> updated + (name to text)
                }
            }
        }
        // The editor keeps what it has, and with no text to compare it to that now counts as unsaved. It is said out
        // loud because saving is what puts the file back, which the user would otherwise have no reason to do.
        results.forEach { (name, text) ->
            if (text == null && _editorDraft.value?.fileName == name) {
                sendMessage(Message.EditedSongFileGone)
            }
        }
    }

    private fun updateBackStack(update: SnapshotStateList<CampfireDestination>.() -> Unit) {
        if (isNavigationTransitionRunning) navigationGeneration++
        backStack.update()
        if (backStack.lastOrNull() != CampfireDestination.Setlists) reorderingSetlistFileName = null
        if (backStack.none { it is CampfireDestination.SongEditor }) retainedEditorField = null
        songDetailsCurrentSongs.keys.retainAll(backStack.mapNotNullTo(mutableSetOf()) { (it as? CampfireDestination.SongDetails)?.id })
        val hadImportReport = isImportReportOnBackStack
        isImportReportOnBackStack = backStack.any { it == CampfireDestination.ImportReport }
        if (hadImportReport && !isImportReportOnBackStack) onImportReportLeft()
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
        savedStateHandle[BACK_STACK_KEY] = (stack.size downTo 1).asSequence()
            .map { Json.encodeToString<List<CampfireDestination>>(stack.subList(0, it)) }
            .firstOrNull { it.length <= MAX_SAVED_BACK_STACK_LENGTH }
            ?: Json.encodeToString<List<CampfireDestination>>(listOf(CampfireDestination.Songs))
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
     * text its field still holds, since this is stepping back to a place rather than asking anything new.
     */
    internal fun restoreNavigationState(state: NavigationState): Boolean {
        if (state.backStack.isEmpty() || hasUnsavedEditorText()) return false
        settingsTab = state.settingsTab
        listOf(songsSearch to state.isSongsSearchOpen, setlistsSearch to state.isSetlistsSearchOpen).forEach { (search, isOpen) ->
            if (isOpen != search.isOpen.value) if (isOpen) search.reopen() else search.close()
        }
        if (backStack.toList() != state.backStack) {
            updateBackStack {
                clear()
                addAll(state.backStack)
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
     * without asking, see [navigateBack].
     */
    fun selectTopLevelDestination(destination: CampfireDestination.TopLevel) {
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
    internal fun openReportedSong(songFileNames: List<String>, index: Int) = openSongDetails(
        CampfireDestination.SongDetails(songFileNames = songFileNames, setlistFileName = null, initialIndex = index),
    )

    /**
     * Every way out of a screen ends up here - the app bar's button, the system's back gesture and the desktop
     * window's Escape key - which is why this is where the editor's unsaved text is caught: nothing the user typed
     * is thrown away without being asked about it first, and why a settings tab other than General goes back to that
     * one before the screen is left ([isSettingsBackToGeneral]).
     */
    fun navigateBack() {
        when {
            isSetlistReordering -> reorderingSetlistFileName = null
            hasUnsavedEditorChanges.value && backStack.lastOrNull() is CampfireDestination.SongEditor -> {
                showDialog(DialogType.UnsavedChanges)
            }
            isSettingsBackToGeneral -> settingsTab = SettingsTab.GENERAL
            else -> popBackStack()
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
     * Lets the sync runs the library still owes the cloud folder happen before a desktop process ends, which is where
     * a quit leads once [requestExit] has let it through - the shell hides the window first, so the quit looks as
     * immediate as it is. The automatic run that is waiting for the library to settle is started now: dropped, the
     * change made just before quitting would reach the other devices only the next time this computer opens Campfire.
     * A run that is going is waited for, and so is one chained behind it. Past [EXIT_SYNC_GRACE] the run is stopped
     * instead and its winding down waited for, briefly, since a stopped run writes its index and clears the marker
     * that would otherwise have the next launch report it as interrupted and start no run of its own.
     */
    suspend fun settleSynchronizationBeforeExit() {
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
    private fun hasUnsavedEditorText() = _editorDraft.value?.let { it.text != _songTexts.value[it.fileName]?.let(::editorTextOf) } == true

    private fun popBackStack() {
        if (backStack.size > 1) {
            updateBackStack { removeAt(lastIndex) }
        }
    }

    // Songs

    /** When the last rescan started or, once it has finished, finished; see [refreshIfStale]. */
    private var lastRescanAt: TimeMark? = null

    fun refresh() = viewModelScope.launch {
        // Marked as it starts as well, so that the focus that follows a start, which comes right behind it on the
        // desktop, does not ask for a second rescan while the first is still running.
        lastRescanAt = TimeSource.Monotonic.markNow()
        loadScreenData(true)
        lastRescanAt = TimeSource.Monotonic.markNow()
    }

    /**
     * [refresh], unless the library has been read again within the last [MIN_RESCAN_INTERVAL]: for the desktop
     * window regaining the focus, which a user editing a song in another window next to it does often, and each time
     * of which re-reading the whole library would be a cost with nothing new to show for it.
     */
    fun refreshIfStale() {
        if (lastRescanAt?.let { it.elapsedNow() < MIN_RESCAN_INTERVAL } == true) return
        refresh()
    }

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
        _songsBeingRenamed.update { it + (song.fileName to song) }
        try {
            val rename = renameSongFile(song) ?: return@launchLibraryChange
            val fileName = rename.fileName
            _songTexts.update { texts -> texts[song.fileName]?.let { texts - song.fileName + (fileName to it) } ?: texts }
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
            persistBackStack()
            // Said after the screens have followed the file, which has moved whatever else could not be rewritten.
            if (!rename.haveReferencesFollowed) sendMessage(Message.SongFileRenamedPartly)
        } finally {
            // Cleared once the screens name the new file - and on the paths that never got that far: a rename that
            // found nothing to do, one that threw, and a view model going away mid-rename.
            _songsBeingRenamed.update { it - song.fileName }
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
        _songTexts.update { it - fileName }
        // Said once the screens have let go of the file, which is gone whatever else could not be rewritten.
        if (!haveReferencesBeenRemoved) sendMessage(Message.SongDeletedPartly)
    }

    /**
     * Makes [tags] the tags a song carries, from the tag dialog: every one of [offeredTags] the dialog left unticked is
     * taken off and every ticked one put on, compared without regard to case, as the file's tags always are. The file
     * is rewritten once for the whole set, as [setSongLanguages] writes one. What it carries is read from the text the
     * edit is built on rather than from the list entry the dialog was opened with, so a tag another device synced in
     * while the dialog was open, and which it therefore never offered, is left on.
     */
    fun setSongTags(fileName: String, isEditorDraft: Boolean, tags: List<String>, offeredTags: List<String>) {
        val keptKeys = tags.mapTo(mutableSetOf()) { it.lowercase() }
        val offeredKeys = offeredTags.mapTo(mutableSetOf()) { it.lowercase() }
        editSong(fileName = fileName, isEditorDraft = isEditorDraft) { text ->
            val removed = parseChordPro(text).metadata.tags.filter { it.lowercase() in offeredKeys && it.lowercase() !in keptKeys }
            val withoutRemoved = removed.fold(text) { current, tag -> setChordProTag(text = current, tag = tag, isSelected = false) }
            tags.fold(withoutRemoved) { current, tag -> setChordProTag(text = current, tag = tag, isSelected = true) }
        }
    }

    /**
     * Declares the languages of a song, from the header of the screen that is playing it. The whole set arrives at
     * once rather than one language at a time, because the picker asks for all of them before it is closed and a
     * file the user owns is better rewritten once than once per checkbox.
     */
    fun setSongLanguages(fileName: String, isEditorDraft: Boolean, codes: List<String>) = editSong(fileName = fileName, isEditorDraft = isEditorDraft) { text ->
        setChordProLanguages(text = text, codes = codes)
    }

    /** Opens the label pickers on the current draft when invoked from the editor. */
    fun showSongTagsDialog(song: Song, isEditorDraft: Boolean) {
        val currentSong = songForLabelEditing(song, isEditorDraft) ?: return
        showDialog(DialogType.SongTags(song = currentSong, isEditorDraft = isEditorDraft))
    }

    fun showSongLanguagesDialog(song: Song, isEditorDraft: Boolean) {
        val currentSong = songForLabelEditing(song, isEditorDraft) ?: return
        showDialog(DialogType.SongLanguages(song = currentSong, isEditorDraft = isEditorDraft))
    }

    private fun songForLabelEditing(song: Song, isEditorDraft: Boolean): Song? {
        if (!isEditorDraft) return song
        val metadata = parseChordPro(songTextOf(song.fileName, isEditorDraft = true) ?: return null).metadata
        return song.copy(tags = metadata.tags.map { it.normalizedToNfc() }.distinctBy { it.lowercase() }, languages = metadata.languages)
    }

    /**
     * Opens the metadata editor on the source text, since the album, the composer and the rest are not part of the
     * song list's lighter metadata, and the title there already has the subtitle in it.
     */
    fun showSongMetadataDialog(song: Song, isEditorDraft: Boolean) {
        val metadata = parseChordPro(songTextOf(song.fileName, isEditorDraft) ?: return).metadata
        showDialog(
            DialogType.SongMetadata(
                song = song,
                values = ChordProMetadataFields.Field.entries.associateWith { ChordProMetadataFields.valueOf(metadata, it).orEmpty() },
                isEditorDraft = isEditorDraft,
            )
        )
    }

    /**
     * Writes the fields of the metadata dialog that were changed there, and only those: a field another device changed
     * while the dialog was open, and which the user left as it was offered, keeps the other device's value.
     */
    fun setSongMetadata(
        fileName: String,
        isEditorDraft: Boolean,
        values: Map<ChordProMetadataFields.Field, String>,
        offeredValues: Map<ChordProMetadataFields.Field, String>,
    ) {
        val changed = values.filter { (field, value) -> value.trim() != offeredValues[field]?.trim() }
        if (changed.isNotEmpty()) editSong(fileName = fileName, isEditorDraft = isEditorDraft) { text -> setChordProMetadata(text = text, values = changed) }
    }

    /** Opens the link editor on the source text, since links are not part of the song list's lighter metadata. */
    fun showSongLinksDialog(song: Song, isEditorDraft: Boolean) {
        val text = songTextOf(song.fileName, isEditorDraft) ?: return
        showDialog(DialogType.SongLinks(song = song, links = parseChordPro(text).metadata.links, isEditorDraft = isEditorDraft))
    }

    /** What [text] says about the song beyond its lines, for the sheet of what the song is and the button opening it. */
    fun songMetadataOf(text: String): ChordProMetadata = parseChordPro(text).metadata

    /**
     * Writes the link dialog's changes together. Links added by sync while it was open and never offered there stay
     * in the file, as tags do: a snapshot of one dialog is not a request to erase another device's additions.
     */
    fun setSongLinks(fileName: String, isEditorDraft: Boolean, links: List<ChordProLink>, offeredLinks: List<ChordProLink>) {
        val offeredUrls = offeredLinks.mapTo(mutableSetOf()) { it.url }
        editSong(fileName = fileName, isEditorDraft = isEditorDraft) { text ->
            val addedElsewhere = parseChordPro(text).metadata.links.filterNot { it.url in offeredUrls }
            setChordProLinks(text = text, links = links + addedElsewhere)
        }
    }

    /** Opens the cover sheet with the cover currently declared in the editor's text. */
    fun showSongCoverArtDialog(song: Song, isEditorDraft: Boolean) {
        val currentSong = if (isEditorDraft) {
            val metadata = parseChordPro(songTextOf(song.fileName, isEditorDraft = true) ?: return).metadata
            song.copy(coverArtUrl = metadata.coverArt)
        } else song
        showDialog(DialogType.CoverArtSearch(song = currentSong, isEditorDraft = isEditorDraft))
    }

    /**
     * Makes [url] the song's cover, or takes the cover off for null, from the cover search sheet. Written into the
     * file like a tag is, so that the cover travels with the song wherever it goes.
     */
    fun setSongCoverArt(fileName: String, isEditorDraft: Boolean, url: String?) = editSong(fileName = fileName, isEditorDraft = isEditorDraft) { text ->
        setChordProCoverArt(text = text, url = url)
    }

    /**
     * What the cover search sheet is prefilled with for [song]: its artist, album and title as the file writes them,
     * read from the text the details screen holds, and from the library's entry where that is not at hand, which has
     * no album and a title with the subtitle after it.
     */
    fun coverArtQueryOf(song: Song, isEditorDraft: Boolean) = songTextOf(song.fileName, isEditorDraft)?.let { text ->
        val metadata = parseChordPro(text).metadata
        CoverArtQuery(
            artist = metadata.artist.orEmpty(),
            album = metadata.album.orEmpty(),
            title = metadata.title ?: song.title,
        )
    } ?: CoverArtQuery(artist = song.artist, album = "", title = song.title)

    /** Searches for [query], cancelling the search still running: only the question asked last is still being asked. */
    fun searchCoverArt(query: CoverArtQuery) {
        coverArtSearchJob?.cancel()
        if (!query.isSearchable) {
            _coverArtSearch.value = CoverArtSearchState.Idle
            return
        }
        coverArtSearchJob = viewModelScope.launch {
            searchCoverArt.invoke(query = query).collect { results ->
                _coverArtSearch.value = CoverArtSearchState.Active(query = query, results = results)
            }
        }
    }

    private fun clearCoverArtSearch() {
        coverArtSearchJob?.cancel()
        coverArtSearchJob = null
        _coverArtSearch.value = CoverArtSearchState.Idle
    }

    /**
     * The text a metadata dialog is built on: the editor's own while it is the editor's draft the dialog edits, since
     * that is what its edit is applied to, and the file's otherwise.
     */
    private fun songTextOf(fileName: String, isEditorDraft: Boolean) = if (isEditorDraft) {
        // Read the field itself: draft reporting runs asynchronously and can still be one edit behind a tap.
        retainedEditorField(fileName)?.text?.toString() ?: _editorDraft.value?.takeIf { it.fileName == fileName }?.text
    } else {
        songTexts.value[fileName]
    }

    /** Writes [edit] into the file, or hands it to the editor where the dialog asking for it edits the editor's draft. */
    private fun editSong(fileName: String, isEditorDraft: Boolean, edit: (String) -> String) {
        if (isEditorDraft) {
            _editorTextEdits.tryEmit(EditorTextEdit(fileName = fileName, edit = edit))
        } else {
            launchLibraryChange { editSongText(fileName, edit) }
        }
    }

    /**
     * Applies [edit] to the text of a song and writes the result, but only over the text the edit was built on: a
     * file that changed underneath it (a sync run that has not reached [songTexts] yet) is read again and the edit
     * applied to that instead, so the other change is kept rather than written over. Once is enough; a file that
     * keeps changing between the read and the write is reported instead of being chased.
     */
    private suspend fun editSongText(fileName: String, edit: (String) -> String) {
        songWriteMutex.withLock {
            repeat(SONG_EDIT_ATTEMPTS) { attempt ->
                // Read inside the lock, so that an edit tapped right after another one starts from what that one wrote.
                // The first attempt may use the text on screen; a retry has to go back to the file.
                val text = songTexts.value[fileName]?.takeIf { attempt == 0 } ?: getSongContent(fileName)?.text ?: return
                val edited = edit(text)
                if (edited == text || withContext(NonCancellable) { writeSongContent(fileName = fileName, text = edited, expectedText = text) }) return
            }
        }
        sendMessage(Message.OperationFailed)
    }

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
            // Stored as the file would hold it rather than as the field shows it, so that it means the same chords
            // whatever the notation is by the time it is reopened.
            val draft = _editorDraft.value?.takeIf { hasUnsavedEditorText() }?.let { it.copy(text = fileTextOf(it.text)) }
            viewModelScope.launch { storeEditorDraft(draft) }
        }
        return syncProgress
    }

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
        content?.let { _songTexts.update { texts -> texts + (it.fileName to it.text) } }
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
                _visibleDialog.value == DialogType.UnsavedChanges -> {
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

    /**
     * The write itself, and [songTexts] brought up to date with it before anything else can read them. The caller holds
     * [songWriteMutex] and runs this on [NonCancellable]: a write that stopped halfway through would leave the file
     * written and the text on screen not.
     *
     * @param expectedText See [SaveSongContentUseCase]: false, and nothing written, when the file no longer holds it.
     */
    private suspend fun writeSongContent(fileName: String, text: String, expectedText: String? = null) =
        saveSongContent.invoke(content = SongContent(fileName = fileName, text = text), expectedText = expectedText).also { isWritten ->
            if (isWritten) _songTexts.update { it + (fileName to text) }
        }

    fun loadSongContent(fileName: String) = viewModelScope.launch {
        // Cleared first, so that a retry shows the loading state again instead of staying on the error.
        _failedSongFileNames.update { it - fileName }
        val content = getSongContent(fileName)
        if (content == null) {
            _failedSongFileNames.update { it + fileName }
        } else {
            _songTexts.update { it + (content.fileName to content.text) }
        }
    }

    /**
     * Parses a song file and applies the file's own `{transpose}` — the one it opens with and the ones further down
     * it — the transposition the user picked and the spelling they prefer, which is what the viewer renders. Build it
     * once for each set of the three rather than on every recomposition, which for a long song would be wasteful. It
     * touches nothing but its arguments and stateless use cases, so it may run on a background thread, and it does:
     * see `rememberSongLyricsModel`.
     */
    fun renderSong(
        text: String,
        transposition: Int,
        spelling: UserPreferences.ChordSpelling,
        writtenIn: UserPreferences.Notation = UserPreferences.Notation.STANDARD,
    ): ChordProSong {
        val parsed = parseChordPro(text, writtenIn)
        // The file's own {transpose} (the one it opens with), the reader's, and the modulations further down: all
        // three are the transposition's to apply, and it leaves a song none of them move exactly as it is.
        val transposed = transposeChordPro(parsed, parsed.metadata.transpose + transposition, spelling.accidentals)
        // Last, and on the model only: the file stays in the standard notation, which the transposition works in.
        return convertChordProNotation(transposed, spelling)
    }

    /**
     * The key a song sounds in once everything that moves it has been applied: the file's own `{transpose}`, the
     * transposition the reader picked for it and the spelling they read chords in. Null for a file that declares
     * no `{key}`, which the lists then say nothing about.
     *
     * It is what [renderSong] arrives at, worked out without the song's text: the library's metadata is read at
     * startup and its lyrics are not, so a list that had to parse a file to name its key would be reading the whole
     * library a second time to fill in one line of each row. Both use cases rewrite the key of whatever song they
     * are handed, so what they are handed here is a song that is nothing but that key.
     */
    fun renderKey(song: Song, transposition: Int, spelling: UserPreferences.ChordSpelling) =
        renderKey(key = song.key, transpose = song.transpose, transposition = transposition, spelling = spelling)

    /**
     * [renderKey] for a song known only by the two things it depends on: the [key] its file declares and the
     * [transpose] it opens with.
     */
    fun renderKey(key: String?, transpose: Int, transposition: Int, spelling: UserPreferences.ChordSpelling) = key?.let {
        val keyOnly = ChordProSong(metadata = ChordProMetadata(key = it), blocks = emptyList())
        convertChordProNotation(transposeChordPro(keyOnly, transpose + transposition, spelling.accidentals), spelling).metadata.key
    }

    /** Formats the current editor draft without saving it or changing its chord notation. */
    fun prettifyText(text: String) = prettifyChordPro(text)

    /**
     * Transposes the chords of the editor's text in place, leaving everything else exactly as it was. Unlike the
     * viewer's transposition this rewrites the file: it is what the editor's "transpose text" does. The text is in the
     * editor's notation, and is transposed in the standard one, which is the only one a semitone means anything in.
     */
    fun transposeText(text: String, semitones: Int, accidentals: UserPreferences.Accidentals) = convertChordProTextNotation(
        text = transposeChordProText(fileTextOf(text), semitones, accidentals),
        from = UserPreferences.Notation.STANDARD,
        to = editorNotation,
    )

    /**
     * The notation the editor's field is written in, the reader's own: the file is converted out of the standard one
     * as it is opened ([editorTextOf]) and back into it as it is saved ([fileTextOf]). It never changes under an open
     * editor, since Settings is only reached by selecting a top level screen, which takes the editor off the stack.
     */
    val editorNotation get() = userPreferences.value?.chordSpelling?.notation ?: UserPreferences.Notation.STANDARD

    /**
     * The text of a file as the editor shows it: in [editorNotation], a file written before every file was in the
     * standard notation brought into it on the way. The last answer is kept, since [hasUnsavedEditorChanges] asks
     * again about the same file on every keystroke.
     */
    fun editorTextOf(fileText: String): String {
        val notation = editorNotation
        lastEditorText?.takeIf { it.fileText == fileText && it.notation == notation }?.let { return it.text }
        return convertChordProTextNotation(text = fileText, from = UserPreferences.Notation.STANDARD, to = notation).also { text ->
            lastEditorText = EditorText(fileText = fileText, notation = notation, text = text)
        }
    }

    private var lastEditorText: EditorText? = null

    /** Follows the editor's text as it is typed, see `ChordProSummaryCache`; its key comes out in the standard notation. */
    fun editorSummaryCache() = ChordProSummaryCache(
        when (editorNotation) {
            UserPreferences.Notation.STANDARD -> ChordNotation.STANDARD
            UserPreferences.Notation.GERMAN -> ChordNotation.GERMAN
        }
    )

    /** A key in the standard notation, as the editor's field would write it. */
    fun editorKeyOf(key: String) = convertChordProNotation(
        song = ChordProSong(metadata = ChordProMetadata(key = key), blocks = emptyList()),
        spelling = UserPreferences.ChordSpelling.Default.copy(notation = editorNotation),
    ).metadata.key

    /** The editor's text as the file is to hold it, in the standard notation. */
    private fun fileTextOf(editorText: String) =
        convertChordProTextNotation(text = editorText, from = editorNotation, to = UserPreferences.Notation.STANDARD)

    /**
     * One step of the transposition stepper. A song opened from a setlist transposes inside that setlist; one opened from
     * the library, in the preferences.
     */
    fun stepTransposition(songFileName: String, setlistFileName: String?, semitones: Int) =
        changeTransposition(songFileName = songFileName, setlistFileName = setlistFileName) { it + semitones }

    /** The stepper's value tapped: the song goes back to the key its file is written in. */
    fun resetTransposition(songFileName: String, setlistFileName: String?) =
        changeTransposition(songFileName = songFileName, setlistFileName = setlistFileName) { 0 }

    /**
     * Applies [change] to the transposition the store holds when the write runs rather than to the one the stepper was
     * drawn with. A setlist's entry only reaches the screen once its write has been round tripped through the
     * repository, and every tap inside that round trip reads the same number off the stepper, so an absolute value would
     * turn five quick taps into two. The setlist's transform is handed the latest version of the file, one write at a
     * time ([UpdateSetlistUseCase]), and the preferences' is applied to what the repository holds when it runs
     * ([UpdateUserPreferencesUseCase]): [userPreferences] is a few hops downstream of it and may not have the previous
     * tap yet.
     *
     * A setlist that is gone by now is not brought back, and saying nothing would leave a stepper that does nothing.
     *
     * The result is wrapped around the octave ([wrapTransposition]), so the stepper never runs into an end.
     */
    private fun changeTransposition(songFileName: String, setlistFileName: String?, change: (Int) -> Int) = launchLibraryChange {
        if (setlistFileName == null) {
            updateUserPreferences { preferences ->
                val transposition = wrapTransposition(change(preferences.transpositions[songFileName] ?: 0))
                preferences.copy(
                    transpositions = if (transposition == 0) {
                        preferences.transpositions - songFileName
                    } else {
                        preferences.transpositions + (songFileName to transposition)
                    }
                )
            }
        } else {
            updateEditableSetlist(setlistFileName) { setlist ->
                setlist.copy(
                    entries = setlist.entries.map { entry ->
                        if (entry.songFileName == songFileName) {
                            entry.copy(transposition = wrapTransposition(change(entry.transposition)))
                        } else {
                            entry
                        }
                    }
                )
            } ?: sendMessage(Message.OperationFailed)
        }
    }

    // Import and export

    /**
     * The pick, export or share that is running, from the tap until its picker has answered. A second one is ignored
     * rather than queued: a double tap on a row is one request, the platforms cannot show two pickers at once (Android
     * stacks them and routes the second answer to nobody, UIKit refuses to present over its own, the desktop nests two
     * modal dialogs), and an export builds its whole archive before its picker shows, which is seconds in which the
     * row looks as if it had not been tapped.
     */
    private var fileTransferJob: Job? = null

    /** Whether [fileTransferJob] is running, for the buttons that would start one and so would be ignored meanwhile. */
    private val _isFileTransferActive = MutableStateFlow(false)
    val isFileTransferActive = _isFileTransferActive.asStateFlow()

    /**
     * How far the export screen's PDF has been drawn, from its Save until the file is ready to be handed to the picker,
     * and null otherwise: once the picker is up there is nothing left to count, and nothing to cancel either (see
     * [cancelPdfExport]).
     */
    private val _pdfExportProgress = MutableStateFlow<PdfExportProgress?>(null)
    val pdfExportProgress = _pdfExportProgress.asStateFlow()

    private var pdfExportJob: Job? = null

    /** Emitted with the screen an export was started from once its file is saved, for that screen, and no other, to close. */
    private val _exportSaved = MutableSharedFlow<DialogType.Export>(extraBufferCapacity = 1)
    val exportSaved = _exportSaved.asSharedFlow()

    /**
     * Only as safe as the pickers are: every one of them has to answer on every way its screen can go away, since a
     * transfer that never ended would keep the app from importing or exporting anything again. Nothing that suspends
     * may come between the tap and the picker either, because the web's file input needs the tap's user activation.
     *
     * [isFileTransferActive] is cleared by the completion of the job that set it, which also comes for a job cancelled
     * before it started, where a `finally` would not run, and only while that job is still the latest: the handler of
     * one that ended late must not clear the flag of the next. The job starts once it is recorded, since the main
     * dispatcher is immediate and a block that never suspends would otherwise complete before it is.
     */
    private fun launchFileTransfer(block: suspend () -> Unit): Job? {
        if (fileTransferJob?.isActive == true) return null
        _isFileTransferActive.value = true
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) { block() }
        fileTransferJob = job
        job.invokeOnCompletion { if (fileTransferJob === job) _isFileTransferActive.value = false }
        job.start()
        return job
    }

    /**
     * The picker is handed in by the composable that has it, but the work runs here: picking a file takes as long as
     * the user takes, and the bottom sheet or menu the action was started from is gone well before that.
     */
    fun importFiles(filePicker: FilePicker) = launchFileTransfer {
        if (_isImporting.value) return@launchFileTransfer
        val files = try {
            filePicker.pickFiles()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not pick the files to import: ${exception.message}")
            sendMessage(Message.ImportFailed)
            return@launchFileTransfer
        }
        enqueueImport(files)
    }

    /**
     * Files the system handed over: opened with Campfire, shared to it, or dropped onto it. Where that turns out to be
     * one song, the song is what the user was after rather than the library it went into, so it is opened.
     */
    fun importFiles(files: List<ImportedFile>) {
        enqueueImport(files, shouldOpenSong = true)
    }

    /**
     * Puts a batch at the end of [importQueue] and returns what completes once it is over, its conflicts question
     * answered, the import that answer decided on written, and its result dismissed. An empty batch is not queued at all.
     */
    private fun enqueueImport(
        files: List<ImportedFile>,
        shouldAnnounceResult: Boolean = true,
        shouldOpenSong: Boolean = false,
    ): CompletableDeferred<Unit> {
        val request = ImportRequest(files = files, shouldAnnounceResult = shouldAnnounceResult, shouldOpenSong = shouldOpenSong)
        if (files.isEmpty()) {
            request.settled.complete(Unit)
        } else {
            _queuedImportCount.update { it + 1 }
            importQueue.trySend(request)
        }
        return request.settled
    }

    /**
     * Suspends until no import is running and none has anything left to report. A conflict leaves its question on
     * the import screen when [import] returns, and the import the answer decides on only starts after that; whatever
     * the screen shows is the one import it is about, so the next batch also waits for it to be left.
     */
    private suspend fun awaitImportSettled() {
        combine(_importReport, _isImporting) { report, isImporting -> report != null || isImporting }.first { !it }
    }

    /**
     * The songs the app is shipped with, put into the library the way any other batch of files is. The settings
     * screen offers this for as long as they are not all there, so the same action both plants them and puts back
     * the ones that have been deleted.
     */
    fun importDemoLibrary() = viewModelScope.launch {
        if (_isImporting.value || !isAddingDemoLibrary.compareAndSet(expect = false, update = true)) return@launch
        val files = readDemoLibrary()
        if (files == null) {
            sendMessage(Message.ImportFailed)
        } else {
            enqueueImport(files).await()
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
                        import(ImportRequest(files = files, shouldAnnounceResult = false, shouldOpenSong = false))
                        awaitImportSettled()
                    }
                }
                // Before the preferences are written rather than after: neither the queue nor the launch screen has any
                // reason to wait for those.
                demoLibraryDecision.complete(Unit)
                isDemoLibraryPending.update { false }
                // Whatever the read came to, rather than for one that succeeded: a read that failed is only tried again by
                // a refresh, which may never come, and waiting for its value could wait for good. With nothing read there
                // is nothing to write either, and the next start is a first run again - which plants nothing into a
                // library that has songs in it.
                userPreferencesState.first { it !is DataState.Loading }.data?.let {
                    saveUserPreferences(it.copy(seenWhatsNewVersions = it.seenWhatsNewVersions + CAMPFIRE_VERSION_NAME))
                }
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
     * other dialog on it - on a first run that is a question about a file the app was opened with - since a welcome
     * that replaced a question would leave it unanswered, and one that waited for the answer would arrive in the middle
     * of whatever the user went on to do next. Showing it is the only time it is shown: the first run's preferences
     * are written as the demo library is settled, before this, so a process that ends with the sheet still up starts
     * the next time without it.
     */
    private suspend fun showWelcomeOnFirstRun() {
        if (!isFirstLaunch.await()) return
        isAppOnScreen.first { it }
        _visibleDialog.compareAndSet(null, DialogType.Welcome)
    }

    /**
     * The first installed version belongs to the welcome, so it is recorded by [plantDemoLibraryOnFirstRun] instead.
     * Later versions wait for the app and for every import queued, running or reported on before opening (see
     * [canShowWhatsNew]), and are recorded by [onWhatsNewShown] once the dialog is on screen rather than here as it is
     * asked for: on Android it is not composed while the update required screen covers the app, and Play's answer
     * right after an update can still be the update it just installed, which puts that screen up and starts its flow,
     * which ends this process - a version recorded before anybody saw it would never be introduced. Recording it as it
     * appears rather than as it closes still keeps a process ended with the dialog up from introducing it again.
     * Keeping every introduced version also makes rolling back and returning to a version silent.
     * An empty release message is recorded at once, so a small release introduces nothing.
     */
    private suspend fun showWhatsNewOnVersionChange() {
        if (isFirstLaunch.await()) return
        val preferences = userPreferencesState.first { it !is DataState.Loading }.data ?: return
        if (CAMPFIRE_VERSION_NAME in preferences.seenWhatsNewVersions) return
        isAppOnScreen.first { it }
        if (LocalizedStrings.get(Res.string.whats_new_message).isNotBlank()) {
            combine(_visibleDialog, _isImporting, _importReport, _queuedImportCount) { dialog, isImporting, report, queuedImportCount ->
                canShowWhatsNew(
                    hasDialog = dialog != null,
                    isImporting = isImporting,
                    hasImportReport = report != null,
                    queuedImportCount = queuedImportCount,
                )
            }.first { it }
            _visibleDialog.compareAndSet(null, DialogType.WhatsNew)
        } else {
            recordWhatsNewVersion()
        }
    }

    /** What [DialogType.WhatsNew] calls as it is composed, see [showWhatsNewOnVersionChange]. */
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

    /**
     * The first half of an import only works out what it would do. Nothing is written until the plan turns out to
     * have nothing worth asking about, or until the user has answered the question it does raise - which is why the
     * plan is kept here rather than on the screen that asks: the answer can arrive long after the screen that started
     * this was left.
     */
    private suspend fun import(request: ImportRequest) {
        // Only ever called by the consumer of importQueue, which waits for each import to settle before the next, and
        // by the first run's demo library before that consumer takes anything, so this holds by construction; it is
        // kept so that a second caller could not start an import over a running one.
        if (request.files.isEmpty() || _isImporting.value || pendingImport != null) return
        _isImporting.update { true }
        isPreparationCancelled = false
        // A sibling of the consumer under the scope's supervisor rather than a child of it, so that the user's Cancel
        // ends only the preparation and never the queue, and a failure inside it only reaches this through await().
        val files = request.files
        val deferred = viewModelScope.async { prepareImport(files) { if (request.shouldAnnounceResult) _importProgress.value = it } }
        preparation = deferred
        val plan = try {
            deferred.await()
        } catch (exception: CancellationException) {
            _importProgress.value = null
            _isImporting.value = false
            // The user's Cancel cancels only the deferred; the consumer itself still being active is what tells the two apart.
            if (isPreparationCancelled && currentCoroutineContext().isActive) return
            throw exception
        } catch (exception: Exception) {
            // Nothing has been written, so there is nothing to list either, and one line says all there is to say.
            println("Could not read the files to import: ${exception.message}")
            _importProgress.value = null
            if (request.shouldAnnounceResult) sendMessage(Message.ImportFailed)
            _isImporting.value = false
            return
        } finally {
            preparation = null
            request.files = emptyList()
        }
        // A Cancel can land after the preparation finished but before this resumed (the resumption is dispatched to the
        // main thread, behind a click already queued there): it was still pressed while the dialog said "reading", so it wins.
        if (isPreparationCancelled) {
            _importProgress.value = null
            _isImporting.value = false
            return
        }
        if (plan.hasConflicts) {
            _importProgress.value = null
            pendingImport = PendingImport(plan = plan, request = request)
            // Nothing is happening while the question is on screen, and a progress bar under it would say otherwise.
            // The report, which is set first, is what keeps the next batch waiting from here on.
            showImportReport(ImportReport.Review(plan.summary))
            _isImporting.value = false
        } else {
            applyImportPlan(plan = plan, resolution = ImportConflictResolution.KEEP_BOTH, request = request, isReported = false)
        }
    }

    /**
     * The progress dialog's Cancel, offered while the files are still being read and compared. Nothing has been written
     * by then, so the library is exactly as it was and nothing is announced; the queue goes on to its next batch. Once
     * the plan is being written or a question is up there is no preparation left to cancel, and a late click does nothing.
     */
    fun cancelImportPreparation() {
        val deferred = preparation ?: return
        isPreparationCancelled = true
        deferred.cancel()
    }

    /**
     * The answer to [ImportReport.Review], which is the only thing that ever overwrites a library file. The import
     * screen stays where it is and shows the import being written, and then what it came to.
     */
    fun resolveImport(resolution: ImportConflictResolution) {
        val pending = pendingImport ?: return
        pendingImport = null
        // Claimed before the question goes away rather than once the import has started, so that nothing waiting for
        // the two of them to be over (see importDemoLibrary) sees a moment with neither.
        _isImporting.update { true }
        _importReport.value = ImportReport.Importing
        viewModelScope.launch { applyImportPlan(plan = pending.plan, resolution = resolution, request = pending.request, isReported = true) }
    }

    /**
     * Expects [isImporting] to have been claimed by the caller, which both of them do before anything can observe the gap.
     *
     * @param isReported Whether the import screen was put up for this import before it started writing, which is
     *   where its outcome goes for as long as that screen has not been left. Otherwise the outcome is a snackbar, unless
     *   something was left out or went wrong, which is what the import screen is put up for.
     */
    private suspend fun applyImportPlan(
        plan: ImportPlan,
        resolution: ImportConflictResolution,
        request: ImportRequest,
        isReported: Boolean,
    ) {
        try {
            // Not cancellable once it has started writing: the view model going away with the Android activity is no
            // reason to leave an archive half imported - its setlists come after all of its songs - and the
            // repositories the files go into outlive it, so whatever screen comes back finds the whole import.
            val result = withContext(NonCancellable) {
                importFiles.invoke(plan, resolution) { if (request.shouldAnnounceResult) _importProgress.value = it }
            }
            _importProgress.value = null
            // A song that was already in the library is opened as well: it is still the song that was asked for, under
            // the name the library has for it. One that the answer to the conflicts left out is in neither list, and
            // the library's own file under that name is a different song.
            val songToOpen = if (request.shouldOpenSong && plan.songs.size == 1 && !plan.songs.single().isConverted && plan.setlists.isEmpty()) {
                (result.importedSongFileNames + result.duplicateFileNames).singleOrNull()
            } else {
                null
            }
            val isReportShown = isReported && isImportReportOnBackStack
            when {
                // The one song the system handed over is what the user was after, and the question about its name has
                // been answered: the import screen gives way to it rather than reporting on one file.
                songToOpen != null -> {
                    // Let go of first, since a report that still says it is being written outlives its screen.
                    if (isReported) _importReport.value = null
                    if (isReportShown) closeImportReport()
                    openImportedSong(songToOpen)
                    if (request.shouldAnnounceResult) sendMessage(Message.ImportFinished(result, hasDetails = false))
                }

                isReportShown -> _importReport.value = ImportReport.Finished(result)
                // Left while it was being written, which is a choice to hear about it the short way.
                isReported -> {
                    _importReport.value = null
                    sendMessage(Message.ImportFinished(result, hasDetails = true))
                }

                !request.shouldAnnounceResult -> Unit
                result.isClean -> sendMessage(Message.ImportFinished(result, hasDetails = plan.entryCount > 1))
                else -> showImportReport(ImportReport.Finished(result))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not import the files: ${exception.message}")
            _importProgress.value = null
            if (isReported && isImportReportOnBackStack) {
                _importReport.value = ImportReport.Finished(null)
            } else {
                if (isReported) _importReport.value = null
                if (request.shouldAnnounceResult) sendMessage(Message.ImportFailed)
            }
        } finally {
            _importProgress.value = null
            _isImporting.update { false }
        }
    }

    /**
     * Sets [report] at once, which is what keeps the next batch waiting, and puts the import screen on top of whatever
     * is on screen once nothing else is in its way: a dialog, whose input would otherwise be left under the screen, or
     * an editor holding unsaved text, which is only ever asked about while it is on top (see [navigateBack]) and would
     * be one closed window away from being lost under it. Launched rather than awaited, so that the import that asked
     * can finish meanwhile.
     */
    private fun showImportReport(report: ImportReport) {
        _importReport.value = report
        viewModelScope.launch {
            combine(_visibleDialog, _editorDraft, _songTexts, snapshotFlow { backStack.toList() }) { dialog, _, _, stack ->
                dialog == null && !(hasUnsavedEditorText() && stack.any { it is CampfireDestination.SongEditor })
            }.first { it }
            if (_importReport.value == report && !isImportReportOnBackStack) {
                updateBackStack { add(CampfireDestination.ImportReport) }
            }
        }
    }

    /** The snackbar's way into the import screen, for an outcome that did not need it but has more to it than one line. */
    internal fun openImportReport(result: ImportResult) {
        if (_importReport.value != null || _isImporting.value) return
        showImportReport(ImportReport.Finished(result))
    }

    /** Takes the import screen off the back stack, with whatever is on top of it, see [onImportReportLeft]. */
    private fun closeImportReport() {
        val index = backStack.indexOf(CampfireDestination.ImportReport)
        if (index >= 0) updateBackStack { while (size > index) removeAt(lastIndex) }
    }

    /**
     * However the import screen went - Back, its Close, Done, or the stack rebuilt under it - a question it asked is
     * answered by cancelling, which leaves the library exactly as it was: the plan is what is thrown away. An import
     * that is being written carries on, and is reported with a snackbar once it is done (see [applyImportPlan]); what
     * a finished one came to has been seen.
     */
    private fun onImportReportLeft() {
        importReportSearch.textFieldState.clearText()
        when (_importReport.value) {
            is ImportReport.Review -> {
                pendingImport = null
                _importReport.value = null
            }

            ImportReport.Importing -> Unit
            is ImportReport.Finished, null -> _importReport.value = null
        }
    }

    internal suspend fun preparePrintSource(dialog: DialogType.Export): PrintSource {
        val preferences = userPreferencesState.value.data
        val setlist = dialog.setlist
        val entries = setlist?.entries ?: listOf(Setlist.Entry(requireNotNull(dialog.song).fileName))
        val songs = screenData.value.data?.unfilteredSongs.orEmpty().associateBy { it.fileName }
        // Read the way the viewer reads it, so that the page is in the key the screen shows: wrapped, and for a song a
        // setlist names more than once, the one amount the viewer settles on rather than whichever entry comes first.
        val setlistFileName = setlist?.fileName ?: dialog.songSetlistFileName
        val printSongs = entries.mapIndexed { index, entry ->
            val song = songs[entry.songFileName] ?: dialog.song
            val content = getSongContent(entry.songFileName)
            val transposition = transpositions.value[entry.songFileName, setlistFileName]
            val rendered = content?.let { withContext(Dispatchers.Default) {
                renderSong(it.text, transposition, preferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default)
            } }
            PrintSong(entry.songFileName, song?.title ?: entry.songFileName.substringBeforeLast('.'), song?.artist,
                index = if (setlist == null) null else index + 1, song = rendered, text = content?.text)
        }
        return PrintSource(title = setlist?.title ?: requireNotNull(dialog.song).title,
            description = setlist?.description.orEmpty(), isSetlist = setlist != null, songs = printSongs)
    }

    fun setPrintSettings(value: PrintSettings) = _pendingPrintSettings.update { value.normalized() }

    private suspend fun savePrintSettings(value: PrintSettings) {
        updateUserPreferences { it.copy(printSettings = value) }
        // Only if nothing newer arrived while this one was being saved, or that one would never be.
        _pendingPrintSettings.compareAndSet(value, null)
    }

    /**
     * [create] draws the pages and reports each one drawn to the callback it is given. Whatever it throws, an
     * `OutOfMemoryError` included, is the export failing rather than the app: a large setlist on a phone with a small
     * heap can run out while drawing, which is reported the way any failed export is. On the web running out of memory
     * is a trap that no handler sees, so there it still ends the app.
     */
    internal fun exportPdf(
        filePicker: FilePicker,
        fileName: String,
        dialog: DialogType.Export,
        pageCount: Int,
        isShare: Boolean,
        create: suspend (onPage: (done: Int) -> Unit) -> ByteArray,
    ) {
        // A Save kept until the pages were laid out can arrive after the screen was closed, and its picker would come up
        // over whatever is on screen by then. Equality rather than identity: the same export closed and opened again
        // while it slides away is an equal instance, and the screen still showing the old one is that export.
        if (_visibleDialog.value != dialog) return
        val job = launchFileTransfer {
            _pdfExportProgress.value = PdfExportProgress(done = 0, total = pageCount)
            try {
                // A share leaves the screen open, since a second share or a save may follow; save() only calls onSaved for a save.
                save(
                    filePicker = filePicker,
                    savedMessage = Message.PdfSaved,
                    isShare = isShare,
                    onSaved = { _exportSaved.tryEmit(dialog) },
                ) {
                    val bytes = try {
                        withContext(Dispatchers.Default) { create { done -> _pdfExportProgress.value = PdfExportProgress(done = done, total = pageCount) } }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (throwable: Throwable) {
                        println("Could not create the PDF: ${throwable::class.simpleName}")
                        null
                    } finally {
                        // Before the picker, which cannot be cancelled from here: Android's would stay up and write the
                        // file all the same once it answered.
                        _pdfExportProgress.value = null
                    }
                    bytes?.let { ExportedFile(fileName, "application/pdf", it) }
                }
            } finally {
                _pdfExportProgress.value = null
            }
        }
        if (job != null) pdfExportJob = job
    }

    /** Stops an export that is still drawing its pages, and leaves one that has its picker up to finish. */
    fun cancelPdfExport() {
        if (_pdfExportProgress.value != null) pdfExportJob?.cancel()
    }

    /**
     * The export screen's other format: a song as the `.cho` file it already is, a setlist as a zip of its manifest and
     * the songs it names, narrowed to [songFileNames] where some were left out (null being all of them). A saved file
     * closes the screen, as a saved PDF does, and the same guard keeps a tap that lands while the screen slides away from
     * bringing a picker up over whatever is under it.
     */
    fun exportFiles(filePicker: FilePicker, dialog: DialogType.Export, songFileNames: Set<String>?, isShare: Boolean) {
        if (_visibleDialog.value != dialog) return
        val setlist = dialog.setlist
        launchFileTransfer {
            save(
                filePicker = filePicker,
                savedMessage = if (setlist == null) Message.SongExported else Message.SetlistExported,
                isShare = isShare,
                onSaved = { _exportSaved.tryEmit(dialog) },
            ) {
                if (setlist == null) {
                    exportSongs(listOf(requireNotNull(dialog.song).fileName))
                } else {
                    exportSetlist.invoke(setlist.fileName, songFileNames)
                }
            }
        }
    }

    fun exportLibrary(filePicker: FilePicker) = launchFileTransfer {
        var skippedFileNames = emptyList<String>()
        save(
            filePicker = filePicker,
            savedMessage = Message.LibraryExported,
            // After the save rather than instead of it: the archive is a real copy of everything that could be read,
            // and what it is missing is the one thing the user could not otherwise find out.
            warnings = { listOfNotNull(skippedFileNames.takeIf { it.isNotEmpty() }?.let(Message::ExportSkippedFiles)) },
        ) {
            exportLibrary.invoke()?.also { skippedFileNames = it.skippedFileNames }?.file
        }
    }

    /** For the Android shell, whose picker can finish an export long after the coroutine that asked for it is gone. */
    fun onExportFailed() {
        sendMessage(Message.ExportFailed)
    }

    /** For the shells, which are what opens a link and so what finds out that nothing did. */
    fun onLinkNotOpened(url: String) {
        sendMessage(Message.LinkNotOpened(url))
    }

    /**
     * Nothing to export and a picker that threw are the same thing to the user: the file did not come out.
     *
     * An archive over what an import takes is saved all the same, since it is still a complete copy that unzips by
     * hand, but the user is told so the day it is made rather than the day it is needed. [onSaved] runs once the file
     * has been saved, and not for one the user dismissed the dialog of.
     *
     * A saved file is confirmed with [savedMessage], unless something about it is worth saying instead ([warnings],
     * read once the file has been made): each of those already says the file was saved, and a confirmation queued
     * after them would only hold up the one line that matters. A share is not confirmed at all, since the platform's
     * own sheet is what the user sees it go out through, and it cannot tell a share that happened from one dismissed.
     */
    private suspend fun save(
        filePicker: FilePicker,
        savedMessage: Message,
        isShare: Boolean = false,
        warnings: () -> List<Message> = { emptyList() },
        onSaved: () -> Unit = {},
        export: suspend () -> ExportedFile?,
    ) = try {
        val file = export()
        when {
            file == null -> sendMessage(Message.ExportFailed)
            isShare -> filePicker.shareFile(file)
            filePicker.saveFile(file) -> {
                val isTooLargeToImport = file.mimeType == ExportedFile.ZIP_MIME_TYPE && file.bytes.size > ImportLimits.MAX_IMPORT_SIZE
                val messages = listOfNotNull(Message.ExportTooLargeToImport.takeIf { isTooLargeToImport }) + warnings()
                messages.ifEmpty { listOf(savedMessage) }.forEach(::sendMessage)
                onSaved()
            }
        }
        Unit
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not export: ${exception.message}")
        sendMessage(Message.ExportFailed)
    }

    // Setlists

    /**
     * A new setlist is followed by the song picker for it, since a setlist is created to have songs put into it and
     * the setlists screen offers no other way of doing that in one place. The picker only opens where nothing else
     * has been opened while the file was being written, and only where the library has songs to pick from.
     */
    fun createSetlist(title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = launchLibraryChange {
        val setlist = createSetlist.invoke(title = title, description = description, date = date, isCountdownShown = isCountdownShown)
        if (allSongs.value.isNotEmpty()) {
            // Not through setVisibleDialog: this only ever replaces no dialog at all, behind which nothing is parked.
            _visibleDialog.compareAndSet(null, DialogType.SongPicker(setlist))
        }
    }

    /**
     * Creating a setlist from the setlist picker of one song, where the only reason it is being created at that
     * moment is that the song should go into it. Both happen in the same library change, so the picker's tick is
     * already there when the new setlist appears in it.
     */
    fun createSetlistWithSong(title: String, description: String, date: LocalDate, isCountdownShown: Boolean, songFileName: String) = launchLibraryChange {
        saveSetlist(
            createSetlist.invoke(title = title, description = description, date = date, isCountdownShown = isCountdownShown)
                .copy(entries = listOf(Setlist.Entry(songFileName = songFileName))),
        )
    }

    fun addSongToSetlist(songFileName: String, setlistFileName: String) = launchLibraryChange {
        updateEditableSetlist(setlistFileName) { setlist ->
            if (setlist.entries.none { it.songFileName == songFileName }) {
                setlist.copy(entries = setlist.entries + Setlist.Entry(songFileName = songFileName))
            } else {
                setlist
            }
        }
    }

    /**
     * Writes the songs a setlist holds as the song picker has them ticked: [songFileNames] is the whole of the setlist
     * in order rather than one song going in or out. The picker is ticked a row at a time, quickly and all into the
     * same file, while [setlists] only catches up with a write once it has been round tripped through the repository,
     * so a toggle worked out from it would build each write on a setlist the previous tick had not reached yet and
     * lose that tick. The entries that stay are taken from the setlist itself, so their transpositions stay with them.
     *
     * The writes go through [UpdateSetlistUseCase], which makes them one at a time and in the order they were asked
     * for, so the file ends up holding what the last tick left rather than whichever write happened to finish last.
     *
     * A setlist that is gone by now - deleted by a sync run while the sheet was open, or unreadable - is not brought
     * back from the sheet's copy: that copy is the setlist as it was when the sheet opened, and nothing else in the app
     * recreates a setlist by changing it.
     */
    fun setSetlistSongs(setlistFileName: String, songFileNames: List<String>) = launchLibraryChange {
        updateEditableSetlist(setlistFileName) { setlist ->
            val entriesBySongFileName = setlist.entries.associateBy { it.songFileName }
            setlist.copy(entries = songFileNames.map { entriesBySongFileName[it] ?: Setlist.Entry(songFileName = it) })
        } ?: sendMessage(Message.OperationFailed)
    }

    /**
     * The title, the description, the date and its countdown are written together, since they are the whole of what
     * the user gets to say about a setlist. Only the title reaches the file name, so the setlist that comes back may be under a name
     * this one has never seen. The setlist is named rather than passed: the dialog has held its copy since it was
     * opened, and the rest of the setlist may have moved on since. One that is gone by now is not brought back.
     */
    fun editSetlist(setlistFileName: String, title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = launchLibraryChange {
        if (setlists.value.firstOrNull { it.fileName == setlistFileName }?.isArchived != false) return@launchLibraryChange
        editSetlist.invoke(
            fileName = setlistFileName,
            title = title,
            description = description,
            date = date,
            isCountdownShown = isCountdownShown,
        ) ?: sendMessage(Message.OperationFailed)
    }

    /**
     * A copy of the setlist under a title and a date of its own: it goes through [CreateSetlistUseCase] rather than
     * through a copied file name, so the copy gets its own name and none of the original's archived state - a copy is
     * made to be worked on.
     */
    fun duplicateSetlist(setlist: Setlist, title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = launchLibraryChange {
        reorderingSetlistFileName = null
        if (setlists.value.firstOrNull { it.fileName == setlist.fileName }?.isArchived != false) return@launchLibraryChange
        val copy = createSetlist.invoke(title = title, description = description, date = date, isCountdownShown = isCountdownShown)
        saveSetlist(copy.copy(entries = setlist.entries))
    }

    /** Archiving is the way a setlist that has been played is put away without the songs in it being lost. */
    fun setSetlistArchived(setlist: Setlist, isArchived: Boolean) = launchLibraryChange {
        reorderingSetlistFileName = null
        updateSetlist(setlist.fileName) { it.copy(isArchived = isArchived) }
    }

    fun deleteSetlist(setlistFileName: String) = launchLibraryChange {
        if (reorderingSetlistFileName == setlistFileName) reorderingSetlistFileName = null
        if (setlists.value.firstOrNull { it.fileName == setlistFileName }?.isArchived != false) return@launchLibraryChange
        deleteSetlist.invoke(setlistFileName)
    }

    /**
     * Only ever offered from the settings screen, whose back stack holds no song screen and no editor, so there is
     * nothing open on a file that is about to go.
     */
    fun deleteLibrary() = launchLibraryChange {
        deleteLibrary.invoke()
    }

    fun clearCoverArtCache() {
        viewModelScope.launch { clearCoverArtCache.invoke() }
    }

    /** The transposition of the song travels in the entry, so removing it takes the transposition with it. */
    fun removeSongFromSetlist(songFileName: String, setlistFileName: String) = launchLibraryChange {
        updateEditableSetlist(setlistFileName) { setlist -> setlist.copy(entries = setlist.entries.filterNot { it.songFileName == songFileName }) }
    }

    /**
     * Writes the order a drag ended on, as one write rather than one per row the finger crossed: a move worked out
     * from [setlists], which only catches up once the previous write has been round tripped through the repository,
     * would be recomputed from an order one or more moves out of date.
     *
     * [songFileNames] is what the screen was showing, which is not necessarily the whole setlist. The entries the
     * filters hide cannot be dragged and must not be moved by a drag that could not see them, so the visible songs
     * are dealt back into the slots visible songs already occupied and everything else stays exactly where it is.
     */
    fun reorderSetlist(setlistFileName: String, songFileNames: List<String>) = launchLibraryChange {
        updateEditableSetlist(setlistFileName) { setlist ->
            val reordered = songFileNames.mapNotNull { songFileName ->
                setlist.entries.firstOrNull { it.songFileName == songFileName }
            }.iterator()
            val movedSongFileNames = songFileNames.toSet()
            setlist.copy(
                entries = setlist.entries.map { entry ->
                    if (entry.songFileName in movedSongFileNames && reordered.hasNext()) reordered.next() else entry
                },
            )
        }
    }

    /** Read the current archived state inside the serialized update, including for already-open dialogs. */
    private suspend fun updateEditableSetlist(fileName: String, transform: (Setlist) -> Setlist): Setlist? =
        updateSetlist(fileName) { setlist ->
            if (setlist.isArchived) setlist else transform(setlist)
        }.also { setlist ->
            if (reorderingSetlistFileName == fileName && (setlist == null || setlist.isArchived || setlist.entries.size < 2)) {
                reorderingSetlistFileName = null
            }
        }

    // User preferences


    fun setShouldShowArchivedSetlists(value: Boolean) = changeUserPreferences { copy(shouldShowArchivedSetlists = value) }

    fun setPerformanceModeEnabled(value: Boolean) = changeUserPreferences { copy(isPerformanceModeEnabled = value) }

    fun setLyricsOnlyModeEnabled(value: Boolean) = changeUserPreferences { copy(isLyricsOnlyModeEnabled = value) }

    /**
     * Folds or unfolds one section of a song (or one tab or grid inside it), [key] being the name the song details
     * screen gives it. One set per song, wherever it is opened from, and kept in the preferences rather than in a
     * setlist, since it is how one reader reads the song rather than how the band plays it. It toggles what the
     * preferences hold when the change runs, which already has the previous tap in it (see [changeTransposition]).
     */
    fun toggleSectionFold(songFileName: String, key: String) = changeUserPreferences {
        val folded = foldedSections[songFileName].orEmpty().let { if (key in it) it - key else it + key }
        copy(foldedSections = if (folded.isEmpty()) foldedSections - songFileName else foldedSections + (songFileName to folded))
    }

    /**
     * Kept in whole percent, which is all the stepper's label shows: a slow pinch moves less than that on most frames,
     * and an equal value is one the snapshot state ignores, so those frames lay nothing out again.
     */
    fun setFontScale(value: Float) {
        val clamped = (value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) * 100).roundToInt() / 100f
        if (clamped == liveFontScale.floatValue) return
        liveFontScale.floatValue = clamped
        unsavedFontScale.value = clamped
    }

    /**
     * Moves the font scale by the given number of [FONT_SCALE_STEP]s. A value set by a gesture is first snapped to the
     * grid of steps in the direction of the change, so that a single tap always lands on the next step (125% goes to
     * 120% or 130%, never past them).
     */
    fun adjustFontScale(steps: Int) {
        val currentSteps = liveFontScale.floatValue / FONT_SCALE_STEP
        val snappedSteps = if (steps > 0) floor(currentSteps + FONT_SCALE_STEP_TOLERANCE) else ceil(currentSteps - FONT_SCALE_STEP_TOLERANCE)
        setFontScale((snappedSteps + steps) * FONT_SCALE_STEP)
        settleFontScale()
    }

    private fun settleFontScale() {
        settledFontScaleState.floatValue = liveFontScale.floatValue
    }

    fun setSortingMode(value: UserPreferences.SortingMode) = changeUserPreferences { copy(sortingMode = value) }

    fun setSetlistSortingMode(value: UserPreferences.SetlistSortingMode) = changeUserPreferences { copy(setlistSortingMode = value) }

    /** A selected tag is matched the way the filter itself matches it, without regard to case. */
    fun toggleTagFilter(tag: String) = _songFilter.update { filter ->
        val without = filter.selectedTags.filterNotTo(mutableSetOf()) { it.equals(tag, ignoreCase = true) }
        filter.copy(selectedTags = if (without.size == filter.selectedTags.size) filter.selectedTags + tag else without)
    }

    internal fun toggleSongPickerTag(tag: String) = _songPickerSelectedTags.update { selected ->
        val key = tag.lowercase()
        if (key in selected) selected - key else selected + key
    }

    internal fun toggleSongPickerLanguage(code: String) = _songPickerSelectedLanguages.update { selected ->
        if (code in selected) selected - code else selected + code
    }

    /** Only the tags the library still has are cleared: a selection this screen never showed is not a tap's to lose. */
    fun clearTagFilter() = _songFilter.update { filter ->
        val libraryTags = tags.value.mapTo(mutableSetOf()) { it.name.lowercase() }
        filter.copy(selectedTags = filter.selectedTags.filterNotTo(mutableSetOf()) { it.lowercase() in libraryTags })
    }

    fun setTagMatchMode(value: UserPreferences.MatchMode) = changeUserPreferences { copy(tagMatchMode = value) }

    fun setLanguageMatchMode(value: UserPreferences.MatchMode) = changeUserPreferences { copy(languageMatchMode = value) }

    fun setTagSortingMode(value: UserPreferences.LabelSortingMode) = changeUserPreferences { copy(tagSortingMode = value) }

    fun setLanguageSortingMode(value: UserPreferences.LabelSortingMode) = changeUserPreferences { copy(languageSortingMode = value) }

    /**
     * Accent and case insensitive text, for a screen that has to sort or search through something the library did
     * not put in order for it - the picker of every language there is, which is ordered by a name that depends on
     * the language the app is set to and so cannot be ordered anywhere below the UI.
     */
    fun normalize(text: String) = normalizeText(text)

    /** What the pickers' search fields and the tag suggestions compare, the same key the two list screens search by. */
    fun normalizeForSearch(text: String) = normalizeSearchText(text)

    /**
     * The language a piece of text names, for the picker's search field: a reader who knows a song is in Hungarian
     * may well type `hun` or `HU` rather than the word the app would show them, and either has to find the one row
     * the library files that language under.
     */
    fun languageCode(value: String) = normalizeLanguageCode(value)

    /** The codes are normalized by the parser, so a selected language is the string the filter chip carries. */
    fun toggleLanguageFilter(code: String) = _songFilter.update { filter ->
        filter.copy(
            selectedLanguages = if (code in filter.selectedLanguages) filter.selectedLanguages - code else filter.selectedLanguages + code,
        )
    }

    /** Only the languages the library still has are cleared, for the same reason [clearTagFilter] is careful. */
    fun clearLanguageFilter() = _songFilter.update { filter ->
        val libraryLanguages = languages.value.mapTo(mutableSetOf()) { it.code }
        filter.copy(selectedLanguages = filter.selectedLanguages.filterNotTo(mutableSetOf()) { it in libraryLanguages })
    }

    fun setUiMode(value: UserPreferences.UiMode) = changeUserPreferences { copy(uiMode = value) }

    fun setThemeColor(value: UserPreferences.ThemeColor) = changeUserPreferences { copy(themeColor = value) }

    fun setAppIconThemed(value: Boolean) = changeUserPreferences { copy(isAppIconThemed = value) }

    fun setCoverArtEnabled(value: Boolean) = changeUserPreferences { copy(isCoverArtEnabled = value) }

    fun setLanguage(value: UserPreferences.Language) = changeUserPreferences { copy(language = value) }

    fun setAccidentals(value: UserPreferences.Accidentals) = changeUserPreferences { copy(chordSpelling = chordSpelling.copy(accidentals = value)) }

    fun setNotation(notation: UserPreferences.Notation) = changeUserPreferences { copy(chordSpelling = chordSpelling.copy(notation = notation)) }

    private fun changeUserPreferences(change: UserPreferences.() -> UserPreferences) {
        viewModelScope.launch { updateUserPreferences { it.change() } }
    }

    // Sync

    /**
     * Kept so that it can be cancelled: an authorization waits on a browser that may never come back, and the
     * cancellation is what closes the sheet on iOS and releases the desktop's socket. While an attempt is being
     * given up on, this is the job doing that, so that the next attempt waits for it.
     */
    private var syncConnectionJob: Job? = null

    /**
     * @param completionPage The words the desktop's redirect page shows, resolved by the screen because that is
     *   where the translations and the language the user picked are, see `AuthorizationCompletionPage`.
     */
    fun connectSyncProvider(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage) {
        if (syncConnectionJob?.isActive == true) return
        // Connecting writes the credentials, and a storage that refuses them is reported by the repository as a
        // failed connection. This is for the exception that one day is not: it must cost a message, not the app.
        syncConnectionJob = launchLibraryChange { connectSyncProvider.invoke(providerId, completionPage) }
    }

    /**
     * Gives up on an authorization that is waiting, which is the way out of a browser the user closed. Cancelling
     * the job is what ends the platform's half of the wait; the repository is asked as well because on the web the
     * job is over as soon as the page starts to navigate away, and a page the browser hands back as it was left is
     * still connecting with nothing to cancel.
     */
    fun cancelSyncConnection() {
        val connection = syncConnectionJob
        syncConnectionJob = viewModelScope.launch {
            // Joined first, so that the clean up of an attempt that is being given up on cannot land on the next
            // one: until it is over this job is the active one, and connectSyncProvider() refuses to start another.
            connection?.cancelAndJoin()
            cancelSyncConnection.invoke()
        }
    }

    fun disconnectSyncProvider() = launchLibraryChange {
        disconnectSyncProvider.invoke()
    }

    /**
     * Not launched in [viewModelScope]: a run belongs to the app rather than to this screen, and carries on while
     * the user moves around it or leaves it entirely. The repository refuses a second run while one is going, so a
     * second tap costs nothing.
     */
    fun synchronizeLibrary(deletionPolicy: SyncDeletionPolicy = SyncDeletionPolicy.ASK) = synchronizeLibrary.invoke(deletionPolicy)

    fun cancelSynchronization() = cancelSynchronization.invoke()

    // Dialogs

    /**
     * The one place [visibleDialog] is given a value, because a dialog can have work parked behind it that nothing
     * else can answer for: the exit behind [DialogType.UnsavedChanges]. It goes with its dialog, however that leaves
     * the screen - answered, dismissed, or replaced, the way the desktop's close button puts the unsaved changes
     * question over anything.
     */
    private fun setVisibleDialog(dialogType: DialogType?) {
        if (dialogType is DialogType.Export || dialogType is DialogType.DuplicateSetlist) reorderingSetlistFileName = null
        // An exit the question was asked for and that is not being run is an exit that was cancelled: its caller
        // may be waiting to hear so (the macOS quit request is).
        if (dialogType != DialogType.UnsavedChanges) takePendingExit()?.onCancelled?.invoke()
        // Nothing but the sheet reads it, and a search nobody is waiting for any more still counts against the
        // service's one request a second.
        if (dialogType !is DialogType.CoverArtSearch) clearCoverArtSearch()
        val previousDialog = _visibleDialog.value
        _underlyingSongInfo.value = (previousDialog as? DialogType.SongInfo)?.takeIf { parent ->
            dialogType is DialogType.SongEdit && !dialogType.isEditorDraft && dialogType.song.fileName == parent.song.fileName &&
                (dialogType is DialogType.SongMetadata || dialogType is DialogType.SongTags ||
                    dialogType is DialogType.SongLinks || dialogType is DialogType.SongLanguages)
        }
        // However the export screen goes - closed, Escape, the web's Back, another dialog put over it - it stays drawn
        // while it slides away, so its own disposal would be too late: its options are saved and its drawing cancelled
        // here, a screen opened again in the next moment finds the options it left, and its Save, Share and options do
        // nothing once it is no longer the dialog on screen.
        if (previousDialog is DialogType.Export && dialogType != previousDialog) {
            _pendingPrintSettings.value?.let { viewModelScope.launch { savePrintSettings(it) } }
            // An export nobody is looking at any more would put its picker up over whatever is on screen by then.
            cancelPdfExport()
        }
        _visibleDialog.update { dialogType }
        // Asked as the sheet is put up rather than by the sheet once it is composed, so that its first frame already
        // says that the search is running instead of crossfading from the hint to it while it slides up.
        if (dialogType is DialogType.CoverArtSearch && previousDialog !is DialogType.CoverArtSearch) searchCoverArt(coverArtQueryOf(song = dialogType.song, isEditorDraft = dialogType.isEditorDraft))
    }

    fun showDialog(dialogType: DialogType) = setVisibleDialog(dialogType)

    fun dismissDialog() = setVisibleDialog(_underlyingSongInfo.value)

    /**
     * What a bottom sheet dismisses itself with: [dialogType] goes only while it is still the dialog on screen. A
     * sheet reports its dismissal from the end of its hide animation, and one that is replaced while it is hiding
     * reports the cancellation of that animation the same way - Material's scrim and back handlers included - by
     * which time the dialog on screen is the one that replaced it. It is also how the export screen closes itself, for
     * the same reason: its saved file and its back gesture can both arrive once another dialog has replaced it.
     */
    fun dismissSheet(dialogType: DialogType) {
        if (_visibleDialog.value == dialogType) dismissDialog()
    }

    // Helpers

    /** The song a dialog is about, for the ones that are about one, see the collector in `init`. */
    private val DialogType.songFileName: String?
        get() = when (this) {
            is DialogType.SetlistPicker -> song.fileName
            is DialogType.DeleteSong -> song.fileName
            is DialogType.SongInfo -> song.fileName
            // The editor's draft is the editor's to keep, whatever became of the file it was opened on.
            is DialogType.SongEdit -> song.fileName.takeUnless { isEditorDraft }
            else -> null
        }

    private fun restoreSearch(key: String) = restore<SavedSearch>(key).let { saved ->
        SearchState(isInitiallyOpen = saved?.isOpen == true, initialQuery = saved?.query.orEmpty())
    }

    /** JSON rather than the values themselves, because a saved state only takes the handful of types a Bundle does. */
    private inline fun <reified T> persist(key: String, value: T) {
        savedStateHandle[key] = Json.encodeToString(value)
    }

    /** Null for nothing saved, and for something saved by a build whose destinations no longer read the same way. */
    private inline fun <reified T> restore(key: String): T? = savedStateHandle.get<String>(key)?.let { saved ->
        try {
            Json.decodeFromString<T>(saved)
        } catch (exception: SerializationException) {
            println("Could not restore \"$key\": ${exception.message}")
            null
        }
    }

    /**
     * [viewModelScope.launch] for the intents that write to the library. A write that fails throws out of the
     * repository, and an exception nobody catches in a launched coroutine takes the whole app down on Android: here
     * it becomes one line at the bottom of the screen instead, and the library stays what it was.
     */
    private fun launchLibraryChange(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("The change could not be written: ${exception.message}")
            sendMessage(Message.OperationFailed)
        }
    }

    /**
     * Every state of this view model is kept up to date from the moment it is created, rather than only while a screen
     * collects it, and that is the one way states are made here.
     *
     * A state that is started by its first collector hands that collector [initialValue] first and its real value a
     * moment later, and a screen answers the difference as a change: the song list's rows fade in, the "New" button
     * expands into the app bar and pushes the search action aside, a settings row is inserted while the screen is still
     * fading in, the lyrics reflow into a different number of columns. Some of the states are also acted on rather than
     * drawn - the writes build what they save out of the preferences and the setlists, leaving the editor asks whether
     * anything is unsaved, the Android shell stops the sync service when it sees no run - and those have to be right
     * whether or not a screen happens to be looking. Letting a state stop only moves the problem: one that keeps its
     * last value comes back with an answer the library may have outgrown meanwhile (a first sync fills it from the
     * settings screen), and one that forgets it comes back to [initialValue].
     *
     * What it costs is that the states doing real work - normalizing every title, artist and tag, grouping the song list,
     * matching the setlists against the library - also do it for changes to the library made while their screen is not
     * showing, which is work those screens would otherwise do the moment they were opened. Those states do it on
     * [Dispatchers.Default] (a `flowOn` before this), since [viewModelScope] would otherwise run it on the main thread.
     */
    private fun <T> Flow<T>.asState(initialValue: T) = distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = initialValue,
    )

    /**
     * Whether a setlist answers the setlists screen's search. The songs are looked up in the library that was
     * normalized once ([indexedSongs]) rather than normalized here, since this runs over every setlist on every
     * character typed; the setlist's own two lines are short enough to fold on the spot.
     *
     * A song whose file has gone missing can only be matched by the name in the entry, which is not what the user
     * searched for, so it matches nothing.
     */
    private fun Setlist.matchesSearch(normalizedQuery: String, songs: Map<String, SearchableSong>): Boolean =
        normalizeSearchText(title).contains(normalizedQuery) ||
            normalizeSearchText(description).contains(normalizedQuery) ||
            entries.any { entry -> songs[entry.songFileName]?.matches(normalizedQuery) == true }

    /**
     * One batch in [importQueue].
     *
     * @param shouldAnnounceResult False for the import nobody asked for: the demo library planted on a first run is
     *   the library the user is about to be shown, and a progress dialog, a snackbar or a result counting the files
     *   of it would be the app reporting on something that, as far as anyone can tell, simply came with it.
     * @param shouldOpenSong True for files the system handed over, see [importFiles].
     * @param files Emptied by [import] once the preparation is over: the plan carries everything the rest of the import
     *   needs, while the request lives for as long as a conflicts question does, which would otherwise keep up to the
     *   whole selection's bytes reachable for nothing.
     */
    private class ImportRequest(
        var files: List<ImportedFile>,
        val shouldAnnounceResult: Boolean,
        val shouldOpenSong: Boolean,
        val settled: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    /** The state [isLoading] is folded from. */
    private data class LoadingLatch(
        val isLoading: Boolean,
        val hasBeenRead: Boolean,
    )

    /**
     * Whether an import went the one way a snackbar is enough for: everything written, nothing left out and nothing
     * stopped. Files that were already in the library count as written, since nothing of them is lost.
     */
    private val ImportResult.isClean
        get() = !isFailed && skippedFileNames.isEmpty() && skippedConflictingFileNames.isEmpty() &&
            oversizedFileNames.isEmpty() && unreadableDocumentFileNames.isEmpty()

    /** How many files the plan has something to say about, which is what makes an import more than one file's. */
    private val ImportPlan.entryCount
        get() = songs.size + setlists.size + skippedFileNames.size + oversizedFileNames.size + unreadableDocumentFileNames.size

    /** An import waiting for the answer to [ImportReport.Review], see [pendingImport]. */
    private class PendingImport(
        val plan: ImportPlan,
        val request: ImportRequest,
    )

    /** The part of a [SearchState] that is worth restoring, see [savedStateHandle]. */
    @Serializable
    private data class SavedSearch(
        val isOpen: Boolean,
        val query: String,
    )

    /** [SongFilter] as it is saved, see [savedStateHandle]; the domain model is not serializable, and has no reason to be. */
    @Serializable
    private data class SavedSongFilter(
        val selectedTags: List<String>,
        val selectedLanguages: List<String>,
    )

    /** One change of [editorTextEdits]: [edit] applied to the text the editor of [fileName] holds when it arrives. */
    class EditorTextEdit(val fileName: String, val edit: (String) -> String)

    /** [text] is [fileText] as an editor showing [notation] shows it, see [editorTextOf]. */
    private class EditorText(val fileText: String, val notation: UserPreferences.Notation, val text: String)

    /** What the cover search sheet shows under its fields, see [coverArtSearch]. */
    sealed interface CoverArtSearchState {

        /** Nothing has been asked yet, or there is nothing to ask by. */
        data object Idle : CoverArtSearchState

        /** A search that was asked, running or answered, see [CoverArtSearchResults]. */
        data class Active(val query: CoverArtQuery, val results: CoverArtSearchResults) : CoverArtSearchState
    }

    /** What [CampfireDestination.ImportReport] shows, see [importReport]. */
    sealed interface ImportReport {

        /** Names the library has given to other files, and the question of what to do about them, see [resolveImport]. */
        data class Review(val summary: ImportPlan.Summary) : ImportReport

        /** The answer being carried out, with [importProgress] saying how far. */
        data object Importing : ImportReport

        /** What the import came to; null for one that failed before it could say. */
        data class Finished(val result: ImportResult?) : ImportReport
    }

    /** Something that has happened and is worth one line of text at the bottom of the screen. */
    sealed interface Message {
        /**
         * @param hasDetails Whether the snackbar offers the import screen, for an outcome that needed none but has
         *   more to it than the counts fit into one line: a batch of more than one file, or one whose screen was left
         *   while it was being written.
         */
        data class ImportFinished(val result: ImportResult, val hasDetails: Boolean) : Message

        data object ImportFailed : Message
        data object ExportFailed : Message
        data object PdfSaved : Message
        data object SongExported : Message
        data object SetlistExported : Message
        data object LibraryExported : Message

        /** An archive that was saved, but that the import would refuse for its size. */
        data object ExportTooLargeToImport : Message

        /** An archive that was saved without the files it names: they could not be read, so they are not in it. */
        data class ExportSkippedFiles(val fileNames: List<String>) : Message
        data object SaveFailed : Message

        /** The file of the song in the editor is no longer there; the editor's text is, and saving writes it back. */
        data object EditedSongFileGone : Message

        /** A long document's unsaved text did not survive the process being killed in the background. */
        data object EditorDraftLost : Message

        /** The editor was reopened on the unsaved text a previous run left when it ended in the background. */
        data object EditorDraftRestored : Message

        /** A change to the library (a new setlist, a deleted song, a moved entry) that could not be written. */
        data object OperationFailed : Message

        /** The song's file was renamed, but a setlist or its saved transposition still names the old file. */
        data object SongFileRenamedPartly : Message

        /** The song's file was deleted, but a setlist or its saved transposition still names it. */
        data object SongDeletedPartly : Message

        /** A link nothing on this machine would open. The address is shown, since reading it is all that is left. */
        data class LinkNotOpened(val url: String) : Message
    }

    /** The settings screen's offer to add the demo library, see [demoLibraryOffer]. */
    enum class DemoLibraryOffer {
        AVAILABLE,

        /** Shown, but not to be taken while an import is running, this one included. */
        UNAVAILABLE,
    }

    /** What a list without content has in its place. */
    enum class Placeholder {
        LOADING,
        ERROR,
        NO_SONGS,
        NO_SETLISTS,

        /** The library has songs, but every one of them is filtered out. */
        ALL_SONGS_HIDDEN,

        /** There are setlists, but every one of them is archived and the screen is not showing those. */
        ALL_SETLISTS_HIDDEN,

        NO_MATCHING_SONGS,
        NO_MATCHING_SETLISTS,
    }

    /**
     * What an empty list has in its place: it is only an error once the load that would have filled it has actually
     * failed, and only [whenEmpty] once a load has finished - until then it is still loading, and saying anything
     * else would have the screen answer a question it cannot answer yet.
     *
     * @param isImporting An empty library with an import running is a library being filled rather than an empty
     *   one, and is worth the same answer as a scan that has not finished. It is what keeps the first launch of the
     *   app from flashing "Your library is empty" over the songs it is planting, and any import into an empty
     *   library from doing the same.
     */
    private fun DataState<ScreenData>.emptyPlaceholder(whenEmpty: Placeholder, isImporting: Boolean) = when {
        this is DataState.Loading || isImporting -> Placeholder.LOADING
        this is DataState.Failure -> Placeholder.ERROR
        else -> whenEmpty
    }

    /**
     * The counts the settings screen shows for the library, only once there is a library to count.
     *
     * @param size The bytes the song and setlist files that were counted take up on disk.
     */
    data class LibrarySummary(
        val songCount: Int,
        val setlistCount: Int,
        val size: Long,
    )

    /** @param filterKey The filter and the preferences [filtered] was built for, see [SongGroups]. */
    private data class IndexedSongInput(
        val all: List<Song>,
        val filtered: List<Song>,
        val sections: List<SongSection>,
        val filterKey: String,
    )

    private data class IndexedSongs(
        val sections: List<SongSection>,
        val search: SongSearchSnapshot,
        val filterKey: String,
    )

    /**
     * What every song in the library is filed under, see [labelsOnEverySong]. The tags are lowercase, so a song's own
     * has to be folded before it is looked up here.
     */
    data class LabelsOnEverySong(
        val tags: Set<String> = emptySet(),
        val languages: Set<String> = emptySet(),
    )

    /**
     * The song list with the filter, the sort and the query it was built for: two equal lists for two filters are two
     * values. The song list scrolls by [filterKey] and keeps a tapped row in place once the list built for it arrives,
     * and with the key read from anywhere else a filter that left every song where it was would never deliver that
     * list, leaving the row to be put back in place by whatever changed the list next.
     */
    data class SongGroups(
        val filterKey: String,
        val groups: List<SongGroup>,
    )

    /** @param header Null for the results of a search, which are ranked rather than filed under anything. */
    data class SongGroup(
        val header: SongSection.Header?,
        val songs: List<Song>,
    )

    /** How many of the [total] pages of a PDF have been drawn, see [pdfExportProgress]. */
    data class PdfExportProgress(
        val done: Int,
        val total: Int,
    )

    /**
     * The transposition of every song the UI can currently show, from both places one can be stored. Looked up by
     * how the song was opened rather than by a composite key, so callers cannot accidentally mix the two up.
     */
    data class Transpositions(
        private val library: Map<String, Int> = emptyMap(),
        private val bySetlist: Map<String, Map<String, Int>> = emptyMap(),
    ) {

        /** Wrapped on the way out too, since a file or a preferences document may hold any amount (see [wrapTransposition]). */
        operator fun get(songFileName: String, setlistFileName: String?): Int = wrapTransposition(
            if (setlistFileName == null) {
                library[songFileName] ?: 0
            } else {
                bySetlist[setlistFileName]?.get(songFileName) ?: 0
            }
        )
    }

    /**
     * One setlist as a list shows it: every entry it has, since a setlist is read as the list somebody wrote down
     * rather than as a view of the library. An entry whose file is not in the library any more (deleted from
     * outside the app) is kept as [Entry.Missing] rather than dropped, so that the user can see it and remove it.
     */
    data class SetlistWithSongs(
        val setlist: Setlist,
        val entries: List<Entry>,
    ) {

        /** The songs that can actually be opened, which is what the pager of the song details screen gets. */
        val songs get() = entries.mapNotNull { (it as? Entry.Present)?.song }

        sealed interface Entry {

            /** The entry's place in the setlist, which is the number its row carries. */
            val index: Int

            val songFileName: String

            data class Present(override val index: Int, val song: Song) : Entry {
                override val songFileName get() = song.fileName
            }

            data class Missing(override val index: Int, override val songFileName: String) : Entry
        }
    }

    sealed interface DialogType {
        data class Export(val song: Song? = null, val setlist: Setlist? = null, val songSetlistFileName: String? = null) : DialogType
        data object NewSetlist : DialogType
        data object NewSong : DialogType
        data object SongFilters : DialogType
        /**
         * Every setlist with a box each, which is how a song is both put into one and taken out of another.
         * [setlistFileName] is the setlist the song is being read through, if any, whose box is shown but cannot be
         * changed: the song is taken out of a setlist from the setlist's own row, not from the screen reading it there.
         */
        data class SetlistPicker(val song: Song, val setlistFileName: String? = null) : DialogType
        /**
         * Every song of the library with a box each, which is how a setlist is filled from its own side rather than
         * one song at a time from the menu of each. [setlist] is the setlist the sheet was opened on, and only stands
         * in for the one in [setlists] until the library has caught up with it, which a setlist created a moment ago
         * may not have.
         */
        data class SongPicker(val setlist: Setlist) : DialogType
        data class DeleteSetlist(val setlist: Setlist) : DialogType
        data class RemoveSongFromSetlist(val songFileName: String, val songTitle: String, val setlistFileName: String) : DialogType
        data class EditSetlist(val setlist: Setlist) : DialogType
        data class DuplicateSetlist(val setlist: Setlist) : DialogType
        data class DeleteSong(val song: Song) : DialogType
        /**
         * What a song says about itself beyond how it is played, opened from the song details app bar. It reads the
         * song's text as it is now rather than a snapshot, so that what its buttons edit is there when it is opened
         * again.
         */
        data class SongInfo(val song: Song) : DialogType
        /**
         * Opened from the song details overflow menu, and offers the song's own tags and
         * the rest of the library's.
         */
        data class SongTags(override val song: Song, override val isEditorDraft: Boolean = false) : SongEdit
        /** A snapshot of what the song says for each field the overflow menu's metadata editor offers, blank for nothing. */
        data class SongMetadata(override val song: Song, val values: Map<ChordProMetadataFields.Field, String>, override val isEditorDraft: Boolean = false) : SongEdit
        /** A snapshot of the links offered by the overflow menu's link editor. */
        data class SongLinks(override val song: Song, val links: List<ChordProLink>, override val isEditorDraft: Boolean = false) : SongEdit
        /** Opened from the same menu, and asking about every language at once rather than one at a time. */
        data class SongLanguages(override val song: Song, override val isEditorDraft: Boolean = false) : SongEdit
        /** The records the song may have come out on, whose front cover can be made the song's, see [searchCoverArt]. */
        data class CoverArtSearch(override val song: Song, override val isEditorDraft: Boolean = false) : SongEdit
        /** Removing a cover rewrites the file, so the cover art sheet asks before doing it. */
        data class RemoveSongCoverArt(override val song: Song, override val isEditorDraft: Boolean = false) : SongEdit

        /**
         * A dialog that edits the metadata of one song, opened from the song details overflow menu or from the editor's.
         * Opened from the editor, it changes the text being typed there rather than the file (see [editorTextEdits]),
         * since nothing but Save writes the file the editor is open on.
         */
        sealed interface SongEdit : DialogType {
            val song: Song
            val isEditorDraft: Boolean
        }
        /**
         * Asked before the connected account is forgotten. Nothing is deleted either way, but reconnecting means
         * going through the consent page again, which is not something to end up in by mistapping a list row.
         */
        data class DisconnectSync(val accountName: String) : DialogType
        /** Asked before the copies of the covers are deleted, which costs a download of each one shown again. */
        data object ClearCoverArtCache : DialogType
        /**
         * Asked before every song and setlist is deleted, and answered by typing a word rather than by a tap, since it
         * is the one thing in the app that loses the user's own work wholesale.
         */
        data object DeleteLibrary : DialogType
        /** Asked before the editor is left with something in it that has not been written yet, see [navigateBack]. */
        data object UnsavedChanges : DialogType

        /**
         * Asked over the import screen before its answer overwrites [count] files of the library, which is the one
         * answer to its question that cannot be taken back.
         */
        data class ConfirmImportReplace(val count: Int) : DialogType
        /** Asked before the editor throws away everything typed since the last save, see [revertEditorChanges]. */
        data object RevertChanges : DialogType

        /** Shown once, over the first run of an installation, see [showWelcomeOnFirstRun]. */
        data object Welcome : DialogType
        /** The current version's introduction, shown once after the first installed version. */
        data object WhatsNew : DialogType
    }

    companion object {
        const val DEFAULT_FONT_SCALE = UserPreferences.DEFAULT_FONT_SCALE
        const val MIN_FONT_SCALE = UserPreferences.MIN_FONT_SCALE
        const val MAX_FONT_SCALE = UserPreferences.MAX_FONT_SCALE
        const val FONT_SCALE_STEP = 0.1f
        private const val FONT_SCALE_STEP_TOLERANCE = 0.01f // Floating point slack, so that 1.1000001 still counts as step 11.
        private const val FONT_SCALE_SAVE_DELAY_MILLIS = 500L
        private const val FONT_SCALE_SETTLE_MILLIS = 200L
        private const val SONG_EDIT_ATTEMPTS = 2
        private const val BACK_STACK_KEY = "backStack"
        private const val DEMO_LIBRARY_READ_TIMEOUT_MILLIS = 10_000L // Past the drawables' five seconds: it cuts short a first impression, not a frame.
        private const val MAX_SAVED_BACK_STACK_LENGTH = 100_000 // Characters of JSON, about 200 KB as the UTF-16 a Bundle writes.
        private const val SONG_FILTER_KEY = "songFilter"
        private const val SONG_PICKER_TAGS_KEY = "songPickerTags"
        private const val SONG_PICKER_LANGUAGES_KEY = "songPickerLanguages"
        private const val SONGS_SEARCH_KEY = "songsSearch"
        private const val SETLISTS_SEARCH_KEY = "setlistsSearch"
        private val MIN_RESCAN_INTERVAL = 10.seconds
        private val EXIT_SYNC_GRACE = 15.seconds // Long enough for the run an edit asks for, short enough to never look hung.
        private val EXIT_SYNC_STOP_GRACE = 2.seconds
        private const val SEMITONES_PER_OCTAVE = 12

        /**
         * [semitones] as the one amount between -5 and +6 that moves the chords to the same names: twelve semitones up
         * or down is the same song, so +7 reads as -5 and -6 as +6, the stepper steps around the octave rather than into
         * an end, and a label never claims more than half an octave either way.
         */
        fun wrapTransposition(semitones: Int) = (semitones + 5).mod(SEMITONES_PER_OCTAVE) - 5
    }
}
