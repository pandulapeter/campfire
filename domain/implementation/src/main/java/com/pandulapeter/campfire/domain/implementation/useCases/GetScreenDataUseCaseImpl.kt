package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.data.repository.api.RawSongDetailsRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class GetScreenDataUseCaseImpl internal constructor(
    private val normalizeText: NormalizeTextUseCase,
    databaseRepository: DatabaseRepository,
    setlistRepository: SetlistRepository,
    songRepository: SongRepository,
    rawSongDetailsRepository: RawSongDetailsRepository,
    userPreferencesRepository: UserPreferencesRepository
) : GetScreenDataUseCase {

    override operator fun invoke() = screenDataFlow

    private var cache: ScreenData? = null
    private val screenDataFlow = combine(
        databaseRepository.databases,
        setlistRepository.setlists,
        songRepository.songs,
        rawSongDetailsRepository.rawSongDetails,
        userPreferencesRepository.userPreferences
    ) { databasesDataState,
        setlistsDataState,
        songsDataState,
        rawSongDetailsDataState,
        userPreferencesDataState ->

        fun createScreenData() = databasesDataState.data?.sortedBy { it.priority }?.let { databases ->
            setlistsDataState.data?.sortedBy { it.priority }?.let { setlists ->
                songsDataState.data?.let { songs ->
                    rawSongDetailsDataState.data?.let { rawSongDetails ->
                        userPreferencesDataState.data?.let { userPreferences ->
                            val filteredDatabases = databases.filter { it.isEnabled }.filter { !userPreferences.unselectedDatabaseUrls.contains(it.url) }
                            ScreenData(
                                databases = databases,
                                setlists = setlists,
                                songs = filteredDatabases.flatMap { songs[it.url].orEmpty() }
                                    .distinctBy { it.id }
                                    .filter { it.isPublic }
                                    .filterExplicit(userPreferences)
                                    .filterHasChords(userPreferences)
                                    .sort(userPreferences),
                                rawSongDetails = rawSongDetails,
                                userPreferences = userPreferences
                            ).also {
                                cache = it
                            }
                        }
                    }
                }
            }
        }

        val dataStates = arrayOf(
            databasesDataState,
            setlistsDataState,
            songsDataState,
            rawSongDetailsDataState,
            userPreferencesDataState
        )
        if (dataStates.any { it is DataState.Failure }) {
            DataState.Failure(createScreenData() ?: cache)
        } else if (dataStates.any { it is DataState.Loading }) {
            DataState.Loading(createScreenData() ?: cache)
        } else {
            DataState.Idle(createScreenData() ?: cache ?: throw IllegalStateException("No data available while all data states are idle."))
        }
    }.distinctUntilChanged()

    private fun List<Song>.filterExplicit(userPreferences: UserPreferences) = if (userPreferences.shouldShowExplicitSongs) this else filterNot { it.isExplicit }

    private fun List<Song>.filterHasChords(userPreferences: UserPreferences) = if (userPreferences.shouldShowSongsWithoutChords) this else filterNot { !it.hasChords }

    private fun List<Song>.sort(userPreferences: UserPreferences) = when (userPreferences.sortingMode) {
        UserPreferences.SortingMode.BY_ARTIST -> sortedBy { normalizeText(it.title) }.sortedBy { normalizeText(it.artist) }
        UserPreferences.SortingMode.BY_TITLE -> sortedBy { normalizeText(it.artist) }.sortedBy { normalizeText(it.title) }
    }
}