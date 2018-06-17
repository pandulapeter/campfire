package com.pandulapeter.campfire.feature.main.home.onboarding.page

import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.annotation.LayoutRes
import android.view.View
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

abstract class OnboardingPageFragment<B : ViewDataBinding, VM : CampfireViewModel>(@LayoutRes layoutResourceId: Int) : CampfireFragment<B, VM>(layoutResourceId) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.tag = binding
    }
}