package com.pandulapeter.campfire.feature.main.home

import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import org.koin.android.ext.android.inject

class HomeContainerViewModel : CampfireViewModel() {

    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val collectionRepository by inject<CollectionRepository>()
    private val songRepository by inject<SongRepository>()

    init {
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_HOME

        // Calling any method from the lazily initialized repositories ensures that the data starts loading in the background.
        collectionRepository.isCacheLoaded()
        songRepository.isCacheLoaded()
    }
}