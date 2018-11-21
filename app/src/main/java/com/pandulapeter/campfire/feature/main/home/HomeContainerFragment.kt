package com.pandulapeter.campfire.feature.main.home

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import com.crashlytics.android.Crashlytics
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.databinding.FragmentHomeContainerBinding
import com.pandulapeter.campfire.feature.main.home.home.HomeFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.OnboardingFragment
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.integration.AnalyticsManager
import io.fabric.sdk.android.Fabric
import org.koin.android.ext.android.inject

class HomeContainerFragment : TopLevelFragment<FragmentHomeContainerBinding, HomeContainerViewModel>(R.layout.fragment_home_container) {

    override val viewModel = HomeContainerViewModel()
    override val shouldShowAppBar get() = preferenceDatabase.isOnboardingDone
    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val currentFragment get() = childFragmentManager.findFragmentById(R.id.home_container)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (preferenceDatabase.isOnboardingDone) {
            postponeEnterTransition()
        }
        super.onViewCreated(view, savedInstanceState)
        analyticsManager.onTopLevelScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
        defaultToolbar.updateToolbarTitle(R.string.main_home)
        if (savedInstanceState == null) {
            if (preferenceDatabase.isOnboardingDone) {
                childFragmentManager.handleReplace { HomeFragment() }
            } else {
                childFragmentManager.handleReplace { OnboardingFragment() }
            }
        }
    }

    override fun updateUI() {
        super.updateUI()
        (currentFragment as? HomeFragment)?.updateUI()
    }

    override fun onBackPressed() = (currentFragment as? HomeFragment)?.onBackPressed() == true

    override fun onNavigationItemSelected(menuItem: MenuItem) =
        (currentFragment as? HomeFragment)?.onNavigationItemSelected(menuItem) ?: super.onNavigationItemSelected(menuItem)

    fun navigateToHome() {
        // Save the fact that the user is done with the onboarding flow.
        preferenceDatabase.isOnboardingDone = true

        // Set up crash reporting.
        @Suppress("ConstantConditionIf")
        if (preferenceDatabase.shouldShareCrashReports && BuildConfig.BUILD_TYPE != "debug") {
            Fabric.with(requireContext().applicationContext, Crashlytics())
        }

        // Inflate the Home Fragment.
        childFragmentManager
            .beginTransaction()
            .replace(R.id.home_container, HomeFragment.newInstance(true))
            .commit()
    }

    private inline fun <reified T : CampfireFragment<*, *>> androidx.fragment.app.FragmentManager.handleReplace(
        tag: String = T::class.java.name,
        crossinline newInstance: () -> T
    ) = beginTransaction()
        .replace(R.id.home_container, findFragmentByTag(tag) ?: newInstance.invoke(), tag)
        .commit()
}