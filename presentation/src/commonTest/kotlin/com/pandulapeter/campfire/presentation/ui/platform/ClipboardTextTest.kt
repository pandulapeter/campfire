/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class ClipboardTextTest {

    @Test
    fun `a copy whose HTML draws chords the text lost is pasted as the HTML's chord sheet`() {
        assertEquals(
            ClipboardText.Text("     G\nThat David played"),
            clipboardTextOf(text = "That David played", html = "<div>That <span data-chord=\"G\"></span>David played</div>"),
        )
    }

    @Test
    fun `a copy whose HTML draws no chords that way is pasted as its text`() {
        assertEquals(
            ClipboardText.Text("G\nThat David played"),
            clipboardTextOf(text = "G\nThat David played", html = "<pre>G\nThat David played</pre>"),
        )
    }
}
