package com.pandulapeter.campfire.data.model

import android.support.annotation.StringRes
import com.pandulapeter.campfire.R

/**
 * Represents all possible section types.
 */
enum class SectionType(val abbreviation: Char, @StringRes val nameResourceId: Int) {
    INTRO('I', R.string.detail_intro),
    VERSE('V', R.string.detail_verse),
    BRIDGE('B', R.string.detail_bridge),
    CHORUS('C', R.string.detail_chorus),
    SOLO('S', R.string.detail_solo),
    OUTRO('O', R.string.detail_outro);

    companion object {
        fun fromAbbreviation(abbreviation: Char): SectionType? {
            SectionType.values().forEach {
                if (abbreviation.toUpperCase() == it.abbreviation) {
                    return it
                }
            }
            return null
        }
    }
}