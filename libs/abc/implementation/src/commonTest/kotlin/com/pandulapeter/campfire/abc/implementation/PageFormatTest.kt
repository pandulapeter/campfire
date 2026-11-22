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

class PageFormatTest {

    @Test
    fun `the tune's own page geometry and scale and text fonts become comments of the same length`() {
        val tune = "X:1\n%%pagewidth 21.59cm\n%%scale 0.83\nI:leftmargin 1cm\n%% vocalfont Times 12\n%%gchordfont Arial\nK:C\nCDEF|\n"
        val written = withoutOwnPageFormat(tune)
        assertEquals(
            "X:1\n% pagewidth 21.59cm\n% scale 0.83\n% leftmargin 1cm\n%  vocalfont Times 12\n% gchordfont Arial\nK:C\nCDEF|\n",
            written,
        )
        assertEquals(tune.length, written.length)
        assertEquals(tune.lines().size, written.lines().size)
    }

    @Test
    fun `a voice's own scale and a directive only starting like one are kept`() {
        val tune = "X:1\n%%staffscale 0.8\n%%voicescale 0.9\n%%scalefactor 2\nV:1 scale=0.7\n%%titlefont Arial\nK:C\nCDEF|\n"
        assertEquals(tune, withoutOwnPageFormat(tune))
    }

    @Test
    fun `a directive in the middle of a line is not one`() {
        val tune = "X:1\nK:C\nCDEF| % %%scale 0.5\n"
        assertEquals(tune, withoutOwnPageFormat(tune))
    }
}
