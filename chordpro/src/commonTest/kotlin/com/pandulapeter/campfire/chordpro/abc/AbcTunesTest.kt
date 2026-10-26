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

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
