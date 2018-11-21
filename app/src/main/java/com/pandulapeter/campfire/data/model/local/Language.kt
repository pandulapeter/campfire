package com.pandulapeter.campfire.data.model.local

import androidx.annotation.StringRes
import com.pandulapeter.campfire.R

sealed class Language(val id: String, @StringRes val nameResource: Int) {

    sealed class Known(id: String, @StringRes nameResource: Int) : Language(id, nameResource) {
        object English : Known(SupportedLanguages.ENGLISH.id, R.string.language_english)
        object Spanish : Known(SupportedLanguages.SPANISH.id, R.string.language_spanish)
        object Hungarian : Known(SupportedLanguages.HUNGARIAN.id, R.string.language_hungarian)
        object Romanian : Known(SupportedLanguages.ROMANIAN.id, R.string.language_romanian)
    }

    object Unknown : Language("?", R.string.language_unknown)

    enum class SupportedLanguages(val id: String, val countryCodes: List<String>) {
        ENGLISH("en", listOf()),
        SPANISH("es", listOf("ARG", "BOL", "CHL", "COL", "CRI", "CUB", "DOM", "ECU", "SLV", "GTM", "HND", "MEX", "NIC", "PAN", "PRY", "PER", "ESP", "URY", "VEN", "USA")),
        HUNGARIAN("hu", listOf("HUN")),
        ROMANIAN("ro", listOf("ROU"))
    }
}