package com.pandulapeter.campfire.data.source.local.implementationDesktop.source

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.SetlistEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.StorageManager
import io.realm.kotlin.ext.query

internal class SetlistLocalSourceImpl(
    private val storageManager: StorageManager
) : SetlistLocalSource {

    override suspend fun loadSetlists() =
        storageManager.database.query<SetlistEntity>().find().toList().map { it.toModel() }

    override suspend fun saveSetlists(setlists: List<Setlist>) = with(storageManager.database) {
        write { delete(query<SetlistEntity>().find()) }
        writeBlocking { setlists.forEach { copyToRealm(it.toEntity()) } }
    }
}