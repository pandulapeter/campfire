package com.pandulapeter.campfire.feature.home.options

import android.os.Bundle
import android.support.design.widget.TabLayout
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsBinding
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
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
        mainActivity.shouldAllowAppBarScrolling = true
        binding.viewPager.adapter = OptionsFragmentPagerAdapter(mainActivity, childFragmentManager)
        binding.viewPager.addPageScrollListener(onPageSelected = { mainActivity.expandAppBar() })
        if (arguments?.shouldOpenChangelog == true) {
            binding.viewPager.currentItem = 1
        }
    }
}