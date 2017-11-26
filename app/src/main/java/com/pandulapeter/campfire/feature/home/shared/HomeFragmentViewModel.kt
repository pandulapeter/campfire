package com.pandulapeter.campfire.feature.home.shared

import com.pandulapeter.campfire.data.repository.SongInfoRepository

/**
 * Parent class for view models that handle the Fragments that can be seen on the main screen.
 *
 * Handles events and logic for subclasses of [HomeFragment].
 */
abstract class HomeFragmentViewModel(
    private val homeCallbacks: HomeFragment.HomeCallbacks?,
    protected val songInfoRepository: SongInfoRepository) {
    val adapter = SongInfoAdapter()

    fun showViewOptions() {
        homeCallbacks?.showViewOptions()
    }
}