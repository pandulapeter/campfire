package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.domain.Setlist

interface SetlistLocalSource {

    suspend fun loadSetlists(): List<Setlist>

    suspend fun saveSetlists(setlists: List<Setlist>)
}