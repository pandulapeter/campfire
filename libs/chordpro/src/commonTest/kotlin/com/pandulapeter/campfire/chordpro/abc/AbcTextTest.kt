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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbcTextTest {

    @Test
    fun `a tune is ABC`() {
        assertTrue(AbcText.isAbc("X:1\nT:The Lantern Reel\nM:4/4\nL:1/8\nK:G\n|:\"G\"GABc dedB:|"))
    }

    @Test
    fun `comments, blank lines, and a file header before the tune are read past`() {
        val text = "﻿% A small collection\n\n%%scale 0.8\nC:Campfire Players\n\nX:1\nT:Hearth\nK:D\nDEFA|"

        assertTrue(AbcText.isAbc(text))
    }

    @Test
    fun `a tune with a title and no reference number is ABC`() {
        assertTrue(AbcText.isAbc("T:Hearth\nK:Ador\nABcd|"))
    }

    @Test
    fun `a header with neither a reference number nor a title is not ABC`() {
        assertFalse(AbcText.isAbc("M:3/4\nK:G\nGAB|"))
    }

    @Test
    fun `a header with no key before the music is not ABC`() {
        assertFalse(AbcText.isAbc("X:1\nT:Hearth\nGABc|dedB|\nK:G"))
    }

    @Test
    fun `a ChordPro file holding an ABC block is not ABC`() {
        val text = "{title: Hearth}\n{start_of_abc}\nX:1\nT:Hearth\nK:G\nGABc|\n{end_of_abc}"

        assertFalse(AbcText.isAbc(text))
    }

    @Test
    fun `prose with a K line in it is not ABC`() {
        assertFalse(AbcText.isAbc("Notes from the session\nT: Tuesday\nK: bring the fiddle"))
        assertFalse(AbcText.isAbc("G       D\nDown by the river\nK:G"))
    }

    @Test
    fun `an empty text is not ABC`() {
        assertFalse(AbcText.isAbc(""))
        assertFalse(AbcText.isAbc("\n% only a comment\n"))
    }
}
