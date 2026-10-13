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

import kotlin.math.abs

/**
 * How far the item at [index] of a pager's indicator is selected at the pager's [position] (its page plus its offset):
 * fully on its own page, not at all a page or more away, and in between while the pages are being turned, so that an
 * indicator follows a swipe frame by frame rather than jumping once the pages pass the halfway point.
 */
internal fun pagerSelection(position: Float, index: Int) = (1f - abs(position - index)).coerceIn(0f, 1f)

/** Where an indicator starts and how wide it is, in pixels. */
internal data class IndicatorSpan(
    val start: Float,
    val width: Float,
)

/**
 * The indicator under a row of tabs at the pager's [position]: over the tab of the page shown, or between the [spans] of
 * the two tabs a turn is between, as far along as the turn, null for a row with no tabs.
 */
internal fun pagerIndicatorSpan(position: Float, spans: List<IndicatorSpan>): IndicatorSpan? {
    if (spans.isEmpty()) return null
    val clamped = position.coerceIn(0f, spans.lastIndex.toFloat())
    val index = clamped.toInt()
    val fraction = clamped - index
    val from = spans[index]
    val to = spans.getOrElse(index + 1) { from }
    return IndicatorSpan(
        start = from.start + (to.start - from.start) * fraction,
        width = from.width + (to.width - from.width) * fraction,
    )
}
