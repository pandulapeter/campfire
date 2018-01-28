package com.pandulapeter.campfire.feature.home.settings

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SettingsBinding
import com.pandulapeter.campfire.data.repository.FirstTimeUserExperienceRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.home.playlist.PlaylistViewModel
import com.pandulapeter.campfire.feature.home.shared.homeChild.HomeChildFragment
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

/**
 * Allows the user to change the global settings of the app.
 *
 * Controlled by [PlaylistViewModel].
 */
class SettingsFragment : HomeChildFragment<SettingsBinding, SettingsViewModel>(R.layout.fragment_settings) {
    private val userPreferenceRepository by inject<UserPreferenceRepository>()
    private val firstTimeUserExperienceRepository by inject<FirstTimeUserExperienceRepository>()

    override fun createViewModel() = SettingsViewModel(analyticsManager, firstTimeUserExperienceRepository, userPreferenceRepository)

    override fun getAppBarLayout() = binding.appBarLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.shouldShowHintsResetSnackbar.onEventTriggered(this) { binding.coordinatorLayout.showSnackbar(R.string.settings_reset_hints_message) }
        viewModel.shouldUseDarkTheme.onPropertyChanged(this) { activity?.recreate() }
    }
}