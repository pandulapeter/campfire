package com.pandulapeter.campfire.shared.ui.catalogue.utilities

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import java.util.Locale

fun UserPreferences?.getUiStrings() = when (this?.language) {
    UserPreferences.Language.ENGLISH -> CampfireStrings.English
    UserPreferences.Language.HUNGARIAN -> CampfireStrings.Hungarian
    UserPreferences.Language.SYSTEM_DEFAULT, null -> when (Locale.getDefault().language) {
        "hu" -> CampfireStrings.Hungarian
        else -> CampfireStrings.English
    }
}