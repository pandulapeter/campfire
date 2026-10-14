/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import com.pandulapeter.campfire.presentation.ui.theme.CampfireColorScheme
import com.pandulapeter.campfire.presentation.ui.theme.ColorSchemePair
import com.pandulapeter.campfire.presentation.ui.theme.GrayColorScheme
import com.pandulapeter.campfire.presentation.ui.theme.MaterialColorSchemes
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingCelebrationTest {

    @Test
    fun `the app's own palette burns in its purple and its orange`() {
        assertEquals(
            listOf(CampfireColorScheme.light.primary, CampfireColorScheme.lightSecondAccent),
            CampfireColorScheme.lightCelebrationColors(),
        )
        assertEquals(
            listOf(CampfireColorScheme.dark.primary, CampfireColorScheme.darkSecondAccent),
            CampfireColorScheme.darkCelebrationColors(),
        )
    }

    @Test
    fun `a one color palette burns in two tones of its own color`() {
        listOf(MaterialColorSchemes.Blue, GrayColorScheme).forEach { pair ->
            assertEquals(listOf(pair.light.primary, pair.light.inversePrimary), pair.lightCelebrationColors())
            assertEquals(listOf(pair.dark.primary, pair.dark.inversePrimary), pair.darkCelebrationColors())
        }
    }

    private fun ColorSchemePair.lightCelebrationColors() = celebrationColors(
        primary = light.primary,
        secondAccent = lightSecondAccent,
        inversePrimary = light.inversePrimary,
    )

    private fun ColorSchemePair.darkCelebrationColors() = celebrationColors(
        primary = dark.primary,
        secondAccent = darkSecondAccent,
        inversePrimary = dark.inversePrimary,
    )
}
