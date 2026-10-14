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

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingSongPreviewTest {

    @Test
    fun `the chords are shown as written where the accidentals are left alone`() {
        assertEquals("C#m", onboardingPreviewChordNames(ChordNotation.STANDARD, UserPreferences.Accidentals.ORIGINAL)["C#m"])
    }

    @Test
    fun `flats respell the black key chord`() {
        assertEquals("Dbm", onboardingPreviewChordNames(ChordNotation.STANDARD, UserPreferences.Accidentals.FLATS)["C#m"])
    }

    @Test
    fun `the accidentals are applied before the notation`() {
        val names = onboardingPreviewChordNames(ChordNotation.LATIN, UserPreferences.Accidentals.FLATS)
        assertEquals("Rebm", names["C#m"])
        assertEquals("Si", names["B"])
    }

    @Test
    fun `german turns the fifth into an h`() {
        assertEquals("H", onboardingPreviewChordNames(ChordNotation.GERMAN, UserPreferences.Accidentals.SHARPS)["B"])
    }

    @Test
    fun `a numbering counts every chord from the key whatever the accidentals`() {
        assertEquals(
            onboardingPreviewChordNames(ChordNotation.NASHVILLE, UserPreferences.Accidentals.SHARPS),
            onboardingPreviewChordNames(ChordNotation.NASHVILLE, UserPreferences.Accidentals.FLATS),
        )
    }
}
