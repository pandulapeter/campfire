/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.scales

import com.pandulapeter.campfire.chordpro.scales.ScaleFamily
import com.pandulapeter.campfire.chordpro.scales.Scales
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScaleLibraryTest {

    @Test
    fun `the first chip lists the common scales and every other one a family`() {
        assertEquals(Scales.common, scaleLibraryScales(0))
        ScaleFamily.entries.forEachIndexed { index, family -> assertEquals(Scales.of(family), scaleLibraryScales(index + 1)) }
    }

    @Test
    fun `a family index this version has no family for lists the common scales`() =
        assertEquals(Scales.common, scaleLibraryScales(ScaleFamily.entries.size + 1))

    private val hungarian = mapOf(
        "major" to "Dúr",
        "dorian" to "Dór",
        "phrygian_dominant" to "Fríg domináns",
        "dorian_sharp_4" to "Dór ♯4",
        "mixolydian_flat_6" to "Mixolíd ♭6",
        "locrian_natural_6" to "Lokriszi ♮6",
        "diminished" to "Szűkített (egész–fél)",
    )

    private val hungarianAliases = mapOf("double_harmonic" to "Bizánci, Cigány dúr", "melodic_minor" to "Jazz-moll")

    private fun search(query: String) =
        scaleLibraryResults(familyIndex = 3, query = query, nameOf = { hungarian[it.id] ?: it.id }, aliasesOf = { hungarianAliases[it.id] }).map { it.id }

    @Test
    fun `a blank search lists the family picked`() = assertEquals(scaleLibraryScales(3), scaleLibraryResults(3, "  ", { it.id }, { null }))

    @Test
    fun `a search finds scales of every family by their name, ignoring case and accents`() {
        assertEquals(listOf("dorian", "dorian_sharp_4", "phrygian_dominant", "mixolydian_flat_6"), search("DO").filter { it in hungarian })
        assertEquals("major", search("dur").first())
        assertTrue("phrygian_dominant" in search("fríg"))
    }

    @Test
    fun `a search finds a scale by another name, after the ones found by theirs`() {
        assertEquals(listOf("phrygian_dominant", "double_harmonic"), search("hijaz"))
        val ionian = search("ionian")
        assertEquals("major", ionian.last())
    }

    @Test
    fun `a search finds a scale by another name in the app's language`() {
        assertEquals(listOf("double_harmonic"), search("cigany dur"))
        assertEquals(listOf("melodic_minor"), search("jazz-moll"))
        assertTrue("melodic_minor" in search("jazz minor"))
    }

    @Test
    fun `a search finds a scale by its signs typed as a keyboard has them`() {
        assertEquals(listOf("dorian_sharp_4"), search("dor #4"))
        assertEquals(listOf("mixolydian_flat_6"), search("mixolid b6"))
        assertEquals(listOf("locrian_natural_6"), search("lokriszi 6"))
        assertTrue("locrian_natural_6" in search("locrian natural 6"))
        assertEquals(listOf("diminished"), search("egesz-fel"))
    }

    @Test
    fun `a search no scale matches finds nothing`() = assertTrue(search("maqam").isEmpty())
}
