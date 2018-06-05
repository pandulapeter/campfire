package com.pandulapeter.campfire.feature.main.home

import android.os.Bundle
import android.transition.Slide
import android.view.Gravity
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentHomeContainerBinding
import com.pandulapeter.campfire.feature.main.home.home.HomeFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.OnboardingFragment
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.integration.AnalyticsManager

class HomeContainerFragment : TopLevelFragment<FragmentHomeContainerBinding, HomeContainerViewModel>(R.layout.fragment_home_container) {

    override val viewModel = HomeContainerViewModel()
    override val shouldShowAppBar get() = childFragmentManager.findFragmentById(R.id.home_container) is HomeFragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        analyticsManager.onTopLevelScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
        defaultToolbar.updateToolbarTitle(R.string.main_home)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction().replace(R.id.home_container, OnboardingFragment()).commit()
        }
    }

    fun navigateToHome() {
        childFragmentManager
            .beginTransaction()
            .replace(R.id.home_container, HomeFragment().apply { enterTransition = Slide(Gravity.BOTTOM) })
            .commit()
    }
}