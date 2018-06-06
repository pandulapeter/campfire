package com.pandulapeter.campfire.feature.main.home.onboarding

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

class OnboardingViewModel(
    private val skip: () -> Unit,
    private val navigateToNextPage: () -> Unit
) : CampfireViewModel() {

    val isOnLastPage = ObservableBoolean()

    fun onSkipButtonClicked() = skip()

    fun onNextButtonClicked() = navigateToNextPage()
}