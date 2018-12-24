package com.pandulapeter.campfire.feature.main.songs

import androidx.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.util.onPropertyChanged

class SearchControlsViewModel(
    private val preferenceDatabase: PreferenceDatabase,
    val isForCollections: Boolean,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker) {

    val isVisible = ObservableBoolean()
    val searchInArtists = ObservableBoolean(if (isForCollections) preferenceDatabase.shouldSearchInCollectionDescriptions else preferenceDatabase.shouldSearchInArtists)
    val searchInTitles = ObservableBoolean(if (isForCollections) preferenceDatabase.shouldSearchInCollectionTitles else preferenceDatabase.shouldSearchInTitles)

    init {
        searchInArtists.onPropertyChanged {
            if (!it) {
                searchInTitles.set(true)
            }
            if (isForCollections) {
                preferenceDatabase.shouldSearchInCollectionDescriptions = it
            } else {
                preferenceDatabase.shouldSearchInArtists = it
            }
        }
        searchInTitles.onPropertyChanged {
            if (!it) {
                searchInArtists.set(true)
            }
            if (isForCollections) {
                preferenceDatabase.shouldSearchInCollectionTitles = it
            } else {
                preferenceDatabase.shouldSearchInTitles = it
            }
        }
    }
}