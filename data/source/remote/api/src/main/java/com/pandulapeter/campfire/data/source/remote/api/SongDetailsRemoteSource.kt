package com.pandulapeter.campfire.data.source.remote.api

interface SongDetailsRemoteSource {

    suspend fun loadSongDetails(url: String): String
}