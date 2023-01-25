package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Setlist
import kotlinx.coroutines.flow.Flow

interface SetlistRepository {

    val setlists: Flow<DataState<List<Setlist>>>

    suspend fun loadSetlistsIfNeeded() : List<Setlist>

    suspend fun saveSetlists(setlists: List<Setlist>)
}