package com.pandulapeter.campfire.feature.main.home.home

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentHomeBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment

class HomeFragment : CampfireFragment<FragmentHomeBinding, HomeViewModel>(R.layout.fragment_home) {

    override val viewModel = HomeViewModel(
        openCollections = { getCampfireActivity().openCollectionsScreen() },
        openSongs = { getCampfireActivity().openSongsScreen() }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        getCampfireActivity().onScreenChanged()
    }
}