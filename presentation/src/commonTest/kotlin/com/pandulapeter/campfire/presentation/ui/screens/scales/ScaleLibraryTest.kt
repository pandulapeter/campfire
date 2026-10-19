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

    private val hungarian = mapOf("major" to "Dúr", "dorian" to "Dór", "phrygian_dominant" to "Fríg domináns")

    private fun search(query: String) = scaleLibraryResults(familyIndex = 3, query = query) { hungarian[it.id] ?: it.id }.map { it.id }

    @Test
    fun `a blank search lists the family picked`() = assertEquals(scaleLibraryScales(3), scaleLibraryResults(3, "  ") { it.id })

    @Test
    fun `a search finds scales of every family by their name, ignoring case and accents`() {
        assertEquals(listOf("dorian", "phrygian_dominant"), search("DO").filter { it in hungarian })
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
    fun `a search no scale matches finds nothing`() = assertTrue(search("maqam").isEmpty())
}
