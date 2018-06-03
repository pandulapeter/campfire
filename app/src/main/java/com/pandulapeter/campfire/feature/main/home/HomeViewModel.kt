package com.pandulapeter.campfire.feature.main.home

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import org.koin.android.ext.android.inject

class HomeViewModel(private val openSongs: () -> Unit) : CampfireViewModel() {

    private val preferenceDatabase by inject<PreferenceDatabase>()
    val buttonIcon = R.drawable.ic_songs_24dp
    val buttonText = R.string.go_to_songs
    val state = StateLayout.State.ERROR
    val placeholderText = R.string.home_placeholder

    init {
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_HOME
    }

    fun onActionButtonClicked() = openSongs()
}