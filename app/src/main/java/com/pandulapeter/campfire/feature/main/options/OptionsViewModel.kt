package com.pandulapeter.campfire.feature.main.options

import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.shared.deprecated.OldCampfireViewModel
import org.koin.android.ext.android.inject

class OptionsViewModel : OldCampfireViewModel() {

    private val preferenceDatabase by inject<PreferenceDatabase>()

    init {
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_OPTIONS
    }
}