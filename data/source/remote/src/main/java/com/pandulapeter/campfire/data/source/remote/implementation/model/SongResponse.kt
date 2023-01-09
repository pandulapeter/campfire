package com.pandulapeter.campfire.data.source.remote.implementation.model

import com.github.theapache64.retrosheet.RetrosheetInterceptor
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class SongResponse(
    @Json(name = KEY_ID) val id: String? = null,
    @Json(name = KEY_URL) val url: String? = null,
    @Json(name = KEY_LANGUAGE_ID) val languageId: String? = null,
    @Json(name = KEY_TITLE) val title: String? = null,
    @Json(name = KEY_ARTIST) val artist: String? = null,
    @Json(name = KEY_KEY) val key: String? = null,
    @Json(name = KEY_IS_EXPLICIT) val isExplicit: Boolean? = null,
    @Json(name = KEY_HAS_CHORDS) val hasChords: Boolean? = null,
    @Json(name = KEY_IS_PUBLIC) val isPublic: Boolean? = null
) {
    companion object {
        const val SHEET_NAME = "songs"
        private const val KEY_ID = "id"
        private const val KEY_URL = "url"
        private const val KEY_LANGUAGE_ID = "language_id"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_KEY = "key"
        private const val KEY_IS_EXPLICIT = "is_explicit"
        private const val KEY_HAS_CHORDS = "has_chords"
        private const val KEY_IS_PUBLIC = "is_public"

        internal fun addSheet(interceptorBuilder: RetrosheetInterceptor.Builder) = interceptorBuilder.addSheet(
            SHEET_NAME,
            KEY_ID,
            KEY_URL,
            KEY_LANGUAGE_ID,
            KEY_TITLE,
            KEY_ARTIST,
            KEY_KEY,
            KEY_IS_EXPLICIT,
            KEY_HAS_CHORDS,
            KEY_IS_PUBLIC
        )
    }
}