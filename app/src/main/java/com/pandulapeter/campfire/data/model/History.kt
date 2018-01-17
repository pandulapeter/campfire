package com.pandulapeter.campfire.data.model

/**
 * Connects a [SongInfo] object ID to the timestamp the user the last time opened it.
 */
data class History(
    val songId: String,
    val timestamp: Long
)