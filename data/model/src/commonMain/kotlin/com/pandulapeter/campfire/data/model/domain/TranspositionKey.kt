package com.pandulapeter.campfire.data.model.domain

data class TranspositionKey(
    val songId: String,
    val setlistId: String? // Null when the song is opened from the main song list.
)
