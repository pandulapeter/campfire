/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChangedPropertiesTest {

    private data class Details(val isTempoShown: Boolean, val tags: List<String>)

    private data class Settings(val fontScale: Float, val details: Details, val capos: Map<String, Int>)

    @Test
    fun `the properties that changed are named, nested values compared whole`() {
        val before = Settings(fontScale = 1f, details = Details(isTempoShown = false, tags = listOf("a", "b")), capos = mapOf("x.cho" to 1))
        val after = before.copy(details = before.details.copy(isTempoShown = true), capos = mapOf("x.cho" to 2))

        assertEquals(listOf("details", "capos"), changedPropertyNames(before.toString(), after.toString()))
    }

    @Test
    fun `nothing is named for equal values`() {
        val settings = Settings(fontScale = 1f, details = Details(isTempoShown = false, tags = emptyList()), capos = emptyMap())

        assertEquals(emptyList(), changedPropertyNames(settings.toString(), settings.copy().toString()))
    }

    @Test
    fun `a value that breaks the brackets says only that something changed`() {
        val before = Settings(fontScale = 1f, details = Details(isTempoShown = false, tags = listOf("(live")), capos = emptyMap())

        assertNull(changedPropertyNames(before.toString(), before.copy(fontScale = 2f).toString()))
    }
}
