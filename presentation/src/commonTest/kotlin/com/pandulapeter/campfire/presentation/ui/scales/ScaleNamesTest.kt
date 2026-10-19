/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.scales

import com.pandulapeter.campfire.chordpro.scales.Scales
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScaleNamesTest {

    @Test
    fun `every scale of the catalogue has a name of its own`() {
        val names = Scales.all.map { assertNotNull(scaleNameResource(it.id), it.id) }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `an id no scale has has no name`() = assertNull(scaleNameResource("maqam_rast"))

    @Test
    fun `a degree is shown with the accidental signs`() = assertEquals(listOf("1", "♭3", "♯4", "♭♭7"), listOf("1", "b3", "#4", "bb7").map(::displayedDegree))
}
