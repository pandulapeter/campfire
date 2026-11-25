/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoundarySearchTest {

    @Test
    fun `the search halves the gap and ends on the widest width found to fit within the tolerance`() {
        val search = BoundarySearch(fitting = 360, tooWide = 488)
        var probes = 0
        while (!search.isDone) {
            val probe = search.probe
            search.record(placed = if (probe <= 450) 488 else 360)
            probes++
        }

        assertTrue(search.fitting in 450 - BOUNDARY_TOLERANCE..450, "The search ended on ${search.fitting}.")
        assertTrue(probes <= MAX_BOUNDARY_PROBES, "The search took $probes engravings.")
    }

    @Test
    fun `the search stops after its most engravings`() {
        val search = BoundarySearch(fitting = 0, tooWide = 100_000)
        repeat(MAX_BOUNDARY_PROBES) { search.record(placed = Int.MAX_VALUE) }

        assertTrue(search.isDone)
    }

    @Test
    fun `a swing is searched between its widest fitting width and the narrowest too wide one above it`() {
        val search = boundarySearchOf(swing = listOf(300, 500, 400, 600), placedFor = mapOf(300 to 500, 500 to 400, 400 to 600, 600 to 300))

        assertEquals(450, search?.probe)
    }

    @Test
    fun `a swing whose placements do not say what they were engraved for is not searched`() {
        assertNull(boundarySearchOf(swing = listOf(400, 500), placedFor = emptyMap()))
        assertNull(boundarySearchOf(swing = listOf(400, 500), placedFor = mapOf(400 to 500)))
    }

    @Test
    fun `a swing already within the tolerance is not searched`() {
        assertNull(boundarySearchOf(swing = listOf(400, 403), placedFor = mapOf(400 to 403, 403 to 400)))
    }
}
