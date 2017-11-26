package com.pandulapeter.campfire.feature.home

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.Subscriber
import com.pandulapeter.campfire.util.onPropertyChanged

/**
 * Handles events and logic for [HomeActivity].
 */
class HomeViewModel(private val songInfoRepository: SongInfoRepository) : Subscriber {
    val selectedItem: ObservableField<NavigationItem> = ObservableField(NavigationItem.CLOUD)
    val isSortedByTitle = ObservableBoolean(songInfoRepository.isSortedByTitle)
    val shouldHideExplicit = ObservableBoolean(songInfoRepository.shouldHideExplicit)

    init {
        isSortedByTitle.onPropertyChanged { songInfoRepository.isSortedByTitle = it }
        shouldHideExplicit.onPropertyChanged { songInfoRepository.shouldHideExplicit = it }
    }

    override fun onUpdate() {
        isSortedByTitle.set(songInfoRepository.isSortedByTitle)
    }

    /**
     * Marks the possible screens the user can reach using the bottom navigation of the home screen.
     */
    enum class NavigationItem {
        CLOUD, DOWNLOADED, FAVORITES
    }
}