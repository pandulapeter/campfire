package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.TranspositionKey
import kotlinx.coroutines.flow.Flow

interface TranspositionRepository {

    val transpositions: Flow<DataState<Map<TranspositionKey, Int>>>

    suspend fun loadTranspositionsIfNeeded(): Map<TranspositionKey, Int>

    suspend fun saveTranspositions(transpositions: Map<TranspositionKey, Int>)
}
