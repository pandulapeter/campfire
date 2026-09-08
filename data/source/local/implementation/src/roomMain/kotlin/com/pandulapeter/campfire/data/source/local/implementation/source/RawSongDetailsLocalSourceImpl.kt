package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.source.local.api.RawSongDetailsLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.RawSongDetailsDao

internal class RawSongDetailsLocalSourceImpl(
    private val rawSongDetailsDao: RawSongDetailsDao
) : RawSongDetailsLocalSource {

    override suspend fun loadDownloadedSongUrls() = rawSongDetailsDao.getAllUrls().toSet()

    override suspend fun loadRawSongDetails(url: String) = rawSongDetailsDao.get(url)?.toModel()

    override suspend fun saveRawSongDetails(rawSongDetails: RawSongDetails) = rawSongDetailsDao.insert(rawSongDetails.toEntity())
}
