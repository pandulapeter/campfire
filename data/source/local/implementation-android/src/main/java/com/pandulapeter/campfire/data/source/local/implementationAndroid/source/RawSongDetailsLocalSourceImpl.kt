package com.pandulapeter.campfire.data.source.local.implementationAndroid.source

import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.source.local.api.RawSongDetailsLocalSource
import com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.RawSongDetailsDao

internal class RawSongDetailsLocalSourceImpl(
    private val rawSongDetailsDao: RawSongDetailsDao
) : RawSongDetailsLocalSource {

    override suspend fun loadRawSongDetails() = rawSongDetailsDao.getAll().map { it.toModel() }

    override suspend fun saveRawSongDetails(rawSongDetails: RawSongDetails) = rawSongDetailsDao.insert(rawSongDetails.toEntity())
}