package com.pandulapeter.campfire.feature.main.home

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.crashlytics.android.Crashlytics
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentHomeContainerBinding
import com.pandulapeter.campfire.feature.main.home.home.HomeFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.OnboardingFragment
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.feature.shared.behavior.TopLevelBehavior
import com.pandulapeter.campfire.integration.AnalyticsManager
import io.fabric.sdk.android.Fabric
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeContainerFragment : CampfireFragment<FragmentHomeContainerBinding, HomeContainerViewModel>(R.layout.fragment_home_container), TopLevelFragment {

    override val viewModel by viewModel<HomeContainerViewModel>()
    override val shouldShowAppBar get() = viewModel.preferenceDatabase.isOnboardingDone
    private val currentFragment get() = childFragmentManager.findFragmentById(R.id.home_container) as? CampfireFragment<*, *>?
    override val topLevelBehavior by lazy { TopLevelBehavior(getCampfireActivity = { getCampfireActivity() }) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        topLevelBehavior.onViewCreated(savedInstanceState)
        if (viewModel.preferenceDatabase.isOnboardingDone) {
            postponeEnterTransition()
        }
        super.onViewCreated(view, savedInstanceState)
        analyticsManager.onTopLevelScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
        topLevelBehavior.defaultToolbar.updateToolbarTitle(R.string.main_home)
        if (savedInstanceState == null && currentFragment == null) {
            if (viewModel.preferenceDatabase.isOnboardingDone) {
                childFragmentManager.handleReplace { HomeFragment.newInstance(false) }
            } else {
                childFragmentManager.handleReplace { OnboardingFragment.newInstance() }
            }
        }
    }

    override fun updateUI() {
        super.updateUI()
        (currentFragment as? HomeFragment)?.updateUI()
    }

    override fun onBackPressed() = currentFragment?.onBackPressed() == true

    override fun onNavigationItemSelected(menuItem: MenuItem) =
        (currentFragment as? HomeFragment)?.onNavigationItemSelected(menuItem) ?: super.onNavigationItemSelected(menuItem)

    fun navigateToHome() {
        viewModel.preferenceDatabase.isOnboardingDone = true
        @Suppress("ConstantConditionIf")
        if (viewModel.preferenceDatabase.shouldShareCrashReports && BuildConfig.BUILD_TYPE != "debug") {
            Fabric.with(requireContext().applicationContext, Crashlytics())
        }
        if (getCampfireActivity()?.wasStartedFromDeepLink() == true) {
            getCampfireActivity()?.openSharedWithYouScreen()
        } else {
            childFragmentManager.handleReplace { HomeFragment.newInstance(true) }
        }
    }

    private inline fun <reified T : Fragment> FragmentManager.handleReplace(
        tag: String = T::class.java.name,
        crossinline newInstance: () -> T
    ) = beginTransaction()
        .replace(R.id.home_container, findFragmentByTag(tag) ?: newInstance.invoke(), tag)
        .commit()

    companion object {

        fun newInstance() = HomeContainerFragment()
    }
}