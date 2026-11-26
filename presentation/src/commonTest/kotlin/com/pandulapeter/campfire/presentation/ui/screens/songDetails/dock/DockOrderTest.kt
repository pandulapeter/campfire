/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import kotlin.test.Test
import kotlin.test.assertEquals

internal class DockOrderTest {

    @Test
    fun `the partition brings the chosen items first and keeps the order within both parts`() =
        assertEquals(listOf(2, 4, 6, 1, 3, 5), listOf(1, 2, 3, 4, 5, 6).partitionedStably { it % 2 == 0 })

    @Test
    fun `a partition of all or none of the items keeps them as they were`() {
        assertEquals(listOf(3, 1, 2), listOf(3, 1, 2).partitionedStably { true })
        assertEquals(listOf(3, 1, 2), listOf(3, 1, 2).partitionedStably { false })
    }

    @Test
    fun `the chords of a song in several keys are brought forward within their own key`() {
        val chords = listOf("A" to "Am", "A" to "E", "A" to "Dm", "B" to "Bm", "B" to "F#")
        val order = chords.partitionedWithinRuns(runOf = { it.first }) { it.second in setOf("Dm", "F#") }
        assertEquals(listOf("Dm", "Am", "E", "F#", "Bm"), order.map { it.second })
    }

    @Test
    fun `the home group's rows stay first however dimmed and the other groups on the screen come next`() {
        val rows = listOf(0 to "home", 0 to "home over E7", 1 to "bridge", 2 to "outro", 2 to "outro over B7")
        val order = dockScaleRowOrder(rows, groupOf = { it.first }, isHome = { it == 0 }, onScreen = setOf(2))
        assertEquals(listOf("home", "home over E7", "outro", "outro over B7", "bridge"), order.map { it.second })
    }

    @Test
    fun `no range known leaves the scale rows in the song's order`() {
        val rows = listOf(0, 1, 2)
        assertEquals(rows, dockScaleRowOrder(rows, groupOf = { it }, isHome = { it == 0 }, onScreen = null))
    }
}
