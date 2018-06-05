package com.pandulapeter.campfire.feature.main.home.onboarding.page.musicianType

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingMusicianTypeBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.waitForLayout

class MusicianTypeFragment : CampfireFragment<FragmentOnboardingMusicianTypeBinding, MusicianTypeViewModel>(R.layout.fragment_onboarding_musician_type) {

    override val viewModel = MusicianTypeViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.linearLayout.apply {
            waitForLayout {
                layoutParams = (layoutParams as FrameLayout.LayoutParams).apply { setMargins(0, -getCampfireActivity().toolbarHeight, 0, 0) }
            }
        }
    }
}