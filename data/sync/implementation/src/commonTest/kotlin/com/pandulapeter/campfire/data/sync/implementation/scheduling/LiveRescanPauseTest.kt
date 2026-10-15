/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.scheduling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** How long the library counts wait between two live rescans of a run, which is what keeps a large library's run linear. */
class LiveRescanPauseTest {

    @Test
    fun `a quick rescan is repeated after the ordinary interval`() {
        assertEquals(LIVE_RESCAN_INTERVAL, liveRescanPauseAfter(40.milliseconds))
    }

    @Test
    fun `a slow rescan waits several times what it cost, in proportion to it`() {
        val pause = liveRescanPauseAfter(3.seconds)

        assertTrue(pause >= 3.seconds * 3)
        assertEquals(pause * 2, liveRescanPauseAfter(6.seconds))
    }
}
