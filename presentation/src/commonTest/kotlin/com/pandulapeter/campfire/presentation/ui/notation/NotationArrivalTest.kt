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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NotationArrivalTest {

    @Test
    fun `a song's first rendering arrives whole`() {
        assertEquals(setOf("a", "b"), arrivingTunesOf(previous = emptySet(), rendered = setOf("a", "b"), rerendered = emptySet()))
    }

    @Test
    fun `a transposition re-renders and nothing arrives`() {
        assertEquals(emptySet(), arrivingTunesOf(previous = setOf("a", "b"), rendered = setOf("a", "b"), rerendered = setOf("a", "b")))
    }

    @Test
    fun `a tune added beside rendered ones is the only one arriving`() {
        assertEquals(setOf("c"), arrivingTunesOf(previous = setOf("a", "b"), rendered = setOf("a", "b", "c"), rerendered = emptySet()))
    }

    @Test
    fun `a tune replacing its stand-in does not arrive`() {
        assertEquals(emptySet(), arrivingTunesOf(previous = setOf("a"), rendered = setOf("a2"), rerendered = setOf("a2")))
    }

    @Test
    fun `a rendering that re-renders a tune is no arrival`() {
        assertNull(notationsOf(rerendered = setOf("a"), arriving = setOf("b")).arrival)
        assertNotNull(notationsOf(rerendered = emptySet(), arriving = setOf("b")).arrival)
    }

    private fun notationsOf(rerendered: Set<String>, arriving: Set<String>) = SongNotations(
        rendered = RenderedNotations(request = null, renderings = emptyMap(), rerenderedTunes = rerendered, arrivingTunes = arriving),
        standIns = emptyMap(),
        unitSize = 1f,
    )
}
