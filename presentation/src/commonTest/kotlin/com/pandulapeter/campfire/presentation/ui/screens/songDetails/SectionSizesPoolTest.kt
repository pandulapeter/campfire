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
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SectionSizesPoolTest {

    @Test
    fun equalSectionsKeepTheirSizes() {
        val pool = SectionSizesPool<String>()
        val before = pool.sizesFor(listOf("verse 1", "chorus", "verse 2"))
        before[1].heightsByWidth[400] = 120
        // A section inserted above the others, and the last one edited.
        val after = pool.sizesFor(listOf("intro", "verse 1", "chorus", "verse 2, edited"))
        assertSame(before[0], after[1])
        assertSame(before[1], after[2])
        assertTrue(after[2].heightsByWidth[400] == 120)
        assertNotSame(before[2], after[3])
    }

    @Test
    fun newSectionsStartWithNothingMeasured() {
        val pool = SectionSizesPool<String>()
        pool.sizesFor(listOf("verse")).single().heightsByWidth[400] = 80
        val after = pool.sizesFor(listOf("bridge")).single()
        assertTrue(after.heightsByWidth.isEmpty())
        assertTrue(after.minWidth < 0)
    }

    @Test
    fun duplicatesAreHandedOutInOrder() {
        val pool = SectionSizesPool<String>()
        val before = pool.sizesFor(listOf("chorus", "verse", "chorus"))
        assertNotSame(before[0], before[2])
        val after = pool.sizesFor(listOf("verse", "chorus", "chorus", "chorus"))
        assertSame(before[1], after[0])
        assertSame(before[0], after[1])
        assertSame(before[2], after[2])
        assertTrue(after[3] !== before[0] && after[3] !== before[2])
    }

    @Test
    fun sizesLeftOutOfOneListAreNotReturnedToALaterOne() {
        val pool = SectionSizesPool<String>()
        val first = pool.sizesFor(listOf("verse")).single()
        pool.sizesFor(listOf("chorus"))
        assertNotSame(first, pool.sizesFor(listOf("verse")).single())
    }
}
