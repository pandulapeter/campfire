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

import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChordVoicingTablesTest {

    @Test
    fun `every shape of the tables sounds exactly the chord it is filed under`() {
        tables.forEach { (tuning, lines) ->
            val instrument = tuning.instrument
            lines.forEach { line ->
                val shape = assertIs<ChordProDefinitions.Reading.Shape>(ChordProDefinitions.read(line, instrument.id), line)
                assertEquals(instrument, shape.instrument, line)
                val chord = assertNotNull(ChordProChords.parse(shape.name), line)
                val voicing = assertIs<ChordVoicing.Fretted>(shape.voicing)
                val played = if (instrument.playsChordOver) chord.copy(bass = null) else chord
                val sounded = ChordShapeGeometry.pitchClasses(voicing, tuning.strings)
                val fifth = (played.root + 7) % 12
                assertTrue(played.pitchClasses.containsAll(sounded), "$line sounds only notes of its chord")
                assertTrue(
                    (played.pitchClasses - sounded).all { it == fifth && played.intervals.size >= 4 || it == played.root && played.pitchClasses.size > instrument.tuning.size },
                    "$line leaves out ${played.pitchClasses - sounded}",
                )
                if (!instrument.playsChordOver) {
                    val bottom = voicing.frets.withIndex().filter { it.value != null }.minBy { tuning.strings[it.index] + it.value!! }
                    val lowest = (tuning.strings[bottom.index] + bottom.value!!) % 12
                    val isOpenFifth = tuning.isOpen && chord.bass == null && lowest == fifth && bottom.value == 0
                    assertTrue(lowest == (chord.bass ?: chord.root) || isOpenFifth, "$line sounds its bass or root lowest, or an open tuning's open string its fifth")
                }
            }
        }
    }

    @Test
    fun `every fingering names as many fingers as frets are stopped`() {
        tables.forEach { (tuning, lines) ->
            lines.forEach { line ->
                val voicing = (ChordProDefinitions.read(line, tuning.instrument.id) as ChordProDefinitions.Reading.Shape).voicing as ChordVoicing.Fretted
                assertTrue(ChordShapeGeometry.isHoldable(voicing.frets), line)
                val fingers = voicing.fingers ?: return@forEach
                voicing.frets.forEachIndexed { string, fret ->
                    assertEquals(fret != null && fret > 0, fingers[string] > 0, "$line, string ${string + 1}")
                }
                assertTrue(fingers.all { it in 0..4 }, line)
            }
        }
    }

    @Test
    fun `every tuning without a table of its own is the one whose table it reads, every string moved by one interval`() {
        ChordTuning.entries.filter { it.tableTuning != it }.forEach { tuning ->
            assertEquals(tuning.tableTuning, tuning.tableTuning.tableTuning, "${tuning.id} reads a table of its own tuning's")
            val intervals = tuning.strings.indices.map { (tuning.strings[it] - tuning.tableTuning.strings[it]).mod(12) }.toSet()
            assertEquals(setOf(tuning.shift), intervals, tuning.id)
        }
        ChordTuning.entries.filter { it.tableTuning == it }.forEach { assertTrue(it in tables || !it.instrument.isFretted, it.id) }
    }

    private val tables = mapOf(
        ChordTuning.GUITAR to ChordVoicingTables.guitar,
        ChordTuning.UKULELE to ChordVoicingTables.ukulele,
        ChordTuning.MANDOLIN to ChordVoicingTables.mandolin,
        ChordTuning.GUITAR_DROP_D to ChordVoicingTables.dropD,
        ChordTuning.GUITAR_DOUBLE_DROP_D to ChordVoicingTables.doubleDropD,
        ChordTuning.GUITAR_OPEN_G to ChordVoicingTables.openG,
        ChordTuning.GUITAR_OPEN_D to ChordVoicingTables.openD,
        ChordTuning.GUITAR_OPEN_C to ChordVoicingTables.openC,
        ChordTuning.GUITAR_DADGAD to ChordVoicingTables.dadgad,
        ChordTuning.MANDOLIN_GDGD to ChordVoicingTables.mandolinGdgd,
    )
}
