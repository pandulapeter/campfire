/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.backup

import com.pandulapeter.campfire.chordpro.ChordProParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnSongFileTest {

    private fun read(text: String, fallbackTitle: String = "riverlight") = OnSongFile.read(text, fallbackTitle)

    private fun count(text: String, of: String) = text.windowed(of.length).count { it == of }

    @Test
    fun `a bare title and artist over chords and lyrics come out as ChordPro`() {
        val text = read("Riverlight\nThe Invented Band\n\n$SHEET")

        val metadata = ChordProParser.parseMetadata(text)
        assertEquals("Riverlight", metadata.title)
        assertEquals("The Invented Band", metadata.artist)
        assertTrue("[G]Down by the [D]river" in text, text)
    }

    @Test
    fun `a labelled header is read`() {
        val text = read("Title: Riverlight\nArtist: The Invented Band\nKey: G\nCapo: 2\nKeywords: Folk, Water\n\n$SHEET")

        val metadata = ChordProParser.parseMetadata(text)
        assertEquals("Riverlight", metadata.title)
        assertEquals("The Invented Band", metadata.artist)
        assertEquals(2, metadata.capo)
        assertEquals(listOf("Folk", "Water"), metadata.tags)
    }

    @Test
    fun `a header the body declares is not written twice`() {
        val text = read("Riverlight\n\n{title: Riverlight}\n$SHEET")

        assertEquals(1, count(text, "{title:"), text)
    }

    @Test
    fun `a file whose first line is a chord row keeps the fallback title`() {
        val text = read(SHEET)

        assertEquals("riverlight", ChordProParser.parseMetadata(text).title)
        assertTrue("[G]Down by the [D]river" in text, text)
    }

    @Test
    fun `a song titled with a chord name keeps its title`() {
        val text = read("Am\nThe Invented Band\n\n$SHEET", fallbackTitle = "Am")

        assertEquals("Am", ChordProParser.parseMetadata(text).title)
        assertEquals("The Invented Band", ChordProParser.parseMetadata(text).artist)
    }

    @Test
    fun `a header without a blank line after it ends at its third bare line`() {
        val text = read("Riverlight\nThe Invented Band\n$SHEET")

        assertEquals("The Invented Band", ChordProParser.parseMetadata(text).artist)
        assertTrue("[G]Down by the [D]river" in text, text)
    }

    @Test
    fun `chords are set on both sides of a directive in the body`() {
        val text = read("Riverlight\n\n$SHEET\n{c: Slowly}\n$SHEET")

        assertEquals(2, count(text, "[G]Down by the [D]river"), text)
        assertTrue("{c: Slowly}" in text, text)
    }

    @Test
    fun `a body of lyrics only is escaped`() {
        val text = read("Riverlight\n\nDown by [the] river\n# not a comment")

        assertFalse("[the]" in text, text)
        assertFalse(text.lines().last().startsWith("#"), text)
    }

    @Test
    fun `line breaks written by Windows are read`() {
        val text = read("Riverlight\r\nThe Invented Band\r\n\r\n${SHEET.replace("\n", "\r\n")}")

        assertEquals("The Invented Band", ChordProParser.parseMetadata(text).artist)
        assertFalse('\r' in text, text)
    }

    private companion object {
        val SHEET = "G" + " ".repeat(11) + "D\nDown by the river"
    }
}
