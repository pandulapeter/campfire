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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChordSheetStructureTest {

    @Test
    fun `a sheet of chord rows over lyrics has song structure`() {
        val sheet = """
            Riverlight
            [Verse 1]
            G         D        Em
            Down by the river light
            C              G
            We sang until morning
        """.trimIndent()

        assertTrue(ChordSheetStructure.hasSongStructure(sheet))
    }

    @Test
    fun `a ChordPro file has song structure`() {
        assertTrue(ChordSheetStructure.hasSongStructure("{title: Riverlight}\n[G]Down by the [D]river light"))
        assertTrue(ChordSheetStructure.hasSongStructure("[G]Down by the [D]river light\n[C]We sang until [G]morning"))
    }

    @Test
    fun `a sheet in Latin notation has song structure`() {
        val sheet = """
            Do        Sol       Lam
            Bajo la luz del río
            Fa             Do
            Cantamos hasta el alba
        """.trimIndent()

        assertTrue(ChordSheetStructure.hasSongStructure(sheet))
    }

    @Test
    fun `prose has no song structure`() {
        val prose = """
            It was a bright cold day in April, and the clocks were striking thirteen.
            A man walked down the road, and nobody looked at him.
        """.trimIndent()

        assertFalse(ChordSheetStructure.hasSongStructure(prose))
    }

    @Test
    fun `a README with links and lettered bullets has no song structure`() {
        val readme = """
            # Campfire
            See [C](https://example.com/c) and [G](https://example.com/g) for the details.
            Options:
            - A
            - B
            - C
            Use the first one.
        """.trimIndent()

        assertFalse(ChordSheetStructure.hasSongStructure(readme))
    }

    @Test
    fun `a licence with lettered clauses has no song structure`() {
        val licence = """
            1. Grant of rights
            (a)
            The licensor grants you a worldwide licence.
            (b)
            You may not sublicense it.
        """.trimIndent()

        assertFalse(ChordSheetStructure.hasSongStructure(licence))
    }

    @Test
    fun `an HTML page and an XML file have no song structure`() {
        assertFalse(ChordSheetStructure.hasSongStructure("<!doctype html>\n<html>\n<body>\n<p>A B C</p>\n</body>\n</html>"))
        assertFalse(ChordSheetStructure.hasSongStructure("<?xml version=\"1.0\"?>\n<song>\n<title>A</title>\n<lyrics>La</lyrics>\n</song>"))
    }
}
