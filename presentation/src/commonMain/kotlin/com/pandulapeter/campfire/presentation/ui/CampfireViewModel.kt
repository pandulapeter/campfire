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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.ExportedFile
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.model.domain.Tag
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.models.SongFilter
import com.pandulapeter.campfire.domain.api.models.SongSection
import com.pandulapeter.campfire.domain.api.useCases.CancelSyncConnectionUseCase
import com.pandulapeter.campfire.domain.api.useCases.CancelSynchronizationUseCase
import com.pandulapeter.campfire.domain.api.useCases.ConnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.useCases.ConvertChordProNotationUseCase
import com.pandulapeter.campfire.domain.api.useCases.CreateSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.CreateSongUseCase
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
import com.pandulapeter.campfire.domain.api.useCases.SetChordProLanguagesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProTagUseCase
import com.pandulapeter.campfire.domain.api.useCases.SynchronizeLibraryUseCase
import com.pandulapeter.campfire.domain.api.useCases.TransposeChordProTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.TransposeChordProUseCase
import com.pandulapeter.campfire.domain.api.useCases.UpdateSetlistUseCase
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.presentation.ui.components.isAnyOverflowMenuOpen
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.NavigationState
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LibraryPersistence
import com.pandulapeter.campfire.presentation.ui.platform.requestLibraryPersistence
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsTab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.math.ceil
import kotlin.math.floor
import org.koin.core.annotation.KoinViewModel

