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
import kotlin.test.assertEquals

class FastScrollerDetentTest {

    @Test
    fun `a new section is a notch`() = assertNotch(FastScrollerNotch.NOTCH, detent(0, "A"), detent(1, "B"))

    @Test
    fun `a new row within a section is a tick`() = assertNotch(FastScrollerNotch.TICK, detent(0, "A"), detent(1, "A"))

    @Test
    fun `staying on the same row is not felt`() = assertNotch(FastScrollerNotch.NONE, detent(3, "A"), detent(3, "A"))

    @Test
    fun `rows with no label are ticks`() {
        assertNotch(FastScrollerNotch.TICK, detent(0, "A"), detent(1, null))
        assertNotch(FastScrollerNotch.TICK, detent(1, null), detent(2, null))
    }

    @Test
    fun `the first label after unlabelled rows is a notch`() = assertNotch(FastScrollerNotch.NOTCH, detent(0, null), detent(1, "A"))

    @Test
    fun `reaching either end is a notch`() {
        assertNotch(FastScrollerNotch.NOTCH, detent(0, null), detent(0, null, TrackEnd.TOP))
        assertNotch(FastScrollerNotch.NOTCH, detent(9, "Z"), detent(9, "Z", TrackEnd.BOTTOM))
    }

    @Test
    fun `staying at or leaving an end is not felt`() {
        assertNotch(FastScrollerNotch.NONE, detent(0, "A", TrackEnd.TOP), detent(0, "A", TrackEnd.TOP))
        assertNotch(FastScrollerNotch.NONE, detent(0, "A", TrackEnd.TOP), detent(0, "A"))
    }

    @Test
    fun `a section and an end reached together are one notch`() =
        assertNotch(FastScrollerNotch.NOTCH, detent(8, "Y"), detent(9, "Z", TrackEnd.BOTTOM))

    private fun assertNotch(expected: FastScrollerNotch, previous: FastScrollerDetent, current: FastScrollerDetent) =
        assertEquals(expected, detentNotch(previous, current))

    private fun detent(item: Int, label: String?, end: TrackEnd? = null) = FastScrollerDetent(item = item, label = label, end = end)
}
