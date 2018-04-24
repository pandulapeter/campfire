package com.pandulapeter.campfire.feature.home.options.preferences

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsPreferencesBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged

class PreferencesFragment : CampfireFragment<FragmentOptionsPreferencesBinding, PreferencesViewModel>(R.layout.fragment_options_preferences),
    AlertDialogFragment.OnDialogItemsSelectedListener {

    companion object {
        private const val DIALOG_ID_RESET_HINTS_CONFIRMATION = 3
    }

    override val viewModel = PreferencesViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.shouldUseDarkTheme.onPropertyChanged(this) { mainActivity.recreate() }
        viewModel.shouldShowHintsResetConfirmation.onEventTriggered(this) {
            AlertDialogFragment.show(
                DIALOG_ID_RESET_HINTS_CONFIRMATION,
                childFragmentManager,
                R.string.options_preferences_reset_hints_confirmation_title,
                R.string.options_preferences_reset_hints_confirmation_message,
                R.string.options_preferences_reset_hints_confirmation_reset,
                R.string.cancel
            )
        }
        viewModel.shouldShareUsageData.onPropertyChanged(this) { mainActivity.restartProcess() }
        viewModel.shouldShowHintsResetSnackbar.onEventTriggered(this) { showSnackbar(R.string.options_preferences_reset_hints_message) }
    }

    override fun onPositiveButtonSelected(id: Int) {
        if (id == DIALOG_ID_RESET_HINTS_CONFIRMATION) {
            viewModel.resetHints()
        }
    }
}