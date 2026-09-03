package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.TranspositionKey
import com.pandulapeter.campfire.data.source.local.api.TranspositionLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toEntities
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.TranspositionDao

internal class TranspositionLocalSourceImpl(
    private val transpositionDao: TranspositionDao
) : TranspositionLocalSource {

    override suspend fun loadTranspositions() = transpositionDao.getAll().toModel()

    override suspend fun saveTranspositions(transpositions: Map<TranspositionKey, Int>) = transpositionDao.updateAll(transpositions.toEntities())
}
