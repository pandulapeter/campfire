package com.pandulapeter.campfire.feature.home.managePlaylists

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.feature.home.shared.homeChild.HomeChildViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager

/**
 * Handles events and logic for [ManagePlaylistsFragment].
 */
class ManagePlaylistsViewModel(analyticsManager: AnalyticsManager) : HomeChildViewModel(analyticsManager) {
    val shouldAllowToolbarScrolling = ObservableBoolean()
}