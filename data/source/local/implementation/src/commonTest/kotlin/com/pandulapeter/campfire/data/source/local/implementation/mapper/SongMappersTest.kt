/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.mapper

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StoredFileInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SongMappersTest {

    @Test
    fun `the tags of a song are composed and merged`() {
        val text = "{title: A}\n{tag: Café}\n{tag: café}"
        val song = StoredFileInfo(name = "a.cho", size = 0, lastModified = 0).toSong(ChordProParser.summarize(text), text)

        assertEquals(listOf("Café"), song.tags)
    }

    @Test
    fun `a song without a title is titled by its file name and has nothing better to be renamed to`() {
        val song = song(fileName = "My Song.cho", text = "{artist: Somebody}\n[Am]La")

        assertEquals("My Song", song.title)
        assertFalse(song.canUpdateFileName)
    }

    @Test
    fun `a song whose header names it differently from its file can be renamed`() {
        assertTrue(song(fileName = "old.cho", text = "{title: New}").canUpdateFileName)
        assertFalse(song(fileName = "new.cho", text = "{title: New}").canUpdateFileName)
    }

    @Test
    fun `the subtitle is part of the name a song can be renamed to`() {
        assertTrue(song(fileName = "bar.cho", text = "{title: Bar}\n{subtitle: Live}").canUpdateFileName)
        assertFalse(song(fileName = "bar_live.cho", text = "{title: Bar}\n{subtitle: Live}").canUpdateFileName)
    }

    @Test
    fun `a blank artist leaves the name to the title alone`() {
        val song = song(fileName = "bar.cho", text = "{title: Bar}\n{artist:  }")

        assertEquals("", song.artist)
        assertFalse(song.canUpdateFileName)
    }

    @Test
    fun `a capo chart's key is read as the key its chords are written in`() {
        assertEquals("D", song(fileName = "a.cho", text = "{key: G}\n{capo: 5}\n[D]a [G]b [A]c [Bm]d [D]e [G]f [A]g [D]h").chordsKey)
        assertEquals("D", song(fileName = "a.cho", text = "{key: D}\n{capo: 5}\n[D]a [G]b [A]c [Bm]d [D]e [G]f [A]g [D]h").chordsKey)
        assertEquals("G", song(fileName = "a.cho", text = "{key: G}\n[D]a [G]b [A]c [Bm]d").chordsKey)
        assertEquals(null, song(fileName = "a.cho", text = "{capo: 5}\n[D]a [G]b").chordsKey)
    }

    @Test
    fun `a song with an abc block carries that it holds sheet music`() {
        assertTrue(song(fileName = "a.cho", text = "{title: A}\n{start_of_abc}\nX:1\nK:C\nCDEF|\n{end_of_abc}").hasSheetMusic)
        assertFalse(song(fileName = "a.cho", text = "{title: A}\n[C]La").hasSheetMusic)
    }

    private fun song(fileName: String, text: String) = StoredFileInfo(name = fileName, size = 0, lastModified = 0)
        .toSong(ChordProParser.summarize(text), text)
}
