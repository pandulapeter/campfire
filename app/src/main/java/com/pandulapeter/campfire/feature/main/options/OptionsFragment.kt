package com.pandulapeter.campfire.feature.main.options

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.databinding.DataBindingUtil
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsBinding
import com.pandulapeter.campfire.databinding.ViewOptionsTabsBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.feature.shared.behavior.TopLevelBehavior
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.addPageScrollListener
import com.pandulapeter.campfire.util.withArguments
import org.koin.androidx.viewmodel.ext.android.viewModel

class OptionsFragment : CampfireFragment<FragmentOptionsBinding, OptionsViewModel>(R.layout.fragment_options), TopLevelFragment {

    override val viewModel by viewModel<OptionsViewModel>()
    private val pagerAdapter by lazy { OptionsFragmentPagerAdapter(requireContext(), childFragmentManager) }
    override val topLevelBehavior by lazy {
        TopLevelBehavior(
            getContext = { context },
            getCampfireActivity = { getCampfireActivity() },
            appBarView = DataBindingUtil.inflate<ViewOptionsTabsBinding>(
                LayoutInflater.from(getCampfireActivity()?.toolbarContext), R.layout.view_options_tabs, null, false
            ).apply {
                tabLayout.setupWithViewPager(binding.viewPager)
            }.root
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        topLevelBehavior.defaultToolbar.updateToolbarTitle(R.string.main_options)
        topLevelBehavior.onViewCreated(savedInstanceState)
        setupViewPager()
    }

    fun navigateToChangelog() {
        binding.viewPager.currentItem = 1
    }

    private fun setupViewPager() {
        binding.viewPager.apply {
            adapter = pagerAdapter
            offscreenPageLimit = 2
            addPageScrollListener(onPageSelected = {
                analyticsManager.run {
                    when (it) {
                        0 -> onOptionsScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_OPTIONS_PREFERENCES)
                        1 -> onOptionsScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_OPTIONS_WHAT_IS_NEW)
                        2 -> onOptionsScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_OPTIONS_ABOUT)
                    }
                }
            })
            if (arguments?.shouldOpenChangelog == true) {
                currentItem = 1
            } else {
                analyticsManager.onOptionsScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_OPTIONS_PREFERENCES)
            }
        }
    }

    companion object {

        private var Bundle.shouldOpenChangelog by BundleArgumentDelegate.Boolean("shouldOpenChangelog")

        fun newInstance(shouldOpenChangelog: Boolean) = OptionsFragment().withArguments { it.shouldOpenChangelog = shouldOpenChangelog }
    }
}