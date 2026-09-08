package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.source.local.api.RawSongDetailsLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.storage.StorageManager

internal class RawSongDetailsLocalSourceImpl(
    private val storageManager: StorageManager
) : RawSongDetailsLocalSource {

    override suspend fun loadDownloadedSongUrls() = storageManager.loadDownloadedSongUrls()

    override suspend fun loadRawSongDetails(url: String) = storageManager.loadRawSongDetails(url)?.toModel()

    // Each song has a key of its own, like the url primary key of the Room table, so this replaces just that one song.
    override suspend fun saveRawSongDetails(rawSongDetails: RawSongDetails) = storageManager.saveRawSongDetails(rawSongDetails.toEntity())
}
