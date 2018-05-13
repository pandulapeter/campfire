package com.pandulapeter.campfire.feature.home.options

import android.os.Bundle
import android.support.design.widget.TabLayout
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsBinding
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.addPageScrollListener
import com.pandulapeter.campfire.util.withArguments

class OptionsFragment : TopLevelFragment<FragmentOptionsBinding, OptionsViewModel>(R.layout.fragment_options) {

    companion object {
        private var Bundle.shouldOpenChangelog by BundleArgumentDelegate.Boolean("shouldOpenChangelog")

        fun newInstance(shouldOpenChangelog: Boolean) = OptionsFragment().withArguments { it.shouldOpenChangelog = shouldOpenChangelog }
    }

    override val viewModel = OptionsViewModel()
    override val appBarView by lazy {
        TabLayout(mainActivity.toolbarContext).apply {
            tabMode = TabLayout.MODE_SCROLLABLE
            setupWithViewPager(binding.viewPager)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        defaultToolbar.updateToolbarTitle(R.string.home_options)
        binding.viewPager.adapter = OptionsFragmentPagerAdapter(mainActivity, childFragmentManager)
        binding.viewPager.addPageScrollListener(onPageSelected = {
            when (it) {
                0 -> analyticsManager.onOptionsScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_OPTIONS_PREFERENCES)
                1 -> analyticsManager.onOptionsScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_OPTIONS_WHAT_IS_NEW)
                2 -> analyticsManager.onOptionsScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_OPTIONS_ABOUT)
            }
        })
        binding.viewPager.currentItem = if (arguments?.shouldOpenChangelog == true) 1 else 0
    }
}