@OptIn(FlowPreview::class)
@KoinViewModel
class CampfireViewModel(
    private val getScreenData: GetScreenDataUseCase,
    getUserPreferences: GetUserPreferencesUseCase,
    getSyncState: GetSyncStateUseCase,
    getSyncProviders: GetSyncProvidersUseCase,
    private val loadScreenData: LoadScreenDataUseCase,
    private val isFirstRun: IsFirstRunUseCase,
    private val getSongContent: GetSongContentUseCase,
    private val getEditorDraft: GetEditorDraftUseCase,
    private val saveEditorDraft: SaveEditorDraftUseCase,
    getSongContentInvalidations: GetSongContentInvalidationsUseCase,
    private val createSong: CreateSongUseCase,
    private val deleteSong: DeleteSongUseCase,
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
    private val setChordProLanguages: SetChordProLanguagesUseCase,
    private val setChordProTag: SetChordProTagUseCase,
    private val connectSyncProvider: ConnectSyncProviderUseCase,
    private val disconnectSyncProvider: DisconnectSyncProviderUseCase,
    private val cancelSyncConnection: CancelSyncConnectionUseCase,
    private val forgetSyncConnection: ForgetSyncConnectionUseCase,
    private val cancelSynchronization: CancelSynchronizationUseCase,
    private val restoreSync: RestoreSyncUseCase,
    private val synchronizeLibrary: SynchronizeLibraryUseCase,
    private val normalizeLanguageCode: NormalizeLanguageCodeUseCase,
    private val normalizeText: NormalizeTextUseCase,
    private val normalizeSearchText: NormalizeSearchTextUseCase,
    private val parseChordPro: ParseChordProUseCase,
    private val transposeChordPro: TransposeChordProUseCase,
    private val transposeChordProText: TransposeChordProTextUseCase,
    private val convertChordProNotation: ConvertChordProNotationUseCase,
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
        addAll(restore<List<CampfireDestination>>(BACK_STACK_KEY)?.takeIf { it.isNotEmpty() } ?: listOf(CampfireDestination.Songs))
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
     * Which tab of the settings screen is open, kept here for the reason the scroll positions are: the screen is taken
     * off the back stack as it is left, and coming back to it should be coming back to where one was. The screen only
     * reads it as it is composed, but it is a state because the web build's address names the tab, and follows it.
     */
    internal var settingsTab by mutableStateOf(SettingsTab.GENERAL)

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

    /**
     * The search a back gesture is about, which is the one belonging to the screen that is on top. Only the two list
     * screens have one at all, and a search left open on a list screen is no business of the song that was opened
     * from it: there, back is back.
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
     * open already, and answers whether it did, so that the key is left to whoever else wants it everywhere else - the
     * browser's own find bar among them. A dialog, a sheet or an overflow menu keeps it from reaching the screen under
     * it, the way it keeps Escape from reaching it.
     */
    internal fun openCurrentSearch(): Boolean {
        if (visibleDialog.value != null || isAnyOverflowMenuOpen) return false
        val search = currentSearch ?: return false
        search.openOrFocus()
        return true
    }

    // Data
    val isLoading = screenData.map { it is DataState.Loading }.asState(true)

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
     * read as the root composition starts: Android recreates its activity, and with it the whole composition, on every
     * rotation, and a composition that started from nothing would put the launch screen back over an app the user is
     * already using. Worse, it would hold the new activity's first frame back until that screen had faded, which is
     * a frozen window and a few hundred milliseconds of lost taps on every configuration change. A process that is
     * started again gets a new view model, which is the start the launch screen is for.
     */
    internal var hasShownApp = false

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
     * The text of the songs opened so far, by file name, read one file at a time as they are opened. Kept in step with
     * the files by [GetSongContentInvalidationsUseCase], see the collector in `init`.
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

    /** Asked for by the confirmation dialog and answered by the editor screen, see [revertEditorChanges]. */
    private val _editorRevertRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val editorRevertRequests = _editorRevertRequests.asSharedFlow()

    /** True while the editor's text differs from what is on disk, which is what [navigateBack] asks before it leaves. */
    val hasUnsavedEditorChanges = combine(_editorDraft, _songTexts) { draft, songTexts ->
        draft != null && draft.text != songTexts[draft.fileName]
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
        LabelsOnEverySong(
            tags = songs.map { song -> song.tags.map { it.lowercase() }.toSet() }.reduceOrNull { a, b -> a intersect b }.orEmpty(),
            languages = songs.map { it.languages.toSet() }.reduceOrNull { a, b -> a intersect b }.orEmpty(),
        )
    }.asState(LabelsOnEverySong())

    /**
     * The library as the song list shows it: narrowed by [songFilter] and sorted the way the preferences ask for.
     * Distinct, because [screenData] also emits for every write to a setlist with the song list exactly as it was, and
     * each of those would otherwise have the whole library normalized again for nothing.
     */
    private val filteredSongs = screenData.map { it.data?.songs.orEmpty() }.distinctUntilChanged()

    /** [filteredSongs] as the domain layer cut it into sections, distinct for the same reason. */
    private val songSections = screenData.map { it.data?.songSections.orEmpty() }.distinctUntilChanged()

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
     * Every song with its title and artist normalized for searching, done once per library rather than
     * once per keystroke: the search runs over the whole list on every character typed.
     */
    private val searchableSongs = filteredSongs.map { songs ->
        songs.map { SearchableSong(song = it, title = normalizeSearchText(it.title), artist = normalizeSearchText(it.artist)) }
    }.asState(emptyList())

    /**
     * The same for the whole library, looked up by file name, which is what the setlists search reads: a setlist
     * names its songs whatever the song filters hide, so it cannot be answered from [searchableSongs].
     *
     * A state rather than a plain flow because two of the states below read it, and this normalizes every title and
     * artist in the library: collected cold it would do all of that once per reader, on every library change.
     */
    private val searchableSongsByFileName = allSongs.map { songs ->
        songs.associateBy({ it.fileName }) { SearchableSong(song = it, title = normalizeSearchText(it.title), artist = normalizeSearchText(it.artist)) }
    }.asState(emptyMap())

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

    // The sections arrive cut, from the same pass that sorted them. Cutting them here would take the sorting mode
    // from the preferences, which change before the list sorted by them arrives.
    val songGroups = combine(songSections, searchableSongs, songsSearch.activeQuery) { sections, songs, query ->
        if (query.isBlank()) {
            sections.map { SongGroup(header = it.header, songs = it.songs) }
        } else {
            // No groups at all when nothing matches, rather than one empty group: a search with no results has to
            // look empty to whoever decides between the list and a placeholder, not like a list with one section.
            songs.filterAndRank(query).takeIf { it.isNotEmpty() }?.let { listOf(SongGroup(header = null, songs = it)) }.orEmpty()
        }
    }.asState(emptyList())

    /** True while an import is running, which the screens that can start one show as a progress bar. */
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

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
            songGroups.isNotEmpty() -> null
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
     * rather than a plain flow for the same reason [searchableSongsByFileName] is, since both of them read it.
     */
    private val visibleSetlists = combine(setlists, searchableSongsByFileName, shouldShowArchivedSetlists) { setlists, songsByFileName, shouldShowArchivedSetlists ->
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
    }.asState(emptyList())

    /**
     * The setlists as the screen lists them, narrowed by its search: a setlist answers it by what it says about
     * itself - its title and its description - or by holding a song that does.
     *
     * A setlist that answers is shown **whole**. The search finds setlists rather than songs inside them: a setlist
     * is the list somebody wrote down, and three of its twelve songs is not that list.
     */
    val setlistsWithSongs = combine(visibleSetlists, searchableSongsByFileName, setlistsSearch.activeQuery) { setlists, songsByFileName, query ->
        if (query.isBlank()) {
            setlists
        } else {
            val normalizedQuery = normalizeSearchText(query)
            setlists.filter { it.setlist.matchesSearch(normalizedQuery = normalizedQuery, songs = songsByFileName) }
        }
    }.asState(emptyList())

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
    private val pendingFontScale = MutableStateFlow<Float?>(null)
    val fontScale = combine(userPreferences, pendingFontScale) { userPreferences, pendingFontScale ->
        pendingFontScale ?: userPreferences?.fontScale ?: DEFAULT_FONT_SCALE
    }.asState(DEFAULT_FONT_SCALE)

    /**
     * The import that has been worked out but not carried out, waiting for the user to answer
     * [DialogType.ImportConflicts]. Not part of the dialog itself, which holds only what it draws: this is the work,
     * and it has to outlive whichever screen the import was started from. It never outlives that dialog: see
     * [setVisibleDialog].
     */
    private var pendingImport: PendingImport? = null

    /**
     * Every batch of files waiting to be imported, taken one at a time by the consumer launched in `init`. Files are
     * handed over whenever the system or the user feels like it - a second archive dropped while the first is still
     * being written, a file opened with the app while the conflicts question is up - and an import can only run while
     * no other one is, so what arrives in the meantime waits here rather than being dropped.
     */
    private val importQueue = Channel<ImportRequest>(Channel.UNLIMITED)

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
     * on every rotation, and a message it had already taken would go with it unshown. A message leaves the queue once
     * it has been shown, see [onMessageShown].
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
    private val _visibleDialog = MutableStateFlow<DialogType?>(null)
    val visibleDialog: StateFlow<DialogType?> = _visibleDialog.asStateFlow()

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
        // in place instead of flashing a loading indicator, and an editor whose file changed underneath it now has
        // unsaved changes, which is what asks the user before their draft replaces the new version — an editor whose
        // file is gone included, where the draft is all there is.
        viewModelScope.launch {
            getSongContentInvalidations().collect { fileName ->
                val affected = _songTexts.value.keys.filter { fileName == null || it == fileName }
                affected.forEach { name ->
                    val content = getSongContent(name)
                    _songTexts.update { if (content == null) it - name else it + (name to content.text) }
                    // The editor keeps what it has, and with no text to compare it to that now counts as unsaved. It
                    // is said out loud because saving is what puts the file back, which the user would otherwise have
                    // no reason to do.
                    if (content == null && _editorDraft.value?.fileName == name) {
                        sendMessage(Message.EditedSongFileGone)
                    }
                }
            }
        }
        viewModelScope.launch {
            demoLibraryDecision.await()
            for (request in importQueue) {
                try {
                    import(request)
                    // The next batch waits for this one's conflicts question too: it would have nowhere to be asked.
                    awaitImportSettled()
                } finally {
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
            }.filterNotNull().collect(::dismissSheet)
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
        listOf(SONGS_SEARCH_KEY to songsSearch, SETLISTS_SEARCH_KEY to setlistsSearch).forEach { (key, search) ->
            viewModelScope.launch {
                combine(search.isOpen, snapshotFlow { search.textFieldState.text.toString() }) { isOpen, query -> SavedSearch(isOpen = isOpen, query = query) }
                    .collect { persist(key, it) }
            }
        }
        viewModelScope.launch {
            pendingFontScale.filterNotNull().debounce(FONT_SCALE_SAVE_DELAY_MILLIS).collect { fontScale ->
                userPreferences.value?.let { saveUserPreferences(it.copy(fontScale = fontScale)) }
            }
        }
    }

    // Navigation

    /** Reported by the UI whenever the state of the navigation transition changes, see [navigationGeneration]. */
    fun setNavigationTransitionRunning(isRunning: Boolean) {
        isNavigationTransitionRunning = isRunning
    }

    private fun updateBackStack(update: SnapshotStateList<CampfireDestination>.() -> Unit) {
        if (isNavigationTransitionRunning) navigationGeneration++
        backStack.update()
        if (backStack.none { it is CampfireDestination.SongEditor }) retainedEditorField = null
        songDetailsCurrentSongs.keys.retainAll(backStack.mapNotNullTo(mutableSetOf()) { (it as? CampfireDestination.SongDetails)?.id })
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
     * consent page opens Settings on its own, and that is the screen the user is waiting for.
     *
     * Called by the shell while it is first composed, which is before the read it waits for can possibly have ended:
     * that read resumes on the main thread, and the composition is holding it.
     */
    internal fun navigateOnLaunch(resolve: (songs: List<Song>, setlists: List<Setlist>) -> NavigationState?) {
        if (hasNavigatedOnLaunch) return
        hasNavigatedOnLaunch = true
        _isLaunchNavigationPending.value = true
        viewModelScope.launch {
            try {
                val state = combine(screenData, isDemoLibraryPending) { state, isDemoLibraryPending -> state.takeUnless { isDemoLibraryPending } }
                    .first { it != null && it !is DataState.Loading }
                    ?.data
                val destination = resolve(state?.unfilteredSongs.orEmpty(), state?.setlists.orEmpty())
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
        settingsTab = SettingsTab.LIBRARY
        selectTopLevelDestination(CampfireDestination.Settings)
    }

    /**
     * Rebuilds the stack around a top level screen. Refused while an editor on the stack holds unsaved text: the
     * navigation chrome that calls this is hidden over the editor, and nothing else may take that text off the screen
     * without asking, see [navigateBack].
     */
    fun selectTopLevelDestination(destination: CampfireDestination.TopLevel) {
        if (backStack.lastOrNull() == destination) return
        if (hasUnsavedEditorText() && backStack.any { it is CampfireDestination.SongEditor }) return
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
    private fun openImportedSong(fileName: String) {
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
     * Every way out of a screen ends up here - the app bar's button, the system's back gesture and the desktop
     * window's Escape key - which is why this is where the editor's unsaved text is caught: nothing the user typed
     * is thrown away without being asked about it first.
     */
    fun navigateBack() {
        if (hasUnsavedEditorChanges.value && backStack.lastOrNull() is CampfireDestination.SongEditor) {
            showDialog(DialogType.UnsavedChanges)
        } else {
            popBackStack()
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

    /** [hasUnsavedEditorChanges] as of this moment, for a decision taken right after a write rather than drawn. */
    private fun hasUnsavedEditorText() = _editorDraft.value?.let { it.text != _songTexts.value[it.fileName] } == true

    private fun popBackStack() {
        if (backStack.size > 1) {
            updateBackStack { removeAt(lastIndex) }
        }
    }

    // Songs

    fun refresh() = viewModelScope.launch {
        loadScreenData(true)
    }

    /** Creates the file and opens it in the editor, which is the only useful thing to do with an empty song. */
    fun createSong(title: String, artist: String) = launchLibraryChange {
        openEditor(fileName = createSong.invoke(title = title, artist = artist).fileName, shouldStartInsideFirstSection = true)
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
     * Puts a tag on a song or takes it off, from the header of the screen that is playing it. The file is rewritten
     * rather than the list entry changed: tags live in the song's own text, which is what makes them travel with the
     * file when it is exported, synced or opened anywhere else.
     */
    fun setSongTag(fileName: String, tag: String, isSelected: Boolean) = launchLibraryChange {
        editSongText(fileName) { text -> setChordProTag(text = text, tag = tag, isSelected = isSelected) }
    }

    /**
     * Declares the languages of a song, from the header of the screen that is playing it. The whole set arrives at
     * once rather than one language at a time, because the picker asks for all of them before it is closed and a
     * file the user owns is better rewritten once than once per checkbox.
     */
    fun setSongLanguages(fileName: String, codes: List<String>) = launchLibraryChange {
        editSongText(fileName) { text -> setChordProLanguages(text = text, codes = codes) }
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
     */
    fun onAppPaused() {
        if (_isEditorDraftRecoveryPending.value) return
        val draft = _editorDraft.value?.takeIf { hasUnsavedEditorText() }
        viewModelScope.launch { storeEditorDraft(draft) }
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
        if (content?.text == draft.text) {
            storeEditorDraft(null)
            return false
        }
        content?.let { _songTexts.update { texts -> texts + (it.fileName to it.text) } }
        // The draft is the editor's before the editor exists, so that nothing asking whether there is unsaved text in
        // the moments before it composes - a pause, the update gate - hears "no".
        onEditorTextChanged(fileName = draft.fileName, text = draft.text)
        updateBackStack { add(CampfireDestination.SongEditor(fileName = draft.fileName)) }
        // Taken by the editor as its field, see LoadedSongEditor, the same way a field it retained across a rotation is.
        retainedEditorField = draft.fileName to TextFieldState(initialText = draft.text)
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
        withContext(NonCancellable) {
            songWriteMutex.withLock { writeSongContent(fileName = fileName, text = text) }
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
     * it — the transposition the user picked and the spelling they prefer, which is what the viewer renders. Call it
     * from a `remember` keyed on all three: parsing a long song on every recomposition would be wasteful.
     */
    fun renderSong(text: String, transposition: Int, spelling: UserPreferences.ChordSpelling): ChordProSong {
        val parsed = parseChordPro(text)
        // The file's own {transpose} (the one it opens with), the reader's, and the modulations further down: all
        // three are the transposition's to apply, and it leaves a song none of them move exactly as it is.
        val transposed = transposeChordPro(parsed, parsed.metadata.transpose + transposition, spelling.accidentals)
        // Last, and on the model only: the file, and the editor's transposition below, stay in the app's own notation.
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
    fun renderKey(song: Song, transposition: Int, spelling: UserPreferences.ChordSpelling) = song.key?.let { key ->
        val keyOnly = ChordProSong(metadata = ChordProMetadata(key = key), blocks = emptyList())
        convertChordProNotation(transposeChordPro(keyOnly, song.transpose + transposition, spelling.accidentals), spelling).metadata.key
    }

    /**
     * Transposes the chords of a document in place, leaving everything else exactly as it was. Unlike the viewer's
     * transposition this rewrites the file: it is what the editor's "transpose text" does.
     */
    fun transposeText(text: String, semitones: Int, accidentals: UserPreferences.Accidentals) =
        transposeChordProText(text, semitones, accidentals)

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
     * time ([UpdateSetlistUseCase]); the preferences are published before they are written, so [userPreferences]
     * already holds the previous tap.
     *
     * A setlist that is gone by now is not brought back, and saying nothing would leave a stepper that does nothing.
     */
    private fun changeTransposition(songFileName: String, setlistFileName: String?, change: (Int) -> Int) = launchLibraryChange {
        if (setlistFileName == null) {
            userPreferences.value?.let { preferences ->
                val transposition = change(preferences.transpositions[songFileName] ?: 0).coerceIn(MIN_TRANSPOSITION, MAX_TRANSPOSITION)
                saveUserPreferences(
                    preferences.copy(
                        transpositions = if (transposition == 0) {
                            preferences.transpositions - songFileName
                        } else {
                            preferences.transpositions + (songFileName to transposition)
                        }
                    )
                )
            }
        } else {
            updateSetlist(setlistFileName) { setlist ->
                setlist.copy(
                    entries = setlist.entries.map { entry ->
                        if (entry.songFileName == songFileName) {
                            entry.copy(transposition = change(entry.transposition).coerceIn(MIN_TRANSPOSITION, MAX_TRANSPOSITION))
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

    /**
     * Only as safe as the pickers are: every one of them has to answer on every way its screen can go away, since a
     * transfer that never ended would keep the app from importing or exporting anything again. Nothing that suspends
     * may come between the tap and the picker either, because the web's file input needs the tap's user activation.
     */
    private fun launchFileTransfer(block: suspend () -> Unit) {
        if (fileTransferJob?.isActive == true) return
        fileTransferJob = viewModelScope.launch { block() }
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
     * answered and the import that answer decided on written. An empty batch is not queued at all.
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
            importQueue.trySend(request)
        }
        return request.settled
    }

    /**
     * Suspends until no import is running and none is waiting for an answer. A conflict leaves its question on screen
     * when [import] returns, and the import the answer decides on only starts after that, so an import is over once
     * there is neither.
     */
    private suspend fun awaitImportSettled() {
        combine(_visibleDialog, _isImporting) { dialog, isImporting -> dialog is DialogType.ImportConflicts || isImporting }.first { !it }
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
                userPreferencesState.first { it !is DataState.Loading }.data?.let { saveUserPreferences(it) }
            }
        } finally {
            // In a finally rather than at the end: whatever went wrong, the app is no longer waiting for this, and
            // the launch screen is over the whole of it - and neither is the queue of imports.
            demoLibraryDecision.complete(Unit)
            isDemoLibraryPending.update { false }
        }
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
     * plan is kept here rather than in the dialog: the answer can arrive long after the screen that started this.
     */
    private suspend fun import(request: ImportRequest) {
        // Only ever called by the consumer of importQueue, which waits for each import to settle before the next, and
        // by the first run's demo library before that consumer takes anything, so this holds by construction; it is
        // kept so that a second caller could not start an import over a running one.
        if (request.files.isEmpty() || _isImporting.value || pendingImport != null) return
        _isImporting.update { true }
        val plan = try {
            prepareImport(request.files)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not read the files to import: ${exception.message}")
            sendMessage(Message.ImportFailed)
            _isImporting.update { false }
            return
        }
        if (plan.hasConflicts) {
            // Nothing is happening while the question is on screen, and a progress bar under it would say otherwise.
            _isImporting.update { false }
            // Asked once nothing else is: the user may be in the middle of another dialog, and whatever they had typed
            // into it would go with it. The plan waits with the queue, which is waiting for this question anyway. The
            // question goes up past setVisibleDialog, which is right since it only ever replaces no dialog, and the
            // pending import is only set once it is up, since any dialog shown meanwhile would have cleared it.
            val question = DialogType.ImportConflicts(plan.summary)
            while (!_visibleDialog.compareAndSet(expect = null, update = question)) {
                _visibleDialog.first { it == null }
            }
            pendingImport = PendingImport(plan = plan, request = request)
        } else {
            applyImportPlan(plan = plan, resolution = ImportConflictResolution.KEEP_BOTH, request = request)
        }
    }

    /** The answer to [DialogType.ImportConflicts], which is the only thing that ever overwrites a library file. */
    fun resolveImport(resolution: ImportConflictResolution) {
        val pending = pendingImport ?: return
        // Claimed before the question goes away rather than once the import has started, so that nothing waiting for
        // the two of them to be over (see importDemoLibrary) sees a moment with neither.
        _isImporting.update { true }
        dismissDialog()
        viewModelScope.launch { applyImportPlan(plan = pending.plan, resolution = resolution, request = pending.request) }
    }

    /** Cancelling leaves the library exactly as it was: the plan is what is thrown away, not a half written import. */
    fun cancelImport() = dismissDialog()

    /**
     * Expects [isImporting] to have been claimed by the caller, which both of them do before anything can observe the gap.
     */
    private suspend fun applyImportPlan(plan: ImportPlan, resolution: ImportConflictResolution, request: ImportRequest) {
        try {
            // Not cancellable once it has started writing: the view model going away with the Android activity is no
            // reason to leave an archive half imported - its setlists come after all of its songs - and the
            // repositories the files go into outlive it, so whatever screen comes back finds the whole import.
            val result = withContext(NonCancellable) { importFiles.invoke(plan, resolution) }
            if (request.shouldOpenSong && plan.songs.size == 1 && plan.setlists.isEmpty()) {
                // A song that was already in the library is opened as well: it is still the song that was asked for,
                // under the name the library has for it. One that the answer to the conflicts left out is in neither
                // list, and the library's own file under that name is a different song.
                (result.importedSongFileNames + result.duplicateFileNames).singleOrNull()?.let(::openImportedSong)
            }
            if (request.shouldAnnounceResult) {
                sendMessage(Message.ImportFinished(result))
                if (result.oversizedFileNames.isNotEmpty()) {
                    sendMessage(Message.ImportOversized(result.oversizedFileNames.size))
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not import the files: ${exception.message}")
            sendMessage(Message.ImportFailed)
        } finally {
            _isImporting.update { false }
        }
    }

    fun exportSong(filePicker: FilePicker, songFileName: String) = launchFileTransfer {
        save(filePicker) { exportSongs(listOf(songFileName)) }
    }

    fun shareSong(filePicker: FilePicker, songFileName: String) = launchFileTransfer {
        save(filePicker, isShare = true) { exportSongs(listOf(songFileName)) }
    }

    fun exportSetlist(filePicker: FilePicker, setlistFileName: String) = launchFileTransfer {
        save(filePicker) { exportSetlist.invoke(setlistFileName) }
    }

    fun exportLibrary(filePicker: FilePicker) = launchFileTransfer {
        var skippedFileNames = emptyList<String>()
        save(
            filePicker = filePicker,
            // After the save rather than instead of it: the archive is a real copy of everything that could be read,
            // and what it is missing is the one thing the user could not otherwise find out.
            onSaved = { if (skippedFileNames.isNotEmpty()) sendMessage(Message.ExportSkippedFiles(skippedFileNames)) },
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
     */
    private suspend fun save(
        filePicker: FilePicker,
        isShare: Boolean = false,
        onSaved: () -> Unit = {},
        export: suspend () -> ExportedFile?,
    ) = try {
        val file = export()
        when {
            file == null -> sendMessage(Message.ExportFailed)
            isShare -> filePicker.shareFile(file)
            filePicker.saveFile(file) -> {
                if (file.mimeType == ExportedFile.ZIP_MIME_TYPE && file.bytes.size > ImportLimits.MAX_IMPORT_SIZE) {
                    sendMessage(Message.ExportTooLargeToImport)
                }
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
    fun createSetlist(title: String, description: String) = launchLibraryChange {
        val setlist = createSetlist.invoke(title = title, description = description)
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
    fun createSetlistWithSong(title: String, description: String, songFileName: String) = launchLibraryChange {
        saveSetlist(createSetlist.invoke(title = title, description = description).copy(entries = listOf(Setlist.Entry(songFileName = songFileName))))
    }

    fun addSongToSetlist(songFileName: String, setlistFileName: String) = launchLibraryChange {
        updateSetlist(setlistFileName) { setlist ->
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
        updateSetlist(setlistFileName) { setlist ->
            val entriesBySongFileName = setlist.entries.associateBy { it.songFileName }
            setlist.copy(entries = songFileNames.map { entriesBySongFileName[it] ?: Setlist.Entry(songFileName = it) })
        } ?: sendMessage(Message.OperationFailed)
    }

    /**
     * The title and the description are written together, since they are the whole of what the user gets to say
     * about a setlist. Only the title reaches the file name, so the setlist that comes back may be under a name
     * this one has never seen. The setlist is named rather than passed: the dialog has held its copy since it was
     * opened, and the rest of the setlist may have moved on since. One that is gone by now is not brought back.
     */
    fun editSetlist(setlistFileName: String, title: String, description: String) = launchLibraryChange {
        editSetlist.invoke(fileName = setlistFileName, title = title, description = description) ?: sendMessage(Message.OperationFailed)
    }

    /**
     * A copy of the setlist under a title of its own, on top of the list the way a new setlist is: it goes through
     * [CreateSetlistUseCase] rather than through a copied file name, so the copy gets its own name, its own priority
     * and none of the original's archived state - a copy is made to be worked on.
     */
    fun duplicateSetlist(setlist: Setlist, title: String, description: String) = launchLibraryChange {
        saveSetlist(createSetlist.invoke(title = title, description = description).copy(entries = setlist.entries))
    }

    /** Archiving is the way a setlist that has been played is put away without the songs in it being lost. */
    fun setSetlistArchived(setlist: Setlist, isArchived: Boolean) = launchLibraryChange {
        updateSetlist(setlist.fileName) { it.copy(isArchived = isArchived) }
    }

    fun deleteSetlist(setlistFileName: String) = launchLibraryChange {
        deleteSetlist.invoke(setlistFileName)
    }

    /** The transposition of the song travels in the entry, so removing it takes the transposition with it. */
    fun removeSongFromSetlist(songFileName: String, setlistFileName: String) = launchLibraryChange {
        updateSetlist(setlistFileName) { setlist -> setlist.copy(entries = setlist.entries.filterNot { it.songFileName == songFileName }) }
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
        updateSetlist(setlistFileName) { setlist ->
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

    // User preferences

    fun setShouldShowSongsWithoutChords(value: Boolean) = updateUserPreferences { copy(shouldShowSongsWithoutChords = value) }

    fun setShouldShowArchivedSetlists(value: Boolean) = updateUserPreferences { copy(shouldShowArchivedSetlists = value) }

    fun setPerformanceModeEnabled(value: Boolean) = updateUserPreferences { copy(isPerformanceModeEnabled = value) }

    fun setLyricsOnlyModeEnabled(value: Boolean) = updateUserPreferences { copy(isLyricsOnlyModeEnabled = value) }

    fun setHorizontalSectionFlowEnabled(value: Boolean) = updateUserPreferences { copy(isHorizontalSectionFlowEnabled = value) }

    fun setFontScale(value: Float) = pendingFontScale.update { value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) }

    /**
     * Moves the font scale by the given number of [FONT_SCALE_STEP]s. A value set by a gesture is first snapped to the
     * grid of steps in the direction of the change, so that a single tap always lands on the next step (125% goes to
     * 120% or 130%, never past them).
     */
    fun adjustFontScale(steps: Int) {
        val currentSteps = fontScale.value / FONT_SCALE_STEP
        val snappedSteps = if (steps > 0) floor(currentSteps + FONT_SCALE_STEP_TOLERANCE) else ceil(currentSteps - FONT_SCALE_STEP_TOLERANCE)
        setFontScale((snappedSteps + steps) * FONT_SCALE_STEP)
    }

    fun setSortingMode(value: UserPreferences.SortingMode) = updateUserPreferences { copy(sortingMode = value) }

    fun setSetlistSortingMode(value: UserPreferences.SetlistSortingMode) = updateUserPreferences { copy(setlistSortingMode = value) }

    /** A selected tag is matched the way the filter itself matches it, without regard to case. */
    fun toggleTagFilter(tag: String) = _songFilter.update { filter ->
        val without = filter.selectedTags.filterNotTo(mutableSetOf()) { it.equals(tag, ignoreCase = true) }
        filter.copy(selectedTags = if (without.size == filter.selectedTags.size) filter.selectedTags + tag else without)
    }

    /** Only the tags the library still has are cleared: a selection this screen never showed is not a tap's to lose. */
    fun clearTagFilter() = _songFilter.update { filter ->
        val libraryTags = tags.value.mapTo(mutableSetOf()) { it.name.lowercase() }
        filter.copy(selectedTags = filter.selectedTags.filterNotTo(mutableSetOf()) { it.lowercase() in libraryTags })
    }

    fun setTagMatchMode(value: UserPreferences.MatchMode) = updateUserPreferences { copy(tagMatchMode = value) }

    fun setLanguageMatchMode(value: UserPreferences.MatchMode) = updateUserPreferences { copy(languageMatchMode = value) }

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

    fun setUiMode(value: UserPreferences.UiMode) = updateUserPreferences { copy(uiMode = value) }

    fun setThemeColor(value: UserPreferences.ThemeColor) = updateUserPreferences { copy(themeColor = value) }

    fun setLanguage(value: UserPreferences.Language) = updateUserPreferences { copy(language = value) }

    fun setAccidentals(value: UserPreferences.Accidentals) = updateUserPreferences { copy(chordSpelling = chordSpelling.copy(accidentals = value)) }

    fun setGermanNotationEnabled(value: Boolean) = updateUserPreferences { copy(chordSpelling = chordSpelling.copy(isGermanNotationEnabled = value)) }

    private fun updateUserPreferences(update: UserPreferences.() -> UserPreferences) = userPreferences.value?.let { userPreferences ->
        viewModelScope.launch { saveUserPreferences(userPreferences.update()) }
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
     * The one place [visibleDialog] is given a value, because two dialogs have work parked behind them that nothing
     * else can answer for: the plan behind [DialogType.ImportConflicts] and the exit behind
     * [DialogType.UnsavedChanges]. Either goes with its dialog, however that leaves the screen - answered, dismissed,
     * or replaced, the way the desktop's close button puts the unsaved changes question over anything. A question
     * nobody can answer any more must not keep every later import from starting, and dropping its plan leaves the
     * library exactly as cancelling would have.
     */
    private fun setVisibleDialog(dialogType: DialogType?) {
        if (dialogType !is DialogType.ImportConflicts) pendingImport = null
        // An exit the question was asked for and that is not being run is an exit that was cancelled: its caller
        // may be waiting to hear so (the macOS quit request is).
        if (dialogType != DialogType.UnsavedChanges) takePendingExit()?.onCancelled?.invoke()
        _visibleDialog.update { dialogType }
    }

    fun showDialog(dialogType: DialogType) = setVisibleDialog(dialogType)

    fun dismissDialog() = setVisibleDialog(null)

    /**
     * What a bottom sheet dismisses itself with: [dialogType] goes only while it is still the dialog on screen. A
     * sheet reports its dismissal from the end of its hide animation, and one that is replaced while it is hiding
     * reports the cancellation of that animation the same way - Material's scrim and back handlers included - by
     * which time the dialog on screen is the one that replaced it.
     */
    fun dismissSheet(dialogType: DialogType) {
        if (_visibleDialog.value == dialogType) dismissDialog()
    }

    // Helpers

    /** The song a dialog is about, for the ones that are about one, see the collector in `init`. */
    private val DialogType.songFileName: String?
        get() = when (this) {
            is DialogType.SetlistPicker -> song.fileName
            is DialogType.SongDisplayControls -> songFileName
            is DialogType.DeleteSong -> song.fileName
            is DialogType.AddSongTag -> song.fileName
            is DialogType.SongLanguages -> song.fileName
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
     * What it costs is that the states doing real work - normalizing every title and artist, grouping the song list,
     * matching the setlists against the library - also do it for changes to the library made while their screen is not
     * showing, which is work those screens would otherwise do the moment they were opened.
     */
    private fun <T> Flow<T>.asState(initialValue: T) = distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = initialValue,
    )

    /**
     * Runs on every keystroke over the whole library, so the songs come pre-normalized ([searchableSongs]) and the
     * ranking is decided before sorting - a comparator's selector runs on every comparison, not once per song.
     */
    private fun List<SearchableSong>.filterAndRank(query: String): List<Song> {
        val normalizedQuery = normalizeSearchText(query)
        return mapNotNull { song ->
            // Both sides are already lower case, so these don't have to pay for a case insensitive comparison.
            if (song.title.contains(normalizedQuery) || song.artist.contains(normalizedQuery)) {
                MatchingSong(
                    song = song.song,
                    doesTitleStartWithQuery = song.title.startsWith(normalizedQuery),
                    doesArtistStartWithQuery = song.artist.startsWith(normalizedQuery),
                )
            } else {
                null
            }
        }.sortedWith(
            compareByDescending<MatchingSong> { it.doesTitleStartWithQuery }.thenByDescending { it.doesArtistStartWithQuery }
        ).map { it.song }
    }

    /**
     * Whether a setlist answers the setlists screen's search. The songs are looked up in the library that was
     * normalized once ([searchableSongsByFileName]) rather than normalized here, since this runs over every setlist
     * on every character typed; the setlist's own two lines are short enough to fold on the spot.
     *
     * A song whose file has gone missing can only be matched by the name in the entry, which is not what the user
     * searched for, so it matches nothing.
     */
    private fun Setlist.matchesSearch(normalizedQuery: String, songs: Map<String, SearchableSong>): Boolean =
        normalizeSearchText(title).contains(normalizedQuery) ||
            normalizeSearchText(description).contains(normalizedQuery) ||
            entries.any { entry ->
                songs[entry.songFileName]?.let { it.title.contains(normalizedQuery) || it.artist.contains(normalizedQuery) } == true
            }

    /**
     * One batch in [importQueue].
     *
     * @param shouldAnnounceResult False for the import nobody asked for: the demo library planted on a first run is
     *   the library the user is about to be shown, and a snackbar counting the files of it would be the app
     *   reporting on something that, as far as anyone can tell, simply came with it.
     * @param shouldOpenSong True for files the system handed over, see [importFiles].
     */
    private class ImportRequest(
        val files: List<ImportedFile>,
        val shouldAnnounceResult: Boolean,
        val shouldOpenSong: Boolean,
        val settled: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    /** An import waiting for the answer to [DialogType.ImportConflicts], see [pendingImport]. */
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

    /** Something that has happened and is worth one line of text at the bottom of the screen. */
    sealed interface Message {
        data class ImportFinished(val result: ImportResult) : Message

        /**
         * Files an import left out for their size, a message of its own rather than one more number in
         * [ImportFinished], which already reads as a row of counts.
         */
        data class ImportOversized(val count: Int) : Message
        data object ImportFailed : Message
        data object ExportFailed : Message

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

    private class MatchingSong(
        val song: Song,
        val doesTitleStartWithQuery: Boolean,
        val doesArtistStartWithQuery: Boolean,
    )

    /** A song with the title and artist the search compares, normalized for searching. */
    private class SearchableSong(
        val song: Song,
        val title: String,
        val artist: String,
    )

    /**
     * What every song in the library is filed under, see [labelsOnEverySong]. The tags are lowercase, so a song's own
     * has to be folded before it is looked up here.
     */
    data class LabelsOnEverySong(
        val tags: Set<String> = emptySet(),
        val languages: Set<String> = emptySet(),
    )

    /** @param header Null for the results of a search, which are ranked rather than filed under anything. */
    data class SongGroup(
        val header: SongSection.Header?,
        val songs: List<Song>,
    )

    /**
     * The transposition of every song the UI can currently show, from both places one can be stored. Looked up by
     * how the song was opened rather than by a composite key, so callers cannot accidentally mix the two up.
     */
    data class Transpositions(
        private val library: Map<String, Int> = emptyMap(),
        private val bySetlist: Map<String, Map<String, Int>> = emptyMap(),
    ) {

        operator fun get(songFileName: String, setlistFileName: String?): Int = if (setlistFileName == null) {
            library[songFileName] ?: 0
        } else {
            bySetlist[setlistFileName]?.get(songFileName) ?: 0
        }
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
        data object NewSetlist : DialogType
        data object NewSong : DialogType
        data object SongsControls : DialogType
        data object SetlistsControls : DialogType
        /**
         * Every setlist with a box each, which is how a song is both put into one and taken out of another.
         *
         * @param lockedSetlistFileName The one setlist whose box cannot be touched, because the screen that
         *   opened the sheet is showing the song as part of that setlist.
         */
        data class SetlistPicker(val song: Song, val lockedSetlistFileName: String?) : DialogType
        /**
         * Every song of the library with a box each, which is how a setlist is filled from its own side rather than
         * one song at a time from the menu of each. [setlist] is the setlist the sheet was opened on, and only stands
         * in for the one in [setlists] until the library has caught up with it, which a setlist created a moment ago
         * may not have.
         */
        data class SongPicker(val setlist: Setlist) : DialogType
        data class SongDisplayControls(val songFileName: String, val setlistFileName: String?) : DialogType
        data class DeleteSetlist(val setlist: Setlist) : DialogType
        data class EditSetlist(val setlist: Setlist) : DialogType
        data class DuplicateSetlist(val setlist: Setlist) : DialogType
        data class DeleteSong(val song: Song) : DialogType
        /** Opened from the tag header of the song details screen; the suggestions come from [tags]. */
        data class AddSongTag(val song: Song) : DialogType
        /** Opened from the same header, and asking about every language at once rather than one at a time. */
        data class SongLanguages(val song: Song) : DialogType
        /**
         * Asked before the connected account is forgotten. Nothing is deleted either way, but reconnecting means
         * going through the consent page again, which is not something to end up in by mistapping a list row.
         */
        data class DisconnectSync(val accountName: String) : DialogType
        /** Asked before the editor is left with something in it that has not been written yet, see [navigateBack]. */
        data object UnsavedChanges : DialogType

        /**
         * Asked when an import would land on names the library has given to other files, see [import]. The plan
         * itself stays in the view model; this carries only what the dialog puts on screen.
         */
        data class ImportConflicts(val summary: ImportPlan.Summary) : DialogType
        /** Asked before the editor throws away everything typed since the last save, see [revertEditorChanges]. */
        data object RevertChanges : DialogType
    }

    companion object {
        const val MIN_TRANSPOSITION = -11
        const val MAX_TRANSPOSITION = 11
        const val DEFAULT_FONT_SCALE = UserPreferences.DEFAULT_FONT_SCALE
        const val MIN_FONT_SCALE = UserPreferences.MIN_FONT_SCALE
        const val MAX_FONT_SCALE = UserPreferences.MAX_FONT_SCALE
        const val FONT_SCALE_STEP = 0.1f
        private const val FONT_SCALE_STEP_TOLERANCE = 0.01f // Floating point slack, so that 1.1000001 still counts as step 11.
        private const val FONT_SCALE_SAVE_DELAY_MILLIS = 500L
        private const val SONG_EDIT_ATTEMPTS = 2
        private const val BACK_STACK_KEY = "backStack"
        private const val DEMO_LIBRARY_READ_TIMEOUT_MILLIS = 10_000L // Past the drawables' five seconds: it cuts short a first impression, not a frame.
        private const val MAX_SAVED_BACK_STACK_LENGTH = 100_000 // Characters of JSON, about 200 KB as the UTF-16 a Bundle writes.
        private const val SONG_FILTER_KEY = "songFilter"
        private const val SONGS_SEARCH_KEY = "songsSearch"
        private const val SETLISTS_SEARCH_KEY = "setlistsSearch"
    }
}
