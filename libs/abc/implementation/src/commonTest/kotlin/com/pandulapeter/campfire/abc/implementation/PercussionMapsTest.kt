/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.abc.implementation

import kotlin.test.Test
import kotlin.test.assertEquals

class PercussionMapsTest {

    @Test
    fun `an exported percussion map loses its shown step and keeps its field and its head`() {
        assertEquals("X:1\nI:percmap ^g 42 x\nK:C perc\n", withAbc2svgPercussionMaps("X:1\nI:percmap ^g g 42 x\nK:C perc\n"))
        assertEquals("%%percmap F 36 triangle", withAbc2svgPercussionMaps("%%percmap F F 36 triangle"))
    }

    @Test
    fun `an exported percussion map without a head or with the normal one is left with none`() {
        assertEquals("I:percmap c 38", withAbc2svgPercussionMaps("I:percmap c c 38"))
        assertEquals("I:percmap c 38\n%%percmap D 35", withAbc2svgPercussionMaps("I:percmap c c 38 normal\n%%percmap D D 35 normal  "))
    }

    @Test
    fun `a percussion map already in abc2svg's form is left as it is`() {
        listOf("%%percmap ^g 42 x", "%%percmap c 38", "%%percmap F snare triangle", "K:C\n% I:percmap ^g g 42 x").forEach { tune ->
            assertEquals(tune, withAbc2svgPercussionMaps(tune), tune)
        }
    }
}
