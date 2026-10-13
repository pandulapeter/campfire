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
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncConsentOriginTest {

    @Test
    fun `a connect tapped on the settings screen is answered there`() {
        assertEquals(SyncConsentOrigin.SETTINGS, syncConsentOriginOf(CampfireDestination.Settings))
    }

    @Test
    fun `a connect tapped in a first run's onboarding is answered there`() {
        assertEquals(SyncConsentOrigin.ONBOARDING, syncConsentOriginOf(CampfireDestination.Onboarding(isOpenedFromSettings = false)))
    }

    @Test
    fun `a connect tapped in an onboarding opened from the settings is answered there`() {
        assertEquals(SyncConsentOrigin.ONBOARDING_FROM_SETTINGS, syncConsentOriginOf(CampfireDestination.Onboarding(isOpenedFromSettings = true)))
    }

    @Test
    fun `an answer on the songs or on the settings screen goes where the connect was tapped`() {
        listOf(listOf(CampfireDestination.Songs), listOf(CampfireDestination.Songs, CampfireDestination.Settings)).forEach { backStack ->
            assertEquals(SyncConsentAnswer.ONBOARDING, syncConsentAnswerOf(backStack, OnboardingStep.WELCOME, SyncConsentOrigin.ONBOARDING))
            assertEquals(SyncConsentAnswer.ONBOARDING_FROM_SETTINGS, syncConsentAnswerOf(backStack, OnboardingStep.WELCOME, SyncConsentOrigin.ONBOARDING_FROM_SETTINGS))
            assertEquals(SyncConsentAnswer.SETTINGS, syncConsentAnswerOf(backStack, OnboardingStep.WELCOME, SyncConsentOrigin.SETTINGS))
            assertEquals(SyncConsentAnswer.SETTINGS, syncConsentAnswerOf(backStack, OnboardingStep.WELCOME, origin = null))
        }
    }

    @Test
    fun `a first run's onboarding still on its first page is moved on to the dropbox page`() {
        val backStack = listOf(CampfireDestination.Songs, CampfireDestination.Onboarding(isOpenedFromSettings = false))
        (SyncConsentOrigin.entries + null).forEach { origin ->
            assertEquals(SyncConsentAnswer.ONBOARDING_ON_SYNC_PAGE, syncConsentAnswerOf(backStack, OnboardingStep.WELCOME, origin))
            OnboardingStep.entries.drop(1).forEach { step -> assertEquals(SyncConsentAnswer.NOTHING, syncConsentAnswerOf(backStack, step, origin)) }
        }
    }

    @Test
    fun `an answer is not shown over a stack the user has built on`() = listOf(
        listOf(CampfireDestination.Songs, CampfireDestination.Settings, CampfireDestination.Onboarding(isOpenedFromSettings = true)),
        listOf(CampfireDestination.Songs, CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = null, initialIndex = 0)),
        listOf(CampfireDestination.Songs, CampfireDestination.Setlists),
    ).forEach { backStack ->
        (SyncConsentOrigin.entries + null).forEach { origin ->
            assertEquals(SyncConsentAnswer.NOTHING, syncConsentAnswerOf(backStack, OnboardingStep.WELCOME, origin))
        }
    }
}
