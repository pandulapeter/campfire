/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.abc

import com.pandulapeter.campfire.chordpro.ChordProParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AbcTunesTest {

    @Test
    fun `each X field starts a tune that ends at a blank line`() {
        val text = """
            X:1
            T:First
            K:G
            GABc|dedB|

            X:2
            T:Second
            K:D
            DEFA|
        """.trimIndent()

        assertEquals(listOf("X:1\nT:First\nK:G\nGABc|dedB|", "X:2\nT:Second\nK:D\nDEFA|"), AbcTunes.split(text))
    }

    @Test
    fun `the file header is written into every tune after its X line`() {
        val text = "%%scale 0.8\nC:Campfire Players\nM:6/8\n\nX:1\nT:First\nK:G\nGAB|\n\nX:2\nT:Second\nK:D\nDEF|"

        val tunes = AbcTunes.split(text)

        assertEquals("X:1\n%%scale 0.8\nC:Campfire Players\nM:6/8\nT:First\nK:G\nGAB|", tunes[0])
        assertEquals("X:2\n%%scale 0.8\nC:Campfire Players\nM:6/8\nT:Second\nK:D\nDEF|", tunes[1])
    }

    @Test
    fun `a field the tune's own header has overrides the file header's`() {
        val text = "%%scale 0.8\nT:Collection\nC:Campfire Players\n+:and friends\nM:6/8\nI:linebreak \$\nR:jig\nQ:1/8=100\n\n" +
            "X:1\nT:First\nC:Somebody Else\nM:3/4\nI:abc-charset utf-8\nR:waltz\nQ:1/4=90\nK:G\nGAB|"

        assertEquals(
            "X:1\n%%scale 0.8\nI:linebreak \$\nT:First\nC:Somebody Else\nM:3/4\nI:abc-charset utf-8\nR:waltz\nQ:1/4=90\nK:G\nGAB|",
            AbcTunes.split(text).single(),
        )
    }

    @Test
    fun `a split tune converts with its own fields where the file header has them too`() {
        val text = "T:Collection\nC:Campfire Players\nM:6/8\nR:jig\nQ:1/8=100\n\nX:1\nT:First\nC:Somebody Else\nM:3/4\nR:waltz\nQ:90\nK:D\n\"D\"DEF|"
        val song = AbcToChordPro.convert(AbcTunes.split(text).single())
        val metadata = ChordProParser.parse(song.text).metadata

        assertEquals("First", metadata.title)
        assertNull(metadata.subtitle)
        assertEquals("Somebody Else", metadata.artist)
        assertEquals("D", metadata.key)
        assertEquals(listOf("waltz"), metadata.tags)
        assertEquals("3/4", metadata.time)
        assertEquals("90", metadata.tempo)
    }

    @Test
    fun `a file header field the tune lacks is kept with its continuation`() {
        val text = "C:Campfire Players\n+:and friends\nM:6/8\n\nX:1\nT:First\nK:G\nGAB|"

        assertEquals("X:1\nC:Campfire Players\n+:and friends\nM:6/8\nT:First\nK:G\nGAB|", AbcTunes.split(text).single())
    }

    @Test
    fun `free text outside the tunes is dropped`() {
        val text = "My favourite tunes, collected over the years.\n\nX:1\nT:First\nK:G\nGAB|\n\nPlayed slowly at first.\n\nX:2\nT:Second\nK:D\nDEF|"

        assertEquals(listOf("X:1\nT:First\nK:G\nGAB|", "X:2\nT:Second\nK:D\nDEF|"), AbcTunes.split(text))
    }

    @Test
    fun `blank lines inside a tune header are read past`() {
        assertEquals(listOf("X:1\nT:First\nK:G\nGAB|"), AbcTunes.split("X:1\n\nT:First\n\nK:G\nGAB|"))
    }

    @Test
    fun `an X field ends the tune before it with no blank line between them`() {
        assertEquals(listOf("X:1\nT:First\nK:G\nGAB|", "X:2\nT:Second\nK:D\nDEF|"), AbcTunes.split("X:1\nT:First\nK:G\nGAB|\nX:2\nT:Second\nK:D\nDEF|"))
    }

    @Test
    fun `a text with no X field is one tune`() {
        assertEquals(listOf("T:Only\nK:G\nGAB|"), AbcTunes.split("\nT:Only\nK:G\nGAB|\n"))
        assertEquals(emptyList(), AbcTunes.split(""))
    }

    @Test
    fun `a long file header stops being copied into the tunes before they outgrow memory`() {
        val header = "%" + "x".repeat(1_000_000)
        val tunes = AbcTunes.split(header + "\n\n" + (1..40).joinToString("") { "X:$it\nK:G\nGABc|\n\n" })

        assertEquals(40, tunes.size)
        assertTrue(header in tunes.first())
        assertTrue(header !in tunes.last())
        assertTrue(tunes.sumOf { it.length } < 20_000_000)
    }
}
