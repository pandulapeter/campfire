package com.pandulapeter.campfire.feature.home.options

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsBinding
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.util.onPageSelected

class OptionsFragment : TopLevelFragment<FragmentOptionsBinding, OptionsViewModel>(R.layout.fragment_options) {

    override val viewModel = OptionsViewModel()

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        defaultToolbar.updateToolbarTitle(R.string.home_options)
        binding.viewPager.adapter = OptionsFragmentPagerAdapter(context, childFragmentManager)
        binding.viewPager.onPageSelected { mainActivity.expandAppBar() }
        mainActivity.enableTabLayout(binding.viewPager)
    }
}