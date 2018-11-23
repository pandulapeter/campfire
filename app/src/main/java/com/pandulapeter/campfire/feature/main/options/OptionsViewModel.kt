package com.pandulapeter.campfire.feature.main.options

import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

class OptionsViewModel(preferenceDatabase: PreferenceDatabase) : CampfireViewModel() {

    init {
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_OPTIONS
    }
}