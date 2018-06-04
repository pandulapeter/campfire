package com.pandulapeter.campfire.feature.main.home.onboarding.page

import com.pandulapeter.campfire.feature.shared.CampfireViewModel

class OnboardingPageViewModel(private val openHome: () -> Unit) : CampfireViewModel() {

    fun onOpenHomeButtonClicked() = openHome()
}