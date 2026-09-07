package com.pandulapeter.campfire.data.source.remote.implementation.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SongResponse(
    @SerialName(KEY_ID) val id: String? = null,
    @SerialName(KEY_URL) val url: String? = null,
    @SerialName(KEY_TITLE) val title: String? = null,
    @SerialName(KEY_ARTIST) val artist: String? = null,
    @SerialName(KEY_KEY) val key: String? = null,
    @SerialName(KEY_HAS_CHORDS) val hasChords: Boolean? = null
) {
    companion object {
        const val SHEET_NAME = "songs"
        const val KEY_ID = "id"
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_KEY = "key"
        const val KEY_HAS_CHORDS = "has_chords"
    }
}
