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

import com.pandulapeter.campfire.abc.api.AbcMessage
import com.pandulapeter.campfire.abc.api.AbcRendering
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** What abc2svg reports about a tune, placed on the tune as the caller wrote it, in the real engine of each platform. */
class AbcRendererMessagesTest {

    private val renderer = AbcRendererImpl()

    @Test
    fun `a tune drawn without complaint carries no messages`() = runTest {
        assertEquals(emptyList(), assertIs<AbcRendering.Systems>(renderer.render(TUNE, width = 800, transposition = 0)).messages)
    }

    @Test
    fun `a tune drawn despite a mistake carries it at its line and column of the tune`() = runTest {
        val rendering = assertIs<AbcRendering.Systems>(renderer.render("X:1\nK:C\n\"Am C D|\n", width = 800, transposition = 0))
        assertTrue(rendering.messages.any { it.line == 2 && it.column == 1 }, rendering.messages.toString())
    }

    @Test
    fun `a message counts its lines in the tune whatever format lines the renderer adds`() = runTest {
        val tablature = "X:1\nL:1/8\nV:1 tab stafflines=6 strings=E2,A2,D3,G3,B3,E4\nK:C\nCDEF GAB@c|\n[CEG C D|\n"
        val rendering = renderer.render(tablature, width = 800, transposition = 3)
        val messages = (rendering as? AbcRendering.Systems)?.messages ?: assertIs<AbcRendering.Failed>(rendering).messages
        assertTrue(messages.any { it.line == 5 && it.column == 1 }, messages.toString())
    }

    @Test
    fun `an unknown decoration is placed on every one written`() = runTest {
        val rendering = assertIs<AbcRendering.Systems>(renderer.render("X:1\nK:C\nC !xyz! D !trill! E|\n!xyz!F +xyz+G|\n", width = 800, transposition = 0))
        val placed = rendering.messages.filter { "xyz" in it.text }.map { it.line to it.column }.toSet()
        assertEquals(setOf(2 to 2, 3 to 0, 3 to 7), placed)
    }

    @Test
    fun `a note length divided by zero is reported where it stands, never handed to abc2svg`() = runTest {
        val rendering = assertIs<AbcRendering.Failed>(renderer.render("X:1\nK:C\nC2 D/0 E|\n", width = 800, transposition = 0))
        assertEquals(listOf(AbcMessage(text = ZERO_DURATION_MESSAGE, line = 2, column = 4)), rendering.messages)
        assertIs<AbcRendering.Systems>(renderer.render(TUNE, width = 800, transposition = 0))
    }

    @Test
    fun `the characters the host separates its output with are blanked out of a tune without moving its positions`() = runTest {
        val rendering = assertIs<AbcRendering.Systems>(renderer.render("X:1\nT:A\u0001B\u0002\nK:C\n\"Am C D|\n", width = 800, transposition = 0))
        assertTrue(rendering.messages.any { it.line == 3 && it.column == 1 }, rendering.messages.toString())
    }

    private companion object {
        const val TUNE = "X:1\nT:Clean\nM:4/4\nL:1/8\nK:G\n|:\"G\"GABc dedB|\"D\"A2FA \"G\"G4:|\n"
    }
}
