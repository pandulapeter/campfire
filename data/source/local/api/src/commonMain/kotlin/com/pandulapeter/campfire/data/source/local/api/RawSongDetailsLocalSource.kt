package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.domain.RawSongDetails

interface RawSongDetailsLocalSource {

    suspend fun loadRawSongDetails(url: String): RawSongDetails?

    suspend fun saveRawSongDetails(rawSongDetails: RawSongDetails)
}
