package com.pandulapeter.campfire.feature.main.home.onboarding

import com.pandulapeter.campfire.feature.shared.CampfireViewModel

class OnboardingViewModel(private val openHome: () -> Unit) : CampfireViewModel() {

    fun onOpenHomeButtonClicked() = openHome()
}