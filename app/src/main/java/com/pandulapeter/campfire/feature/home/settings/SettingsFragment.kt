package com.pandulapeter.campfire.feature.home.settings

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SettingsBinding
import com.pandulapeter.campfire.feature.home.favorites.FavoritesViewModel
import com.pandulapeter.campfire.feature.home.shared.HomeFragment


/**
 * Allows the user to change the global settings of the app.
 *
 * Controlled by [FavoritesViewModel].
 */
//TODO: Don't extend HomeFragment as this page shouldn't handle lists. Create an intermediary parent.
class SettingsFragment : HomeFragment<SettingsBinding, SettingsViewModel>(R.layout.fragment_settings) {

    override val viewModel by lazy { SettingsViewModel(callbacks, songInfoRepository) }

    override fun getRecyclerView() = binding.recyclerView

    override fun getSwipeRefreshLayout() = binding.swipeRefreshLayout

    override fun searchInputVisible() = false

    override fun closeSearchInput() = Unit
}