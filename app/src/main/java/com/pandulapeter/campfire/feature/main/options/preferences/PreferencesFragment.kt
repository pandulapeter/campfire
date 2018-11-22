package com.pandulapeter.campfire.feature.main.options.preferences

import com.crashlytics.android.Crashlytics
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsPreferencesBinding
import com.pandulapeter.campfire.feature.shared.deprecated.OldCampfireFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.feature.shared.dialog.BaseDialogFragment
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged
import io.fabric.sdk.android.Fabric

class PreferencesFragment : OldCampfireFragment<FragmentOptionsPreferencesBinding, PreferencesViewModel>(R.layout.fragment_options_preferences),
    BaseDialogFragment.OnDialogItemSelectedListener,
    ThemeSelectorBottomSheetFragment.OnThemeSelectedListener,
    LanguageSelectorBottomSheetFragment.OnLanguageSelectedListener {

    companion object {
        private const val DIALOG_ID_RESET_HINTS_CONFIRMATION = 3
    }

    override val viewModel by lazy {
        PreferencesViewModel(getCampfireActivity()).apply {
            shouldShowThemeSelector.onEventTriggered {
                if (!getCampfireActivity().isUiBlocked) {
                    theme.get()?.let { ThemeSelectorBottomSheetFragment.show(childFragmentManager, it.id) }
                }
            }
            shouldShowLanguageSelector.onEventTriggered {
                if (!getCampfireActivity().isUiBlocked) {
                    language.get()?.let { LanguageSelectorBottomSheetFragment.show(childFragmentManager, it.id) }
                }
            }
            theme.onPropertyChanged(this@PreferencesFragment) { binding.root.post { if (isAdded) getCampfireActivity().recreate() } }
            language.onPropertyChanged(this@PreferencesFragment) { binding.root.post { if (isAdded) getCampfireActivity().recreate() } }
            shouldShowHintsResetConfirmation.onEventTriggered(this@PreferencesFragment) {
                if (!getCampfireActivity().isUiBlocked) {
                    AlertDialogFragment.show(
                        DIALOG_ID_RESET_HINTS_CONFIRMATION,
                        childFragmentManager,
                        R.string.are_you_sure,
                        R.string.options_preferences_reset_hints_confirmation_message,
                        R.string.options_preferences_reset_hints_confirmation_reset,
                        R.string.cancel
                    )
                }
            }
            shouldShareUsageData.onPropertyChanged(this@PreferencesFragment) {
                analyticsManager.updateCollectionEnabledState()
                if (it) {
                    analyticsManager.onConsentGiven(System.currentTimeMillis())
                }
            }
            shouldShareCrashReports.onPropertyChanged(this@PreferencesFragment) {
                if (it) {
                    @Suppress("ConstantConditionIf")
                    if (BuildConfig.BUILD_TYPE != "debug") {
                        Fabric.with(requireContext().applicationContext, Crashlytics())
                    }
                } else {
                    getCampfireActivity().restartProcess()
                }
            }
            shouldShowHintsResetSnackbar.onEventTriggered(this@PreferencesFragment) { showSnackbar(R.string.options_preferences_reset_hints_message) }
        }
    }

    override fun onPositiveButtonSelected(id: Int) {
        if (id == DIALOG_ID_RESET_HINTS_CONFIRMATION) {
            analyticsManager.onHintsReset()
            viewModel.resetHints()
        }
    }

    override fun onThemeSelected(theme: PreferencesViewModel.Theme) = viewModel.theme.set(theme)

    override fun onLanguageSelected(language: PreferencesViewModel.Language) = viewModel.language.set(language)
}