package com.pandulapeter.campfire.feature.main.home.onboarding.page.welcome

import com.pandulapeter.campfire.feature.shared.CampfireViewModel

class WelcomeViewModel(private val openHome: () -> Unit) : CampfireViewModel() {

    fun onOpenHomeButtonClicked() = openHome()
}