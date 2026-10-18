/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class PaddingValuesSidesTest {

    private val source = PaddingValues(start = 1.dp, top = 2.dp, end = 3.dp, bottom = 4.dp)
    private var deferredEnd = 10.dp
    private var deferredBottom = 20.dp
    private val readEnd = { deferredEnd }
    private val readBottom = { deferredBottom }

    private fun padding() = source.only(start = true, end = true, bottom = true, extraBottom = 8.dp, deferredExtraEnd = readEnd, deferredExtraBottom = readBottom)

    @Test
    fun `two paddings with the same readers are equal whatever they read`() {
        val first = padding()
        deferredEnd = 24.dp
        deferredBottom = 88.dp
        assertEquals(first, padding())
        assertEquals(first.hashCode(), padding().hashCode())
    }

    @Test
    fun `the deferred extras are what the readers return when the padding is used`() {
        val padding = padding()
        deferredEnd = 24.dp
        deferredBottom = 88.dp
        LayoutDirection.entries.forEach { layoutDirection ->
            assertEquals(3.dp + 24.dp, padding.calculateEndPadding(layoutDirection))
            assertEquals(1.dp, padding.calculateStartPadding(layoutDirection))
        }
        assertEquals(4.dp + 8.dp + 88.dp, padding.calculateBottomPadding())
    }
}
