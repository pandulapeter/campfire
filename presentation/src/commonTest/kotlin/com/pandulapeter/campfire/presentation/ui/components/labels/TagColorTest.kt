/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.labels

import com.pandulapeter.campfire.presentation.ui.components.isDarkerMoreLegible
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagColorTest {

    @Test
    fun `a tag is found under its color whatever its case and Unicode form`() {
        val tagColors = mapOf("café" to "teal")
        assertEquals(TagColor.TEAL, tagColorOf(tagColors, "Café"))
        assertEquals(TagColor.TEAL, tagColorOf(tagColors, "CAFE\u0301"))
    }

    @Test
    fun `a tag with no color, and one whose color this version does not know, have none`() {
        val tagColors = mapOf("folk" to "ultraviolet")
        assertNull(tagColorOf(tagColors, "folk"))
        assertNull(tagColorOf(tagColors, "rock"))
    }

    @Test
    fun `every color has an id of its own`() {
        assertEquals(TagColor.entries.size, TagColor.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `every color's check contrasts with it by at least three to one`() {
        TagColor.entries.flatMap { listOf(it.light, it.dark) }.forEach { color ->
            val check = if (isDarkerMoreLegible(color)) Color.Black else Color.White
            val (lighter, darker) = listOf(color.luminance(), check.luminance()).sortedDescending()
            assertTrue((lighter + 0.05f) / (darker + 0.05f) >= 3f, "The check on $color")
        }
    }
}
