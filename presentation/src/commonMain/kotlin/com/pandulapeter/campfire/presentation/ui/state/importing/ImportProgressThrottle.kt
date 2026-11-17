/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.importing.ImportProgress
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Hands one preparation's progress on to [deliver] at most once per [INTERVAL] while it counts bytes: a download, a copy
 * into the staging folder, or an unpack reports every chunk it moves (64 KiB each), many hundreds a second, and every one
 * of them would recompose the import screen and wake whatever watches the import's work. Everything else passes at once:
 * a new phase or file, progress counted in files, and the last bytes of a known total. A value held back is kept and
 * replaced by the next, and [flush] hands it on where the phase ends without one, so what is shown is never older than
 * what was last offered by more than the interval, and the final value always arrives. No timer delivers it: a hidden
 * browser tab runs timers once a minute, and nothing of the import may wait on one.
 *
 * Offered to from one coroutine at a time, as a preparation reports its progress.
 */
internal class ImportProgressThrottle(
    private val timeSource: TimeSource,
    private val deliver: (ImportProgress) -> Unit,
) {
    private var delivered: ImportProgress? = null
    private var deliveredAt: TimeMark? = null
    private var pending: ImportProgress? = null

    fun offer(progress: ImportProgress) {
        val last = delivered
        val bytes = progress.completedBytes
        val isDue = last == null || bytes == null || progress.phase != last.phase || progress.fileName != last.fileName ||
            progress.totalBytes?.let { bytes >= it } == true || deliveredAt?.let { it.elapsedNow() >= INTERVAL } != false
        if (isDue) send(progress) else pending = progress
    }

    /** Hands on the value held back, if any: what a phase that ended without its total reached last. */
    fun flush() {
        pending?.let(::send)
    }

    private fun send(progress: ImportProgress) {
        pending = null
        delivered = progress
        deliveredAt = timeSource.markNow()
        deliver(progress)
    }

    companion object {
        /** A few updates a second, which a progress bar moving smoothly needs and a screen redrawing can afford. */
        val INTERVAL = 250.milliseconds
    }
}
