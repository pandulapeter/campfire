package com.pandulapeter.campfire.data.source.local.implementationDesktop.source

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.SongEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.StorageManager
import io.realm.kotlin.ext.query

internal class SongLocalSourceImpl(
    private val storageManager: StorageManager
) : SongLocalSource {

    override suspend fun loadSongs(databaseUrl: String) =
        storageManager.database.query<SongEntity>().find().toList().map { it.toModel() }

    override suspend fun saveSongs(databaseUrl: String, songs: List<Song>) = with(storageManager.database) {
        write { delete(query<SongEntity>().find()) }
        writeBlocking { songs.forEach { copyToRealm(it.toEntity(databaseUrl)) } }
    }
}