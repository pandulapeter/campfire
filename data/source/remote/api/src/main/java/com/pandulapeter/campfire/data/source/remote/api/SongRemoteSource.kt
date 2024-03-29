package com.pandulapeter.campfire.data.source.remote.api

import com.pandulapeter.campfire.data.model.domain.Song

interface SongRemoteSource {

    suspend fun loadSongs(databaseUrl: String): List<Song>
}