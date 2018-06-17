package com.pandulapeter.campfire.feature.main.home.onboarding.page.languageSelector

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingLanguageSelectorBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment

class LanguageSelectorFragment : CampfireFragment<FragmentOnboardingLanguageSelectorBinding, LanguageSelectorViewModel>(R.layout.fragment_onboarding_language_selector) {

    override val viewModel = LanguageSelectorViewModel()
}