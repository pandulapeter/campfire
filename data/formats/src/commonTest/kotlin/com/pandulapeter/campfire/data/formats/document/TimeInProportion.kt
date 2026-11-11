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
import kotlin.time.TimeSource

/**
 * Compares the time [read] takes over an input of [quarterSize] with the time it takes over one four times as large,
 * rather than with a fixed bound, since a CI runner is several times slower than a laptop: work growing with the product
 * of two parts of a hostile document (a margin's lines and the page's, a run of marks and what follows each) outgrows the
 * reader's own work in proportion to the input at the sizes these tests pick.
 */
internal inline fun assertTakesTimeInProportion(quarterSize: Int, read: (size: Int) -> Unit) {
    read(quarterSize)
    val quarter = List(3) {
        val started = TimeSource.Monotonic.markNow()
        read(quarterSize)
        started.elapsedNow()
    }.min()
    val started = TimeSource.Monotonic.markNow()
    read(quarterSize * 4)
    val whole = started.elapsedNow()
    assertTrue(whole < quarter * 7, "$whole for the whole input, $quarter for a quarter of it")
}
