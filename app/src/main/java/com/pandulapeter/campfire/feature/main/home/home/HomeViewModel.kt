package com.pandulapeter.campfire.feature.main.home.home

import com.pandulapeter.campfire.feature.shared.CampfireViewModel

class HomeViewModel(
    private val openCollections: () -> Unit,
    private val openSongs: () -> Unit
) : CampfireViewModel() {

    fun onCollectionsButtonClicked() = openCollections()

    fun onSongsButtonClicked() = openSongs()
}