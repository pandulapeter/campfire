package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.source.local.api.RawSongDetailsLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.storage.StorageManager

internal class RawSongDetailsLocalSourceImpl(
    private val storageManager: StorageManager
) : RawSongDetailsLocalSource {

    override suspend fun loadRawSongDetails() = storageManager.loadRawSongDetails().map { it.toModel() }

    // The url is the primary key of the Room table, so the entity with a matching one is replaced rather than appended.
    override suspend fun saveRawSongDetails(rawSongDetails: RawSongDetails) = rawSongDetails.toEntity().let { entity ->
        storageManager.saveRawSongDetails(storageManager.loadRawSongDetails().filterNot { it.url == entity.url } + entity)
    }
}
