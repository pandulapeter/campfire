package com.pandulapeter.campfire.data.source.remote.implementation.model

import com.github.theapache64.retrosheet.RetrosheetInterceptor
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class LanguageResponse(
    @Json(name = KEY_ID) val id: String? = null,
    @Json(name = KEY_NAME_EN) val nameEn: String? = null,
    @Json(name = KEY_NAME_HU) val nameHu: String? = null,
    @Json(name = KEY_NAME_RO) val nameRo: String? = null,
) {
    companion object {
        const val SHEET_NAME = "languages"
        private const val KEY_ID = "id"
        private const val KEY_NAME_EN = "name_en"
        private const val KEY_NAME_HU = "name_hu"
        private const val KEY_NAME_RO = "name_ro"

        internal fun addSheet(interceptorBuilder: RetrosheetInterceptor.Builder) = interceptorBuilder.addSheet(
            SHEET_NAME,
            KEY_ID,
            KEY_NAME_EN,
            KEY_NAME_HU,
            KEY_NAME_RO
        )
    }
}