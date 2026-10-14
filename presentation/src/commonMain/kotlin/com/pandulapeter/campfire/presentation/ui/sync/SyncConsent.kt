/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.sync

import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.Navigator
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab
import com.pandulapeter.campfire.presentation.ui.platform.keepSyncConsentOrigin
import com.pandulapeter.campfire.presentation.ui.platform.takeSyncConsentOrigin
import com.pandulapeter.campfire.presentation.ui.screens.onboarding.OnboardingStep

/**
 * The screen a Connect was tapped on, which is where the answer to its consent page is shown: the onboarding's Dropbox
 * page, or the sync section of Settings.
 */
internal enum class SyncConsentOrigin {
    SETTINGS,
    ONBOARDING,
    ONBOARDING_FROM_SETTINGS,
}

/** Where a Connect tapped with [topDestination] on top of the back stack was tapped: Settings unless it is the onboarding. */
internal fun syncConsentOriginOf(topDestination: CampfireDestination?) = when {
    topDestination !is CampfireDestination.Onboarding -> SyncConsentOrigin.SETTINGS
    topDestination.isOpenedFromSettings -> SyncConsentOrigin.ONBOARDING_FROM_SETTINGS
    else -> SyncConsentOrigin.ONBOARDING
}

/** What showing a consent's answer does to the app, see [showSyncConsentAnswer]. */
internal enum class SyncConsentAnswer {
    NOTHING,
    ONBOARDING,
    ONBOARDING_FROM_SETTINGS,
    SETTINGS,
    ONBOARDING_ON_SYNC_PAGE,
}

/**
 * Where the answer to a consent asked from [origin] goes on [backStack]: only onto a stack nobody has built on since the
 * app started — the songs, the settings screen Android brings back, or an owed onboarding still on its first page
 * ([onboardingStep]), which a start up that still owes it opens while the code is being exchanged, and
 * which is moved on to its Dropbox page rather than covered by Settings.
 */
internal fun syncConsentAnswerOf(backStack: List<CampfireDestination>, onboardingStep: OnboardingStep, origin: SyncConsentOrigin?) = when {
    backStack == listOf(CampfireDestination.Songs, CampfireDestination.Onboarding(isOpenedFromSettings = false)) ->
        if (onboardingStep == OnboardingStep.entries.first()) SyncConsentAnswer.ONBOARDING_ON_SYNC_PAGE else SyncConsentAnswer.NOTHING
    backStack != listOf(CampfireDestination.Songs) && backStack != listOf(CampfireDestination.Songs, CampfireDestination.Settings) -> SyncConsentAnswer.NOTHING
    origin == SyncConsentOrigin.ONBOARDING -> SyncConsentAnswer.ONBOARDING
    origin == SyncConsentOrigin.ONBOARDING_FROM_SETTINGS -> SyncConsentAnswer.ONBOARDING_FROM_SETTINGS
    else -> SyncConsentAnswer.SETTINGS
}

/** Remembers where a Connect was tapped, see [keepSyncConsentOrigin], from the screen on top. */
internal fun Navigator.rememberSyncConsentOrigin() = keepSyncConsentOrigin(syncConsentOriginOf(backStack.lastOrNull()))

/**
 * Shows the answer to a consent page the app was sent away to, which is where the user was when they left
 * ([takeSyncConsentOrigin]): the onboarding's Dropbox page, over the settings screen's About tab where that was what opened it, or
 * Settings, on the tab holding the sync section. Only onto a stack nobody has built on since the app started - the
 * songs the app opens on, the settings screen Android brings back after reclaiming the process behind the browser, or
 * an owed onboarding still on its first page, which a start up that still owes it opens while the
 * code is being exchanged and which is moved on to its Dropbox page (see [syncConsentAnswerOf]); an onboarding
 * Android brings back is already on the page that says how it went. The code exchange this
 * follows can take a minute on a bad network, and a user who has gone somewhere else meanwhile has moved on: the
 * settings screen says the same thing whenever they get there, and an editor they opened in the meantime holds text
 * that nothing but its own ways out may take away.
 */
internal fun Navigator.showSyncConsentAnswer(origin: SyncConsentOrigin?) {
    when (syncConsentAnswerOf(backStack = backStack.toList(), onboardingStep = onboardingStep, origin = origin)) {
        SyncConsentAnswer.NOTHING -> Unit
        SyncConsentAnswer.ONBOARDING -> openOnboarding(isOpenedFromSettings = false, step = OnboardingStep.SYNC)
        SyncConsentAnswer.ONBOARDING_FROM_SETTINGS -> {
            openSettingsOn(SettingsTab.ABOUT)
            openOnboarding(isOpenedFromSettings = true, step = OnboardingStep.SYNC)
        }
        SyncConsentAnswer.SETTINGS -> openSettingsOn(SettingsTab.LIBRARY)
        SyncConsentAnswer.ONBOARDING_ON_SYNC_PAGE -> showOnboardingStepIfOpen(OnboardingStep.SYNC)
    }
}

private fun Navigator.openSettingsOn(tab: SettingsTab) {
    // Not through the re-selection of an open settings screen, which would send it back to General a moment after.
    if (backStack.lastOrNull() != CampfireDestination.Settings) selectTopLevelDestination(CampfireDestination.Settings)
    settingsTab = tab
}
