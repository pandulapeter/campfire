package com.pandulapeter.campfire.feature.home.collections

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.CollectionsBinding
import com.pandulapeter.campfire.feature.home.playlist.PlaylistViewModel
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment

/**
 * Displays a list of curated song collections.
 *
 * Controlled by [CollectionsViewModel].
 */
class CollectionsFragment : HomeFragment<CollectionsBinding, CollectionsViewModel>(R.layout.fragment_collections) {

    override fun createViewModel() = CollectionsViewModel(callbacks)
}