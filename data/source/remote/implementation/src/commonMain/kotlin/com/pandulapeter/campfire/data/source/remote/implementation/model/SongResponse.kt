package com.pandulapeter.campfire.data.source.remote.implementation.model

import io.github.theapache64.retrosheet.core.RetrosheetConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SongResponse(
    @SerialName(KEY_ID) val id: String? = null,
    @SerialName(KEY_URL) val url: String? = null,
    @SerialName(KEY_TITLE) val title: String? = null,
    @SerialName(KEY_ARTIST) val artist: String? = null,
    @SerialName(KEY_KEY) val key: String? = null,
    @SerialName(KEY_IS_EXPLICIT) val isExplicit: Boolean? = null,
    @SerialName(KEY_HAS_CHORDS) val hasChords: Boolean? = null,
    @SerialName(KEY_IS_PUBLIC) val isPublic: Boolean? = null
) {
    companion object {
        const val SHEET_NAME = "songs"
        private const val KEY_ID = "id"
        private const val KEY_URL = "url"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_KEY = "key"
        private const val KEY_IS_EXPLICIT = "is_explicit"
        private const val KEY_HAS_CHORDS = "has_chords"
        private const val KEY_IS_PUBLIC = "is_public"

        internal fun addSheet(configBuilder: RetrosheetConfig.Builder) = configBuilder.addSheet(
            SHEET_NAME,
            KEY_ID,
            KEY_URL,
            KEY_TITLE,
            KEY_ARTIST,
            KEY_KEY,
            KEY_IS_EXPLICIT,
            KEY_HAS_CHORDS,
            KEY_IS_PUBLIC
        )
    }
}
