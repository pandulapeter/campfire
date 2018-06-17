package com.pandulapeter.campfire.feature.main.home.onboarding.page.musicianType

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingMusicianTypeBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment

class MusicianTypeFragment : CampfireFragment<FragmentOnboardingMusicianTypeBinding, MusicianTypeViewModel>(R.layout.fragment_onboarding_musician_type) {

    override val viewModel = MusicianTypeViewModel()
}