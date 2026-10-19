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
import kotlin.test.assertTrue

class JsonSongsTest {

    private fun texts(json: String) = JsonSongs.read(json).map { it.bytes.decodeToString() }

    private fun song(title: String, extra: String = "") = """{"title": "$title", "content": "$SHEET"$extra}"""

    @Test
    fun `reads a root array of songs`() {
        val files = JsonSongs.read("[${song("One")}, ${song("Two")}]")

        assertEquals(listOf("One.cho", "Two.cho"), files.map { it.name })
    }

    @Test
    fun `reads the songs inside a wrapper`() {
        assertEquals(listOf("One.cho"), JsonSongs.read("""{"data": {"items": [${song("One")}]}}""").map { it.name })
    }

    @Test
    fun `reads a song whose fields are JSON inside a string`() {
        val inner = song("One").replace("\\", "\\\\").replace("\"", "\\\"")

        assertEquals(listOf("One.cho"), JsonSongs.read("""{"songs": "[$inner]"}""").map { it.name })
    }

    @Test
    fun `reads numbers written as text`() {
        val text = texts(song("One", extra = """, "capo": "2", "bpm": "96.0"""")).single()

        val metadata = ChordProParser.parseMetadata(text)
        assertEquals(2, metadata.capo)
        assertEquals("96", metadata.tempo)
    }

    @Test
    fun `a content of prose with a title yields nothing`() {
        assertEquals(emptyList(), JsonSongs.read("""{"title": "Notes", "content": "Buy milk.\nCall the plumber about the sink."}"""))
    }

    @Test
    fun `a notes export yields nothing, while four lines of lyrics do`() {
        val prose = "First line of a note\\nSecond line of it\\nThird line of it\\nFourth line of it"

        assertEquals(emptyList(), JsonSongs.read("""[{"title": "Note", "content": "$prose"}]"""))
        val lyrics = texts("""[{"title": "Song", "lyrics": "$prose"}]""").single()
        assertEquals("Song", ChordProParser.parseMetadata(lyrics).title)
        assertTrue("Fourth line of it" in lyrics, lyrics)
    }

    @Test
    fun `a Campfire setlist and a preferences document yield nothing`() {
        assertEquals(emptyList(), JsonSongs.read("""{"title": "Gig", "date": "2026-01-01", "songs": [{"file": "a.cho"}, {"file": "b.cho"}]}"""))
        assertEquals(emptyList(), JsonSongs.read("""{"themeColor": "purple", "language": "en", "instrument": {"name": "guitar"}}"""))
    }

    @Test
    fun `the depth and node caps stop cleanly`() {
        val deep = "[".repeat(40) + song("Deep") + "]".repeat(40)
        val wide = "[" + List(250_000) { "0" }.joinToString(",") + ", ${song("Late")}]"

        assertEquals(emptyList(), JsonSongs.read(deep))
        assertEquals(emptyList(), JsonSongs.read(wide))
        assertEquals(emptyList(), JsonSongs.read("{not json"))
    }

    @Test
    fun `brackets nested past the parser's stack read as nothing, and brackets inside a text do not count`() {
        val bracketsInText = song("One", extra = """, "notes": "${"[".repeat(5_000)}"""")

        assertEquals(emptyList(), JsonSongs.read("[".repeat(200_000)))
        assertEquals(emptyList(), JsonSongs.read("""{"songs": "${"[".repeat(5_000)}"}"""))
        assertEquals(listOf("One.cho"), JsonSongs.read(bracketsInText).map { it.name })
    }

    @Test
    fun `chords over lyrics come out as ChordPro with the title and artist in the header`() {
        val text = texts(song("Riverlight", extra = """, "author": "The Invented Band"""")).single()

        val metadata = ChordProParser.parseMetadata(text)
        assertEquals("Riverlight", metadata.title)
        assertEquals("The Invented Band", metadata.artist)
        assertTrue("[G]Down" in text, text)
    }

    @Test
    fun `two songs of one title are numbered`() {
        assertEquals(listOf("One.cho", "One 2.cho"), JsonSongs.read("[${song("One")}, ${song("One")}]").map { it.name })
    }

    @Test
    fun `a lyric over the first chord row stays a lyric under the object's own title`() {
        val content = "Hum along softly now\\nG        D\\nWalking down the road\\nC        G\\nSinging all the way"
        val text = texts("""{"title": "Riverlight", "content": "$content"}""").single()

        assertEquals("Riverlight", ChordProParser.parseMetadata(text).title)
        assertTrue("Hum along softly now\n[G]Walking [D]down the road" in text, text)
    }

    @Test
    fun `content repeating the object's title is not written twice`() {
        val content = "Riverlight\\n\\nG        D\\nWalking down the road\\nC        G\\nSinging all the way"
        val text = texts("""{"title": "Riverlight", "content": "$content"}""").single()

        assertEquals(listOf("{title: Riverlight}"), text.lines().filter { "Riverlight" in it })
    }

    @Test
    fun `the object's spelling of its title wins over the content's`() {
        val content = "RIVERLIGHT\\nby The Invented Band\\n\\nG        D\\nWalking down the road\\nC        G\\nSinging all the way"
        val text = texts("""{"title": "Riverlight", "content": "$content"}""").single()

        val metadata = ChordProParser.parseMetadata(text)
        assertEquals("Riverlight", metadata.title)
        assertEquals("The Invented Band", metadata.artist)
        assertTrue(text.lines().none { it.trim() == "RIVERLIGHT" }, text)
    }

    @Test
    fun `content naming the song differently keeps that line`() {
        val content = "Riverlight - The Invented Band\\n\\nG        D\\nWalking down the road\\nC        G\\nSinging all the way"
        val text = texts("""{"title": "Riverlight", "content": "$content"}""").single()

        assertEquals("Riverlight", ChordProParser.parseMetadata(text).title)
        assertTrue(text.lines().any { it.trim() == "Riverlight - The Invented Band" }, text)
    }

    private companion object {
        const val SHEET = "G         D\\nDown by the river light\\nC         G\\nWe sang until morning"
    }
}
