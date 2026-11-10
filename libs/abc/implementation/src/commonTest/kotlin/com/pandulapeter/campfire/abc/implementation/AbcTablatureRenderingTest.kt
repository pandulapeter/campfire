/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.abc.implementation

import com.pandulapeter.campfire.abc.api.AbcRendering
import com.pandulapeter.campfire.abc.api.AbcTablature
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AbcTablatureRenderingTest {

    private val renderer = AbcRendererImpl()

    @Test
    fun `a tab is fretted on the strings it names where nobody reads it`() = runTest {
        assertEquals(listOf("2", "2", "3", "3"), frets(tabOf("E,A,DG"), tablature = null))
    }

    @Test
    fun `a tab is fretted above the reader's capo`() = runTest {
        assertEquals(listOf("0", "0", "1", "1"), frets(tabOf("E,A,DG"), tablature(capo = 2)))
    }

    @Test
    fun `a tab's own capo is counted once`() = runTest {
        assertEquals(listOf("0", "0", "1", "1"), frets(tabOf("E,A,DG", voice = "capo=2"), tablature()))
    }

    @Test
    fun `a transposed tab is fretted as its staff is drawn`() = runTest {
        assertEquals(listOf("2", "3", "3", "8"), frets(tabOf("E,A,DG"), tablature(), transposition = 5))
    }

    @Test
    fun `a tab is fretted on the reader's tuning`() = runTest {
        assertEquals(listOf("0", "0"), frets(tabOf("D,,A,,"), tablature(tuning = DROP_D)))
    }

    @Test
    fun `a tab whose notes leave the neck is moved by an octave`() = runTest {
        assertEquals(listOf("0", "2"), frets(tabOf("D,,A,,"), tablature()))
    }

    @Test
    fun `a tab for an instrument of other strings than the reader's keeps its own`() = runTest {
        assertEquals(listOf("2", "2", "3", "3"), frets(tabOf("E,A,DG"), tablature(tuning = listOf(67, 60, 64, 69))))
    }

    @Test
    fun `a note written on a string stays on it where it fits`() = runTest {
        assertEquals(listOf("19", "7"), frets(tabOf("!5s!E !3s!D"), tablature(tuning = DROP_D)))
    }

    @Test
    fun `a tab's strings are named in front of it`() = runTest {
        val svg = svgOf(tabOf("E,A,DG"), tablature(tuning = DROP_D))
        assertEquals(listOf("D", "A", "d", "G", "B", "E"), STRING_NAME.findAll(svg).map { it.groupValues[1] }.toList())
        assertTrue(">TAB<" !in svg)
    }

    private fun tabOf(notes: String, voice: String = "") = "X:1\nL:1/4\nK:C\nV:1 clef=tab $voice\n$notes|\n"

    private fun tablature(tuning: List<Int> = STANDARD, capo: Int = 0) = AbcTablature(tuning = tuning, capo = capo, maxFret = 24, noteNames = NOTE_NAMES)

    private suspend fun svgOf(tune: String, tablature: AbcTablature?, transposition: Int = 0) =
        assertIs<AbcRendering.Systems>(renderer.render(tune, width = 400, transposition = transposition, tablature = tablature)).systems.joinToString("") { it.svg }

    private suspend fun frets(tune: String, tablature: AbcTablature?, transposition: Int = 0) =
        FRET.findAll(svgOf(tune, tablature, transposition)).map { it.groupValues[1] }.toList()

    private companion object {
        val STANDARD = listOf(40, 45, 50, 55, 59, 64)
        val DROP_D = listOf(38, 45, 50, 55, 59, 64)
        val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val FRET = Regex("""<text class="bg\d+" x="[^"]*" y="[^"]*">([^<]*)</text>""")
        val STRING_NAME = Regex("""<text class="bg\d+" x="[^"]*" y="[^"]*" style="[^"]*">([^<]*)</text>""")
    }
}
