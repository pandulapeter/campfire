package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalDataRepository
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource

internal class SongRepositoryImpl(
    songLocalSource: SongLocalSource
) : BaseLocalDataRepository<List<Song>>(
    loadDataFromLocalSource = songLocalSource::loadSongs,
    saveDataToLocalSource = songLocalSource::saveSongs
), SongRepository {

    override val songs = dataState

    override suspend fun loadSongsIfNeeded() = loadDataIfNeeded()
}
