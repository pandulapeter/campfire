package com.pandulapeter.campfire.feature.home.settings

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SettingsBinding
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.home.playlist.PlaylistViewModel
import com.pandulapeter.campfire.feature.home.shared.homeChild.HomeChildFragment
import org.koin.android.ext.android.inject

/**
 * Allows the user to change the global settings of the app.
 *
 * Controlled by [PlaylistViewModel].
 */
class SettingsFragment : HomeChildFragment<SettingsBinding, SettingsViewModel>(R.layout.fragment_settings) {
    private val userPreferenceRepository by inject<UserPreferenceRepository>()

    override fun createViewModel() = SettingsViewModel(analyticsManager, userPreferenceRepository)

    override fun getAppBarLayout() = binding.appBarLayout
}