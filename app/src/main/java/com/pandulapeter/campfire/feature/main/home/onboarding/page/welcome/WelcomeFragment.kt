package com.pandulapeter.campfire.feature.main.home.onboarding.page.welcome

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingWelcomeBinding
import com.pandulapeter.campfire.feature.main.home.onboarding.page.OnboardingPageFragment
import com.pandulapeter.campfire.feature.main.options.preferences.LanguageSelectorBottomSheetFragment
import com.pandulapeter.campfire.feature.main.options.preferences.PreferencesViewModel
import com.pandulapeter.campfire.feature.main.options.preferences.ThemeSelectorBottomSheetFragment
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged


class WelcomeFragment : OnboardingPageFragment<FragmentOnboardingWelcomeBinding, WelcomeViewModel>(R.layout.fragment_onboarding_welcome),
    ThemeSelectorBottomSheetFragment.OnThemeSelectedListener,
    LanguageSelectorBottomSheetFragment.OnLanguageSelectedListener {

    override val viewModel by lazy { WelcomeViewModel(getCampfireActivity()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.shouldShowLanguageSelector.onEventTriggered(this) {
            if (!getCampfireActivity().isUiBlocked) {
                viewModel.language.get()?.let { LanguageSelectorBottomSheetFragment.show(childFragmentManager, it.id) }
            }
        }
        viewModel.language.onPropertyChanged(this) {
            getCampfireActivity().isUiBlocked = true
            binding.root.post { if (isAdded) getCampfireActivity().recreate() }
        }
        viewModel.shouldShowThemeSelector.onEventTriggered(this) {
            if (!getCampfireActivity().isUiBlocked) {
                viewModel.theme.get()?.let { ThemeSelectorBottomSheetFragment.show(childFragmentManager, it.id) }
            }
        }
        viewModel.theme.onPropertyChanged(this@WelcomeFragment) {
            getCampfireActivity().isUiBlocked = true
            binding.root.post { if (isAdded) getCampfireActivity().recreate() }
        }
    }

    override fun onThemeSelected(theme: PreferencesViewModel.Theme) = viewModel.theme.set(theme)

    override fun onLanguageSelected(language: PreferencesViewModel.Language) = viewModel.language.set(language)
}