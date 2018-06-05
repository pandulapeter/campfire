package com.pandulapeter.campfire.feature.main.home.onboarding.page

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingPageBinding
import com.pandulapeter.campfire.feature.main.home.onboarding.OnboardingFragment
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.waitForLayout

class OnboardingPageFragment : CampfireFragment<FragmentOnboardingPageBinding, OnboardingPageViewModel>(R.layout.fragment_onboarding_page) {

    override val viewModel = OnboardingPageViewModel { (parentFragment as? OnboardingFragment)?.navigateToHome() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.linearLayout.apply {
            waitForLayout {
                layoutParams = (layoutParams as FrameLayout.LayoutParams).apply { setMargins(0, -getCampfireActivity().toolbarHeight, 0, 0) }
            }
        }
    }
}