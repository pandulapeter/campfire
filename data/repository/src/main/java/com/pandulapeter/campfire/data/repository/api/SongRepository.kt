package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.domain.Song

interface SongRepository {

    suspend fun getSongs(sheetUrl: String, isForceRefresh: Boolean): List<Song>
}