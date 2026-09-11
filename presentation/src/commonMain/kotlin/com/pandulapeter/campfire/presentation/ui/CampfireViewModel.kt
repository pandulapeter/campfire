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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.ExportedFile
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportResult
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.model.domain.Tag
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.useCases.CancelSynchronizationUseCase
import com.pandulapeter.campfire.domain.api.useCases.ConnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.useCases.ConvertChordProNotationUseCase
import com.pandulapeter.campfire.domain.api.useCases.CreateSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.CreateSongUseCase
import com.pandulapeter.campfire.domain.api.useCases.DeleteSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.DeleteSongUseCase
import com.pandulapeter.campfire.domain.api.useCases.DisconnectSyncProviderUseCase
import com.pandulapeter.campfire.domain.api.useCases.ExportLibraryUseCase
import com.pandulapeter.campfire.domain.api.useCases.ExportSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.ExportSongsUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSyncProvidersUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSyncStateUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetUserPreferencesUseCase
import com.pandulapeter.campfire.domain.api.useCases.ImportFilesUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeLanguageCodeUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.ParseChordProUseCase
import com.pandulapeter.campfire.domain.api.useCases.PrepareImportUseCase
import com.pandulapeter.campfire.domain.api.useCases.RestoreSyncUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveUserPreferencesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProLanguagesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProTagUseCase
import com.pandulapeter.campfire.domain.api.useCases.SynchronizeLibraryUseCase
import com.pandulapeter.campfire.domain.api.useCases.TransposeChordProTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.TransposeChordProUseCase
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor

