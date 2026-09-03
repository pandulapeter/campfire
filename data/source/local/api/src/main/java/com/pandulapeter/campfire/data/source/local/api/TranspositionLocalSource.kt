package com.pandulapeter.campfire.data.source.local.api

interface TranspositionLocalSource {

    suspend fun loadTranspositions(): Map<String, Int>

    suspend fun saveTranspositions(transpositions: Map<String, Int>)
}
