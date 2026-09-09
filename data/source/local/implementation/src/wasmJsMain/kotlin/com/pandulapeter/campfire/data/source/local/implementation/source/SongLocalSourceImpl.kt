package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.storage.StorageManager

internal class SongLocalSourceImpl(
    private val storageManager: StorageManager
) : SongLocalSource {

    override suspend fun loadSongs() = storageManager.loadSongs().map { it.toModel() }

    override suspend fun saveSongs(songs: List<Song>) = storageManager.saveSongs(songs.map { it.toEntity() })
}