@OptIn(FlowPreview::class)
class CampfireViewModel(
    getScreenData: GetScreenDataUseCase,
    getUserPreferences: GetUserPreferencesUseCase,
    getSyncState: GetSyncStateUseCase,
    getSyncProviders: GetSyncProvidersUseCase,
    private val loadScreenData: LoadScreenDataUseCase,
    private val getSongContent: GetSongContentUseCase,
    private val createSong: CreateSongUseCase,
    private val deleteSong: DeleteSongUseCase,
    private val prepareImport: PrepareImportUseCase,
    private val importFiles: ImportFilesUseCase,
    private val exportSongs: ExportSongsUseCase,
    private val exportSetlist: ExportSetlistUseCase,
    private val exportLibrary: ExportLibraryUseCase,
    private val createSetlist: CreateSetlistUseCase,
    private val saveSetlist: SaveSetlistUseCase,
    private val deleteSetlist: DeleteSetlistUseCase,
    private val saveSongContent: SaveSongContentUseCase,
    private val saveUserPreferences: SaveUserPreferencesUseCase,
    private val setChordProLanguages: SetChordProLanguagesUseCase,
    private val setChordProTag: SetChordProTagUseCase,
    private val connectSyncProvider: ConnectSyncProviderUseCase,
    private val disconnectSyncProvider: DisconnectSyncProviderUseCase,
    private val cancelSynchronization: CancelSynchronizationUseCase,
    private val restoreSync: RestoreSyncUseCase,
    private val synchronizeLibrary: SynchronizeLibraryUseCase,
    private val normalizeLanguageCode: NormalizeLanguageCodeUseCase,
    private val normalizeText: NormalizeTextUseCase,
    private val parseChordPro: ParseChordProUseCase,
    private val transposeChordPro: TransposeChordProUseCase,
    private val transposeChordProText: TransposeChordProTextUseCase,
    private val convertChordProNotation: ConvertChordProNotationUseCase,
) : ViewModel() {

    /**
     * The single subscription to the domain layer: every state below maps over this instead of over
     * [GetScreenDataUseCase] directly, which would re-run the whole repository combine once per state. Started
     * eagerly so that the data is loaded into memory as the app starts, rather than when a screen first asks for it.
     */
    private val screenData: StateFlow<DataState<ScreenData>> = getScreenData().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        // Loading, not Failure: nothing has been asked for yet, which is not something to show an error for.
        initialValue = DataState.Loading(null),
    )

    // Navigation
    val backStack: SnapshotStateList<CampfireDestination> = mutableStateListOf(CampfireDestination.Songs)

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
     * Where each of the three top level screens is scrolled to, kept here because a tab that is left is taken off
     * the back stack and loses everything it remembered with it, see [ScrollPosition].
     */
    internal val songsScrollPosition = ScrollPosition()
    internal val setlistsScrollPosition = ScrollPosition()
    internal val settingsScrollPosition = ScrollPosition()

    // Data
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val isLoading = screenData.map { it is DataState.Loading }.asState(true)

    /**
     * Read straight from its own repository rather than out of [screenData], which only has anything once every
     * source has been read: the theme and the language come from here, and waiting for a scan of the whole song
     * library would leave the app in the system's theme and language for as long as that takes. Both states below
     * are derived from this one, so that they can never disagree about whether the read has happened.
     */
    private val userPreferencesState = getUserPreferences().asEagerState(DataState.Loading(null))

    /**
     * Eager for the same reason as [setlists]: `updateUserPreferences` and `setTransposition` build the preferences
     * they save out of this value, and a null one (which is what a state with no subscriber holds) would silently
     * drop the change.
     */
    val userPreferences = userPreferencesState.map { it.data }.asEagerState(null)

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
        .asEagerState(false)

    /**
     * The one preference enough screens ask about to be worth a state of its own: every list, menu, sheet and app
     * bar in the app has something it takes away. Eager for the reason [allSongs] is: a state that only starts
     * collecting once a screen subscribes hands that screen its initial value for one frame first, and here that
     * frame would be an app that can still be edited.
     */
    val isPerformanceModeEnabled = userPreferences.map { it?.isPerformanceModeEnabled == true }.asEagerState(false)

    /**
     * Read straight from its own repository, like the preferences and for the same reason: sync runs on its own
     * schedule, and a settings screen must not wait for a scan of the library to say whether an account is on.
     *
     * Eager, because its first value is acted on: the Android shell stops the sync service when it sees no run, and
     * a state that started out as "disconnected" for the one frame before the real value arrived would stop a run
     * that was going perfectly well in the background whenever the app was opened onto it.
     */
    val syncState = getSyncState().asEagerState(SyncState.Disconnected)

    /** Fixed for the life of the build, so it is a value rather than a flow. Empty means sync is not configured. */
    val syncProviders: List<SyncProviderId> = getSyncProviders()

    /**
     * Eager, unlike most of the states here: the write paths below (adding a song to a setlist, transposing inside
     * one) read this list to build the setlist they save, so it has to be current even when no screen showing
     * setlists happens to be subscribed. [screenData] is already collected eagerly, so this costs nothing extra.
     */
    val setlists = screenData.map { it.data?.setlists.orEmpty() }.asEagerState(emptyList())
    /** The text of the songs opened so far, by file name, read one file at a time as they are opened. */
    private val _songTexts = MutableStateFlow(emptyMap<String, String>())
    val songTexts: StateFlow<Map<String, String>> = _songTexts.asStateFlow()

    /**
     * What the open editor currently has in it, reported by the screen as it is typed but never written until the
     * user asks for it. The text itself still lives in the field's own state; this copy exists so that leaving the
     * screen can be stopped ([navigateBack]) and the save finished from the confirmation dialog without the screen
     * that holds the field being there any more. Null whenever no editor is open.
     */
    private val _editorDraft = MutableStateFlow<SongContent?>(null)

    /** Asked for by the confirmation dialog and answered by the editor screen, see [revertEditorChanges]. */
    private val _editorRevertRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val editorRevertRequests = _editorRevertRequests.asSharedFlow()

    /**
     * True while the editor's text differs from what is on disk. Eager, like [setlists] and for the same reason:
     * [navigateBack] reads it, and it has to be current whether or not anything happens to be subscribed.
     */
    val hasUnsavedEditorChanges = combine(_editorDraft, _songTexts) { draft, songTexts ->
        draft != null && draft.text != songTexts[draft.fileName]
    }.asEagerState(false)

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
    }.asEagerState(Transpositions())

    /**
     * Eager, like [setlists]: the song details screen picks the page it opens on from this list, and a list that was
     * still empty on its first frame would open every setlist on its first song.
     */
    val allSongs = screenData.map { it.data?.songs.orEmpty() }.asEagerState(emptyList())

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
     * Every song with its title and artist normalized for searching and grouping, done once per library rather than
     * once per keystroke: the search runs over the whole list on every character typed.
     */
    private val searchableSongs = allSongs.map { songs ->
        songs.map { SearchableSong(song = it, title = normalizeText(it.title), artist = normalizeText(it.artist)) }
    }

    /**
     * The file names of every song in the library, filters included. Eager for the same reason as [setlists]:
     * [setlistsWithSongs] tells a hidden song from a missing one with it, and an empty set would turn every song the
     * filters hide into a "file not found" row.
     */
    private val songFileNames = screenData.map { it.data?.songFileNames.orEmpty() }.asEagerState(emptySet())

    /**
     * Null until the library has actually been read, so that the settings screen never flashes a count of zero.
     *
     * Eager, like [setlists] and for a reason of its own: a state that starts collecting when the settings screen
     * subscribes to it hands that screen its initial value first and the real one a frame later, which inserts the
     * summary row while the screen is still animating in - the screen arrives in two pieces instead of one.
     * [screenData] is already collected eagerly, so this costs nothing extra.
     */
    val librarySummary = screenData
        .map { state -> state.data?.let { LibrarySummary(songCount = it.songFileNames.size, setlistCount = it.setlists.size) } }
        .asEagerState(null)
    // Distinct on the sorting mode alone, or every other change to the preferences (a transposition, the text size
    // settling after a pinch) would have the whole library grouped again for nothing.
    val songGroups = combine(searchableSongs, query, userPreferences.map { it?.sortingMode }.distinctUntilChanged()) { songs, query, sortingMode ->
        if (query.isBlank()) {
            songs.groupIntoSections(sortingMode ?: UserPreferences.SortingMode.BY_ARTIST)
        } else {
            // No groups at all when nothing matches, rather than one empty group: a search with no results has to
            // look empty to whoever decides between the list and a placeholder, not like a list with one section.
            songs.filterAndRank(query).takeIf { it.isNotEmpty() }?.let { listOf(SongGroup(header = null, songs = it)) }.orEmpty()
        }
    }.asState(emptyList())

    /**
     * What the song list has to show instead of songs, null while it has songs. A library that is empty because
     * the search matched nothing is told apart from one that is empty because the load has not finished (or has
     * failed) here, so that a list without data never sits on a loading indicator that nothing will ever replace.
     */
    val songsPlaceholder = combine(screenData, songGroups) { screenData, songGroups ->
        val data = screenData.data
        when {
            songGroups.isNotEmpty() -> null
            // The library itself, not the filtered list: a library that only holds songs the filters hide is not an
            // empty one, and offering to create a first song there would be answering a question nobody asked.
            data == null || data.songFileNames.isEmpty() -> screenData.emptyPlaceholder(Placeholder.NO_SONGS)
            data.songs.isEmpty() -> Placeholder.ALL_SONGS_HIDDEN
            else -> Placeholder.NO_SEARCH_RESULTS
        }
    }.asState(Placeholder.LOADING)

    /** The same for the screens that show the library without the search query, such as the setlists. */
    val libraryPlaceholder = screenData
        .map { if (it.data?.songFileNames.isNullOrEmpty()) it.emptyPlaceholder(Placeholder.NO_SONGS) else null }
        .asState(Placeholder.LOADING)

    val setlistsWithSongs = combine(setlists, allSongs, songFileNames) { setlists, songs, songFileNames ->
        val songsByFileName = songs.associateBy { it.fileName }
        setlists.map { setlist ->
            SetlistWithSongs(
                setlist = setlist,
                entries = setlist.entries.mapNotNull { entry ->
                    val song = songsByFileName[entry.songFileName]
                    when {
                        song != null -> SetlistWithSongs.Entry.Present(song)
                        // In the library but hidden by a filter: not shown, but not missing either.
                        entry.songFileName in songFileNames -> null
                        else -> SetlistWithSongs.Entry.Missing(entry.songFileName)
                    }
                },
            )
        }
    }.asState(emptyList())

    /**
     * What the setlists screen shows instead of setlists, null while it has some. Same reasoning as
     * [songsPlaceholder]: without it a load in progress is indistinguishable from a user who has no setlists, and
     * the screen claims there are none for as long as reading the library takes.
     */
    val setlistsPlaceholder = combine(screenData, setlistsWithSongs) { screenData, setlistsWithSongs ->
        if (setlistsWithSongs.isEmpty()) screenData.emptyPlaceholder(Placeholder.NO_SETLISTS) else null
    }.asState(Placeholder.LOADING)

    /** The file names of the songs whose text could not be read. */
    private val _failedSongFileNames = MutableStateFlow(emptySet<String>())
    val failedSongFileNames: StateFlow<Set<String>> = _failedSongFileNames.asStateFlow()

    /**
     * The text size multiplier of the song details screen. A pinch gesture changes it on every frame, so the latest
     * value is kept here and only written to the user preferences once the changes have settled.
     *
     * Started eagerly instead of with [asState]: the song details screen is the only subscriber, so a flow that only
     * starts with it would hand the first song [DEFAULT_FONT_SCALE] and the saved scale a frame later, reflowing the
     * lyrics into a different number of columns right as the screen animates in.
     */
    private val pendingFontScale = MutableStateFlow<Float?>(null)
    val fontScale = combine(userPreferences, pendingFontScale) { userPreferences, pendingFontScale ->
        pendingFontScale ?: userPreferences?.fontScale ?: DEFAULT_FONT_SCALE
    }.asEagerState(DEFAULT_FONT_SCALE)

    /**
     * The import that has been worked out but not carried out, waiting for the user to answer
     * [DialogType.ImportConflicts]. Not part of the dialog itself, which holds only what it draws: this is the work,
     * and it has to outlive whichever screen the import was started from.
     */
    private var pendingImportPlan: ImportPlan? = null

    /** True while an import is running, which the screens that can start one show as a progress bar. */
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    /** True while the editor's text is being written, which it shows in place of its "Saved" label. */
    private val _isSavingSong = MutableStateFlow(false)
    val isSavingSong: StateFlow<Boolean> = _isSavingSong.asStateFlow()

    /**
     * One-shot notifications for the snackbar. A channel rather than a state, so that two identical results in a row
     * are two messages and a message that has been shown is not shown again when the screen is recreated.
     */
    private val _messages = Channel<Message>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    // Dialogs
    private val _visibleDialog = MutableStateFlow<DialogType?>(null)
    val visibleDialog: StateFlow<DialogType?> = _visibleDialog.asStateFlow()

    init {
        viewModelScope.launch { loadScreenData(false) }
        // Picks a connected account back up, finishes a consent the app was closed in the middle of, and runs a
        // first sync. Its own coroutine, so that a slow network never holds up the library appearing on screen.
        viewModelScope.launch {
            // On the web the consent page replaces the app, so this start up is the second half of a tap on
            // Settings: whether it ended up connected or not, that is the screen the answer is on.
            try {
                if (restoreSync()) {
                    selectTopLevelDestination(CampfireDestination.Settings)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // Sync is something the app does on the side: nothing about it may keep the library from appearing.
                println("Could not restore the sync connection: ${exception.message}")
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
    }

    fun selectTopLevelDestination(destination: CampfireDestination.TopLevel) {
        if (backStack.lastOrNull() == destination) return
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

    private fun popBackStack() {
        if (backStack.size > 1) {
            updateBackStack { removeAt(lastIndex) }
        }
    }

    // Songs

    fun onQueryChanged(newQuery: String) = _query.update { newQuery }

    fun refresh() = viewModelScope.launch {
        loadScreenData(true)
    }

    /** Creates the file and opens it in the editor, which is the only useful thing to do with an empty song. */
    fun createSong(title: String, artist: String) = launchLibraryChange {
        openEditor(fileName = createSong.invoke(title = title, artist = artist).fileName, shouldStartInsideFirstSection = true)
    }

    fun deleteSong(fileName: String) = launchLibraryChange {
        deleteSong.invoke(fileName)
        // Nobody is asked to save a file that has just been deleted, so the draft goes before the screens holding it.
        _editorDraft.update { null }
        // A screen showing the file that has just gone is closed first, or it would sit there on nothing. The editor
        // goes before the details screen underneath it, so both have to be checked rather than only the top one.
        while (backStack.lastOrNull().let { it is CampfireDestination.SongEditor && it.fileName == fileName || it is CampfireDestination.SongDetails && fileName in it.songFileNames }) {
            popBackStack()
        }
        _songTexts.update { it - fileName }
    }

    /**
     * Puts a tag on a song or takes it off, from the header of the screen that is playing it. The file is rewritten
     * rather than the list entry changed: tags live in the song's own text, which is what makes them travel with the
     * file when it is exported, synced or opened anywhere else.
     */
    fun setSongTag(fileName: String, tag: String, isSelected: Boolean) = launchLibraryChange {
        val text = songTexts.value[fileName] ?: getSongContent(fileName)?.text ?: return@launchLibraryChange
        val edited = setChordProTag(text = text, tag = tag, isSelected = isSelected)
        if (edited != text) saveSongContent(fileName = fileName, text = edited)
    }

    /**
     * Declares the languages of a song, from the header of the screen that is playing it. The whole set arrives at
     * once rather than one language at a time, because the picker asks for all of them before it is closed and a
     * file the user owns is better rewritten once than once per checkbox.
     */
    fun setSongLanguages(fileName: String, codes: List<String>) = launchLibraryChange {
        val text = songTexts.value[fileName] ?: getSongContent(fileName)?.text ?: return@launchLibraryChange
        val edited = setChordProLanguages(text = text, codes = codes)
        if (edited != text) saveSongContent(fileName = fileName, text = edited)
    }

    // The editor

    fun openEditor(fileName: String, shouldStartInsideFirstSection: Boolean = false) {
        if (backStack.lastOrNull() !is CampfireDestination.SongEditor) {
            updateBackStack { add(CampfireDestination.SongEditor(fileName = fileName, shouldStartInsideFirstSection = shouldStartInsideFirstSection)) }
        }
    }

    /** Reported by the editor on every change, see [_editorDraft]. Nothing is written here. */
    fun onEditorTextChanged(fileName: String, text: String) = _editorDraft.update { SongContent(fileName = fileName, text = text) }

    /** Reported by the editor once it is gone, whatever became of the text it had. */
    fun onEditorClosed() = _editorDraft.update { null }

    /** The "Save" answer of the unsaved changes dialog. The write outlives this screen, see [saveSongContent]. */
    fun saveEditorChangesAndLeave() {
        _editorDraft.value?.let { saveSongContent(fileName = it.fileName, text = it.text) }
        leaveEditor()
    }

    /** The "Discard" answer of the unsaved changes dialog, and the only way typed text is ever thrown away. */
    fun leaveEditorWithoutSaving() = leaveEditor()

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

    /**
     * Writes the edited text and keeps the copy the viewer renders from in step. Runs on [NonCancellable] because
     * the last save of an editing session can be started as the screen is going away, which cancels its scope.
     */
    fun saveSongContent(fileName: String, text: String) = viewModelScope.launch {
        _isSavingSong.update { true }
        try {
            withContext(NonCancellable) {
                saveSongContent.invoke(SongContent(fileName = fileName, text = text))
                _songTexts.update { it + (fileName to text) }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not save the song \"$fileName\": ${exception.message}")
            _messages.send(Message.SaveFailed)
        } finally {
            _isSavingSong.update { false }
        }
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
     * Parses a song file and applies the file's own `{transpose}`, the transposition the user picked and the spelling
     * they prefer, which is what the viewer renders. Call it from a `remember` keyed on all three: parsing a long song
     * on every recomposition would be wasteful.
     */
    fun renderSong(text: String, transposition: Int, spelling: UserPreferences.ChordSpelling): ChordProSong {
        val parsed = parseChordPro(text)
        val semitones = parsed.metadata.transpose + transposition
        // A preferred spelling still respells a song nobody transposed, so only the two together mean there is nothing to do.
        val transposed = if (semitones == 0 && spelling.accidentals == UserPreferences.Accidentals.ORIGINAL) {
            parsed
        } else {
            transposeChordPro(parsed, semitones, spelling.accidentals)
        }
        // Last, and on the model only: the file, and the editor's transposition below, stay in the app's own notation.
        return convertChordProNotation(transposed, spelling)
    }

    /**
     * Transposes the chords of a document in place, leaving everything else exactly as it was. Unlike the viewer's
     * transposition this rewrites the file: it is what the editor's "transpose text" does.
     */
    fun transposeText(text: String, semitones: Int, accidentals: UserPreferences.Accidentals) =
        transposeChordProText(text, semitones, accidentals)

    /** A song opened from a setlist transposes inside that setlist; one opened from the library, in the preferences. */
    fun setTransposition(songFileName: String, setlistFileName: String?, transposition: Int) = launchLibraryChange {
        val clamped = transposition.coerceIn(MIN_TRANSPOSITION, MAX_TRANSPOSITION)
        if (setlistFileName == null) {
            userPreferences.value?.let { preferences ->
                saveUserPreferences(
                    preferences.copy(
                        transpositions = if (clamped == 0) {
                            preferences.transpositions - songFileName
                        } else {
                            preferences.transpositions + (songFileName to clamped)
                        }
                    )
                )
            }
        } else {
            setlists.value.firstOrNull { it.fileName == setlistFileName }?.let { setlist ->
                saveSetlist(
                    setlist.copy(
                        entries = setlist.entries.map { if (it.songFileName == songFileName) it.copy(transposition = clamped) else it }
                    )
                )
            }
        }
    }

    // Import and export

    /**
     * The picker is handed in by the composable that has it, but the work runs here: picking a file takes as long as
     * the user takes, and the bottom sheet or menu the action was started from is gone well before that.
     */
    fun importFiles(filePicker: FilePicker) = viewModelScope.launch {
        if (_isImporting.value) return@launch
        val files = try {
            filePicker.pickFiles()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not pick the files to import: ${exception.message}")
            _messages.send(Message.ImportFailed)
            return@launch
        }
        import(files)
    }

    /** Files the system handed over: opened with Campfire, shared to it, or dropped onto it. */
    fun importFiles(files: List<ImportedFile>) = viewModelScope.launch { import(files) }

    /**
     * The first half of an import only works out what it would do. Nothing is written until the plan turns out to
     * have nothing worth asking about, or until the user has answered the question it does raise - which is why the
     * plan is kept here rather than in the dialog: the answer can arrive long after the screen that started this.
     */
    private suspend fun import(files: List<ImportedFile>) {
        if (files.isEmpty() || _isImporting.value || pendingImportPlan != null) return
        _isImporting.update { true }
        val plan = try {
            prepareImport(files)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not read the files to import: ${exception.message}")
            _messages.send(Message.ImportFailed)
            _isImporting.update { false }
            return
        }
        if (plan.hasConflicts) {
            // Nothing is happening while the question is on screen, and a progress bar under it would say otherwise.
            _isImporting.update { false }
            pendingImportPlan = plan
            showDialog(DialogType.ImportConflicts(plan.summary))
        } else {
            applyImportPlan(plan, ImportConflictResolution.KEEP_BOTH)
        }
    }

    /** The answer to [DialogType.ImportConflicts], which is the only thing that ever overwrites a library file. */
    fun resolveImport(resolution: ImportConflictResolution) {
        val plan = pendingImportPlan ?: return
        pendingImportPlan = null
        dismissDialog()
        viewModelScope.launch { applyImportPlan(plan, resolution) }
    }

    /** Cancelling leaves the library exactly as it was: the plan is what is thrown away, not a half written import. */
    fun cancelImport() {
        pendingImportPlan = null
        dismissDialog()
    }

    private suspend fun applyImportPlan(plan: ImportPlan, resolution: ImportConflictResolution) {
        _isImporting.update { true }
        try {
            _messages.send(Message.ImportFinished(importFiles.invoke(plan, resolution)))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not import the files: ${exception.message}")
            _messages.send(Message.ImportFailed)
        } finally {
            _isImporting.update { false }
        }
    }

    fun exportSong(filePicker: FilePicker, songFileName: String) = viewModelScope.launch {
        save(filePicker) { exportSongs(listOf(songFileName)) }
    }

    fun shareSong(filePicker: FilePicker, songFileName: String) = viewModelScope.launch {
        save(filePicker, isShare = true) { exportSongs(listOf(songFileName)) }
    }

    fun exportSetlist(filePicker: FilePicker, setlistFileName: String) = viewModelScope.launch {
        save(filePicker) { exportSetlist.invoke(setlistFileName) }
    }

    fun exportLibrary(filePicker: FilePicker) = viewModelScope.launch {
        save(filePicker) { exportLibrary.invoke() }
    }

    /** Nothing to export and a picker that threw are the same thing to the user: the file did not come out. */
    private suspend fun save(filePicker: FilePicker, isShare: Boolean = false, export: suspend () -> ExportedFile?) = try {
        export()?.let { if (isShare) filePicker.shareFile(it) else filePicker.saveFile(it) } ?: _messages.send(Message.ExportFailed)
        Unit
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not export: ${exception.message}")
        _messages.send(Message.ExportFailed)
    }

    // Setlists

    fun createSetlist(title: String) = launchLibraryChange {
        createSetlist.invoke(title)
    }

    /**
     * Creating a setlist from the setlist picker of one song, where the only reason it is being created at that
     * moment is that the song should go into it. Both happen in the same library change, so the picker's tick is
     * already there when the new setlist appears in it.
     */
    fun createSetlistWithSong(title: String, songFileName: String) = launchLibraryChange {
        saveSetlist(createSetlist.invoke(title).copy(entries = listOf(Setlist.Entry(songFileName = songFileName))))
    }

    fun addSongToSetlist(songFileName: String, setlistFileName: String) = launchLibraryChange {
        setlists.value.firstOrNull { it.fileName == setlistFileName }?.let { setlist ->
            if (setlist.entries.none { it.songFileName == songFileName }) {
                saveSetlist(setlist.copy(entries = listOf(Setlist.Entry(songFileName = songFileName)) + setlist.entries))
            }
        }
    }

    fun renameSetlist(setlist: Setlist, title: String) = launchLibraryChange {
        saveSetlist(setlist.copy(title = title.trim()))
    }

    fun deleteSetlist(setlistFileName: String) = launchLibraryChange {
        deleteSetlist.invoke(setlistFileName)
    }

    /** The transposition of the song travels in the entry, so removing it takes the transposition with it. */
    fun removeSongFromSetlist(songFileName: String, setlistFileName: String) = launchLibraryChange {
        setlists.value.firstOrNull { it.fileName == setlistFileName }?.let { setlist ->
            saveSetlist(setlist.copy(entries = setlist.entries.filterNot { it.songFileName == songFileName }))
        }
    }

    fun moveSongInSetlist(setlistFileName: String, fromSongFileName: String, toSongFileName: String) = launchLibraryChange {
        setlists.value.firstOrNull { it.fileName == setlistFileName }?.let { setlist ->
            saveSetlist(
                setlist.copy(
                    entries = setlist.entries.toMutableList().apply {
                        val toIndex = indexOfFirst { it.songFileName == toSongFileName }
                        val fromIndex = indexOfFirst { it.songFileName == fromSongFileName }
                        if (toIndex >= 0 && fromIndex >= 0) add(toIndex, removeAt(fromIndex))
                    }
                )
            )
        }
    }

    // User preferences

    fun setShouldShowSongsWithoutChords(value: Boolean) = updateUserPreferences { copy(shouldShowSongsWithoutChords = value) }

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

    /** A selected tag is matched the way the filter itself matches it, without regard to case. */
    fun toggleTagFilter(tag: String) = updateUserPreferences {
        val without = selectedTags.filterNotTo(mutableSetOf()) { it.equals(tag, ignoreCase = true) }
        copy(selectedTags = if (without.size == selectedTags.size) selectedTags + tag else without)
    }

    /** Only the tags the library still has are cleared: a selection this screen never showed is not a tap's to lose. */
    fun clearTagFilter() = updateUserPreferences {
        val libraryTags = tags.value.mapTo(mutableSetOf()) { it.name.lowercase() }
        copy(selectedTags = selectedTags.filterNotTo(mutableSetOf()) { it.lowercase() in libraryTags })
    }

    fun setTagMatchMode(value: UserPreferences.TagMatchMode) = updateUserPreferences { copy(tagMatchMode = value) }

    /**
     * Accent and case insensitive text, for a screen that has to sort or search through something the library did
     * not put in order for it - the picker of every language there is, which is ordered by a name that depends on
     * the language the app is set to and so cannot be ordered anywhere below the UI.
     */
    fun normalize(text: String) = normalizeText(text)

    /**
     * The language a piece of text names, for the picker's search field: a reader who knows a song is in Hungarian
     * may well type `hun` or `HU` rather than the word the app would show them, and either has to find the one row
     * the library files that language under.
     */
    fun languageCode(value: String) = normalizeLanguageCode(value)

    /** The codes are normalized by the parser, so a selected language is the string the filter chip carries. */
    fun toggleLanguageFilter(code: String) = updateUserPreferences {
        copy(selectedLanguages = if (code in selectedLanguages) selectedLanguages - code else selectedLanguages + code)
    }

    /** Only the languages the library still has are cleared, for the same reason [clearTagFilter] is careful. */
    fun clearLanguageFilter() = updateUserPreferences {
        val libraryLanguages = languages.value.mapTo(mutableSetOf()) { it.code }
        copy(selectedLanguages = selectedLanguages.filterNotTo(mutableSetOf()) { it in libraryLanguages })
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
     * cancellation is what closes the sheet on iOS and releases the desktop's socket.
     */
    private var syncConnectionJob: Job? = null

    /**
     * @param completionPage The words the desktop's redirect page shows, resolved by the screen because that is
     *   where the translations and the language the user picked are, see `AuthorizationCompletionPage`.
     */
    fun connectSyncProvider(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage) {
        if (syncConnectionJob?.isActive == true) return
        syncConnectionJob = viewModelScope.launch { connectSyncProvider.invoke(providerId, completionPage) }
    }

    /** Gives up on an authorization that is waiting, which is the way out of a browser the user closed. */
    fun cancelSyncConnection() {
        syncConnectionJob?.cancel()
        syncConnectionJob = null
    }

    fun disconnectSyncProvider() = launchLibraryChange {
        disconnectSyncProvider.invoke()
    }

    /**
     * Not launched in [viewModelScope]: a run belongs to the app rather than to this screen, and carries on while
     * the user moves around it or leaves it entirely. The repository refuses a second run while one is going, so a
     * second tap costs nothing.
     */
    fun synchronizeLibrary() = synchronizeLibrary.invoke()

    fun cancelSynchronization() = cancelSynchronization.invoke()

    // Dialogs

    fun showDialog(dialogType: DialogType) = _visibleDialog.update { dialogType }

    fun dismissDialog() = _visibleDialog.update { null }

    // Helpers

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
            _messages.send(Message.OperationFailed)
        }
    }

    /**
     * A state that only runs while a screen is looking at it, and that forgets what it last said as soon as it
     * stops: [initialValue] is what each of these means by "nothing has been worked out yet", and that is the only
     * honest answer a state which has not been recomputed since can give.
     *
     * Kept, the answer goes stale as soon as the library changes while its screen is away - which is exactly what a
     * first sync does, since it fills the library from the settings screen. The song list would then be entered on
     * the answer worked out before the sync ("Your library is empty"), with the settings screen next to it already
     * counting the songs, and the real list only arriving a frame later.
     */
    private fun <T> Flow<T>.asState(initialValue: T) = distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
        initialValue = initialValue,
    )

    /** Like [asState], but kept up to date from app start, so that the first subscriber never sees [initialValue]. */
    private fun <T> Flow<T>.asEagerState(initialValue: T) = distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = initialValue,
    )

    /**
     * Runs on every keystroke over the whole library, so the songs come pre-normalized ([searchableSongs]) and the
     * ranking is decided before sorting - a comparator's selector runs on every comparison, not once per song.
     */
    private fun List<SearchableSong>.filterAndRank(query: String): List<Song> {
        val normalizedQuery = normalizeText(query)
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
     * The songs arrive sorted, so a song joins the previous group whenever it has the same key. Comparing the keys
     * rather than the headers keeps this to one comparison per song.
     */
    private fun List<SearchableSong>.groupIntoSections(sortingMode: UserPreferences.SortingMode): List<SongGroup> {
        val groups = mutableListOf<Pair<SongGroup.Header, MutableList<Song>>>()
        var lastKey: String? = null
        forEach { song ->
            val key = when (sortingMode) {
                UserPreferences.SortingMode.BY_ARTIST -> song.artist
                UserPreferences.SortingMode.BY_TITLE -> song.title.initialLetter()?.toString().orEmpty()
            }
            val lastGroup = groups.lastOrNull()
            if (lastGroup != null && key == lastKey) {
                lastGroup.second += song.song
            } else {
                val header = when (sortingMode) {
                    UserPreferences.SortingMode.BY_ARTIST -> SongGroup.Header.Artist(name = song.song.artist, initial = song.artist.initialLetter())
                    UserPreferences.SortingMode.BY_TITLE -> key.firstOrNull()?.let { SongGroup.Header.Letter(it) } ?: SongGroup.Header.Symbols
                }
                groups += header to mutableListOf(song.song)
                lastKey = key
            }
        }
        return groups.map { (header, songs) -> SongGroup(header, songs) }
    }

    /** The upper case first character of an already normalized (lower case, accent-free) text if it is a letter. */
    private fun String.initialLetter() = firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()

    /** Something that has happened and is worth one line of text at the bottom of the screen. */
    sealed interface Message {
        data class ImportFinished(val result: ImportResult) : Message
        data object ImportFailed : Message
        data object ExportFailed : Message
        data object SaveFailed : Message

        /** A change to the library (a new setlist, a deleted song, a moved entry) that could not be written. */
        data object OperationFailed : Message
    }

    /** What a list without content has in its place. */
    enum class Placeholder {
        LOADING,
        ERROR,
        NO_SONGS,
        NO_SETLISTS,

        /** The library has songs, but every one of them is filtered out. */
        ALL_SONGS_HIDDEN,
        NO_SEARCH_RESULTS,
    }

    /**
     * What an empty list has in its place: it is only an error once the load that would have filled it has actually
     * failed, and only [whenEmpty] once a load has finished - until then it is still loading, and saying anything
     * else would have the screen answer a question it cannot answer yet.
     */
    private fun DataState<ScreenData>.emptyPlaceholder(whenEmpty: Placeholder) = when (this) {
        is DataState.Loading -> Placeholder.LOADING
        is DataState.Failure -> Placeholder.ERROR
        is DataState.Idle -> whenEmpty
    }

    /** The counts the settings screen shows for the library, only once there is a library to count. */
    data class LibrarySummary(
        val songCount: Int,
        val setlistCount: Int,
    )

    private class MatchingSong(
        val song: Song,
        val doesTitleStartWithQuery: Boolean,
        val doesArtistStartWithQuery: Boolean,
    )

    /** A song with the normalized title and artist the search and the grouping compare. */
    private class SearchableSong(
        val song: Song,
        val title: String,
        val artist: String,
    )

    data class SongGroup(
        val header: Header?,
        val songs: List<Song>,
    ) {
        sealed interface Header {
            /** @param initial The first letter of the artist's name, null if the name starts with a symbol. */
            data class Artist(val name: String, val initial: Char?) : Header
            data class Letter(val letter: Char) : Header
            data object Symbols : Header
        }
    }

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
     * One setlist as a list shows it. An entry whose file is not in the library any more (deleted from outside the
     * app) is kept as [Entry.Missing] rather than dropped, so that the user can see it and remove it; entries the
     * filters hide are left out entirely.
     */
    data class SetlistWithSongs(
        val setlist: Setlist,
        val entries: List<Entry>,
    ) {

        /** The songs that can actually be opened, which is what the pager of the song details screen gets. */
        val songs get() = entries.mapNotNull { (it as? Entry.Present)?.song }

        sealed interface Entry {

            val songFileName: String

            data class Present(val song: Song) : Entry {
                override val songFileName get() = song.fileName
            }

            data class Missing(override val songFileName: String) : Entry
        }
    }

    sealed interface DialogType {
        data object NewSetlist : DialogType
        data object NewSong : DialogType
        data object SongsControls : DialogType
        data object SetlistsControls : DialogType
        data class SetlistPicker(val songFileName: String, val currentSetlistFileName: String?) : DialogType
        data class SongDisplayControls(val songFileName: String, val setlistFileName: String?) : DialogType
        data class DeleteSetlist(val setlist: Setlist) : DialogType
        data class RenameSetlist(val setlist: Setlist) : DialogType
        /** The actions of one song, shown as a bottom sheet where there is no room for a dropdown menu. */
        data class SongActions(
            val song: Song,
            val setlistFileName: String?,
            /** False where the screen that opened the sheet offers it already. */
            val shouldIncludeAddToSetlist: Boolean = true,
        ) : DialogType
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
        const val DEFAULT_FONT_SCALE = 1f
        const val MIN_FONT_SCALE = 0.5f
        const val MAX_FONT_SCALE = 2.5f
        const val FONT_SCALE_STEP = 0.1f
        private const val FONT_SCALE_STEP_TOLERANCE = 0.01f // Floating point slack, so that 1.1000001 still counts as step 11.
        private const val FONT_SCALE_SAVE_DELAY_MILLIS = 500L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
