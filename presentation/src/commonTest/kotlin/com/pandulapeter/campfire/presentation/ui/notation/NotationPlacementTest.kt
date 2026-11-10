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

class NotationPlacementTest {

    private val first = AbcTune("X:1\nK:C\nCDEF|", 0)
    private val second = AbcTune("X:2\nK:G\nGABc|", 0)
    private val tunes = setOf(first, second)

    private fun NotationPlacement.width(rememberedWidth: Int? = null, availableWidth: Int = 2_000, unitSize: Float = 2f) =
        widthFor(tunes, narrowestWidth = 300, availableWidth = availableWidth, unitSize = unitSize, rememberedWidth = rememberedWidth)

    @Test
    fun `tunes not yet placed are engraved for the narrowest column, or for where the song was placed before`() {
        assertEquals(300, NotationPlacement().width())
        assertEquals(450, NotationPlacement().width(rememberedWidth = 450))
    }

    @Test
    fun `tunes are engraved for the narrowest column any of them was placed in, in units`() {
        val placement = NotationPlacement()
        placement.width()
        placement.place(first, widthPx = 1_000)
        placement.place(second, widthPx = 900)

        assertEquals(450, placement.width())
    }

    @Test
    fun `a layout swinging back to a width the tunes were engraved for settles on the narrowest column`() {
        val placement = NotationPlacement()
        placement.width()
        placement.place(first, widthPx = 1_000)
        assertEquals(500, placement.width())
        placement.place(first, widthPx = 800)
        assertEquals(400, placement.width())
        placement.place(first, widthPx = 1_000)

        assertEquals(300, placement.width())
        placement.place(first, widthPx = 800)
        assertEquals(300, placement.width())
    }

    @Test
    fun `a new page width follows the placement again`() {
        val placement = NotationPlacement()
        placement.width()
        placement.place(first, widthPx = 1_000)
        placement.width()
        placement.place(first, widthPx = 800)
        placement.width()
        placement.place(first, widthPx = 1_000)
        assertEquals(300, placement.width())

        assertEquals(500, placement.width(availableWidth = 2_100))
    }

    @Test
    fun `a column narrower than the narrowest is engraved for the narrowest`() {
        val placement = NotationPlacement()
        placement.place(first, widthPx = 100)

        assertEquals(300, placement.width())
    }
}
