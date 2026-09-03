package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.TranspositionKey
import com.pandulapeter.campfire.data.repository.api.TranspositionRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalDataRepository
import com.pandulapeter.campfire.data.source.local.api.TranspositionLocalSource

internal class TranspositionRepositoryImpl(
    transpositionLocalSource: TranspositionLocalSource
) : BaseLocalDataRepository<Map<TranspositionKey, Int>>(
    loadDataFromLocalSource = transpositionLocalSource::loadTranspositions,
    saveDataToLocalSource = transpositionLocalSource::saveTranspositions
), TranspositionRepository {

    override val transpositions = dataState

    override suspend fun loadTranspositionsIfNeeded() = loadDataIfNeeded()

    override suspend fun saveTranspositions(transpositions: Map<TranspositionKey, Int>) = saveData(transpositions)
}
