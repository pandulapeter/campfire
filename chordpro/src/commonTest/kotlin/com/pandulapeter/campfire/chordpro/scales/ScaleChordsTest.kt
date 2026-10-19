/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.scales

import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicingSearch
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScaleChordsTest {

    private fun scale(id: String) = Scales.fromId(id)!!

    private fun scalesFor(name: String) = ScaleChords.scalesContaining(ChordProChords.parse(name)!!).map { it.id }

    @Test
    fun `the major scale stacks into the chords every chart names its degrees by`() {
        assertEquals("C Dm Em F G Am Bdim", ScaleChords.triads(scale("major"), "C").joinToString(" ") { it.name })
        assertEquals("Cmaj7 Dm7 Em7 Fmaj7 G7 Am7 Bm7b5", ScaleChords.sevenths(scale("major"), "C").joinToString(" ") { it.name })
        assertEquals("I ii iii IV V vi vii°", ScaleChords.triads(scale("major"), "C").joinToString(" ") { it.numeral })
    }

    @Test
    fun `the harmonic minor has its major fifth and augmented third`() {
        val triads = ScaleChords.triads(scale("harmonic_minor"), "A")
        assertEquals("E", triads[4].name)
        assertEquals("Caug", triads[2].name)
        assertEquals("bIII+", triads[2].numeral)
        assertEquals("E7", ScaleChords.sevenths(scale("harmonic_minor"), "A")[4].name)
    }

    @Test
    fun `a chord is numbered by the scale's own degree, not by the step its semitones make`() {
        assertEquals("bV", ScaleChords.triads(scale("locrian"), "C")[4].numeral)
        assertEquals("#iv°", ScaleChords.triads(scale("lydian"), "C")[3].numeral)
        assertEquals("#ii°", ScaleChords.triads(scale("lydian_sharp_2"), "C")[1].numeral)
        assertEquals("bbVII", ScaleChords.triads(scale("ultralocrian"), "C")[6].numeral)
        assertEquals("#vø7", ScaleChords.sevenths(scale("lydian_augmented"), "C")[4].numeral)
    }

    @Test
    fun `a scale of another size than seven has no chords`() {
        assertTrue(ScaleChords.triads(scale("minor_pentatonic"), "A").isEmpty())
        assertTrue(ScaleChords.sevenths(scale("diminished"), "C").isEmpty())
    }

    @Test
    fun `every chord of every scale is read back as the notes it was built from`() {
        Scales.all.filter { it.intervals.size == 7 }.forEach { scale ->
            listOf("C", "Eb", "F#", "Bb").forEach { root ->
                val pitchClasses = Scales.pitchClasses(scale, ChordProChords.pitchClass(root)!!)
                listOf(ScaleChords.triads(scale, root) to 3, ScaleChords.sevenths(scale, root) to 4).forEach { (chords, size) ->
                    chords.forEach { chord ->
                        val parsed = assertNotNull(ChordProChords.parse(chord.name), chord.name)
                        assertEquals(size, parsed.pitchClasses.size, "$root ${scale.id}: ${chord.name}")
                        assertTrue(pitchClasses.containsAll(parsed.pitchClasses), "$root ${scale.id}: ${chord.name}")
                    }
                }
            }
        }
    }

    @Test
    fun `a chord is played over the scales on its root that hold its notes`() {
        assertTrue("mixolydian" in scalesFor("C7"))
        assertFalse("major" in scalesFor("C7"))
        assertTrue("dorian" in scalesFor("Am7") && "natural_minor" in scalesFor("Am7"))
        assertFalse("harmonic_minor" in scalesFor("Am7"))
        assertTrue("mixolydian" in scalesFor("C13") && "bebop_dominant" in scalesFor("C13"))
    }

    @Test
    fun `the common scales come first and the chromatic one never`() {
        val forC = scalesFor("C")
        assertEquals(listOf("major", "major_pentatonic", "mixolydian", "major_blues"), forC.take(4))
        assertFalse(Scales.CHROMATIC_ID in forC)
    }

    @Test
    fun `a song is played over the scales on its key that hold the notes of all of its chords`() {
        val notes = listOf("G", "C", "D", "Em").flatMap { ChordProChords.parse(it)!!.pitchClasses }.toSet()
        val inG = ScaleChords.scalesContaining(root = 7, pitchClasses = notes).map { it.id }
        assertEquals("major", inG.first())
        assertFalse("major_pentatonic" in inG)
        assertEquals("natural_minor", ScaleChords.scalesContaining(root = 4, pitchClasses = notes).first().id)
        assertTrue(ScaleChords.scalesContaining(root = 7, pitchClasses = notes + 1).isEmpty())
    }

    @Test
    fun `a slash chord's bass is left out`() = assertEquals(scalesFor("D"), scalesFor("D/F#"))

    @Test
    fun `a chord with both a flat and a natural ninth has no scales`() =
        assertTrue(ScaleChords.scalesContaining(Chord(root = 0, intervals = listOf(0, 1, 2, 4, 7, 10))).isEmpty())

    @Test
    fun `every scale suggested holds every note of the chord`() {
        listOf("C", "Cm", "C7", "Cmaj7", "Cm7", "Cm7b5", "Cdim7", "Csus4", "C9", "Caug").forEach { name ->
            val chord = ChordProChords.parse(name)!!
            ScaleChords.scalesContaining(chord).forEach { scale ->
                assertTrue(Scales.pitchClasses(scale, chord.root).containsAll(chord.pitchClasses), "$name ${scale.id}")
            }
        }
    }

    @Test
    fun `every chord a seven-note scale stacks has a table shape in the standard and lowered tunings`() {
        val missing = missingTableShapes(ChordTuning.entries.filter { it.instrument != ChordInstrument.KEYBOARD } - TUNINGS_LACKING_SHAPES)
        assertTrue(missing.isEmpty(), missing.toString())
    }

    /**
     * The same over the tunings whose tables lack chords a scale sheet shows, which it draws as empty frames. The
     * mandolin and the mandolin a half step down lack 7#5, 7b5, dim, maj7#5, maj7b5, and mmaj7; double drop D lacks
     * every quality past the plain triads and sevenths (aug, dim, dim7, m7b5, maj7, the altered sevenths, mmaj7, sus2,
     * sus4); the open guitar tunings, DADGAD, the AEAE and GDGD mandolins, and every banjo tuning lack most of them, and
     * in some keys even major and minor triads.
     */
    @Ignore
    @Test
    fun `every chord a seven-note scale stacks has a table shape in the open and banjo and mandolin tunings`() {
        val missing = missingTableShapes(TUNINGS_LACKING_SHAPES)
        assertTrue(missing.isEmpty(), missing.toString())
    }

    /**
     * Every distinct chord the seven-note scales stack in every key that has no table shape in one of [tunings], named
     * by the first scale to stack it and the tuning. The tables answer by pitch content, so a chord two scales share is
     * checked once. They are asked directly rather than through `ChordVoicings`, whose session cache another test in
     * the same run may have filled with a searched shape.
     */
    private fun missingTableShapes(tunings: List<ChordTuning>): List<String> {
        val chords = LinkedHashMap<Chord, String>()
        Scales.all.filter { it.intervals.size == 7 }.forEach { scale ->
            ROOTS.forEach { root ->
                (ScaleChords.triads(scale, root) + ScaleChords.sevenths(scale, root)).forEach {
                    chords.getOrPut(ChordProChords.parse(it.name)!!) { "${scale.id} ${it.name}" }
                }
            }
        }
        return tunings.flatMap { tuning ->
            chords.filterKeys { !ChordVoicingSearch.hasTableShapes(it, tuning) }.values.map { "$it ${tuning.id}" }
        }
    }
}

private val ROOTS = listOf("C", "C#", "Db", "D", "Eb", "E", "F", "F#", "Gb", "G", "Ab", "A", "Bb", "B")

private val TUNINGS_LACKING_SHAPES = listOf(
    ChordTuning.GUITAR_DOUBLE_DROP_D,
    ChordTuning.GUITAR_OPEN_G,
    ChordTuning.GUITAR_OPEN_D,
    ChordTuning.GUITAR_OPEN_E,
    ChordTuning.GUITAR_OPEN_A,
    ChordTuning.GUITAR_OPEN_C,
    ChordTuning.GUITAR_DADGAD,
    ChordTuning.MANDOLIN,
    ChordTuning.MANDOLIN_HALF_STEP_DOWN,
    ChordTuning.MANDOLIN_AEAE,
    ChordTuning.MANDOLIN_GDGD,
    ChordTuning.BANJO,
    ChordTuning.BANJO_DOUBLE_C,
    ChordTuning.BANJO_C,
    ChordTuning.BANJO_OPEN_D,
    ChordTuning.BANJO_G_MODAL,
)
