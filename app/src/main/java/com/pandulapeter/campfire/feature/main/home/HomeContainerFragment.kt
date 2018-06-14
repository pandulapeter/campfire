package com.pandulapeter.campfire.feature.main.home

import android.os.Bundle
import android.transition.Slide
import android.view.Gravity
import android.view.View
import com.crashlytics.android.Crashlytics
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.databinding.FragmentHomeContainerBinding
import com.pandulapeter.campfire.feature.main.home.home.HomeFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.OnboardingFragment
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.integration.AnalyticsManager
import io.fabric.sdk.android.Fabric
import org.koin.android.ext.android.inject

class HomeContainerFragment : TopLevelFragment<FragmentHomeContainerBinding, HomeContainerViewModel>(R.layout.fragment_home_container) {

    override val viewModel = HomeContainerViewModel()
    override val shouldShowAppBar get() = childFragmentManager.findFragmentById(R.id.home_container) is HomeFragment
    private val preferenceDatabase by inject<PreferenceDatabase>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        analyticsManager.onTopLevelScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
        defaultToolbar.updateToolbarTitle(R.string.main_home)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction().replace(R.id.home_container, OnboardingFragment()).commit()
        }
    }

    fun navigateToHome() {
        //TODO: Save the fact that the user is done with the onboarding flow.

        // Set up crash reporting.
        @Suppress("ConstantConditionIf")
        if (preferenceDatabase.shouldShareCrashReports && BuildConfig.BUILD_TYPE != "debug") {
            Fabric.with(requireContext().applicationContext, Crashlytics())
        }

        // Inflate the Home Fragment.
        childFragmentManager
            .beginTransaction()
            .replace(R.id.home_container, HomeFragment().apply { enterTransition = Slide(Gravity.BOTTOM) })
            .commit()
    }
}