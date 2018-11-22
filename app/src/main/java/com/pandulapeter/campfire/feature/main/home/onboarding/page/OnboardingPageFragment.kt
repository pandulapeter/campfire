package com.pandulapeter.campfire.feature.main.home.onboarding.page

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.databinding.ViewDataBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.OldCampfireViewModel

abstract class OnboardingPageFragment<B : ViewDataBinding, VM : OldCampfireViewModel>(@LayoutRes layoutResourceId: Int) : CampfireFragment<B, VM>(layoutResourceId) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.tag = binding
    }
}