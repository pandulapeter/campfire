package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.domain.Setlist

interface SetlistLocalSource {

    /** Every `*.setlist.json` file in the library. Files that cannot be parsed are skipped, never deleted. */
    suspend fun loadSetlists(): List<Setlist>

    /**
     * Writes an empty setlist under a file name derived from the title, suffixed until it is free, and returns it.
     * File naming is the storage layer's business, so callers only supply what goes inside.
     */
    suspend fun createSetlist(title: String, priority: Int): Setlist

    suspend fun saveSetlist(setlist: Setlist)

    suspend fun deleteSetlist(fileName: String)
}
