/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

import kotlin.time.Duration

/**
 * How long a setlist takes to play, from the durations of its songs. [isMinimum] where some of them declared none that
 * could be read, or are missing from the library, since the total then leaves them out rather than guessing at them.
 */
internal data class SetlistTotalDuration(val total: Duration, val isMinimum: Boolean)

/** The total of [durations], one per entry of the setlist, or null where not one of them is known. */
internal fun setlistTotalDuration(durations: List<Duration?>): SetlistTotalDuration? {
    val known = durations.filterNotNull()
    if (known.isEmpty()) return null
    return SetlistTotalDuration(total = known.fold(Duration.ZERO, Duration::plus), isMinimum = known.size < durations.size)
}

internal val SetlistWithSongs.totalDuration
    get() = setlistTotalDuration(entries.map { (it as? SetlistWithSongs.Entry.Present)?.song?.duration })
