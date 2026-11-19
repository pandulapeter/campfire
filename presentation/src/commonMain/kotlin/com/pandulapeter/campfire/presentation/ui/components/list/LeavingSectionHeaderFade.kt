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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import com.pandulapeter.campfire.presentation.ui.components.appBar.LIST_APP_BAR_HEIGHT

/**
 * Fades the copy of a [pushed] header out as the next one pushes it up: it travels on with its row past the top of the
 * list, under the status bar, and is gone by the time its name reaches the window's top
 * ([leavingSectionHeaderOpacity]), so that it never stands over the clock. The list itself stays below the status bar
 * (its cards are faded out under the header's row anyway); only the header leaving it uses that room. Its place in the
 * window is written as it is placed and read as it is drawn, so that a push recomposes nothing.
 */
@Composable
internal fun Modifier.fadingUpUnderStatusBar(pushed: State<PushedSectionHeader?>): Modifier {
    val rowTop = remember { mutableFloatStateOf(0f) }
    return this
        .onPlaced { rowTop.floatValue = it.positionInWindow().y }
        .graphicsLayer {
            val header = pushed.value
            alpha = if (header == null) 0f else leavingSectionHeaderOpacity(
                rowTop = rowTop.floatValue,
                pushedDistance = header.pushedDistance.toFloat(),
                height = header.height.toFloat(),
                nameCenter = LIST_APP_BAR_HEIGHT.toPx() / 2,
            )
        }
}

/**
 * How much is left of a leaving header whose row is at [rowTop] in the window, [pushedDistance] above its pinned place:
 * all of it there, fading as it rises, none once its name, [nameCenter] below the row's pinned top (the bar's center
 * line), reaches the window's top - or once the row has been pushed its whole [height], where the list stops reporting
 * it, whichever comes first, so that a deep status bar does not see it vanish halfway through.
 */
internal fun leavingSectionHeaderOpacity(rowTop: Float, pushedDistance: Float, height: Float, nameCenter: Float): Float {
    val travel = minOf(rowTop + pushedDistance + nameCenter, height)
    return if (travel <= 0f) 0f else (1f - pushedDistance / travel).coerceIn(0f, 1f)
}
