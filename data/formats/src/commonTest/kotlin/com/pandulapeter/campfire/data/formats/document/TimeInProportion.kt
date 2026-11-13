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
 * Compares the time [read] takes over the input [inputOf] builds of [partSize] with the time it takes over one eight
 * times as large, rather than with a fixed bound, since a CI runner is several times slower than a laptop: work growing
 * with the product of two parts of a hostile document (a margin's lines and the page's, a run of marks and what follows
 * each) outgrows the reader's own work in proportion to the input at the sizes these tests pick. Both inputs are built
 * before any run is timed, since building a million spans is often slower than reading them and the collections its
 * garbage brings land on whichever run follows. Both sizes are then timed five times, alternately, and their fastest
 * runs compared: a shared runner's other test workers and a collection pause land on single runs, often ten times slower
 * than the rest. Even the fastest runs of a linear reader there take up to three times its eightfold growth, so the
 * bound sits far above it, while a step gone quadratic slows every run sixty-four times.
 */
internal inline fun <T> assertTakesTimeInProportion(partSize: Int, inputOf: (size: Int) -> T, read: (input: T) -> Unit) {
    val partInput = inputOf(partSize)
    val wholeInput = inputOf(partSize * 8)
    read(partInput)
    val parts = mutableListOf<Duration>()
    val wholes = mutableListOf<Duration>()
    repeat(5) {
        parts += TimeSource.Monotonic.measureTime { read(partInput) }
        wholes += TimeSource.Monotonic.measureTime { read(wholeInput) }
    }
    val part = parts.min()
    val whole = wholes.min()
    assertTrue(whole < part * 24, "$whole for the whole input, $part for an eighth of it")
}
