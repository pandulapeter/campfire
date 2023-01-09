package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.source.local.SongLocalSource
import com.pandulapeter.campfire.data.source.remote.api.SongRemoteSource

internal class SongRepositoryImpl(
    songLocalSource: SongLocalSource,
    songRemoteSource: SongRemoteSource
) : BaseRepository<List<Song>>(
    getDataFromLocalSource = songLocalSource::getSongs,
    getDataFromRemoteSource = songRemoteSource::getSongs,
    saveDataToLocalSource = songLocalSource::saveSongs,
), SongRepository {

    override fun isDataValid(data: List<Song>) = data.isNotEmpty()

    override fun areSongsAvailable() = isDataAvailable()

    override suspend fun getSongs(isForceRefresh: Boolean) = getData(
        isForceRefresh = isForceRefresh
    )
}