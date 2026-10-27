/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

/**
 * Shrinks text that does not fit from [maxFontSize] down to [minFontSize] at most, in [stepSize] steps, before the
 * text's overflow (an ellipsis) cuts it. Compose's own [TextAutoSize.StepBased] judges an ellipsized line by
 * `isLineEllipsized`, which Skiko (desktop, iOS, the web) always answers false, so there it never shrinks anything;
 * a line past the text's maximum is what every platform reports.
 */
internal data class ShrinkToFit(
    private val minFontSize: TextUnit,
    private val maxFontSize: TextUnit,
    private val stepSize: TextUnit = 0.25.sp,
) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit =
        largestFittingSize(minFontSize.toPx(), maxFontSize.toPx(), stepSize.toPx()) { size ->
            !performLayout(constraints, text, size.toSp()).isCut
        }.toSp()
}

/** Whether a layout leaves text out: lines past its maximum, or a line wider or taller than its room. */
private val TextLayoutResult.isCut get() = multiParagraph.didExceedMaxLines || didOverflowWidth || didOverflowHeight

/**
 * The largest of [smallest], [smallest] + [step], … up to [largest] (itself a candidate) that [fits], or [smallest]
 * where none does. A binary search over the steps, since each candidate is a text layout; it takes the sizes that fit
 * to be the ones below some size, as a text's do.
 */
internal fun largestFittingSize(smallest: Float, largest: Float, step: Float, fits: (Float) -> Boolean): Float {
    if (largest <= smallest || step <= 0f) return smallest
    val count = ceil((largest - smallest) / step - STEP_TOLERANCE).toInt()
    val size = { index: Int -> minOf(smallest + index * step, largest) }
    var low = 0
    var high = count
    while (low < high) {
        val middle = (low + high + 1) / 2
        if (fits(size(middle))) low = middle else high = middle - 1
    }
    return size(low)
}

/** Keeps a range that is a whole number of steps, give or take a float's rounding, from counting one step too many. */
private const val STEP_TOLERANCE = 1e-4f
