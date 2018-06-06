package com.pandulapeter.campfire.feature.main.home.onboarding.page.welcome

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingWelcomeBinding
import com.pandulapeter.campfire.feature.main.options.preferences.LanguageSelectorBottomSheetFragment
import com.pandulapeter.campfire.feature.main.options.preferences.PreferencesViewModel
import com.pandulapeter.campfire.feature.main.options.preferences.ThemeSelectorBottomSheetFragment
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.waitForLayout

class WelcomeFragment : CampfireFragment<FragmentOnboardingWelcomeBinding, WelcomeViewModel>(R.layout.fragment_onboarding_welcome),
    ThemeSelectorBottomSheetFragment.OnThemeSelectedListener,
    LanguageSelectorBottomSheetFragment.OnLanguageSelectedListener {

    override val viewModel = WelcomeViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.alpha = 0f
        binding.root.animate().alpha(1f).start()
        binding.linearLayout.apply {
            waitForLayout {
                layoutParams = (layoutParams as FrameLayout.LayoutParams).apply { setMargins(0, -getCampfireActivity().toolbarHeight, 0, 0) }
            }
        }
        viewModel.apply {
            shouldShowLanguageSelector.onEventTriggered {
                if (!getCampfireActivity().isUiBlocked) {
                    language.get()?.let { LanguageSelectorBottomSheetFragment.show(childFragmentManager, it.id) }
                }
            }
            language.onPropertyChanged(this@WelcomeFragment) { binding.root.post { if (isAdded) getCampfireActivity().recreate() } }
            shouldShowThemeSelector.onEventTriggered {
                if (!getCampfireActivity().isUiBlocked) {
                    theme.get()?.let { ThemeSelectorBottomSheetFragment.show(childFragmentManager, it.id) }
                }
            }
            theme.onPropertyChanged(this@WelcomeFragment) { binding.root.post { if (isAdded) getCampfireActivity().recreate() } }
        }
    }

    override fun onThemeSelected(theme: PreferencesViewModel.Theme) = viewModel.theme.set(theme)

    override fun onLanguageSelected(language: PreferencesViewModel.Language) = viewModel.language.set(language)
}