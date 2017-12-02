package com.pandulapeter.campfire.data.model

import android.support.annotation.StringRes
import com.pandulapeter.campfire.R

/**
 * Connects a language to the localised string resource.
 */
data class Language(val id: String, @StringRes val nameResource: Int = when (id) {
    Language.ENGLISH.id -> R.string.language_english
    Language.HUNGARIAN.id -> R.string.language_hungarian
    else -> R.string.language_unknown
}) {

    enum class Language(val id: String) {
        ENGLISH("en"), HUNGARIAN("hu")
    }
}