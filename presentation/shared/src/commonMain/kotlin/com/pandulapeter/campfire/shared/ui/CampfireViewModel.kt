package com.pandulapeter.campfire.shared.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.TranspositionKey
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadSongDetailsUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveDatabasesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSetlistsUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveTranspositionsUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveUserPreferencesUseCase
import com.pandulapeter.campfire.domain.api.useCases.TransposeRawSongDetailsUseCase
import com.pandulapeter.campfire.shared.ui.navigation.CampfireDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CampfireViewModel(
    getScreenData: GetScreenDataUseCase,
    private val loadScreenData: LoadScreenDataUseCase,
    private val loadSongDetails: LoadSongDetailsUseCase,
    private val saveDatabases: SaveDatabasesUseCase,
    private val saveSetlists: SaveSetlistsUseCase,
    private val saveUserPreferences: SaveUserPreferencesUseCase,
    private val saveTranspositions: SaveTranspositionsUseCase,
    private val normalizeText: NormalizeTextUseCase,
    private val transposeRawSongDetails: TransposeRawSongDetailsUseCase
) : ViewModel() {

    private val screenData = getScreenData()

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
    val userPreferences = screenData.map { it.data?.userPreferences }.asState(null)
    val databases = screenData.map { it.data?.databases.orEmpty() }.asState(emptyList())
    val setlists = screenData.map { it.data?.setlists.orEmpty() }.asState(emptyList())
    val rawSongDetails = screenData.map { it.data?.rawSongDetails.orEmpty() }.asState(emptyMap())
    val transpositions = screenData.map { it.data?.transpositions.orEmpty() }.asState(emptyMap())
    val allSongs = screenData.map { it.data?.songs.orEmpty() }.asState(emptyList())
    val songGroups = combine(allSongs, query, userPreferences.map { it?.sortingMode }) { songs, query, sortingMode ->
        if (query.isBlank()) {
            songs.groupIntoSections(sortingMode ?: UserPreferences.SortingMode.BY_ARTIST)
        } else {
            listOf(SongGroup(header = null, songs = songs.filterAndRank(query)))
        }
    }.asState(emptyList())
    val setlistsWithSongs = combine(setlists, allSongs) { setlists, songs ->
        val songsById = songs.associateBy { it.id }
        setlists.map { setlist -> SetlistWithSongs(setlist = setlist, songs = setlist.songIds.mapNotNull { songsById[it] }) }
    }.asState(emptyList())

    // Dialogs
    private val _visibleDialog = MutableStateFlow<DialogType?>(null)
    val visibleDialog: StateFlow<DialogType?> = _visibleDialog.asStateFlow()

    init {
        viewModelScope.launch { loadScreenData(false) }
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
        CampfireDestination.SongDetails(songIds = listOf(song.id), setlistId = null, initialIndex = 0)
    )

    fun openSongInSetlist(setlistWithSongs: SetlistWithSongs, index: Int) = openSongDetails(
        CampfireDestination.SongDetails(
            songIds = setlistWithSongs.songs.map { it.id },
            setlistId = setlistWithSongs.setlist.id,
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

    fun refresh() = viewModelScope.launch { loadScreenData(true) }

    fun loadSongDetails(song: Song) = viewModelScope.launch { loadSongDetails(song.url, false) }

    fun transpose(rawData: String, transposition: Int) = transposeRawSongDetails(rawData, transposition)

    fun setTransposition(songId: String, setlistId: String?, transposition: Int) = viewModelScope.launch {
        saveTranspositions(
            transpositions.value.toMutableMap().apply {
                val key = TranspositionKey(songId = songId, setlistId = setlistId)
                val clampedTransposition = transposition.coerceIn(MIN_TRANSPOSITION, MAX_TRANSPOSITION)
                if (clampedTransposition == 0) remove(key) else put(key, clampedTransposition)
            }
        )
    }

    // Setlists

    fun createSetlist(title: String) = viewModelScope.launch {
        val currentSetlists = setlists.value
        saveSetlists(
            listOf(
                Setlist(
                    id = Uuid.random().toString(),
                    title = title.trim(),
                    songIds = emptyList(),
                    priority = currentSetlists.size
                )
            ) + currentSetlists
        )
    }

    fun addSongToSetlist(songId: String, setlistId: String) = viewModelScope.launch {
        saveSetlists(
            setlists.value.map { setlist ->
                if (setlist.id == setlistId) setlist.copy(songIds = (listOf(songId) + setlist.songIds).distinct()) else setlist
            }
        )
    }

    fun deleteSetlist(setlistId: String) = viewModelScope.launch {
        val updatedSetlists = setlists.value
            .filterNot { it.id == setlistId }
            .sortedBy { it.priority }
            .mapIndexed { index, setlist -> setlist.copy(priority = index) }
        saveSetlists(updatedSetlists)
        removeOrphanedTranspositions(updatedSetlists)
    }

    fun removeSongFromSetlist(songId: String, setlistId: String) = viewModelScope.launch {
        val updatedSetlists = setlists.value.map { setlist ->
            if (setlist.id == setlistId) setlist.copy(songIds = setlist.songIds.filterNot { it == songId }) else setlist
        }
        saveSetlists(updatedSetlists)
        removeOrphanedTranspositions(updatedSetlists)
    }

    // Transpositions of the main song list are kept forever, the rest only live as long as the song stays in the setlist.
    private suspend fun removeOrphanedTranspositions(setlists: List<Setlist>) {
        val transpositions = transpositions.value
        val remainingTranspositions = transpositions.filterKeys { key ->
            key.setlistId == null || setlists.any { it.id == key.setlistId && key.songId in it.songIds }
        }
        if (remainingTranspositions.size != transpositions.size) {
            saveTranspositions(remainingTranspositions)
        }
    }

    fun moveSongInSetlist(setlistId: String, fromSongId: String, toSongId: String) = viewModelScope.launch {
        saveSetlists(
            setlists.value.map { setlist ->
                if (setlist.id == setlistId) {
                    setlist.copy(
                        songIds = setlist.songIds.toMutableList().apply {
                            val toIndex = indexOf(toSongId)
                            val fromIndex = indexOf(fromSongId)
                            if (toIndex >= 0 && fromIndex >= 0) add(toIndex, removeAt(fromIndex))
                        }
                    )
                } else {
                    setlist
                }
            }
        )
    }

    // Databases

    fun addDatabase(name: String, url: String) = viewModelScope.launch {
        val currentDatabases = databases.value
        saveDatabases(
            listOf(
                Database(
                    url = url.trim(),
                    name = name.trim(),
                    isEnabled = true,
                    priority = currentDatabases.size,
                    isAddedByUser = true
                )
            ) + currentDatabases
        )
    }

    fun setDatabaseEnabled(database: Database, isEnabled: Boolean) = viewModelScope.launch {
        saveDatabases(databases.value.map { if (it.url == database.url) it.copy(isEnabled = isEnabled) else it })
    }

    fun removeDatabase(database: Database) = viewModelScope.launch {
        saveDatabases(databases.value.filterNot { it.url == database.url })
    }

    // User preferences

    fun setDatabaseSelected(database: Database, isSelected: Boolean) = updateUserPreferences {
        copy(
            unselectedDatabaseUrls = (if (isSelected) unselectedDatabaseUrls - database.url else unselectedDatabaseUrls + database.url).distinct()
        )
    }

    fun setShouldShowExplicitSongs(value: Boolean) = updateUserPreferences { copy(shouldShowExplicitSongs = value) }

    fun setShouldShowSongsWithoutChords(value: Boolean) = updateUserPreferences { copy(shouldShowSongsWithoutChords = value) }

    fun setLyricsOnlyModeEnabled(value: Boolean) = updateUserPreferences { copy(isLyricsOnlyModeEnabled = value) }

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

    private fun List<Song>.filterAndRank(query: String): List<Song> {
        val normalizedQuery = normalizeText(query)
        return filter { normalizeText(it.title).contains(normalizedQuery, true) || normalizeText(it.artist).contains(normalizedQuery, true) }
            .sortedByDescending { normalizeText(it.artist).startsWith(normalizedQuery, true) }
            .sortedByDescending { normalizeText(it.title).startsWith(normalizedQuery, true) }
    }

    private fun List<Song>.groupIntoSections(sortingMode: UserPreferences.SortingMode): List<SongGroup> {
        val groups = mutableListOf<Pair<SongGroup.Header, MutableList<Song>>>()
        forEach { song ->
            val header = when (sortingMode) {
                UserPreferences.SortingMode.BY_ARTIST -> SongGroup.Header.Artist(name = song.artist, initial = song.artist.initialLetter())
                UserPreferences.SortingMode.BY_TITLE -> song.title.initialLetter()?.let { SongGroup.Header.Letter(it) } ?: SongGroup.Header.Symbols
            }
            val lastGroup = groups.lastOrNull()
            if (lastGroup != null && lastGroup.first.matches(header)) {
                lastGroup.second += song
            } else {
                groups += header to mutableListOf(song)
            }
        }
        return groups.map { (header, songs) -> SongGroup(header, songs) }
    }

    /** The upper case, accent-free first character of the text if it is a letter. */
    private fun String.initialLetter() = normalizeText(take(1)).firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()

    private fun SongGroup.Header.matches(other: SongGroup.Header) = when (this) {
        is SongGroup.Header.Artist -> other is SongGroup.Header.Artist && normalizeText(name) == normalizeText(other.name)
        else -> this == other
    }

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

    data class SetlistWithSongs(
        val setlist: Setlist,
        val songs: List<Song>
    )

    sealed interface DialogType {
        data object NewSetlist : DialogType
        data object NewDatabase : DialogType
        data object SongsControls : DialogType
        data object SetlistsControls : DialogType
        data class SetlistPicker(val songId: String, val currentSetlistId: String?) : DialogType
        data class DeleteSetlist(val setlist: Setlist) : DialogType
        data class DeleteDatabase(val database: Database) : DialogType
    }

    companion object {
        const val MIN_TRANSPOSITION = -11
        const val MAX_TRANSPOSITION = 11
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
