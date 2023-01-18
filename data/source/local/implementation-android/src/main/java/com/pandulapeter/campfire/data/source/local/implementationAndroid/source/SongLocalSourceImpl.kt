package com.pandulapeter.campfire.data.source.local.implementationAndroid.source

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.SongDao
import com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper.toModel

internal class SongLocalSourceImpl(
    private val songDao: SongDao
) : SongLocalSource {

    override suspend fun loadSongs(databaseUrl: String) = songDao.getAll(databaseUrl).map { it.toModel() }

    override suspend fun saveSongs(databaseUrl: String, songs: List<Song>) = songDao.updateAll(databaseUrl, songs.map { it.toEntity(databaseUrl) })

    override suspend fun deleteAllSongs() = songDao.deleteAll()
}