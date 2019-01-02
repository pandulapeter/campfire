package com.pandulapeter.campfire.feature.main.songs

import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.util.mutableLiveDataOf

class SearchControlsViewModel(
    private val preferenceDatabase: PreferenceDatabase,
    val isForCollections: Boolean,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker) {

    val isVisible = mutableLiveDataOf(false)
    val searchInArtists = mutableLiveDataOf(if (isForCollections) preferenceDatabase.shouldSearchInCollectionDescriptions else preferenceDatabase.shouldSearchInArtists)
    val searchInTitles = mutableLiveDataOf(if (isForCollections) preferenceDatabase.shouldSearchInCollectionTitles else preferenceDatabase.shouldSearchInTitles)

    init {
        searchInArtists.observeForever {
            if (!it) {
                searchInTitles.value = true
            }
            if (isForCollections) {
                preferenceDatabase.shouldSearchInCollectionDescriptions = it
            } else {
                preferenceDatabase.shouldSearchInArtists = it
            }
        }
        searchInTitles.observeForever {
            if (!it) {
                searchInArtists.value = true
            }
            if (isForCollections) {
                preferenceDatabase.shouldSearchInCollectionTitles = it
            } else {
                preferenceDatabase.shouldSearchInTitles = it
            }
        }
    }
}