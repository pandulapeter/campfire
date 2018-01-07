package com.pandulapeter.campfire.feature.home.manageplaylists

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeChildViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager

/**
 * Handles events and logic for [ManagePlaylistsFragment].
 */
class ManagePlaylistsViewModel(analyticsManager: AnalyticsManager) : HomeChildViewModel(analyticsManager) {
    val shouldAllowToolbarScrolling = ObservableBoolean()
}