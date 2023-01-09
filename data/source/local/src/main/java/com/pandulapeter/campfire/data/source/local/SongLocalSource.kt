package com.pandulapeter.campfire.data.source.local

import com.pandulapeter.campfire.data.model.domain.Song

interface SongLocalSource {

    suspend fun getSongs(): List<Song>

    suspend fun saveSongs(songs: List<Song>)
}