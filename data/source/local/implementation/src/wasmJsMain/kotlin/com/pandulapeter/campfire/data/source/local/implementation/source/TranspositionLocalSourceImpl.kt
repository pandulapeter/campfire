package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.TranspositionKey
import com.pandulapeter.campfire.data.source.local.api.TranspositionLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toEntities
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.storage.StorageManager

internal class TranspositionLocalSourceImpl(
    private val storageManager: StorageManager
) : TranspositionLocalSource {

    override suspend fun loadTranspositions() = storageManager.loadTranspositions().toModel()

    override suspend fun saveTranspositions(transpositions: Map<TranspositionKey, Int>) = storageManager.saveTranspositions(transpositions.toEntities())
}
