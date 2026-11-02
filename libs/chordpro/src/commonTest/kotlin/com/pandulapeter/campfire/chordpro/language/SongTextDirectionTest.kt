/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.language

import com.pandulapeter.campfire.chordpro.ChordProParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongTextDirectionTest {

    private fun directionOf(text: String) = ChordProParser.parse(text).textDirection

    @Test
    fun `a song of Latin lyrics reads from the left`() {
        assertEquals(WritingDirection.LEFT_TO_RIGHT, directionOf("{title: House}\n[Am]There is a [C]house"))
    }

    @Test
    fun `a song mostly of Hebrew lyrics reads from the right, its Latin lines and chord rows included`() {
        val song = "{title: Shir}\n[Am]שָׁלוֹם עֲלֵיכֶם מַלְאֲכֵי\nOK [C]yes\n[G] [D]\nהַשָּׁרֵת מַלְאֲכֵי עֶלְיוֹן"
        assertEquals(WritingDirection.RIGHT_TO_LEFT, directionOf(song))
    }

    @Test
    fun `Arabic lyrics read from the right as Hebrew ones do`() {
        assertEquals(WritingDirection.RIGHT_TO_LEFT, directionOf("[Am]يا ليل [C]يا عين"))
    }

    @Test
    fun `a declared right-to-left language decides whatever the lyrics hold`() {
        assertEquals(WritingDirection.RIGHT_TO_LEFT, directionOf("{meta: language he}\n[Am]Shalom aleichem"))
        assertEquals(WritingDirection.RIGHT_TO_LEFT, directionOf("{meta: language iw}\n[Am]Shalom aleichem"))
        assertEquals(WritingDirection.RIGHT_TO_LEFT, directionOf("{meta: language heb}\n[Am]Shalom aleichem"))
        assertEquals(WritingDirection.RIGHT_TO_LEFT, directionOf("{meta: language ar}\n{meta: language fa}\nla la"))
    }

    @Test
    fun `a declared left-to-right language leaves the direction to the lyrics`() {
        assertEquals(WritingDirection.RIGHT_TO_LEFT, directionOf("{meta: language en}\n[Am]שָׁלוֹם עֲלֵיכֶם"))
        assertEquals(WritingDirection.LEFT_TO_RIGHT, directionOf("{meta: language he}\n{meta: language en}\nHello there friend\nשלום"))
        assertEquals(WritingDirection.RIGHT_TO_LEFT, directionOf("{meta: language en}\n{meta: language he}\nשלום עליכם\nHi"))
    }

    @Test
    fun `marks, digits, and punctuation cast no vote, and a tie reads from the left`() {
        assertEquals(WritingDirection.LEFT_TO_RIGHT, directionOf("[G]1, 2, 3!\n[C]..."))
        assertEquals(WritingDirection.LEFT_TO_RIGHT, directionOf("ab\nשל"))
        assertEquals(WritingDirection.LEFT_TO_RIGHT, directionOf("[G] [C] [D]"))
        assertEquals(WritingDirection.RIGHT_TO_LEFT, directionOf("a\nשָׁל"))
    }

    @Test
    fun `a supplementary right-to-left letter counts as one`() {
        assertEquals(WritingDirection.RIGHT_TO_LEFT, directionOf("𞤀𞤁 a"))
    }

    @Test
    fun `chords and comments are not lyrics`() {
        assertEquals(WritingDirection.LEFT_TO_RIGHT, directionOf("{comment: פזמון חוזר}\n[Am]la [C]la"))
    }

    @Test
    fun `the right-to-left languages are CLDR's, retired codes folded`() {
        listOf("ar", "arc", "ckb", "dv", "fa", "he", "iw", "ji", "ks", "ps", "sd", "syr", "ug", "ur", "yi").forEach {
            assertTrue(RightToLeftLanguages.contains(it), it)
        }
        listOf("en", "hu", "ro", "id", "in", "tr", "ku").forEach { assertFalse(RightToLeftLanguages.contains(it), it) }
    }

    @Test
    fun `a text's direction is that of most of its letters`() {
        assertEquals(WritingDirection.RIGHT_TO_LEFT, writingDirectionOf("Am  G\nשבת אחים גם יחד (Psalm 133)"))
        assertEquals(WritingDirection.LEFT_TO_RIGHT, writingDirectionOf("Hine ma tov (מה טוב)"))
        assertEquals(WritingDirection.LEFT_TO_RIGHT, writingDirectionOf("12:30"))
    }
}
