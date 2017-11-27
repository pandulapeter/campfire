package com.pandulapeter.campfire.feature.home.settings

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SettingsBinding
import com.pandulapeter.campfire.feature.home.favorites.FavoritesViewModel
import com.pandulapeter.campfire.feature.shared.CampfireFragment

/**
 * Allows the user to change the global settings of the app.
 *
 * Controlled by [FavoritesViewModel].
 */
class SettingsFragment : CampfireFragment<SettingsBinding, SettingsViewModel>(R.layout.fragment_settings) {

    override val viewModel = SettingsViewModel()
}