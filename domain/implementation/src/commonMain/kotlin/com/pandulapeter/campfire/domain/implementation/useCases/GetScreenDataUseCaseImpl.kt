package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.data.repository.api.RawSongDetailsRepository
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
    databaseRepository: DatabaseRepository,
    setlistRepository: SetlistRepository,
    songRepository: SongRepository,
    rawSongDetailsRepository: RawSongDetailsRepository,
    userPreferencesRepository: UserPreferencesRepository,
    transpositionRepository: TranspositionRepository
) : GetScreenDataUseCase {

    override operator fun invoke() = screenDataFlow

    private var cache: ScreenData? = null
    private val screenDataFlow = combine(
        combine(
            databaseRepository.databases,
            setlistRepository.setlists,
            songRepository.songs,
            ::Triple
        ),
        combine(
            rawSongDetailsRepository.downloadedSongUrls,
            userPreferencesRepository.userPreferences,
            transpositionRepository.transpositions,
            ::Triple
        )
    ) { (databasesDataState, setlistsDataState, songsDataState),
        (downloadedSongUrlsDataState, userPreferencesDataState, transpositionsDataState) ->

        fun createScreenData() = databasesDataState.data?.let { databases ->
            setlistsDataState.data?.sortedByDescending { it.priority }?.let { setlists ->
                songsDataState.data?.let { songs ->
                    downloadedSongUrlsDataState.data?.let { downloadedSongUrls ->
                        userPreferencesDataState.data?.let { userPreferences ->
                            transpositionsDataState.data?.let { transpositions ->
                                val selectedDatabases = databases
                                    .filter { it.isEnabled && it.url !in userPreferences.unselectedDatabaseUrls }
                                    .sortedBy { it.priority }
                                ScreenData(
                                    setlists = setlists,
                                    songs = selectedDatabases.flatMap { songs[it.url].orEmpty() }
                                        .distinctBy { it.id }
                                        .filterDownloaded(userPreferences, downloadedSongUrls)
                                        .filterHasChords(userPreferences)
                                        .sort(userPreferences),
                                    userPreferences = userPreferences,
                                    downloadedSongUrls = downloadedSongUrls,
                                    transpositions = transpositions
                                ).also {
                                    cache = it
                                }
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
            downloadedSongUrlsDataState,
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

    private fun List<Song>.filterDownloaded(
        userPreferences: UserPreferences,
        downloadedSongUrls: Set<String>
    ) = if (userPreferences.showOnlyDownloadedSongs) filter { it.url in downloadedSongUrls } else this

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
