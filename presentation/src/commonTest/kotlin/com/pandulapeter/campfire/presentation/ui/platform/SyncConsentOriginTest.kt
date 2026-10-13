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
}
