package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.TranspositionRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class GetScreenDataUseCaseImpl internal constructor(
    private val normalizeText: NormalizeTextUseCase,
    setlistRepository: SetlistRepository,
    songRepository: SongRepository,
    userPreferencesRepository: UserPreferencesRepository,
    transpositionRepository: TranspositionRepository
) : GetScreenDataUseCase {

    override operator fun invoke() = screenDataFlow

    private var cache: ScreenData? = null
    private val screenDataFlow = combine(
        setlistRepository.setlists,
        songRepository.songs,
        userPreferencesRepository.userPreferences,
        transpositionRepository.transpositions
    ) { setlistsDataState, songsDataState, userPreferencesDataState, transpositionsDataState ->

        fun createScreenData() = setlistsDataState.data?.sortedByDescending { it.priority }?.let { setlists ->
            songsDataState.data?.let { songs ->
                userPreferencesDataState.data?.let { userPreferences ->
                    transpositionsDataState.data?.let { transpositions ->
                        ScreenData(
                            setlists = setlists,
                            songs = songs
                                .filterHasChords(userPreferences)
                                .sort(userPreferences),
                            userPreferences = userPreferences,
                            transpositions = transpositions
                        ).also {
                            cache = it
                        }
                    }
                }
            }
        }

        val dataStates = arrayOf(
            setlistsDataState,
            songsDataState,
            userPreferencesDataState,
            transpositionsDataState
        )
        if (dataStates.any { it is DataState.Failure }) {
            DataState.Failure(createScreenData() ?: cache)
        } else if (dataStates.any { it is DataState.Loading }) {
            DataState.Loading(createScreenData() ?: cache)
        } else {
            DataState.Idle(createScreenData() ?: cache ?: throw IllegalStateException("No data available while all data states are idle."))
        }
    }.distinctUntilChanged()

    private fun List<Song>.filterHasChords(userPreferences: UserPreferences) = if (userPreferences.shouldShowSongsWithoutChords) this else filter { it.hasChords }

    /**
     * The selector of a comparator runs on every comparison, so sorting this way used to normalize each title and
     * artist a logarithmic number of times over. The keys are computed once per song here instead.
     */
    private fun List<Song>.sort(userPreferences: UserPreferences): List<Song> {
        val comparator = when (userPreferences.sortingMode) {
            UserPreferences.SortingMode.BY_ARTIST -> compareBy<SortableSong>({ it.artist }, { it.title })
            UserPreferences.SortingMode.BY_TITLE -> compareBy<SortableSong>({ it.title }, { it.artist })
        }
        return map { SortableSong(song = it, artist = normalizeText(it.artist), title = normalizeText(it.title)) }
            .sortedWith(comparator)
            .map { it.song }
    }

    private class SortableSong(
        val song: Song,
        val artist: String,
        val title: String
    )
}
