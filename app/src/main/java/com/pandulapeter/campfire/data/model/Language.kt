package com.pandulapeter.campfire.data.model

import android.support.annotation.StringRes
import com.pandulapeter.campfire.R

sealed class Language(@StringRes val nameResource: Int) {

    sealed class Known(val id: String, @StringRes nameResource: Int) : Language(nameResource) {
        object English : Known(SupportedLanguages.ENGLISH.id, R.string.language_english)
        object Hungarian : Known(SupportedLanguages.HUNGARIAN.id, R.string.language_hungarian)
    }

    object Unknown : Language(R.string.language_unknown)

    enum class SupportedLanguages(val id: String) {
        ENGLISH("en"),
        HUNGARIAN("hu")
    }
}