/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.list

import kotlin.math.max
import kotlin.math.min

/**
 * How many pixels a dragged thumb that moved by [step] pixels' worth of the list scrolls it by, when the list moved by
 * that step alone would be estimated [error] pixels short of where the thumb says it should be (negative when past it).
 *
 * The estimate is worked out from the average height of the rows on screen, which changes whenever a row of another
 * height scrolls in, so the list cannot be placed where the estimate says on every frame: a thumb dragged down at an even
 * speed would send it back up whenever the average grew. The list moves by the thumb's own step instead, which is exact,
 * and only a share of the error is added to it, never more than the step itself in either direction: the list always
 * goes the way the thumb went, at between half and twice its speed, and it is where the thumb says by the time the
 * thumb has travelled a little further.
 */
internal fun fastScrollerStep(step: Float, error: Float): Float {
    val corrected = step + error * ERROR_SHARE_PER_FRAME
    return corrected.coerceIn(min(step / 2, step * 2), max(step / 2, step * 2))
}

private const val ERROR_SHARE_PER_FRAME = 0.1f
