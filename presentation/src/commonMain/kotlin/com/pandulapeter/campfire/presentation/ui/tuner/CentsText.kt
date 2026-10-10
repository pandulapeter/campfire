/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import kotlin.math.roundToInt

/**
 * How far off a reading is, as the display writes it: a whole number of cents with its sign, sharp being positive, and
 * a plain `0` for a reading too close to call either way. The minus is the typographic one rather than a hyphen, so
 * that it is as wide as the plus and the number does not shift as the reading crosses the centre.
 */
internal fun signedCents(cents: Float): String {
    val rounded = cents.roundToInt()
    return when {
        rounded < 0 -> "$MINUS_SIGN${-rounded}"
        rounded > 0 -> "$PLUS_SIGN$rounded"
        else -> "0"
    }
}

private const val MINUS_SIGN = '−'
private const val PLUS_SIGN = '+'
