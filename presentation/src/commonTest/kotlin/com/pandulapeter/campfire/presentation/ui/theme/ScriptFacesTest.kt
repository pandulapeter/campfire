/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptFacesTest {

    private val hebrew = scriptFaces.single { it.script == "Hebrew" }

    @Test
    fun `a Hebrew word needs the Hebrew face, pointed or not, and Latin, Cyrillic, and emoji need none`() {
        assertTrue(hebrew.isNeededFor("Hallelujah הללויה"))
        assertTrue(hebrew.isNeededFor("שָׁלוֹם"))
        assertTrue(hebrew.isNeededFor("שׁ"))
        assertFalse(hebrew.isNeededFor("Kalinka Калинка 🎸"))
        assertFalse(hebrew.isNeededFor(""))
    }

    @Test
    fun `a library needs the faces of the scripts its texts use and of the languages it is written in`() {
        assertEquals(setOf(hebrew), scriptFacesNeededBy(languages = listOf("en"), texts = sequenceOf("Hava Nagila", "הבה נגילה")))
        assertEquals(setOf(hebrew), scriptFacesNeededBy(languages = listOf("en", "yi"), texts = emptySequence()))
        assertEquals(emptySet(), scriptFacesNeededBy(languages = listOf("en", "hu"), texts = sequenceOf("Wonderwall", "Ébredj")))
    }

    @Test
    fun `no two faces cover the same character, since the text engine would draw whichever was registered first`() {
        scriptFaces.forEach { face ->
            (scriptFaces - face).forEach { other ->
                assertTrue(face.ranges.none { range -> other.ranges.any { it.first <= range.last && range.first <= it.last } }, "$face and $other")
            }
        }
    }
}
