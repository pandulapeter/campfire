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
import kotlin.time.Duration.Companion.minutes

/**
 * [SyncSimulation] over a fixed list of seeds, so that a failure names a seed that replays it step by step. Run with
 * many more seeds by hand when sync changes; the ones here keep the suite to a few seconds.
 *
 * The seeds run on one thread with no virtual delays, so the whole list is CPU time against `runTest`'s wall-clock
 * timeout; a few seconds on a developer machine is more than its default minute on a busy two-core CI runner.
 */
class SyncSimulationTest {

    @Test
    fun `random edits and runs on several devices never lose a saved version and settle on one library`() =
        runTest(timeout = 10.minutes) {
            (1..SEEDS).forEach { seed -> SyncSimulation(seed = seed, steps = STEPS).run(this) }
        }

    private companion object {
        const val SEEDS = 1000
        const val STEPS = 60
    }
}
