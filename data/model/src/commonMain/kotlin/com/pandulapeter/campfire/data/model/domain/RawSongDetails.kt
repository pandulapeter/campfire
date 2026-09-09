package com.pandulapeter.campfire.data.model.domain

/**
 * The text of a single song, as it was last saved.
 *
 * @param refreshTimestamp When the text was last read from the network, in milliseconds since the epoch. Songs are
 * edited after they have been published, so a saved copy is refreshed once it is older than a day - see
 * `RawSongDetailsRepository.loadRawSongDetails`.
 */
data class RawSongDetails(
    val url: String,
    val rawData: String,
    val refreshTimestamp: Long
)
