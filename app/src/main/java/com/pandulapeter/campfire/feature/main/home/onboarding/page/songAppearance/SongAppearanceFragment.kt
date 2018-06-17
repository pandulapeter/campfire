package com.pandulapeter.campfire.feature.main.home.onboarding.page.songAppearance

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingSongAppearanceBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment

class SongAppearanceFragment : CampfireFragment<FragmentOnboardingSongAppearanceBinding, SongAppearanceViewModel>(R.layout.fragment_onboarding_song_appearance) {

    override val viewModel = SongAppearanceViewModel()
}