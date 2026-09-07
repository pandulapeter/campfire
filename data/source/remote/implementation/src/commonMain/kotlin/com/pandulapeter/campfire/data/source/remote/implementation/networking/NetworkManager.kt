package com.pandulapeter.campfire.data.source.remote.implementation.networking

import com.pandulapeter.campfire.data.source.remote.implementation.model.SongResponse

/**
 * Reads the song list of a Google Sheets database.
 *
 * Every platform but the web goes through Retrosheet; the wasmJs implementation calls the same CSV endpoint directly,
 * because Retrosheet's Ktorfit converter makes the Kotlin/Wasm compiler emit a binary the browser refuses to load.
 */
internal interface NetworkManager {

    suspend fun loadSongs(databaseUrl: String): List<SongResponse>
}
