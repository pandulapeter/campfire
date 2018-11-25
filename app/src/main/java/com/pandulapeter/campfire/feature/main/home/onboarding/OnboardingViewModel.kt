package com.pandulapeter.campfire.feature.main.home.onboarding

import androidx.lifecycle.MutableLiveData
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

class OnboardingViewModel : CampfireViewModel() {

    val doneButtonOffset = MutableLiveData<Float>()
    val canSkip = MutableLiveData<Boolean>()
    val shouldShowLegalDocuments = MutableLiveData<Boolean?>()
    val shouldNavigateToNextPage = MutableLiveData<Boolean?>()
    val shouldSkip = MutableLiveData<Boolean?>()

    fun onSkipButtonClicked() {
        if (!isUiBlocked) {
            shouldSkip.value = true
        }
    }

    fun onNextButtonClicked() {
        if (!isUiBlocked) {
            shouldNavigateToNextPage.value = true
        }
    }

    fun onLegalDocumentsClicked() {
        if (!isUiBlocked) {
            shouldShowLegalDocuments.value = true
        }
    }
}