package com.pandulapeter.campfire.data.source.local.implementationAndroid.source

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.SetlistDao

internal class SetlistLocalSourceImpl(
    private val setlistDao: SetlistDao
) : SetlistLocalSource {

    override suspend fun loadSetlists() = setlistDao.getAll().map { it.toModel() }

    override suspend fun saveSetlists(setlists: List<Setlist>) = setlistDao.updateAll(setlists.map { it.toEntity() })
}