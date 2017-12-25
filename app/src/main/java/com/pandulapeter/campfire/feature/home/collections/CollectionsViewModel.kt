package com.pandulapeter.campfire.feature.home.collections

import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragmentViewModel
import com.pandulapeter.campfire.integration.AppShortcutManager

/**
 * Handles events and logic for [CollectionsFragment].
 */
class CollectionsViewModel(appShortcutManager: AppShortcutManager,
                           homeCallbacks: HomeFragment.HomeCallbacks?) : HomeFragmentViewModel(homeCallbacks) {

    init {
        appShortcutManager.onCollectionsOpened()
    }
}