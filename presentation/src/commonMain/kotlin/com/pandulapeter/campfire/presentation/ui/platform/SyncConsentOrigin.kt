/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
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

/** What showing a consent's answer does to the app, see `CampfireViewModel.showSyncConsentAnswer`. */
internal enum class SyncConsentAnswer {
    NOTHING,
    ONBOARDING,
    ONBOARDING_FROM_SETTINGS,
    SETTINGS,
    ONBOARDING_ON_SYNC_PAGE,
}

/**
 * Where the answer to a consent asked from [origin] goes on [backStack]: only onto a stack nobody has built on since the
 * app started — the songs, the settings screen Android brings back, or a first run's onboarding still on its first page
 * ([onboardingStep]), which a start up that still counts as a first one opens while the code is being exchanged, and
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

/**
 * Remembers [origin] for [takeSyncConsentOrigin], across the start up the consent page may be answered in. Only the web
 * keeps it, since only there does every consent end in a new start: the app lives on everywhere else, and an Android
 * process reclaimed behind the browser comes back on its saved back stack, which already says where the tap was made.
 */
internal expect fun keepSyncConsentOrigin(origin: SyncConsentOrigin)

/** What [keepSyncConsentOrigin] remembered, forgotten as it is read; null where nothing was, or nothing could be. */
internal expect fun takeSyncConsentOrigin(): SyncConsentOrigin?
