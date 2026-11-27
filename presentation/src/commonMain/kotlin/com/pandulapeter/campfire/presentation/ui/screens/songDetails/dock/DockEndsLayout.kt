/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * A row of what stands at its ends ([start], [end]) and what is between them ([middle], as wide as they leave), each
 * centred in its height: the dock's rows, and the setlist's pager bar holding the dock's controls. [end] is measured
 * first and [start] with what it leaves but [minMiddleRoom] (see [dockScrollingRowStartMaxWidth]), so that a one-line
 * instrument card with long names ("12-string guitar", "Full step down") is cut short rather than taking the whole row
 * from what it is there beside. As large as its constraints allow, which are bounded.
 */
@Composable
internal fun DockEndsLayout(
    modifier: Modifier = Modifier,
    minMiddleRoom: Dp,
    start: @Composable () -> Unit,
    middle: @Composable () -> Unit,
    end: @Composable () -> Unit,
) = Layout(modifier = modifier, contents = listOf(start, middle, end)) { (startMeasurables, middleMeasurables, endMeasurables), constraints ->
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val endPlaceables = endMeasurables.map { it.measure(loose) }
    val endWidth = endPlaceables.sumOf { it.width }
    val startMaxWidth = dockScrollingRowStartMaxWidth(constraints.maxWidth, endWidth, minMiddleRoom.roundToPx())
    val startPlaceables = startMeasurables.map { it.measure(loose.copy(maxWidth = startMaxWidth)) }
    val startWidth = startPlaceables.sumOf { it.width }
    val middleWidth = (constraints.maxWidth - startWidth - endWidth).coerceAtLeast(0)
    val middlePlaceables = middleMeasurables.map { it.measure(Constraints(minWidth = middleWidth, maxWidth = middleWidth, maxHeight = constraints.maxHeight)) }
    layout(constraints.maxWidth, constraints.maxHeight) {
        val center = { placeableHeight: Int -> (constraints.maxHeight - placeableHeight) / 2 }
        startPlaceables.forEach { it.placeRelative(0, center(it.height)) }
        middlePlaceables.forEach { it.placeRelative(startWidth, center(it.height)) }
        endPlaceables.forEach { it.placeRelative(constraints.maxWidth - endWidth, center(it.height)) }
    }
}

/**
 * What stands at an end of a [DockEndsLayout], fading and growing in from that end ([expandFrom]) and back into it, so
 * that a row gaining or losing it gives or takes the room gradually.
 */
@Composable
internal fun DockRowEnd(isShown: Boolean, expandFrom: Alignment.Horizontal, content: @Composable () -> Unit) = AnimatedVisibility(
    visible = isShown,
    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec(), expandFrom = expandFrom),
    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec(), shrinkTowards = expandFrom),
) {
    content()
}
