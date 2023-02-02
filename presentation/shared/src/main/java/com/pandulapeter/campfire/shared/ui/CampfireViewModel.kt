package com.pandulapeter.campfire.shared.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongDetailsUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadSongDetailsUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveDatabasesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveUserPreferencesUseCase
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireIcons
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class CampfireViewModel(
    getScreenData: GetScreenDataUseCase,
    getSongDetails: GetSongDetailsUseCase,
    private val loadScreenData: LoadScreenDataUseCase,
    private val loadSongDetails: LoadSongDetailsUseCase,
    private val saveDatabases: SaveDatabasesUseCase,
    private val saveUserPreferences: SaveUserPreferencesUseCase,
    private val normalizeText: NormalizeTextUseCase
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    val songs = combine(
        getScreenData().map { it.data?.songs.orEmpty() },
        query
    ) { songs, query ->
        if (query.isBlank()) {
            songs
        } else {
            val normalizedQuery = normalizeText(query)
            songs
                .filter { normalizeText(it.title).contains(normalizedQuery, true) || normalizeText(it.artist).contains(normalizedQuery, true) }
                .sortedByDescending { it.artist.startsWith(normalizedQuery, true) }
                .sortedByDescending { it.title.startsWith(normalizedQuery, true) }
        }
    }.distinctUntilChanged()
    val songDetails = getSongDetails().map { it.data }.distinctUntilChanged()
    val databases = getScreenData().map { it.data?.databases.orEmpty() }.distinctUntilChanged()
    val userPreferences = getScreenData().map { it.data?.userPreferences }.distinctUntilChanged()
    val uiMode = userPreferences.map { it?.uiMode }
    val shouldShowLoadingIndicator = getScreenData().map { it is DataState.Loading }.distinctUntilChanged()
    private val _selectedNavigationDestination = MutableStateFlow(NavigationDestination.SONGS)
    val selectedNavigationDestination: Flow<NavigationDestination> = _selectedNavigationDestination
    val navigationDestinations = selectedNavigationDestination.map { selectedNavigationDestination ->
        NavigationDestination.values().map { navigationDestination ->
            NavigationDestinationWrapper(
                destination = navigationDestination,
                isSelected = navigationDestination == selectedNavigationDestination
            )
        }
    }
    private val _selectedSong = MutableStateFlow<Song?>(null)
    val selectedSong: Flow<Song?> = _selectedSong

    suspend fun onInitialize() = loadScreenData(false)

    suspend fun onDatabaseEnabledChanged(databases: List<Database>, database: Database, isEnabled: Boolean) = saveDatabases(
        databases.map { if (it.url == database.url) database.copy(isEnabled = isEnabled) else it }
    )

    suspend fun onDatabaseSelectedChanged(userPreferences: UserPreferences, database: Database, isSelected: Boolean) = saveUserPreferences(
        userPreferences.copy(unselectedDatabaseUrls = userPreferences.unselectedDatabaseUrls.toMutableList().apply {
            if (isSelected) {
                remove(database.url)
            } else {
                add(database.url)
            }
        }.distinct())
    )

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    suspend fun onSongClicked(song: Song?) {
        _selectedSong.value = song
        if (song != null) {
            loadSongDetails(song.url, false)
        }
    }

    suspend fun onShouldShowExplicitSongsChanged(userPreferences: UserPreferences, shouldShowExplicitSongs: Boolean) = saveUserPreferences(
        userPreferences.copy(shouldShowExplicitSongs = shouldShowExplicitSongs)
    )

    suspend fun onShouldShowSongsWithoutChordsChanged(userPreferences: UserPreferences, shouldShowSongsWithoutChords: Boolean) = saveUserPreferences(
        userPreferences.copy(shouldShowSongsWithoutChords = shouldShowSongsWithoutChords)
    )

    suspend fun onSortingModeChanged(userPreferences: UserPreferences, sortingMode: UserPreferences.SortingMode) = saveUserPreferences(
        userPreferences.copy(sortingMode = sortingMode)
    )

    suspend fun onUiModeChanged(userPreferences: UserPreferences, uiMode: UserPreferences.UiMode) = saveUserPreferences(
        userPreferences.copy(uiMode = uiMode)
    )

    suspend fun onLanguageChanged(userPreferences: UserPreferences, language: UserPreferences.Language) = saveUserPreferences(
        userPreferences.copy(language = language)
    )

    suspend fun onForceRefreshTriggered() = loadScreenData(true)

    fun onNavigationDestinationSelected(navigationDestination: NavigationDestination) {
        _selectedNavigationDestination.value = navigationDestination
    }

    data class NavigationDestinationWrapper(
        val destination: NavigationDestination,
        val isSelected: Boolean
    )

    enum class NavigationDestination(
        val icon: ImageVector
    ) {
        SONGS(CampfireIcons.songs),
        SETLISTS(CampfireIcons.setlists),
        SETTINGS(CampfireIcons.settings)
    }
}