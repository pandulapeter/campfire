package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.storage.StorageManager

internal class SongLocalSourceImpl(
    private val storageManager: StorageManager
) : SongLocalSource {

    override suspend fun loadSongs(databaseUrl: String) = storageManager.loadSongs().filter { it.databaseUrl == databaseUrl }.map { it.toModel() }

    // Songs of every database share one document, so only the rows of the given one are replaced.
    override suspend fun saveSongs(databaseUrl: String, songs: List<Song>) = storageManager.saveSongs(
        storageManager.loadSongs().filterNot { it.databaseUrl == databaseUrl } + songs.map { it.toEntity(databaseUrl) }
    )

    override suspend fun deleteAllSongs() = storageManager.saveSongs(emptyList())
}
