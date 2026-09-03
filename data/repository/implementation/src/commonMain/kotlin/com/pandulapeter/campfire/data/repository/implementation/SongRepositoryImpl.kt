package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalRemoteDataRepository
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.remote.api.SongRemoteSource

internal class SongRepositoryImpl(
    songLocalSource: SongLocalSource,
    songRemoteSource: SongRemoteSource
) : BaseLocalRemoteDataRepository<Song>(
    loadDataFromLocalSource = songLocalSource::loadSongs,
    loadDataFromRemoteSource = songRemoteSource::loadSongs,
    saveDataToLocalSource = songLocalSource::saveSongs,
    deleteDataFromLocalSource = songLocalSource::deleteAllSongs
), SongRepository {

    override val songs = dataState

    override suspend fun loadSongs(databaseUrls: List<String>, isForceRefresh: Boolean) = loadData(
        databaseUrls = databaseUrls,
        isForceRefresh = isForceRefresh
    )

    override suspend fun deleteLocalSongs() = deleteLocalData()

    override fun List<Song>?.isValid() = !isNullOrEmpty()
}