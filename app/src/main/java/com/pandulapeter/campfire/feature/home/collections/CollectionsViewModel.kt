package com.pandulapeter.campfire.feature.home.collections

import com.pandulapeter.campfire.feature.home.shared.homeChild.HomeChildViewModel
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.networking.AnalyticsManager

/**
 * Handles events and logic for [CollectionsFragment].
 */
class CollectionsViewModel(
    analyticsManager: AnalyticsManager,
    appShortcutManager: AppShortcutManager
) : HomeChildViewModel(analyticsManager) {

    init {
        appShortcutManager.onCollectionsOpened()
    }
}