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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenSongSongTest {

    private fun song(lyrics: String, title: String = "Riverlight", extra: String = "") =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<song><title>$title</title><author>The Invented Band</author>$extra<lyrics>$lyrics</lyrics></song>"

    @Test
    fun `a song of two verses and a chorus comes out with its header, sections, and chords set`() {
        val lyrics = """
            [V1]
            .G           D
             Down by the river
            [C]
            .C             G
             We sang until morning
            [V2]
            .G           D
             Over in the meadow
        """.trimIndent()

        val text = OpenSongSong.read(song(lyrics, extra = "<key>G</key><capo print=\"false\">2</capo><tempo>96</tempo><theme>Folk; Water</theme>"), "x")!!

        val metadata = ChordProParser.parseMetadata(text)
        assertEquals("Riverlight", metadata.title)
        assertEquals("The Invented Band", metadata.artist)
        assertEquals(2, metadata.capo)
        assertEquals(listOf("Folk", "Water"), metadata.tags)
        assertTrue("{start_of_verse: Verse 1}\n[G]Down by the [D]river\n{end_of_verse}" in text, text)
        assertTrue("{start_of_chorus: Chorus}\n[C]We sang until [G]morning\n{end_of_chorus}" in text, text)
        assertTrue("{start_of_verse: Verse 2}\n[G]Over in the [D]meadow" in text, text)
    }

    @Test
    fun `numbered lyric lines sharing one chord row are one verse each`() {
        val lyrics = "[V]\n.G           D\n1Down by the river\n2Over in the meadow"

        val text = OpenSongSong.read(song(lyrics), "x")!!

        assertTrue("{start_of_verse: Verse 1}\n[G]Down by the [D]river\n{end_of_verse}" in text, text)
        assertTrue("{start_of_verse: Verse 2}\n[G]Over in the [D]meadow\n{end_of_verse}" in text, text)
    }

    @Test
    fun `comments and line breaks are kept`() {
        val text = OpenSongSong.read(song("[V1]\n;Slowly\n Down by the river||we sang\n---\n Until morning"), "x")!!

        assertTrue("{comment: Slowly}" in text, text)
        assertTrue("Down by the river\nwe sang" in text, text)
        assertFalse("---" in text, text)
    }

    @Test
    fun `an OpenLyrics document, another XML document, and malformed XML are not OpenSong songs`() {
        val openLyrics = "<song xmlns=\"http://openlyrics.info/namespace/2009/song\"><lyrics><verse name=\"v1\"><lines>La</lines></verse></lyrics></song>"

        assertNull(OpenSongSong.read(openLyrics, "x"))
        assertNull(OpenSongSong.read("<set name=\"Sunday\"><slide_groups/></set>", "x"))
        assertNull(OpenSongSong.read("<song><lyrics>[V1]", "x"))
        assertNull(OpenSongSong.read("<br/>notes</div>", "x"))
    }

    @Test
    fun `a song of lyrics only comes out escaped`() {
        val text = OpenSongSong.read(song("[V1]\n Down by [the] river\n {not a directive}"), "x")!!

        assertFalse("[the]" in text, text)
        assertEquals(listOf("Riverlight"), ChordProParser.parseMetadata(text).let { listOfNotNull(it.title) })
    }

    @Test
    fun `unmarked lyric lines are lyrics`() {
        val text = OpenSongSong.read(song("[V1]\n.G           D\nDown by the river"), "x")!!

        assertTrue("[G]Down by the [D]river" in text, text)
    }

    @Test
    fun `a song with an empty title is named by its file`() {
        val text = OpenSongSong.read(song("[V1]\n Down by the river", title = ""), fallbackTitle = "Riverlight")!!

        assertEquals("Riverlight", ChordProParser.parseMetadata(text).title)
    }
}
