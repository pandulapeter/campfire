package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.storage.StorageManager

internal class SetlistLocalSourceImpl(
    private val storageManager: StorageManager
) : SetlistLocalSource {

    override suspend fun loadSetlists() = storageManager.loadSetlists().map { it.toModel() }

    override suspend fun saveSetlists(setlists: List<Setlist>) = storageManager.saveSetlists(setlists.map { it.toEntity() })
}
