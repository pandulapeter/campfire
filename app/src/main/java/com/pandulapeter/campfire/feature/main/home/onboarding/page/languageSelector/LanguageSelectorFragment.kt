package com.pandulapeter.campfire.feature.main.home.onboarding.page.languageSelector

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingLanguageSelectorBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.waitForLayout

class LanguageSelectorFragment : CampfireFragment<FragmentOnboardingLanguageSelectorBinding, LanguageSelectorViewModel>(R.layout.fragment_onboarding_language_selector) {

    override val viewModel = LanguageSelectorViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.linearLayout.apply {
            waitForLayout {
                if (isAdded) {
                    layoutParams = (layoutParams as FrameLayout.LayoutParams).apply { setMargins(0, -getCampfireActivity().toolbarHeight, 0, 0) }
                }
            }
        }
    }
}