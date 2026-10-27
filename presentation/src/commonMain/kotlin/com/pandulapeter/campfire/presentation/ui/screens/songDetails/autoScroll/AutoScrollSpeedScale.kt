/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll

import com.pandulapeter.campfire.data.model.preferences.SongLayout
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The speed at [fraction] of the way up the speed slider. The scale is logarithmic, so that half again and two thirds
 * are as far from the song's own pace either way, and its own pace sits in the middle of the slider.
 */
internal fun autoScrollSpeedAt(fraction: Float): Int {
    val range = SongLayout.AUTO_SCROLL_SPEED_RANGE
    val speed = range.first * (range.last.toDouble() / range.first).pow(fraction.coerceIn(0f, 1f).toDouble())
    // Snapped to the song's own pace near it, since a slider dragged back to the middle by hand lands a percent or two off.
    return if (abs(speed - SongLayout.DEFAULT_AUTO_SCROLL_SPEED) < OWN_PACE_SNAP) SongLayout.DEFAULT_AUTO_SCROLL_SPEED else speed.roundToInt().coerceIn(range)
}

/** Where [speed] sits on the speed slider, the inverse of [autoScrollSpeedAt]. */
internal fun autoScrollSpeedFraction(speed: Int): Float {
    val range = SongLayout.AUTO_SCROLL_SPEED_RANGE
    return (ln(speed.coerceIn(range).toDouble() / range.first) / ln(range.last.toDouble() / range.first)).toFloat()
}

private const val OWN_PACE_SNAP = 4
