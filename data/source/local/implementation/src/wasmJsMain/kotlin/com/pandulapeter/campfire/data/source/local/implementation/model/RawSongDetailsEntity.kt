package com.pandulapeter.campfire.data.source.local.implementation.model

import kotlinx.serialization.Serializable

@Serializable
internal data class RawSongDetailsEntity(
    val url: String,
    val rawData: String
)
