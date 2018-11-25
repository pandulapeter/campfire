package com.pandulapeter.campfire.feature.main.home.onboarding.songAppearance

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingSongAppearanceBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import org.koin.androidx.viewmodel.ext.android.viewModel

class SongAppearanceFragment : CampfireFragment<FragmentOnboardingSongAppearanceBinding, SongAppearanceViewModel>(R.layout.fragment_onboarding_song_appearance) {

    override val viewModel by viewModel<SongAppearanceViewModel>()
}