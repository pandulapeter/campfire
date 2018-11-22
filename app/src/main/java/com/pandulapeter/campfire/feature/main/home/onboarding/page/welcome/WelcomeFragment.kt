package com.pandulapeter.campfire.feature.main.home.onboarding.page.welcome

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingWelcomeBinding
import com.pandulapeter.campfire.feature.main.home.onboarding.page.OnboardingPageFragment
import com.pandulapeter.campfire.feature.main.options.preferences.LanguageSelectorBottomSheetFragment
import com.pandulapeter.campfire.feature.main.options.preferences.PreferencesViewModel
import com.pandulapeter.campfire.feature.main.options.preferences.ThemeSelectorBottomSheetFragment
import com.pandulapeter.campfire.util.obtainColor
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged


class WelcomeFragment : OnboardingPageFragment<FragmentOnboardingWelcomeBinding, WelcomeViewModel>(R.layout.fragment_onboarding_welcome),
    ThemeSelectorBottomSheetFragment.OnThemeSelectedListener,
    LanguageSelectorBottomSheetFragment.OnLanguageSelectedListener {

    override val viewModel = WelcomeViewModel()
    private val languageText by lazy { getString(R.string.welcome_language) }
    private val themeText by lazy { getString(R.string.welcome_theme) }
    private val secondaryTextColor by lazy { getCampfireActivity().obtainColor(android.R.attr.textColorSecondary) }

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
            updateLanguageDescription()
        }
        viewModel.shouldShowThemeSelector.onEventTriggered(this) {
            if (!getCampfireActivity().isUiBlocked) {
                viewModel.theme.get()?.let { ThemeSelectorBottomSheetFragment.show(childFragmentManager, it.id) }
            }
        }
        viewModel.theme.onPropertyChanged(this@WelcomeFragment) {
            getCampfireActivity().isUiBlocked = true
            binding.root.post { if (isAdded) getCampfireActivity().recreate() }
            updateThemeDescription()
        }
        updateLanguageDescription()
        updateThemeDescription()
    }

    override fun onThemeSelected(theme: PreferencesViewModel.Theme) = viewModel.theme.set(theme)

    override fun onLanguageSelected(language: PreferencesViewModel.Language) = viewModel.language.set(language)

    private fun updateLanguageDescription() {
        binding.language.text = SpannableString(
            "$languageText ${getString(
                when (viewModel.language.get()) {
                    null, PreferencesViewModel.Language.AUTOMATIC -> R.string.options_preferences_language_automatic
                    PreferencesViewModel.Language.ENGLISH -> R.string.options_preferences_language_english
                    PreferencesViewModel.Language.HUNGARIAN -> R.string.options_preferences_language_hungarian
                    PreferencesViewModel.Language.ROMANIAN -> R.string.options_preferences_language_romanian
                }
            )}"
        ).apply { setSpan(ForegroundColorSpan(secondaryTextColor), languageText.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }

    private fun updateThemeDescription() {
        binding.theme.text = SpannableString(
            "$themeText ${getString(
                when (viewModel.theme.get()) {
                    null, PreferencesViewModel.Theme.AUTOMATIC -> R.string.options_preferences_app_theme_automatic
                    PreferencesViewModel.Theme.DARK -> R.string.options_preferences_app_theme_dark
                    PreferencesViewModel.Theme.LIGHT -> R.string.options_preferences_app_theme_light
                }
            )}"
        ).apply { setSpan(ForegroundColorSpan(secondaryTextColor), themeText.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }
}