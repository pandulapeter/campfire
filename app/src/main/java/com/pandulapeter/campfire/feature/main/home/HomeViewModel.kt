package com.pandulapeter.campfire.feature.main.home

import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import org.koin.android.ext.android.inject

class HomeViewModel(
    private val openCollections: () -> Unit,
    private val openSongs: () -> Unit
) : CampfireViewModel() {

    private val preferenceDatabase by inject<PreferenceDatabase>()

    init {
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_HOME
    }

    fun onCollectionsButtonClicked() = openCollections()

    fun onSongsButtonClicked() = openSongs()
}