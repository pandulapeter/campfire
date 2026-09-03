package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.domain.TranspositionKey

interface TranspositionLocalSource {

    suspend fun loadTranspositions(): Map<TranspositionKey, Int>

    suspend fun saveTranspositions(transpositions: Map<TranspositionKey, Int>)
}
