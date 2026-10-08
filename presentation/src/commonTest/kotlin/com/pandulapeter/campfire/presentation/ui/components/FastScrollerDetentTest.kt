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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FastScrollerDetentTest {

    @Test
    fun `a new section is felt`() = assertTrue(isDetentReached(detent("A"), detent("B")))

    @Test
    fun `moving within a section is not felt`() = assertFalse(isDetentReached(detent("A"), detent("A")))

    @Test
    fun `rows with no label are not felt`() {
        assertFalse(isDetentReached(detent("A"), detent(null)))
        assertFalse(isDetentReached(detent(null), detent(null)))
    }

    @Test
    fun `the first label after unlabelled rows is felt`() = assertTrue(isDetentReached(detent(null), detent("A")))

    @Test
    fun `reaching either end is felt`() {
        assertTrue(isDetentReached(detent(null), detent(null, TrackEnd.TOP)))
        assertTrue(isDetentReached(detent("Z"), detent("Z", TrackEnd.BOTTOM)))
    }

    @Test
    fun `staying at or leaving an end is not felt`() {
        assertFalse(isDetentReached(detent("A", TrackEnd.TOP), detent("A", TrackEnd.TOP)))
        assertFalse(isDetentReached(detent("A", TrackEnd.TOP), detent("A")))
    }

    @Test
    fun `a section and an end reached together are one notch`() = assertTrue(isDetentReached(detent("Y"), detent("Z", TrackEnd.BOTTOM)))

    private fun detent(label: String?, end: TrackEnd? = null) = FastScrollerDetent(label = label, end = end)
}
