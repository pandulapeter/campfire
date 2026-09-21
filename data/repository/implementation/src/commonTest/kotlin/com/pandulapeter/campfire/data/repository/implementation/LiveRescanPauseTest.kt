/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** How long the library counts wait between two live rescans of a run, which is what keeps a large library's run linear. */
class LiveRescanPauseTest {

    @Test
    fun `a quick rescan is repeated after the ordinary interval`() {
        assertEquals(1.seconds, liveRescanPauseAfter(40.milliseconds))
    }

    @Test
    fun `a slow rescan is not repeated until five times what it cost has passed`() {
        assertEquals(15.seconds, liveRescanPauseAfter(3.seconds))
    }
}
