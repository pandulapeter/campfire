package com.pandulapeter.campfire.feature.main.songs

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.util.mutableLiveDataOf

class SearchControlsViewModel(
    private val preferenceDatabase: PreferenceDatabase,
    private val type: Type,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker) {

    val isVisible = mutableLiveDataOf(false)
    val firstCheckbox = mutableLiveDataOf(
        when (type) {
            Type.HOME -> preferenceDatabase.isSearchInSongsEnabled
            Type.COLLECTIONS -> preferenceDatabase.shouldSearchInCollectionDescriptions
            Type.SONGS -> preferenceDatabase.shouldSearchInArtists
        }
    )
    val firstCheckboxName = when (type) {
        Type.HOME -> R.string.main_songs
        Type.COLLECTIONS -> R.string.songs_search_in_titles
        Type.SONGS -> R.string.songs_search_in_titles
    }
    val secondCheckbox = mutableLiveDataOf(
        when (type) {
            Type.HOME -> preferenceDatabase.isSearchInCollectionsEnabled
            Type.COLLECTIONS -> preferenceDatabase.shouldSearchInCollectionTitles
            Type.SONGS -> preferenceDatabase.shouldSearchInTitles
        }
    )
    val secondCheckboxName = when (type) {
        Type.HOME -> R.string.main_collections
        Type.COLLECTIONS -> R.string.collections_search_in_descriptions
        Type.SONGS -> R.string.songs_search_in_artists
    }

    init {
        firstCheckbox.observeForever {
            if (!it) {
                secondCheckbox.value = true
            }
            when (type) {
                Type.HOME -> preferenceDatabase.isSearchInSongsEnabled
                Type.COLLECTIONS -> preferenceDatabase.shouldSearchInCollectionDescriptions = it
                Type.SONGS -> preferenceDatabase.shouldSearchInArtists = it
            }
        }
        secondCheckbox.observeForever {
            if (!it) {
                firstCheckbox.value = true
            }
            when (type) {
                Type.HOME -> preferenceDatabase.isSearchInCollectionsEnabled
                Type.COLLECTIONS -> preferenceDatabase.shouldSearchInCollectionTitles = it
                Type.SONGS -> preferenceDatabase.shouldSearchInTitles = it
            }
        }
    }

    enum class Type {
        HOME, COLLECTIONS, SONGS
    }
}