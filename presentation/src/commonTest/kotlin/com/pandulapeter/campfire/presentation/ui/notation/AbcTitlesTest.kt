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

class AbcTitlesTest {

    @Test
    fun `a tune named as the song leaves its title to the page`() {
        assertEquals("X:1\n%%writefields TC 0\nT:The  Reel\nK:G\nGAB|", abcTuneWithoutSongTitle("X:1\nT:The  Reel\nK:G\nGAB|", songTitle = "the reel"))
    }

    @Test
    fun `the tunes of a medley named otherwise keep their names`() {
        val medley = "X:1\nT:The Reel\nK:G\nGAB|\n\nX:2\nT:Another Reel\nK:D\nDEF|"
        assertEquals(
            "X:1\n%%writefields TC 0\nT:The Reel\nK:G\nGAB|\n\nX:2\nT:Another Reel\nK:D\nDEF|",
            abcTuneWithoutSongTitle(medley, songTitle = "The Reel"),
        )
        assertEquals(medley, abcTuneWithoutSongTitle(medley, songTitle = "A Medley"))
    }

    @Test
    fun `a tune with no reference number is read as one tune`() {
        assertEquals("%%writefields TC 0\nT:Tune\nK:G", abcTuneWithoutSongTitle("T:Tune\nK:G", songTitle = "Tune"))
    }

    @Test
    fun `a title in the music is not the tune's`() {
        assertEquals("X:1\nK:G\nT:Part B\nGAB|", abcTuneWithoutSongTitle("X:1\nK:G\nT:Part B\nGAB|", songTitle = "Part B"))
    }
}
