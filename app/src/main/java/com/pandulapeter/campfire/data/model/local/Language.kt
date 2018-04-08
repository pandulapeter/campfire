package com.pandulapeter.campfire.data.model.local

import android.support.annotation.StringRes
import com.pandulapeter.campfire.R

sealed class Language(val id: String, @StringRes val nameResource: Int) {

    sealed class Known(id: String, @StringRes nameResource: Int) : Language(id, nameResource) {
        object English : Known(SupportedLanguages.ENGLISH.id, R.string.language_english)
        object Hungarian : Known(SupportedLanguages.HUNGARIAN.id, R.string.language_hungarian)
        object Romanian : Known(SupportedLanguages.ROMANIAN.id, R.string.language_romanian)
    }

    object Unknown : Language("?", R.string.language_unknown)

    enum class SupportedLanguages(val id: String) {
        ENGLISH("en"),
        HUNGARIAN("hu"),
        ROMANIAN("ro")
    }
}