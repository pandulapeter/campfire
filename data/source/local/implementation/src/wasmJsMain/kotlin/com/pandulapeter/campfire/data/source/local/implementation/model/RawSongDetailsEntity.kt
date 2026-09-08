package com.pandulapeter.campfire.data.source.local.implementation.model

import kotlinx.serialization.Serializable

@Serializable
internal data class RawSongDetailsEntity(
    val url: String,
    val rawData: String,
    // Missing from the documents written before this field existed. Those are dated to the epoch rather than
    // discarded, so the first time each of them is opened it gets refreshed - the Room migration does the same.
    val refreshTimestamp: Long = 0
)
