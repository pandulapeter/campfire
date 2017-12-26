package com.pandulapeter.campfire.feature.home.settings

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SettingsBinding
import com.pandulapeter.campfire.feature.home.playlist.PlaylistViewModel
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment

/**
 * Allows the user to change the global settings of the app.
 *
 * Controlled by [PlaylistViewModel].
 */
class SettingsFragment : HomeFragment<SettingsBinding, SettingsViewModel>(R.layout.fragment_settings) {

    override fun createViewModel() = SettingsViewModel()
}