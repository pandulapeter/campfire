package com.pandulapeter.campfire.data.source.remote.implementation.model

import com.github.theapache64.retrosheet.RetrosheetInterceptor
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class CollectionResponse(
    @Json(name = KEY_ID) val id: String? = null,
    @Json(name = KEY_TITLE) val title: String? = null,
    @Json(name = KEY_DESCRIPTION) val description: String? = null,
    @Json(name = KEY_THUMBNAIL_URL) val thumbnailUrl: String? = null,
    @Json(name = KEY_SONGS_IDS) val songIds: String? = null,
    @Json(name = KEY_IS_PUBLIC) val isPublic: Boolean? = null
) {
    companion object {
        const val SHEET_NAME = "collections"
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_THUMBNAIL_URL = "thumbnail_url"
        private const val KEY_SONGS_IDS = "song_ids"
        private const val KEY_IS_PUBLIC = "is_public"

        internal fun addSheet(interceptorBuilder: RetrosheetInterceptor.Builder) = interceptorBuilder.addSheet(
            SHEET_NAME,
            KEY_ID,
            KEY_TITLE,
            KEY_DESCRIPTION,
            KEY_THUMBNAIL_URL,
            KEY_SONGS_IDS,
            KEY_IS_PUBLIC
        )
    }
}