package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Setlist
import kotlinx.coroutines.flow.Flow

interface SetlistRepository {

    val setlists: Flow<DataState<List<Setlist>>>

    /** The saved setlists, or null if they could not be read. */
    suspend fun loadSetlistsIfNeeded(): List<Setlist>?

    /** Reads the setlists directory again, which is what a rescan and an import need. */
    suspend fun rescan()

    /** Writes a new, empty setlist under a free file name and returns it. */
    suspend fun createSetlist(title: String, priority: Int): Setlist

    /** Creates the file or overwrites it, and updates that one entry of the cached list. */
    suspend fun saveSetlist(setlist: Setlist)

    /** See `SetlistLocalSource.parseSetlist`. */
    suspend fun parseSetlist(document: String): Setlist?

    /** Writes an imported setlist under a free file name and returns it. Ends with a [rescan], like an imported song. */
    suspend fun importSetlist(setlist: Setlist): Setlist

    /** The stored document of one setlist, for exporting it unchanged. Null if it is missing. */
    suspend fun loadSetlistDocument(fileName: String): String?

    suspend fun deleteSetlist(fileName: String)
}
