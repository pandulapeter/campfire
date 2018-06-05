package com.pandulapeter.campfire.feature.main.home.onboarding

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingBinding
import com.pandulapeter.campfire.feature.main.home.HomeContainerFragment
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.waitForLayout

class OnboardingFragment : CampfireFragment<FragmentOnboardingBinding, OnboardingViewModel>(R.layout.fragment_onboarding) {

    override val viewModel = OnboardingViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.viewPager.adapter = OnboardingAdapter(childFragmentManager)
        binding.viewPager.run {
            waitForLayout {
                layoutParams = (layoutParams as FrameLayout.LayoutParams).apply { setMargins(0, -getCampfireActivity().toolbarHeight, 0, 0) }
            }
        }
    }

    fun navigateToHome() {
        (parentFragment as? HomeContainerFragment)?.navigateToHome()
    }
}