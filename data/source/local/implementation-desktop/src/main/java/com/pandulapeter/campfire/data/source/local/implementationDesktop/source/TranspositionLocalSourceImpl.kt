package com.pandulapeter.campfire.data.source.local.implementationDesktop.source

import com.pandulapeter.campfire.data.source.local.api.TranspositionLocalSource
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toEntities
import com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.dao.TranspositionDao

internal class TranspositionLocalSourceImpl(
    private val transpositionDao: TranspositionDao
) : TranspositionLocalSource {

    override suspend fun loadTranspositions() = transpositionDao.getAll().toModel()

    override suspend fun saveTranspositions(transpositions: Map<String, Int>) = transpositionDao.updateAll(transpositions.toEntities())
}
