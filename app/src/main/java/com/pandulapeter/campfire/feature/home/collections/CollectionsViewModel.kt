package com.pandulapeter.campfire.feature.home.collections

import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeChildViewModel
import com.pandulapeter.campfire.integration.AppShortcutManager

/**
 * Handles events and logic for [CollectionsFragment].
 */
class CollectionsViewModel(appShortcutManager: AppShortcutManager) : HomeChildViewModel() {

    init {
        appShortcutManager.onCollectionsOpened()
    }
}