package com.pandulapeter.campfire.old.feature.home.collections

import com.pandulapeter.campfire.old.feature.home.shared.homeChild.HomeChildViewModel
import com.pandulapeter.campfire.old.integration.AppShortcutManager
import com.pandulapeter.campfire.old.networking.AnalyticsManager

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