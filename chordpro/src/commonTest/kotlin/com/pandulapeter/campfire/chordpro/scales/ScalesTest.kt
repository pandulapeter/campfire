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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScalesTest {

    @Test
    fun `every scale is a sorted set of notes within the octave starting at its root`() {
        Scales.all.forEach { scale ->
            assertEquals(0, scale.intervals.first(), scale.id)
            assertEquals(scale.intervals.sorted().distinct(), scale.intervals, scale.id)
            assertTrue(scale.intervals.all { it in 0..11 }, scale.id)
            assertEquals(scale.intervals.size, scale.degrees.size, scale.id)
        }
    }

    @Test
    fun `every degree label names its interval`() {
        val semitones = mapOf(
            "1" to 0, "b2" to 1, "2" to 2, "b3" to 3, "#2" to 3, "3" to 4, "b4" to 4, "4" to 5, "#4" to 6, "b5" to 6,
            "5" to 7, "#5" to 8, "b6" to 8, "6" to 9, "bb7" to 9, "b7" to 10, "#6" to 10, "7" to 11,
        )
        Scales.all.forEach { scale ->
            assertEquals(scale.intervals, scale.degrees.map { semitones.getValue(it) }, scale.id)
        }
    }

    @Test
    fun `every mode is its parent rotated to start on its degree`() {
        Scales.all.forEach { scale ->
            val (parentId, degree) = scale.modeOf ?: return@forEach
            val parent = assertNotNull(Scales.fromId(parentId), scale.id)
            val start = parent.intervals[degree - 1]
            assertEquals(scale.intervals, parent.intervals.map { (it - start).mod(12) }.sorted(), scale.id)
        }
    }

    @Test
    fun `no two scales are the same notes and every id is one scale`() {
        assertEquals(Scales.all.size, Scales.all.map { it.intervals }.distinct().size)
        assertEquals(Scales.all.size, Scales.all.map { it.id }.distinct().size)
        assertTrue(Scales.all.containsAll(Scales.common))
    }

    @Test
    fun `the families are browsed in their order and hold every scale`() {
        assertEquals(Scales.all, ScaleFamily.entries.flatMap(Scales::of))
    }

    @Test
    fun `every mode is browsed in the family of the scale it is a mode of`() {
        Scales.all.filter { it.modeOf != null }.forEach { scale ->
            assertEquals(Scales.fromId(scale.modeOf!!.first)!!.family, scale.family, scale.id)
        }
    }

    @Test
    fun `an unknown id is no scale`() = assertNull(Scales.fromId("maqam_rast"))

    @Test
    fun `the steps of a scale close the octave`() {
        assertEquals(listOf(2, 2, 1, 2, 2, 2, 1), Scales.steps(Scales.fromId("major")!!))
        assertEquals(listOf(3, 2, 2, 3, 2), Scales.steps(Scales.fromId("minor_pentatonic")!!))
        Scales.all.forEach { assertEquals(12, Scales.steps(it).sum(), it.id) }
    }

    @Test
    fun `a mode's parent is played on the root it is counted from`() {
        assertEquals(0, Scales.parentRoot(Scales.fromId("dorian")!!, root = 2))
        assertEquals(0, Scales.parentRoot(Scales.fromId("natural_minor")!!, root = 9))
        assertEquals(0, Scales.parentRoot(Scales.fromId("phrygian_dominant")!!, root = 7))
        assertNull(Scales.parentRoot(Scales.fromId("major")!!, root = 0))
    }

    @Test
    fun `the pitch classes of a scale are counted from its root`() =
        assertEquals(setOf(2, 4, 5, 7, 9, 11, 0), Scales.pitchClasses(Scales.fromId("dorian")!!, root = 2))

    @Test
    fun `the minor 6 pentatonic is the minor pentatonic with a sixth, and the dominant pentatonic its mode`() {
        assertEquals(listOf(0, 3, 5, 7, 9), Scales.fromId("minor_6_pentatonic")!!.intervals)
        val dominant = Scales.fromId("dominant_pentatonic")!!
        assertEquals(listOf(0, 2, 4, 7, 10), dominant.intervals)
        assertEquals(2, Scales.parentRoot(dominant, root = 7))
    }
}
