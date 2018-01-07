package com.pandulapeter.campfire.feature.home.collections

import com.pandulapeter.campfire.CollectionsBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeChildFragment
import com.pandulapeter.campfire.integration.AppShortcutManager
import javax.inject.Inject

/**
 * Displays a list of curated song collections.
 *
 * Controlled by [CollectionsViewModel].
 */
class CollectionsFragment : HomeChildFragment<CollectionsBinding, CollectionsViewModel>(R.layout.fragment_collections) {
    @Inject lateinit var appShortcutManager: AppShortcutManager

    override fun createViewModel() = CollectionsViewModel(appShortcutManager)

    override fun getAppBarLayout() = binding.appBarLayout
}