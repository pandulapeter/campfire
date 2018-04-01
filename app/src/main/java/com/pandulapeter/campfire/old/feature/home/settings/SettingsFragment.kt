package com.pandulapeter.campfire.old.feature.home.settings

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SettingsBinding
import com.pandulapeter.campfire.old.data.repository.FirstTimeUserExperienceRepository
import com.pandulapeter.campfire.old.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.old.feature.home.shared.homeChild.HomeChildFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.old.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

/**
 * Allows the user to change the global settings of the app.
 *
 * Controlled by [SettingsViewModel].
 */
class SettingsFragment : HomeChildFragment<SettingsBinding, SettingsViewModel>(R.layout.fragment_settings_old), AlertDialogFragment.OnDialogItemsSelectedListener {
    private val userPreferenceRepository by inject<UserPreferenceRepository>()
    private val firstTimeUserExperienceRepository by inject<FirstTimeUserExperienceRepository>()

    override fun createViewModel() = SettingsViewModel(analyticsManager, firstTimeUserExperienceRepository, userPreferenceRepository)

    override fun getAppBarLayout() = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.shouldShowHintsResetConfirmation.onEventTriggered(this) {
            AlertDialogFragment.show(
                childFragmentManager,
                R.string.settings_reset_hints_confirmation_title,
                R.string.settings_reset_hints_confirmation_message,
                R.string.settings_reset_hints_confirmation_reset,
                R.string.cancel
            )
        }
        viewModel.shouldShowHintsResetSnackbar.onEventTriggered(this) { binding.root.showSnackbar(R.string.settings_reset_hints_message) }
        viewModel.shouldUseDarkTheme.onPropertyChanged(this) { activity?.recreate() }
    }

    override fun onPositiveButtonSelected() = viewModel.resetHints()
}