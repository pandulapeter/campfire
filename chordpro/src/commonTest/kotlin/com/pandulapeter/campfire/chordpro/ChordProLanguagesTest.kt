/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChordProLanguagesTest {

    @Test
    fun `languages are read from every spelling a file may use`() {
        val metadata = ChordProParser.parseMetadata("{title: T}\n{meta: language en}\n{meta: lang de}\n{language: hu}\n{lang: ro}")

        assertEquals(listOf("en", "de", "hu", "ro"), metadata.languages)
    }

    @Test
    fun `a language is read as a lowercase primary subtag`() {
        val metadata = ChordProParser.parseMetadata("{meta: language EN-us}\n{meta: language pt_BR}")

        assertEquals(listOf("en", "pt"), metadata.languages)
    }

    @Test
    fun `a three letter code is read as the two letter one where the standard has one`() {
        val metadata = ChordProParser.parseMetadata("{meta: language eng}\n{language: GER}\n{meta: lang fra}")

        assertEquals(listOf("en", "de", "fr"), metadata.languages)
    }

    @Test
    fun `a three letter code with no two letter equivalent is kept as it is`() {
        val metadata = ChordProParser.parseMetadata("{meta: language rom}\n{meta: language haw}")

        assertEquals(listOf("rom", "haw"), metadata.languages)
    }

    @Test
    fun `the codes the standard has replaced are read as the ones that replaced them`() {
        val metadata = ChordProParser.parseMetadata("{meta: language iw}\n{meta: language ind}\n{meta: language mol}")

        assertEquals(listOf("he", "id", "ro"), metadata.languages)
    }

    @Test
    fun `the same language written in two and in three letters is read once`() {
        val metadata = ChordProParser.parseMetadata("{meta: language en}\n{meta: language eng}")

        assertEquals(listOf("en"), metadata.languages)
    }

    @Test
    fun `a language the song declares in three letters is left alone when it is kept`() {
        val text = "{title: T}\n{meta: language eng}\n\nThe first line"

        assertEquals(text, ChordProLanguages.setLanguages(text, listOf("en")))
    }

    @Test
    fun `the codes for an absent language are not read as one`() {
        val metadata = ChordProParser.parseMetadata("{meta: language und}\n{meta: language zxx}\n{meta: language }")

        assertEquals(emptyList(), metadata.languages)
    }

    @Test
    fun `the same language written twice is read once`() {
        val metadata = ChordProParser.parseMetadata("{meta: language en}\n{language: EN}")

        assertEquals(listOf("en"), metadata.languages)
    }

    @Test
    fun `languages do not end up among the custom metadata`() {
        val metadata = ChordProParser.parseMetadata("{meta: language en}\n{meta: language und}\n{meta: tuning DADGAD}")

        assertEquals(mapOf("tuning" to listOf("DADGAD")), metadata.custom)
    }

    @Test
    fun `serializing writes every language as a meta directive`() {
        val song = ChordProParser.parse("{title: T}\n{language: hu}\n{meta: lang en}")

        assertEquals("{title: T}\n{meta: language hu}\n{meta: language en}", ChordProSerializer.serialize(song))
    }

    @Test
    fun `the first language of a song is written after its metadata`() {
        val text = "{title: T}\n{artist: A}\n\nThe first line"

        assertEquals("{title: T}\n{artist: A}\n{meta: language hu}\n\nThe first line", ChordProLanguages.setLanguages(text, listOf("hu")))
    }

    @Test
    fun `a new language is written after the last one the song already has`() {
        val text = "{title: T}\n{meta: language hu}\n{artist: A}\n\nThe first line"

        assertEquals(
            "{title: T}\n{meta: language hu}\n{meta: language en}\n{artist: A}\n\nThe first line",
            ChordProLanguages.setLanguages(text, listOf("hu", "en")),
        )
    }

    @Test
    fun `a language that is kept stays on the line it was written on`() {
        val text = "{title: T}\n{language: HU}\n{meta: lang de}\n\nThe first line"

        assertEquals("{title: T}\n{language: HU}\n\nThe first line", ChordProLanguages.setLanguages(text, listOf("hu")))
    }

    @Test
    fun `taking every language off leaves the rest of the file as it was`() {
        val text = "# A comment\n{title: T}\n{meta: language en}\n\n[C]The first line"

        assertEquals("# A comment\n{title: T}\n\n[C]The first line", ChordProLanguages.setLanguages(text, emptyList()))
    }

    @Test
    fun `a language repeated in the file is written only once`() {
        val text = "{meta: language en}\n{lang: en}"

        assertEquals("{meta: language en}", ChordProLanguages.setLanguages(text, listOf("en")))
    }

    @Test
    fun `setting the languages a song already declares changes nothing`() {
        val text = "{title: T}\n{language: EN}\n\nThe first line"

        assertEquals(text, ChordProLanguages.setLanguages(text, listOf("en-US")))
    }

    @Test
    fun `a code is normalized the same way whether it comes from a file or from a caller`() {
        assertEquals("hu", ChordProLanguages.code("HUN"))
        assertEquals("hu", ChordProLanguages.code(" hu-HU "))
        assertEquals("de", ChordProLanguages.code("ger"))
        assertEquals("rom", ChordProLanguages.code("ROM"))
        assertNull(ChordProLanguages.code("und"))
        assertNull(ChordProLanguages.code(" "))
    }

    @Test
    fun `an absent language code is not written`() {
        val text = "{title: T}"

        assertEquals(text, ChordProLanguages.setLanguages(text, listOf("und", "")))
    }
}
