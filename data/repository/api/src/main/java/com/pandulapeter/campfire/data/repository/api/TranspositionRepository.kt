package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import kotlinx.coroutines.flow.Flow

interface TranspositionRepository {

    val transpositions: Flow<DataState<Map<String, Int>>>

    suspend fun loadTranspositionsIfNeeded(): Map<String, Int>

    suspend fun saveTranspositions(transpositions: Map<String, Int>)
}
