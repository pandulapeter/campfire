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
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AbcFileTest {

    @Test
    fun `every tune is a song named by its title`() {
        val files = AbcFile.read("X:1\nT:Lantern Reel\nK:G\n\"G\"GABc|\"D\"dedB|\n\nX:2\nT:Meadow Jig\nK:D\n\"D\"DEF|", fallbackTitle = "tunes")

        assertEquals(listOf("Lantern Reel.cho", "Meadow Jig.cho"), files.map { it.name })
        val song = ChordProParser.parse(files.first().bytes.decodeToString())
        assertEquals("Lantern Reel", song.metadata.title)
        val lines = song.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }
        assertTrue(lines.any { it is ChordProLine.Grid })
        assertTrue(lines.any { it is ChordProLine.Verbatim && it.text == "\"G\"GABc|\"D\"dedB|" })
    }

    @Test
    fun `tunes of one title are numbered within the batch`() {
        val files = AbcFile.read("X:1\nT:Reel\nK:G\nGAB|\n\nX:2\nT:Reel\nK:D\nDEF|", fallbackTitle = "tunes")

        assertEquals(listOf("Reel.cho", "Reel 2.cho"), files.map { it.name })
    }

    @Test
    fun `a tune with no title is titled and named by the file`() {
        val file = AbcFile.read("X:1\nK:G\nGAB|", fallbackTitle = "lantern").single()

        assertEquals("lantern.cho", file.name)
        assertEquals("lantern", ChordProParser.parseMetadata(file.bytes.decodeToString()).title)
    }

    @Test
    fun `a text with no tune gives no song`() {
        assertEquals(emptyList(), AbcFile.read("", fallbackTitle = "empty"))
    }
}
