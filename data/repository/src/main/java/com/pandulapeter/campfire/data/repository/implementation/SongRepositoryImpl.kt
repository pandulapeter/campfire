package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalRemoteDataRepository
import com.pandulapeter.campfire.data.source.local.SongLocalSource
import com.pandulapeter.campfire.data.source.remote.api.SongRemoteSource

internal class SongRepositoryImpl(
    songLocalSource: SongLocalSource,
    songRemoteSource: SongRemoteSource
) : BaseLocalRemoteDataRepository<List<Song>>(
    loadDataFromLocalSource = songLocalSource::loadSongs,
    loadDataFromRemoteSource = songRemoteSource::loadSongs,
    saveDataToLocalSource = songLocalSource::saveSongs,
), SongRepository {

    override val songs = dataState

    override suspend fun loadSongs(sheetUrl: String, isForceRefresh: Boolean) = loadData(
        sheetUrl = sheetUrl,
        isForceRefresh = isForceRefresh
    )
}