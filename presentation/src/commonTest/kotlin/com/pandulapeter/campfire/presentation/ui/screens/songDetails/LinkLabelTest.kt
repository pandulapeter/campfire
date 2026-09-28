/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import kotlin.test.Test
import kotlin.test.assertEquals

class LinkLabelTest {

    @Test
    fun `a link is named by its host`() {
        assertEquals("youtube.com", linkLabel("https://www.youtube.com/watch?v=abc"))
        assertEquals("youtu.be", linkLabel("https://youtu.be/abc"))
        assertEquals("open.spotify.com", linkLabel("https://open.spotify.com/track/abc#top"))
        assertEquals("example.com", linkLabel("http://user@EXAMPLE.com:8080?q=1"))
    }

    @Test
    fun `a backslash ends the host the way a browser ends it`() {
        assertEquals("evil.example", linkLabel("https://evil.example\\@youtube.com/watch"))
        assertEquals("example.com", linkLabel("https://example.com\\path"))
    }

    @Test
    fun `a link with no host to speak of is named by its address`() {
        assertEquals("https://", linkLabel("https://"))
    }
}
