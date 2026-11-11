/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * [SyncSimulation] over a fixed list of seeds, so that a failure names a seed that replays it step by step. Run with
 * many more seeds by hand when sync changes; the ones here keep the suite to a few seconds.
 */
class SyncSimulationTest {

    @Test
    fun `random edits and runs on several devices never lose a saved version and settle on one library`() = runTest {
        (1..SEEDS).forEach { seed -> SyncSimulation(seed = seed, steps = STEPS).run(this) }
    }

    private companion object {
        const val SEEDS = 1000
        const val STEPS = 60
    }
}
