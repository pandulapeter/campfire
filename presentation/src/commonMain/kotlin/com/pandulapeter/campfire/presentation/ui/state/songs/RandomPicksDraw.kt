/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource

/**
 * The seed the songs list's random picks are drawn with ([randomPicks]). A new one is drawn by [reshuffle], and by
 * [catchUp] once an hour has gone by since the last draw - which is only called as the songs screen is opened, so the
 * picks never change under somebody reading the list, and never on a song closed over it.
 *
 * The seed is this device's own and kept nowhere: two devices of one synced library pick differently, and a launch is a
 * new draw.
 */
internal class RandomPicksDraw(
    private val timeSource: TimeSource,
    private val random: Random,
) {
    private var drawnAt = timeSource.markNow()
    private val _seed = MutableStateFlow(random.nextLong())
    val seed = _seed.asStateFlow()

    /** For the songs screen being opened: the app coming back to the front on it, or another tab left for it. */
    fun catchUp() {
        if (drawnAt.elapsedNow() >= REDRAW_INTERVAL) reshuffle()
    }

    fun reshuffle() {
        drawnAt = timeSource.markNow()
        _seed.value = random.nextLong()
    }

    private companion object {
        val REDRAW_INTERVAL = 1.hours
    }
}
