package com.pandulapeter.campfire.feature.main.options

import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker

class OptionsViewModel(
    preferenceDatabase: PreferenceDatabase,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker) {

    init {
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_OPTIONS
    }
}