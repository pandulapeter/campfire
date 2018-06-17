package com.pandulapeter.campfire.feature.main.home.onboarding.page.contentLanguage

import android.support.v7.widget.AppCompatCheckBox
import android.view.ViewGroup
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingContentLanguageBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment

class ContentLanguageFragment : CampfireFragment<FragmentOnboardingContentLanguageBinding, ContentLanguageViewModel>(R.layout.fragment_onboarding_content_language) {

    override val viewModel = ContentLanguageViewModel {
        if (isAdded) {
            binding.languageContainer.apply {
                removeAllViews()
                it.forEach {
                    addView(AppCompatCheckBox(getCampfireActivity()).apply { setText(it.nameResource) }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
            }
        }
    }
}