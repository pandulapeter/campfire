/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.document

import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.measureTime

/**
 * Compares the time [read] takes over the input [inputOf] builds of [wholeSize] with the time it takes over one a
 * sixty-fourth as large, rather than with a fixed bound, since a CI runner is several times slower than a laptop: work
 * growing with the product of two parts of a hostile document (a margin's lines and the page's, a run of marks and what
 * follows each) outgrows the reader's own work in proportion to the input at the sizes these tests pick. Both inputs are
 * built before any run is timed, since building a million spans is often slower than reading them and the collections
 * its garbage brings land on whichever run follows. Both sizes are then timed five times, alternately, and their fastest
 * runs compared: a shared runner's other test workers and a collection pause land on single runs, often ten times slower
 * than the rest. Even the fastest run of a linear reader over the whole there takes several times its share, the small
 * input living in the caches and the whole in memory, so the sizes lie far apart: a linear reader grows sixty-four
 * times, a step gone quadratic four thousand, and the bound sits between them, eight times from either.
 */
internal inline fun <T> assertTakesTimeInProportion(wholeSize: Int, inputOf: (size: Int) -> T, read: (input: T) -> Unit) {
    val partInput = inputOf(wholeSize / 64)
    val wholeInput = inputOf(wholeSize)
    read(partInput)
    val parts = mutableListOf<Duration>()
    val wholes = mutableListOf<Duration>()
    repeat(5) {
        parts += TimeSource.Monotonic.measureTime { read(partInput) }
        wholes += TimeSource.Monotonic.measureTime { read(wholeInput) }
    }
    val part = parts.min()
    val whole = wholes.min()
    assertTrue(whole < part * 512, "$whole for the whole input, $part for a sixty-fourth of it")
}
