package com.pandulapeter.campfire.feature.main.home.onboarding.page.userData

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingUserDataBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.waitForLayout

class UserDataFragment : CampfireFragment<FragmentOnboardingUserDataBinding, UserDataViewModel>(R.layout.fragment_onboarding_user_data) {

    override val viewModel = UserDataViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.linearLayout.apply {
            waitForLayout {
                layoutParams = (layoutParams as FrameLayout.LayoutParams).apply { setMargins(0, -getCampfireActivity().toolbarHeight, 0, 0) }
            }
        }
    }
}