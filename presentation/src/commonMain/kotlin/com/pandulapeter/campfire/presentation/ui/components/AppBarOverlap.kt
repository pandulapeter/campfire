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

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import kotlin.math.roundToInt

/**
 * What of a list the app bar's buttons stand over while the bar is not filled in: how far in from the list's end edge
 * they reach, which a pinned [SectionHeader] keeps its text and its action clear of, and how far down from its top,
 * which the [FastScroller] starts below so that its thumb is never under a button that would take the press.
 *
 * @param reach How far the buttons reach in from the end edge of the list, whether or not the list is under them.
 * @param coverage How much of the row at the top of the list is still under the buttons: all of it while the bar is
 *   not filled in, none of it once the list has moved down out of its way. The header narrows by it the way it does
 *   by its own pinned fraction, since the list moving down under a pinned header and a header scrolling up into the
 *   pinned place are the same movement relative to the buttons.
 */
internal data class AppBarOverlap(
    val reach: Dp,
    val coverage: Float,
    val height: Dp,
) {

    companion object {

        /**
         * @param reach How far the buttons of the closed bar reach in from the end edge of the list.
         * @param appBarReveal How far the bar is filled in, which moves the list out from under it by as much.
         */
        fun of(reach: Dp, appBarReveal: Float): AppBarOverlap {
            val uncovered = 1f - appBarReveal.coerceIn(0f, 1f)
            return AppBarOverlap(reach = reach, coverage = uncovered, height = LIST_APP_BAR_HEIGHT * uncovered)
        }
    }
}

/**
 * Lays a list out under the part of the app bar [appBarReveal] says is filled in, reading it while the list is laid
 * out so that the bar filling in moves the list without recomposing it.
 */
internal fun Modifier.underAppBar(appBarReveal: () -> Float) = layout { measurable, constraints ->
    val top = (LIST_APP_BAR_HEIGHT.toPx() * appBarReveal().coerceIn(0f, 1f)).roundToInt()
    val placeable = measurable.measure(constraints.offset(vertical = -top))
    layout(placeable.width, placeable.height + top) { placeable.placeRelative(x = 0, y = top) }
}

/** Lays a list's overlay out below the part of the app bar the list is still under, read while laying out. */
internal fun Modifier.belowAppBarOverlap(appBarOverlap: () -> AppBarOverlap) = layout { measurable, constraints ->
    val top = appBarOverlap().height.roundToPx()
    val placeable = measurable.measure(constraints.offset(vertical = -top))
    layout(placeable.width, placeable.height + top) { placeable.placeRelative(x = 0, y = top) }
}

/**
 * The height of a list screen's app bar, which is also the height of the list's section headers, since the one pinned
 * at the top stands in the bar's place with its text level with the bar's buttons. A little lower than a `TopAppBar`:
 * a header is a row of the list as well, repeated all the way down it, and the 48dp buttons still fit with room to
 * spare.
 */
internal val LIST_APP_BAR_HEIGHT = 56.dp
