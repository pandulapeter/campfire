package com.pandulapeter.campfire.data.source.local.implementationDesktop.source

import com.pandulapeter.campfire.data.model.domain.Playlist
import com.pandulapeter.campfire.data.source.local.api.PlaylistLocalSource
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.DatabaseEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.PlaylistEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.StorageManager
import io.realm.kotlin.ext.query

internal class PlaylistLocalSourceImpl(
    private val storageManager: StorageManager
) : PlaylistLocalSource {

    override suspend fun loadPlaylists() =
        storageManager.database.query<PlaylistEntity>().find().toList().map { it.toModel() }

    override suspend fun savePlaylists(playlists: List<Playlist>) = with(storageManager.database) {
        write { delete(query<DatabaseEntity>().find()) }
        writeBlocking { playlists.forEach { copyToRealm(it.toEntity()) } }
    }
}