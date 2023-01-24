package com.pandulapeter.campfire.shared.ui.catalogue.utilities

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import java.util.Locale

fun CampfireStrings.getUiStrings(userPreferences: UserPreferences?) = when (userPreferences?.language) {
    UserPreferences.Language.ENGLISH -> english
    UserPreferences.Language.HUNGARIAN -> hungarian
    UserPreferences.Language.SYSTEM_DEFAULT, null -> when (Locale.getDefault().language) {
        "hu" -> hungarian
        else -> english
    }
}