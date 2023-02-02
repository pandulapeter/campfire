package com.pandulapeter.campfire.data.source.local.implementationDesktop.source

import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.source.local.api.RawSongDetailsLocalSource
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.RawSongDetailsEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.StorageManager
import io.realm.kotlin.ext.query

internal class RawSongDetailsLocalSourceImpl(
    private val storageManager: StorageManager
) : RawSongDetailsLocalSource {

    override suspend fun loadRawSongDetails() = storageManager.database.query<RawSongDetailsEntity>().find().toList().map { it.toModel() }

    override suspend fun saveRawSongDetails(rawSongDetails: RawSongDetails) {
        with(storageManager.database) {
            write { delete(query<RawSongDetailsEntity>("url == $0", rawSongDetails.url).find()) }
            writeBlocking { copyToRealm(rawSongDetails.toEntity()) }
        }
    }
}