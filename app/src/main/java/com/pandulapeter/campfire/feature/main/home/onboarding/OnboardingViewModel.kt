package com.pandulapeter.campfire.feature.main.home.onboarding

import android.databinding.ObservableBoolean
import android.databinding.ObservableFloat
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

class OnboardingViewModel(
    private val skip: () -> Unit,
    private val navigateToNextPage: () -> Unit
) : CampfireViewModel() {

    val doneButtonOffset = ObservableFloat()
    val canSkip = ObservableBoolean(true)

    fun onSkipButtonClicked() = skip()

    fun onNextButtonClicked() = navigateToNextPage()
}