package com.pandulapeter.campfire.feature.home.options

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsBinding
import com.pandulapeter.campfire.feature.CampfireFragment

class OptionsFragment : CampfireFragment<FragmentOptionsBinding>(R.layout.fragment_options) {

    override val fragmentPagerAdapter by lazy { OptionsFragmentPagerAdapter(context, childFragmentManager) }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        defaultToolbar.updateToolbarTitle(R.string.home_options)
        binding.viewPager.adapter = fragmentPagerAdapter
        mainActivity.tabLayout.setupWithViewPager(binding.viewPager)
    }
}