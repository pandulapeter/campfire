/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class TagsTest {

    @Test
    fun `decomposed and composed spellings are one tag`() {
        assertEquals(listOf("café"), normalizedTags(listOf("café", "café")))
    }

    @Test
    fun `spellings that differ by case keep the first`() {
        assertEquals(listOf("Rock"), normalizedTags(listOf("Rock", "rock", "ROCK")))
    }

    @Test
    fun `the order is kept`() {
        assertEquals(listOf("Demo", "Folk", "Rock"), normalizedTags(listOf("Demo", "Folk", "demo", "Rock")))
    }
}
