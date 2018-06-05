package com.pandulapeter.campfire.feature.main.home.onboarding

import com.pandulapeter.campfire.feature.shared.CampfireViewModel

class OnboardingViewModel(
    private val skip: () -> Unit,
    private val navigateToNextPage: () -> Unit
) : CampfireViewModel() {

    fun onSkipButtonClicked() = skip()

    fun onNextButtonClicked() = navigateToNextPage()
}