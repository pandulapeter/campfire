/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll

import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals

internal class SongAutoScrollingTest {

    private val writes = mutableListOf<Pair<String, Int>>()
    private val autoScrolling = SongAutoScrolling(TestScope()).apply {
        writeSpeed = { fileName, speed -> writes += fileName to speed }
        song = SONG to 100
    }

    @Test
    fun `a drag is written once it ends and played until the store agrees`() {
        autoScrolling.dragSpeed(150)
        autoScrolling.writeDraggedSpeed()

        assertEquals(listOf(SONG to 150), writes)
        assertEquals(150, autoScrolling.speed)
    }

    @Test
    fun `a drag that ends on the stored speed leaves a later synced speed to play`() {
        autoScrolling.dragSpeed(150)
        autoScrolling.dragSpeed(100)
        autoScrolling.writeDraggedSpeed()
        autoScrolling.song = SONG to 130
        autoScrolling.settleDraggedSpeed()

        assertEquals(emptyList(), writes)
        assertEquals(130, autoScrolling.speed)
    }

    @Test
    fun `a drag cut short by playing stopping is written`() {
        autoScrolling.dragSpeed(150)
        autoScrolling.pause()

        assertEquals(listOf(SONG to 150), writes)
    }

    private companion object {
        const val SONG = "song.cho"
    }
}
