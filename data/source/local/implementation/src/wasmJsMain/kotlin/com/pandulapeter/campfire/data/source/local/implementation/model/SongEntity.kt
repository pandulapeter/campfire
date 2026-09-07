package com.pandulapeter.campfire.data.source.local.implementation.model

import kotlinx.serialization.Serializable

@Serializable
internal data class SongEntity(
    val id: String,
    val url: String,
    val title: String,
    val artist: String,
    val key: String,
    val hasChords: Boolean,
    val databaseUrl: String
)
