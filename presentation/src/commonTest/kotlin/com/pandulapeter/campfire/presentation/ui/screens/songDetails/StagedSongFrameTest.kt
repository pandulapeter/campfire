/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.preferences.UserPreferences.Notation
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StagedSongFrameTest {

    private val everything = SongLook(
        shouldShowChords = true,
        hasChordDiagrams = true,
        hasScaleSuggestions = true,
        showsDegrees = true,
        chordSpelling = UserPreferences.ChordSpelling(accidentals = UserPreferences.Accidentals.ORIGINAL, notation = Notation.STANDARD),
        shouldNumberSections = false,
        shouldShowTempo = true,
    )

    @Test
    fun `switching the chords off takes everything they bring`() {
        val parts = leavingPartsOf(everything, everything.copy(shouldShowChords = false, hasChordDiagrams = false, hasScaleSuggestions = false))
        assertTrue(parts.chords && parts.chordsSection && parts.scalesSection && parts.instrumentPill)
        assertFalse(parts.timing)
    }

    @Test
    fun `switching the diagrams off keeps the chords over the lyrics`() {
        val parts = leavingPartsOf(everything, everything.copy(hasChordDiagrams = false))
        assertFalse(parts.chords)
        assertTrue(parts.chordsSection && parts.instrumentPill)
    }

    @Test
    fun `switching something on takes nothing away`() {
        val shown = everything.copy(shouldShowChords = false, hasChordDiagrams = false, hasScaleSuggestions = false, shouldShowTempo = false)
        assertTrue(leavingPartsOf(shown, everything).isEmpty)
    }

    @Test
    fun `a change of spelling takes nothing away`() {
        val parts = leavingPartsOf(everything, everything.copy(chordSpelling = everything.chordSpelling.copy(accidentals = UserPreferences.Accidentals.FLATS)))
        assertTrue(parts.isEmpty)
    }
}
