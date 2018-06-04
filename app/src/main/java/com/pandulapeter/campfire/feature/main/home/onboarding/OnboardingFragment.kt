package com.pandulapeter.campfire.feature.main.home.onboarding

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingBinding
import com.pandulapeter.campfire.feature.main.home.HomeContainerFragment
import com.pandulapeter.campfire.feature.shared.CampfireFragment

class OnboardingFragment : CampfireFragment<FragmentOnboardingBinding, OnboardingViewModel>(R.layout.fragment_onboarding) {

    override val viewModel = OnboardingViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.viewPager.adapter = OnboardingAdapter(childFragmentManager)
    }

    fun navigateToHome() {
        (parentFragment as? HomeContainerFragment)?.navigateToHome()
    }
}