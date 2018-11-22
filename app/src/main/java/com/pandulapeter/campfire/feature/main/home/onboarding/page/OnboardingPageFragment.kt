package com.pandulapeter.campfire.feature.main.home.onboarding.page

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.databinding.ViewDataBinding
import com.pandulapeter.campfire.feature.shared.deprecated.OldCampfireFragment
import com.pandulapeter.campfire.feature.shared.deprecated.OldCampfireViewModel

abstract class OnboardingPageFragment<B : ViewDataBinding, VM : OldCampfireViewModel>(@LayoutRes layoutResourceId: Int) : OldCampfireFragment<B, VM>(layoutResourceId) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.tag = binding
    }
}