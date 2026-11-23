/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.tabChrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CounterpartTest {

    private class Copy(val isEntering: Boolean)

    private fun Copy.counterpartIn(parts: List<Copy>) = counterpartAmong(part = this, parts = parts, isEntering = { it.isEntering })

    @Test
    fun `the copies of a two tab swap match each other`() {
        val leaving = Copy(isEntering = false)
        val entering = Copy(isEntering = true)
        val parts = listOf(leaving, entering)
        assertEquals(entering, leaving.counterpartIn(parts))
        assertEquals(leaving, entering.counterpartIn(parts))
    }

    @Test
    fun `a copy alone in a swap matches nothing`() {
        val leaving = Copy(isEntering = false)
        assertNull(leaving.counterpartIn(listOf(leaving)))
    }

    @Test
    fun `two copies being left never match each other`() {
        val first = Copy(isEntering = false)
        val second = Copy(isEntering = false)
        assertNull(first.counterpartIn(listOf(first, second)))
        assertNull(second.counterpartIn(listOf(first, second)))
    }

    @Test
    fun `a third tab opened during a swap pairs with one copy being left and leaves the other alone`() {
        val first = Copy(isEntering = false)
        val second = Copy(isEntering = false)
        val entering = Copy(isEntering = true)
        val parts = listOf(first, second, entering)
        assertEquals(first, entering.counterpartIn(parts))
        assertEquals(entering, first.counterpartIn(parts))
        assertNull(second.counterpartIn(parts))
    }
}
