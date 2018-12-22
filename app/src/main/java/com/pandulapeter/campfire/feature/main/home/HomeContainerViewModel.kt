package com.pandulapeter.campfire.feature.main.home

import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker

class HomeContainerViewModel(
    val preferenceDatabase: PreferenceDatabase,
    collectionRepository: CollectionRepository,
    songRepository: SongRepository,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker) {

    init {
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_HOME

        // Calling any method from the lazily initialized repositories ensures that the data starts loading in the background.
        collectionRepository.isCacheLoaded()
        songRepository.isCacheLoaded()
    }
}