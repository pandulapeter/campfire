/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar

import kotlin.test.Test
import kotlin.test.assertEquals

class ImageLinesTest {

    private fun inserted(text: String, offset: Int, vararg names: String): Pair<String, Int> {
        val insertion = imageLinesInsertion(text, offset, names.toList())
        return text.substring(0, insertion.offset) + insertion.text + text.substring(insertion.offset) to insertion.caret
    }

    @Test
    fun `a picture at the start of a line goes above it - the caret after its directive`() {
        assertEquals("[G]One\n{image: src=a.png}\n[C]Two" to 25, inserted("[G]One\n[C]Two", 7, "a.png"))
    }

    @Test
    fun `a picture inside a line goes below it never splitting it`() {
        assertEquals("[G]One\n{image: src=a.png}\n[C]Two" to 25, inserted("[G]One\n[C]Two", 3, "a.png"))
        assertEquals("[G]One\n{image: src=a.png}" to 25, inserted("[G]One", 6, "a.png"))
    }

    @Test
    fun `a picture into an empty text or after its last line break is its last line`() {
        assertEquals("{image: src=a.png}" to 18, inserted("", 0, "a.png"))
        assertEquals("[G]One\n{image: src=a.png}" to 25, inserted("[G]One\n", 7, "a.png"))
    }

    @Test
    fun `several pictures are a line each - in order`() {
        assertEquals("{image: src=a.png}\n{image: src=b.jpg}\nx" to 37, inserted("x", 0, "a.png", "b.jpg"))
    }

    @Test
    fun `a name that needs quoting is quoted`() {
        assertEquals("{image: src=\"my photo.jpg\"}", inserted("", 0, "my photo.jpg").first)
    }
}
