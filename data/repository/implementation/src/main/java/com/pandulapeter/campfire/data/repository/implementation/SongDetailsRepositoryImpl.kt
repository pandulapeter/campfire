package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongDetails
import com.pandulapeter.campfire.data.repository.api.SongDetailsRepository
import com.pandulapeter.campfire.data.source.remote.api.SongDetailsRemoteSource
import kotlinx.coroutines.flow.MutableStateFlow

internal class SongDetailsRepositoryImpl(
    private val songDetailsRemoteSource: SongDetailsRemoteSource
) : SongDetailsRepository {

    private val _songDetails = MutableStateFlow<DataState<SongDetails>>(DataState.Failure(null))
    override val songDetails = _songDetails

    override suspend fun loadSongDetails(song: Song?, isForceRefresh: Boolean) {
        if (song == null) {
            _songDetails.value = DataState.Failure(null)
        } else {
            val currentSong = _songDetails.value.data
            _songDetails.value = DataState.Loading(if (currentSong?.song?.id == song.id) currentSong else null)
            try {
                _songDetails.value = DataState.Idle(
                    SongDetails(
                        song = song,
                        rawData = songDetailsRemoteSource.loadSongDetails(song.url)
                    )
                )
            } catch (exception: Exception) {
                println(exception.message)
                _songDetails.value = DataState.Failure(_songDetails.value.data)
            }
        }
    }
}