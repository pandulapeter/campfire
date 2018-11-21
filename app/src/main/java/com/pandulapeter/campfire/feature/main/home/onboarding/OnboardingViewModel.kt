package com.pandulapeter.campfire.feature.main.home.onboarding

import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableFloat
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

class OnboardingViewModel(
    private val skip: () -> Unit,
    private val navigateToNextPage: () -> Unit
) : CampfireViewModel() {

    val doneButtonOffset = ObservableFloat()
    val canSkip = ObservableBoolean()
    val shouldShowLegalDocuments = ObservableBoolean()

    fun onSkipButtonClicked() = skip()

    fun onNextButtonClicked() = navigateToNextPage()

    fun onLegalDocumentsClicked() = shouldShowLegalDocuments.set(true)
}