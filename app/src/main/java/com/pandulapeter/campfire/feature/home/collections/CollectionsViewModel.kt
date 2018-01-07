package com.pandulapeter.campfire.feature.home.collections

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeChildViewModel
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.networking.AnalyticsManager

/**
 * Handles events and logic for [CollectionsFragment].
 */
class CollectionsViewModel(analyticsManager: AnalyticsManager,
                           appShortcutManager: AppShortcutManager) : HomeChildViewModel(analyticsManager) {
    val shouldAllowToolbarScrolling = ObservableBoolean()

    init {
        appShortcutManager.onCollectionsOpened()
    }
}