package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.domain.Song

interface SongLocalSource {

    suspend fun loadSongs(databaseUrl: String): List<Song>

    suspend fun saveSongs(databaseUrl: String, songs: List<Song>)

    suspend fun deleteAllSongs()
}