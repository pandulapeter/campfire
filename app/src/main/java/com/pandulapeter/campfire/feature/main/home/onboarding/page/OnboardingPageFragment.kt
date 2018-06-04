package com.pandulapeter.campfire.feature.main.home.onboarding.page

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingPageBinding
import com.pandulapeter.campfire.feature.main.home.onboarding.OnboardingFragment
import com.pandulapeter.campfire.feature.shared.CampfireFragment

class OnboardingPageFragment : CampfireFragment<FragmentOnboardingPageBinding, OnboardingPageViewModel>(R.layout.fragment_onboarding_page) {

    override val viewModel = OnboardingPageViewModel { (parentFragment as? OnboardingFragment)?.navigateToHome() }
}