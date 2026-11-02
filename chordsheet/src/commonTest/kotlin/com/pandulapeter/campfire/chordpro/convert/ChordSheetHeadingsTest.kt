/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

import kotlin.test.Test
import kotlin.test.assertEquals

class ChordSheetHeadingsTest {

    @Test
    fun `a label opens the section its words name, in every language of the app and with any words after it`() {
        // The labels for verse, chorus, bridge, pre-chorus, intro, solo, and outro, in each language of the app.
        val labels = listOf(
            listOf("Verse", "Chorus", "Bridge", "Pre-chorus", "Intro", "Solo", "Outro"),
            listOf("\u05d1\u05d9\u05ea", "\u05e4\u05d6\u05de\u05d5\u05df", "\u05d2\u05e9\u05e8", "\u05d8\u05e8\u05d5\u05dd\u05be\u05e4\u05d6\u05de\u05d5\u05df", "\u05e4\u05ea\u05d9\u05d7\u05d4", "\u05e1\u05d5\u05dc\u05d5", "\u05e1\u05d9\u05d5\u05dd"),
            listOf("Strophe", "Refrain", "Bridge", "Pre-Chorus", "Intro", "Solo", "Outro"),
            listOf("Couplet", "Refrain", "Pont", "Pr\u00e9-refrain", "Intro", "Solo", "Outro"),
            listOf("Estrofa", "Estribillo", "Puente", "Preestribillo", "Intro", "Solo", "Outro"),
            listOf("Strofa", "Ritornello", "Bridge", "Pre-ritornello", "Intro", "Assolo", "Outro"),
            listOf("Strof\u0103", "Refren", "Bridge", "Pre-refren", "Intro", "Solo", "Outro"),
            listOf("Versszak", "Refr\u00e9n", "\u00c1tk\u00f6t\u00e9s", "Pre-refr\u00e9n", "Intro", "Sz\u00f3l\u00f3", "Outro"),
        )
        val types = listOf("verse", "chorus", "bridge", "pre-chorus", "intro", "solo", "outro")
        labels.forEach { language ->
            assertEquals(types, language.map { ChordSheetHeadings.sectionType(it) }, language.toString())
            assertEquals(types.take(4), language.take(4).map { ChordSheetHeadings.sectionType("$it 2") }, language.toString())
        }
        assertEquals("chorus", ChordSheetHeadings.sectionType("Chorus (twice)"))
        assertEquals("bridge", ChordSheetHeadings.sectionType("Bridge \u2014 build"))
        assertEquals("verse", ChordSheetHeadings.sectionType("Riffs and more"))
        assertEquals("solo", ChordSheetHeadings.sectionType("Organ solo"))
    }
}
