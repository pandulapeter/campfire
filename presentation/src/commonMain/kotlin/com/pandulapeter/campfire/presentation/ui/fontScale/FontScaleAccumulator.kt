/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.fontScale

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlin.math.abs

/**
 * Adds up a stream of small changes to the font scale that has no start or end of its own - a scroll wheel, or the
 * steps a touchpad pinch arrives in. The view model keeps the scale in whole percent, and a touchpad reports a slow
 * pinch in steps smaller than that, so each of them applied to the rounded value would be rounded away again and the
 * text would never move. The unrounded value the changes have arrived at is carried from one to the next instead, for
 * as long as the rounded one is still what it rounds to; once something else has moved the scale - a step of the
 * stepper, a shortcut, a touch pinch - the next change starts from that.
 *
 * The value is kept within the bounds the view model clamps to, so that a gesture that went past one of them turns
 * back at once rather than after undoing everything it did beyond it.
 */
internal class FontScaleAccumulator {

    private var unrounded: Float? = null

    /** Applies [change] to the scale the changes so far have arrived at, or to [current] if that is not it any more. */
    fun next(current: Float, change: (Float) -> Float): Float {
        val base = unrounded?.takeIf { abs(it - current) <= ROUNDING_TOLERANCE } ?: current
        return change(base).coerceIn(UserPreferences.MIN_FONT_SCALE, UserPreferences.MAX_FONT_SCALE).also { unrounded = it }
    }
}

private const val ROUNDING_TOLERANCE = 0.0051f // Half a percent, which is as far as rounding moves it, plus float slack.
