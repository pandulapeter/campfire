package com.pandulapeter.campfire.feature.main.options.preferences

import android.os.Bundle
import android.view.View
import com.crashlytics.android.Crashlytics
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsPreferencesBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.feature.shared.dialog.BaseDialogFragment
import io.fabric.sdk.android.Fabric
import org.koin.androidx.viewmodel.ext.android.viewModel

class PreferencesFragment : CampfireFragment<FragmentOptionsPreferencesBinding, PreferencesViewModel>(R.layout.fragment_options_preferences),
    BaseDialogFragment.OnDialogItemSelectedListener,
    ThemeSelectorBottomSheetFragment.OnThemeSelectedListener,
    LanguageSelectorBottomSheetFragment.OnLanguageSelectedListener {

    override val viewModel by viewModel<PreferencesViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.apply {
            shouldShowThemeSelector.observeAndReset { showThemeSelector() }
            shouldShowLanguageSelector.observeAndReset { showLanguageSelector() }
            shouldShowHintsResetConfirmation.observeAndReset { showHintsResetConfirmation() }
            shouldShowHintsResetSnackbar.observeAndReset { showSnackbar(R.string.options_preferences_reset_hints_message) }
            shouldShareUsageData.observe { onShouldShareUsageDataChanged(it) }
            shouldShareCrashReports.observe { onShouldShareCrashReportsChanged(it) }
            theme.observe { binding.root.post { getCampfireActivity()?.recreate() } }
            language.observe { binding.root.post { getCampfireActivity()?.recreate() } }
        }
    }

    override fun onPositiveButtonSelected(id: Int) {
        if (id == DIALOG_ID_RESET_HINTS_CONFIRMATION) {
            analyticsManager.onHintsReset()
            viewModel.resetHints()
        }
    }

    override fun onThemeSelected(theme: PreferencesViewModel.Theme) {
        viewModel.theme.value = theme
    }

    override fun onLanguageSelected(language: PreferencesViewModel.Language) {
        viewModel.language.value = language
    }

    private fun showThemeSelector() {
        viewModel.theme.value?.let { theme -> ThemeSelectorBottomSheetFragment.show(childFragmentManager, theme.id) }
    }

    private fun showLanguageSelector() {
        viewModel.language.value?.let { language -> LanguageSelectorBottomSheetFragment.show(childFragmentManager, language.id) }
    }

    private fun showHintsResetConfirmation() = AlertDialogFragment.show(
        DIALOG_ID_RESET_HINTS_CONFIRMATION,
        childFragmentManager,
        R.string.are_you_sure,
        R.string.options_preferences_reset_hints_confirmation_message,
        R.string.options_preferences_reset_hints_confirmation_reset,
        R.string.cancel
    )

    private fun onShouldShareUsageDataChanged(shouldShareUsageData: Boolean) {
        if (shouldShareUsageData) {
            analyticsManager.onConsentGiven(System.currentTimeMillis())
        }
    }

    private fun onShouldShareCrashReportsChanged(shouldShareCrashReports: Boolean) {
        getCampfireActivity()?.also { context ->
            if (shouldShareCrashReports) {
                @Suppress("ConstantConditionIf")
                if (BuildConfig.BUILD_TYPE != "debug") {
                    Fabric.with(context.applicationContext, Crashlytics())
                }
            } else {
                context.restartProcess()
            }
        }
    }

    companion object {
        private const val DIALOG_ID_RESET_HINTS_CONFIRMATION = 3

        fun newInstance() = PreferencesFragment()
    }
}