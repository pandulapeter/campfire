package com.pandulapeter.campfire.data.source.localImpl.implementation

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.source.local.SongLocalSource
import com.pandulapeter.campfire.data.source.localImpl.implementation.storage.dao.SongDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toModel

internal class SongLocalSourceImpl(
    private val songDao: SongDao
) : SongLocalSource {

    override suspend fun loadSongs(databaseUrl: String) = songDao.getAll(databaseUrl).map { it.toModel() }

    override suspend fun saveSongs(databaseUrl: String, songs: List<Song>) = songDao.updateAll(databaseUrl, songs.map { it.toEntity(databaseUrl) })
}