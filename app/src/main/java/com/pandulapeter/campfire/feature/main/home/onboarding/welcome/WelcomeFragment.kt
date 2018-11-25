package com.pandulapeter.campfire.feature.main.home.onboarding.welcome

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingWelcomeBinding
import com.pandulapeter.campfire.feature.main.home.onboarding.OnboardingPageFragment
import com.pandulapeter.campfire.feature.main.options.preferences.LanguageSelectorBottomSheetFragment
import com.pandulapeter.campfire.feature.main.options.preferences.PreferencesViewModel
import com.pandulapeter.campfire.feature.main.options.preferences.ThemeSelectorBottomSheetFragment
import com.pandulapeter.campfire.util.obtainColor
import org.koin.androidx.viewmodel.ext.android.viewModel


class WelcomeFragment : OnboardingPageFragment<FragmentOnboardingWelcomeBinding, WelcomeViewModel>(R.layout.fragment_onboarding_welcome),
    ThemeSelectorBottomSheetFragment.OnThemeSelectedListener,
    LanguageSelectorBottomSheetFragment.OnLanguageSelectedListener {

    override val viewModel by viewModel<WelcomeViewModel>()
    private val languageText by lazy { getString(R.string.welcome_language) }
    private val themeText by lazy { getString(R.string.welcome_theme) }
    private val secondaryTextColor by lazy { requireContext().obtainColor(android.R.attr.textColorSecondary) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.shouldShowLanguageSelector.observeAndReset { showLanguageSelectorBottomSheet() }
        viewModel.language.observe {
            binding.root.post { if (isAdded) activity?.recreate() }
            updateLanguageDescription()
        }
        viewModel.shouldShowThemeSelector.observeAndReset { showThemeSelectorBottomSheet() }
        viewModel.theme.observe {
            binding.root.post { if (isAdded) activity?.recreate() }
            updateThemeDescription()
        }
        updateLanguageDescription()
        updateThemeDescription()
    }

    override fun onThemeSelected(theme: PreferencesViewModel.Theme) {
        viewModel.theme.value = theme
    }

    override fun onLanguageSelected(language: PreferencesViewModel.Language) {
        viewModel.language.value = language
    }

    private fun showLanguageSelectorBottomSheet() {
        viewModel.language.value?.let { language -> LanguageSelectorBottomSheetFragment.show(childFragmentManager, language.id) }
    }

    private fun updateLanguageDescription() {
        binding.language.text = SpannableString(
            "$languageText ${getString(
                when (viewModel.language.value) {
                    null, PreferencesViewModel.Language.AUTOMATIC -> R.string.options_preferences_language_automatic
                    PreferencesViewModel.Language.ENGLISH -> R.string.options_preferences_language_english
                    PreferencesViewModel.Language.HUNGARIAN -> R.string.options_preferences_language_hungarian
                    PreferencesViewModel.Language.ROMANIAN -> R.string.options_preferences_language_romanian
                }
            )}"
        ).apply { setSpan(ForegroundColorSpan(secondaryTextColor), languageText.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }

    private fun showThemeSelectorBottomSheet() {
        viewModel.theme.value?.let { theme -> ThemeSelectorBottomSheetFragment.show(childFragmentManager, theme.id) }
    }

    private fun updateThemeDescription() {
        binding.theme.text = SpannableString(
            "$themeText ${getString(
                when (viewModel.theme.value) {
                    null, PreferencesViewModel.Theme.AUTOMATIC -> R.string.options_preferences_app_theme_automatic
                    PreferencesViewModel.Theme.DARK -> R.string.options_preferences_app_theme_dark
                    PreferencesViewModel.Theme.LIGHT -> R.string.options_preferences_app_theme_light
                }
            )}"
        ).apply { setSpan(ForegroundColorSpan(secondaryTextColor), themeText.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }

    companion object {

        fun newInstance() = WelcomeFragment()
    }
}