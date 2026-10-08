/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordShapeGeometryTest {

    @Test
    fun `fingers are counted with the barres a hand makes`() {
        assertEquals(3, ChordShapeGeometry.fingerCount(listOf(null, 3, 2, 0, 1, 0)))
        assertEquals(3, ChordShapeGeometry.fingerCount(listOf(1, 3, 3, 2, 1, 1)))
        assertEquals(2, ChordShapeGeometry.fingerCount(listOf(null, 1, 3, 3, 3, 3)))
        assertEquals(4, ChordShapeGeometry.fingerCount(listOf(1, 0, 3, 2, 1, 1)))
        assertEquals(0, ChordShapeGeometry.fingerCount(listOf(0, 0, 0, 0)))
        assertTrue(ChordShapeGeometry.isHoldable(listOf(null, 13, 15, 15, 15, 13)))
        assertTrue(!ChordShapeGeometry.isHoldable(listOf(null, 16, 18, 18, 18, 16)))
        assertTrue(!ChordShapeGeometry.isHoldable(listOf(1, 2, 3, 4, 5, null)))
    }

    @Test
    fun `a diagram starts at the nut where the shape fits there`() {
        assertEquals(1, ChordShapeGeometry.baseFret(listOf(null, 3, 2, 0, 1, 0)))
        assertEquals(1, ChordShapeGeometry.baseFret(listOf(null, 1, 3, 3, 3, 4)))
        assertEquals(3, ChordShapeGeometry.baseFret(listOf(null, 3, 5, 5, 5, 3)))
        assertEquals(1, ChordShapeGeometry.baseFret(listOf(0, 0, 0, 0)))
    }
}
