package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.domain.Song

interface SongRepository {

    fun areSongsAvailable(): Boolean

    suspend fun getSongs(isForceRefresh: Boolean): List<Song>
}