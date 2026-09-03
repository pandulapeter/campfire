package com.pandulapeter.campfire.data.source.remote.api

interface RawSongDetailsRemoteSource {

    suspend fun loadRawSongDetails(url: String): String
}