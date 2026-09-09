package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Setlist
import kotlinx.coroutines.flow.Flow

interface SetlistRepository {

    val setlists: Flow<DataState<List<Setlist>>>

    /** The saved setlists, or null if they could not be read. */
    suspend fun loadSetlistsIfNeeded(): List<Setlist>?

    /** Writes a new, empty setlist under a free file name and returns it. */
    suspend fun createSetlist(title: String, priority: Int): Setlist

    /** Creates the file or overwrites it, and updates that one entry of the cached list. */
    suspend fun saveSetlist(setlist: Setlist)

    suspend fun deleteSetlist(fileName: String)
}
