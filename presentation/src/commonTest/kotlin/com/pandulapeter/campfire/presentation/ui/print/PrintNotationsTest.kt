/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import com.pandulapeter.campfire.abc.api.AbcRenderer
import com.pandulapeter.campfire.abc.api.AbcRendering
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.data.model.preferences.UserPreferences.Instrument
import com.pandulapeter.campfire.presentation.ui.notation.NotationFixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrintNotationsTest {

    @Test
    fun `a score is engraved in the reader's part where one is theirs`() = runTest {
        val renderer = RecordingRenderer()
        printNotationsOf(sourceFor(Instrument.BASS_FIVE_STRING), PrintSettings(format = PrintSettings.Format.PDF), renderer)
        val tune = renderer.tunes.single()
        assertTrue("%%score 2" in tune && "C,|" in tune, tune)
        assertTrue("%%score 1 2" !in tune && "V:1\nC|" !in tune, tune)
    }

    @Test
    fun `a score with no part of the reader's is engraved whole`() = runTest {
        val renderer = RecordingRenderer()
        printNotationsOf(sourceFor(Instrument.UKULELE), PrintSettings(format = PrintSettings.Format.PDF), renderer)
        assertTrue("%%score 1 2" in renderer.tunes.single())
    }

    @Test
    fun `a part that fails on its own is engraved as the whole score`() = runTest {
        val renderer = RecordingRenderer(failing = "%%score 2")
        val notations = printNotationsOf(sourceFor(Instrument.BASS), PrintSettings(format = PrintSettings.Format.PDF), renderer)
        assertEquals(2, renderer.tunes.size)
        assertTrue("%%score 1 2" in renderer.tunes.last())
        assertEquals(1, notations.getValue(FILE_NAME).size)
    }

    /** Engraves anything as the same fixture, but a tune holding [failing], and remembers every tune it was handed. */
    private class RecordingRenderer(private val failing: String? = null) : AbcRenderer {
        val tunes = mutableListOf<String>()

        override suspend fun render(tune: String, width: Int, transposition: Int): AbcRendering {
            tunes += tune
            return if (failing != null && failing in tune) {
                AbcRendering.Failed("Failing on purpose")
            } else {
                AbcRendering.Systems(listOf(NotationFixtures.HEADER, NotationFixtures.SYSTEM))
            }
        }
    }

    private fun sourceFor(instrument: Instrument) = PrintSource(
        title = "Band",
        songs = listOf(PrintSong(fileName = FILE_NAME, title = "Band", artist = null, song = ChordProParser.parse("{title: Band}\n{start_of_abc}\n$BAND\n{end_of_abc}"))),
        instrument = instrument,
    )

    private companion object {
        const val FILE_NAME = "band.cho"
        const val BAND = "X:1\n%%score 1 2\nV:1 nm=\"Vocals\"\nV:2 nm=\"Bass\"\nK:C\nV:1\nC|\nV:2\nC,|"
    }
}
