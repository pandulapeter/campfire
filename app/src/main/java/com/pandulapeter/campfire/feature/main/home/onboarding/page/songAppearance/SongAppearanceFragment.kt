package com.pandulapeter.campfire.feature.main.home.onboarding.page.songAppearance

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingSongAppearanceBinding
import com.pandulapeter.campfire.feature.main.home.onboarding.page.OnboardingPageFragment

class SongAppearanceFragment : OnboardingPageFragment<FragmentOnboardingSongAppearanceBinding, SongAppearanceViewModel>(R.layout.fragment_onboarding_song_appearance) {

    override val viewModel = SongAppearanceViewModel()

    override fun onResume() {
        super.onResume()
        viewModel.initialize()
    }
}