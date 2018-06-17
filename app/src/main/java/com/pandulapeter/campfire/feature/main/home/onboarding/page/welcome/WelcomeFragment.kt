package com.pandulapeter.campfire.feature.main.home.onboarding.page.welcome

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.customtabs.CustomTabsIntent
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingWelcomeBinding
import com.pandulapeter.campfire.feature.main.home.onboarding.page.OnboardingPageFragment
import com.pandulapeter.campfire.feature.main.options.about.AboutViewModel
import com.pandulapeter.campfire.feature.main.options.preferences.LanguageSelectorBottomSheetFragment
import com.pandulapeter.campfire.feature.main.options.preferences.PreferencesViewModel
import com.pandulapeter.campfire.feature.main.options.preferences.ThemeSelectorBottomSheetFragment
import com.pandulapeter.campfire.util.color
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
        val first = getString(R.string.welcome_conditions_part_1)
        val second = getString(R.string.welcome_conditions_part_2)
        val third = getString(R.string.welcome_conditions_part_3)
        val fourth = getString(R.string.welcome_conditions_part_4)
        val fifth = getString(R.string.welcome_conditions_part_5)
        binding.textBottom.setText(SpannableString("$first$second$third$fourth$fifth").apply {
            setSpan(
                UrlClickableSpan(requireContext(), AboutViewModel.PRIVACY_POLICY_URL),
                first.length,
                first.length + second.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                UrlClickableSpan(requireContext(), AboutViewModel.TERMS_AND_CONDITIONS_URL),
                first.length + second.length + third.length,
                length - fifth.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }, TextView.BufferType.SPANNABLE)
        binding.textBottom.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onThemeSelected(theme: PreferencesViewModel.Theme) = viewModel.theme.set(theme)

    override fun onLanguageSelected(language: PreferencesViewModel.Language) = viewModel.language.set(language)

    private class UrlClickableSpan(private val context: Context, private val url: String) : ClickableSpan() {
        override fun onClick(widget: View?) {
            CustomTabsIntent.Builder()
                .setToolbarColor(context.color(R.color.accent))
                .build()
                .launchUrl(context, Uri.parse(url))
        }

        override fun updateDrawState(ds: TextPaint?) {
            super.updateDrawState(ds)
            ds?.isUnderlineText = false
        }
    }
}