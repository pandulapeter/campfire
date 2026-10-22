/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.sync

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncProgressPulseTest {

    @Test
    fun `the pulse always stays clear of the fill and inside the window`() {
        for (step in 0..100) {
            val span = syncPulseSpan(width = 1000f, fillEnd = 300f, capRadius = 2f, phase = step / 100f) ?: continue
            assertTrue(span.start >= 306f, "starts at ${span.start}")
            assertTrue(span.end <= 998f, "ends at ${span.end}")
        }
    }

    @Test
    fun `the pulse travels away from the fill as its cycle goes on`() {
        val early = assertNotNull(syncPulseSpan(width = 1000f, fillEnd = 300f, capRadius = 2f, phase = 0.2f))
        val late = assertNotNull(syncPulseSpan(width = 1000f, fillEnd = 300f, capRadius = 2f, phase = 0.8f))
        assertTrue(late.start > early.start)
    }

    @Test
    fun `the pulse is out of sight at the ends of its cycle`() {
        assertNull(syncPulseSpan(width = 1000f, fillEnd = 300f, capRadius = 2f, phase = 0f))
        assertNull(syncPulseSpan(width = 1000f, fillEnd = 300f, capRadius = 2f, phase = 1f))
    }

    @Test
    fun `a full bar leaves no room for the pulse`() {
        for (step in 0..10) {
            assertNull(syncPulseSpan(width = 1000f, fillEnd = 1000f, capRadius = 2f, phase = step / 10f))
        }
    }
}
