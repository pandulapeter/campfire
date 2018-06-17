package com.pandulapeter.campfire.feature.main.home.onboarding.page.contentLanguage

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingContentLanguageBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment

class ContentLanguageFragment : CampfireFragment<FragmentOnboardingContentLanguageBinding, ContentLanguageViewModel>(R.layout.fragment_onboarding_content_language) {

    override val viewModel = ContentLanguageViewModel()
}