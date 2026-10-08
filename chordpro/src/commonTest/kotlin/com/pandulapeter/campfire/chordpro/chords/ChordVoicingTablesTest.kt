/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.chords

import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChordVoicingTablesTest {

    @Test
    fun `every shape of the tables sounds exactly the chord it is filed under`() {
        tables.forEach { (instrument, lines) ->
            lines.forEach { line ->
                val shape = assertIs<ChordProDefinitions.Reading.Shape>(ChordProDefinitions.read(line), line)
                assertEquals(instrument, shape.instrument, line)
                val chord = assertNotNull(ChordProChords.parse(shape.name), line)
                val voicing = assertIs<ChordVoicing.Fretted>(shape.voicing)
                val played = if (instrument == ChordInstrument.UKULELE) chord.copy(bass = null) else chord
                val sounded = ChordShapeGeometry.pitchClasses(voicing, instrument)
                val fifth = (played.root + 7) % 12
                assertTrue(played.pitchClasses.containsAll(sounded), "$line sounds only notes of its chord")
                assertTrue(
                    (played.pitchClasses - sounded).all { it == fifth && played.intervals.size >= 4 || it == played.root && played.pitchClasses.size > instrument.tuning.size },
                    "$line leaves out ${played.pitchClasses - sounded}",
                )
                if (instrument == ChordInstrument.GUITAR) {
                    val lowest = voicing.frets.withIndex().filter { it.value != null }.minOf { instrument.tuning[it.index] + it.value!! }
                    assertEquals(chord.bass ?: chord.root, lowest % 12, "$line sounds its bass or root lowest")
                }
            }
        }
    }

    @Test
    fun `every fingering names as many fingers as frets are stopped`() {
        tables.forEach { (_, lines) ->
            lines.forEach { line ->
                val voicing = (ChordProDefinitions.read(line) as ChordProDefinitions.Reading.Shape).voicing as ChordVoicing.Fretted
                val fingers = assertNotNull(voicing.fingers, line)
                voicing.frets.forEachIndexed { string, fret ->
                    assertEquals(fret != null && fret > 0, fingers[string] > 0, "$line, string ${string + 1}")
                }
                assertTrue(fingers.all { it in 0..4 }, line)
                assertTrue(ChordShapeGeometry.isHoldable(voicing.frets), line)
            }
        }
    }

    private val tables = mapOf(
        ChordInstrument.GUITAR to ChordVoicingTables.guitar,
        ChordInstrument.UKULELE to ChordVoicingTables.ukulele,
    )
}
