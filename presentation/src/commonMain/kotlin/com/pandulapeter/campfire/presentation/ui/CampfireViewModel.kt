package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.useCases.CreateSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.DeleteSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveUserPreferencesUseCase
import com.pandulapeter.campfire.domain.api.useCases.TransposeChordProTextUseCase
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor

@OptIn(FlowPreview::class)
class CampfireViewModel(
    getScreenData: GetScreenDataUseCase,
    private val loadScreenData: LoadScreenDataUseCase,
    private val getSongContent: GetSongContentUseCase,
    private val createSetlist: CreateSetlistUseCase,
    private val saveSetlist: SaveSetlistUseCase,
    private val deleteSetlist: DeleteSetlistUseCase,
    private val saveUserPreferences: SaveUserPreferencesUseCase,
    private val normalizeText: NormalizeTextUseCase,
    private val transposeChordProText: TransposeChordProTextUseCase
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
        initialValue = DataState.Loading(null)
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

    // Data
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val isLoading = screenData.map { it is DataState.Loading }.asState(false)
    /**
     * Eager for the same reason as [setlists]: `updateUserPreferences` and `setTransposition` build the preferences
     * they save out of this value, and a null one (which is what a state with no subscriber holds) would silently
     * drop the change.
     */
    val userPreferences = screenData.map { it.data?.userPreferences }.asEagerState(null)

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
     * Where a song's transposition is kept depends on how it was opened, so both places are folded into one lookup:
     * a song opened from a setlist reads the setlist's entry, one opened from the library reads the preferences.
     */
    val transpositions = combine(userPreferences, setlists) { userPreferences, setlists ->
        Transpositions(
            library = userPreferences?.transpositions.orEmpty(),
            bySetlist = setlists.associate { setlist ->
                setlist.fileName to setlist.entries.filter { it.transposition != 0 }.associate { it.songFileName to it.transposition }
            }
        )
    }.asState(Transpositions())
    val allSongs = screenData.map { it.data?.songs.orEmpty() }.asState(emptyList())
    val songGroups = combine(allSongs, query, userPreferences.map { it?.sortingMode }) { songs, query, sortingMode ->
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
        when {
            songGroups.isNotEmpty() -> null
            screenData.data?.songs.isNullOrEmpty() -> screenData.emptyLibraryPlaceholder
            else -> Placeholder.NO_SEARCH_RESULTS
        }
    }.asState(Placeholder.LOADING)

    /** The same for the screens that show the library without the search query, such as the setlists. */
    val libraryPlaceholder = screenData
        .map { if (it.data?.songs.isNullOrEmpty()) it.emptyLibraryPlaceholder else null }
        .asState(Placeholder.LOADING)

    val setlistsWithSongs = combine(setlists, allSongs) { setlists, songs ->
        val songsByFileName = songs.associateBy { it.fileName }
        setlists.map { setlist -> SetlistWithSongs(setlist = setlist, songs = setlist.entries.mapNotNull { songsByFileName[it.songFileName] }) }
    }.asState(emptyList())

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

    // Dialogs
    private val _visibleDialog = MutableStateFlow<DialogType?>(null)
    val visibleDialog: StateFlow<DialogType?> = _visibleDialog.asStateFlow()

    init {
        viewModelScope.launch { loadScreenData(false) }
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

    fun openSongInSetlist(setlistWithSongs: SetlistWithSongs, index: Int) = openSongDetails(
        CampfireDestination.SongDetails(
            songFileNames = setlistWithSongs.songs.map { it.fileName },
            setlistFileName = setlistWithSongs.setlist.fileName,
            initialIndex = index
        )
    )

    private fun openSongDetails(destination: CampfireDestination.SongDetails) {
        if (backStack.lastOrNull() !is CampfireDestination.SongDetails) {
            updateBackStack { add(destination) }
        }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            updateBackStack { removeAt(lastIndex) }
        }
    }

    // Songs

    fun onQueryChanged(newQuery: String) = _query.update { newQuery }

    fun refresh() = viewModelScope.launch {
        loadScreenData(true)
    }

    fun loadSongContent(song: Song) = viewModelScope.launch {
        // Cleared first, so that a retry shows the loading state again instead of staying on the error.
        _failedSongFileNames.update { it - song.fileName }
        val content = getSongContent(song.fileName)
        if (content == null) {
            _failedSongFileNames.update { it + song.fileName }
        } else {
            _songTexts.update { it + (content.fileName to content.text) }
        }
    }

    fun transpose(text: String, transposition: Int) = transposeChordProText(text, transposition)

    /** A song opened from a setlist transposes inside that setlist; one opened from the library, in the preferences. */
    fun setTransposition(songFileName: String, setlistFileName: String?, transposition: Int) = viewModelScope.launch {
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

    // Setlists

    fun createSetlist(title: String) = viewModelScope.launch {
        createSetlist.invoke(title)
    }

    fun addSongToSetlist(songFileName: String, setlistFileName: String) = viewModelScope.launch {
        setlists.value.firstOrNull { it.fileName == setlistFileName }?.let { setlist ->
            if (setlist.entries.none { it.songFileName == songFileName }) {
                saveSetlist(setlist.copy(entries = listOf(Setlist.Entry(songFileName = songFileName)) + setlist.entries))
            }
        }
    }

    fun deleteSetlist(setlistFileName: String) = viewModelScope.launch {
        deleteSetlist.invoke(setlistFileName)
    }

    /** The transposition of the song travels in the entry, so removing it takes the transposition with it. */
    fun removeSongFromSetlist(songFileName: String, setlistFileName: String) = viewModelScope.launch {
        setlists.value.firstOrNull { it.fileName == setlistFileName }?.let { setlist ->
            saveSetlist(setlist.copy(entries = setlist.entries.filterNot { it.songFileName == songFileName }))
        }
    }

    fun moveSongInSetlist(setlistFileName: String, fromSongFileName: String, toSongFileName: String) = viewModelScope.launch {
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

    fun setUiMode(value: UserPreferences.UiMode) = updateUserPreferences { copy(uiMode = value) }

    fun setLanguage(value: UserPreferences.Language) = updateUserPreferences { copy(language = value) }

    private fun updateUserPreferences(update: UserPreferences.() -> UserPreferences) = userPreferences.value?.let { userPreferences ->
        viewModelScope.launch { saveUserPreferences(userPreferences.update()) }
    }

    // Dialogs

    fun showDialog(dialogType: DialogType) = _visibleDialog.update { dialogType }

    fun dismissDialog() = _visibleDialog.update { null }

    // Helpers

    private fun <T> Flow<T>.asState(initialValue: T) = distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = initialValue
    )

    /** Like [asState], but kept up to date from app start, so that the first subscriber never sees [initialValue]. */
    private fun <T> Flow<T>.asEagerState(initialValue: T) = distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = initialValue
    )

    /**
     * Runs on every keystroke over the whole library, so each song is normalized once and the ranking is decided
     * before sorting - a comparator's selector runs on every comparison, not once per song.
     */
    private fun List<Song>.filterAndRank(query: String): List<Song> {
        val normalizedQuery = normalizeText(query)
        return mapNotNull { song ->
            val title = normalizeText(song.title)
            val artist = normalizeText(song.artist)
            // Both sides are already lower case, so these don't have to pay for a case insensitive comparison.
            if (title.contains(normalizedQuery) || artist.contains(normalizedQuery)) {
                MatchingSong(
                    song = song,
                    doesTitleStartWithQuery = title.startsWith(normalizedQuery),
                    doesArtistStartWithQuery = artist.startsWith(normalizedQuery)
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
     * rather than the headers keeps this to one normalization per song.
     */
    private fun List<Song>.groupIntoSections(sortingMode: UserPreferences.SortingMode): List<SongGroup> {
        val groups = mutableListOf<Pair<SongGroup.Header, MutableList<Song>>>()
        var lastKey: String? = null
        forEach { song ->
            val key = when (sortingMode) {
                UserPreferences.SortingMode.BY_ARTIST -> normalizeText(song.artist)
                UserPreferences.SortingMode.BY_TITLE -> song.title.initialLetter()?.toString().orEmpty()
            }
            val lastGroup = groups.lastOrNull()
            if (lastGroup != null && key == lastKey) {
                lastGroup.second += song
            } else {
                val header = when (sortingMode) {
                    UserPreferences.SortingMode.BY_ARTIST -> SongGroup.Header.Artist(name = song.artist, initial = song.artist.initialLetter())
                    UserPreferences.SortingMode.BY_TITLE -> key.firstOrNull()?.let { SongGroup.Header.Letter(it) } ?: SongGroup.Header.Symbols
                }
                groups += header to mutableListOf(song)
                lastKey = key
            }
        }
        return groups.map { (header, songs) -> SongGroup(header, songs) }
    }

    /** The upper case, accent-free first character of the text if it is a letter. */
    private fun String.initialLetter() = normalizeText(take(1)).firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()

    /** What a list without content has in its place. */
    enum class Placeholder {
        LOADING,
        ERROR,
        NO_SONGS,
        NO_SEARCH_RESULTS
    }

    /** A library with no songs in it is only an error once the load that would have filled it has actually failed. */
    private val DataState<ScreenData>.emptyLibraryPlaceholder
        get() = when (this) {
            is DataState.Loading -> Placeholder.LOADING
            is DataState.Failure -> Placeholder.ERROR
            is DataState.Idle -> Placeholder.NO_SONGS
        }

    private class MatchingSong(
        val song: Song,
        val doesTitleStartWithQuery: Boolean,
        val doesArtistStartWithQuery: Boolean
    )

    data class SongGroup(
        val header: Header?,
        val songs: List<Song>
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
        private val bySetlist: Map<String, Map<String, Int>> = emptyMap()
    ) {

        operator fun get(songFileName: String, setlistFileName: String?): Int = if (setlistFileName == null) {
            library[songFileName] ?: 0
        } else {
            bySetlist[setlistFileName]?.get(songFileName) ?: 0
        }
    }

    data class SetlistWithSongs(
        val setlist: Setlist,
        val songs: List<Song>
    )

    sealed interface DialogType {
        data object NewSetlist : DialogType
        data object SongsControls : DialogType
        data object SetlistsControls : DialogType
        data class SetlistPicker(val songFileName: String, val currentSetlistFileName: String?) : DialogType
        data class SongDisplayControls(val songFileName: String, val setlistFileName: String?) : DialogType
        data class DeleteSetlist(val setlist: Setlist) : DialogType
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
