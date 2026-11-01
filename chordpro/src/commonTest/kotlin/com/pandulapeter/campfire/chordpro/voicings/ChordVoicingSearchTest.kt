/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.voicings

import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ChordVoicingSearchTest {

    @BeforeTest
    fun resetCache() = ChordVoicings.resetCache()

    @Test
    fun `the search offers a shape that keeps the root before any that leaves it out`() {
        listOf(
            "C69" to ChordTuning.MANDOLIN,
            "Cmaj9" to ChordTuning.MANDOLIN,
            "D11" to ChordTuning.UKULELE,
            "E11" to ChordTuning.UKULELE,
            "Em11" to ChordTuning.BANJO_OPEN_D,
        ).forEach { (name, tuning) ->
            val chord = ChordProChords.parse(name)!!
            val first = ChordVoicingSearch.search(chord, tuning).first()
            assertTrue(chord.root in ChordShapeGeometry.pitchClasses(first, tuning.strings), "$name in ${tuning.id}: ${ChordVoicings.write(first)}")
        }
    }

    @Test
    fun `a chord with no shape that keeps the root within reach still has its rootless shapes`() {
        val chord = ChordProChords.parse("Ab13")!!
        val shapes = ChordVoicingSearch.search(chord, ChordTuning.MANDOLIN_GDGD).filter { it.frets.filterNotNull().max() <= 12 }
        assertTrue(shapes.isNotEmpty())
        assertTrue(shapes.none { chord.root in ChordShapeGeometry.pitchClasses(it, ChordTuning.MANDOLIN_GDGD.strings) })
    }

    @Test
    fun `a rootless shape is still offered, after the ones that keep the root`() {
        val chord = ChordProChords.parse("C69")!!
        val all = ChordVoicings.all(chord, ChordTuning.MANDOLIN).map { it as ChordVoicing.Fretted }
        val rootless = all.indexOfFirst { ChordVoicings.write(it) == "0 0 0 0" }
        assertTrue(rootless > 0, "the open strings are offered")
        assertTrue(all.take(rootless).all { chord.root in ChordShapeGeometry.pitchClasses(it, ChordTuning.MANDOLIN.strings) })
        assertTrue(chord.root in ChordShapeGeometry.pitchClasses(all.first(), ChordTuning.MANDOLIN.strings))
    }
}
