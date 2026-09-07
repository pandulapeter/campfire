package com.pandulapeter.campfire.data.source.local.implementation.model

import kotlinx.serialization.Serializable

@Serializable
internal data class TranspositionEntity(
    val songId: String,
    val setlistId: String?, // Null when the song is opened from the main song list.
    val transposition: Int
)
