package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.SongDao
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel

internal class SongLocalSourceImpl(
    private val songDao: SongDao
) : SongLocalSource {

    override suspend fun loadSongs() = songDao.getAll().map { it.toModel() }

    override suspend fun saveSongs(songs: List<Song>) = songDao.updateAll(songs.map { it.toEntity() })
}
