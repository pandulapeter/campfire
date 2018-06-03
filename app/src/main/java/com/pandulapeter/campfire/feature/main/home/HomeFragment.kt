package com.pandulapeter.campfire.feature.main.home

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentHomeBinding
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.integration.AnalyticsManager

class HomeFragment : TopLevelFragment<FragmentHomeBinding, HomeViewModel>(R.layout.fragment_home) {

    override val viewModel = HomeViewModel { getCampfireActivity().openSongsScreen() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        analyticsManager.onTopLevelScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
        defaultToolbar.updateToolbarTitle(R.string.main_home)
    }
}