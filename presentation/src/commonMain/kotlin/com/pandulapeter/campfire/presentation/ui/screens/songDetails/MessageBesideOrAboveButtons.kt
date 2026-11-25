/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.presentation.ui.components.animatePlacement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * The message, and the buttons beside it at the end, centred on it, where the width left to the message is at least
 * [MIN_MESSAGE_WIDTH], else under it at the end. The buttons travel between the two places as the window changes width,
 * rather than jumping. Decided by width alone, not by the message's intrinsic width: the review's choices under it are
 * drawn by layouts that cannot be asked for one.
 */
@Composable
internal fun MessageBesideOrAboveButtons(
    modifier: Modifier = Modifier,
    message: @Composable () -> Unit,
    buttons: @Composable () -> Unit,
) {
    val placement = Modifier.animatePlacement(MaterialTheme.motionScheme.defaultSpatialSpec())
    Layout(
        modifier = modifier,
        contents = listOf(message, { Box(placement) { buttons() } }),
    ) { (messageMeasurables, buttonMeasurables), constraints ->
        val gap = MESSAGE_GAP.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val buttonPlaceable = buttonMeasurables.single().measure(loose)
        val hasButtons = buttonPlaceable.width > 0
        val besideWidth = constraints.maxWidth - buttonPlaceable.width - gap
        val isBeside = !hasButtons || besideWidth >= MIN_MESSAGE_WIDTH.roundToPx()
        val messagePlaceable = messageMeasurables.single().measure(loose.copy(maxWidth = if (hasButtons && isBeside) besideWidth else constraints.maxWidth))
        val width = constraints.maxWidth
        val height = when {
            isBeside -> max(messagePlaceable.height, buttonPlaceable.height)
            else -> messagePlaceable.height + gap + buttonPlaceable.height
        }
        layout(width, max(height, constraints.minHeight)) {
            if (isBeside) {
                messagePlaceable.placeRelative(0, (height - messagePlaceable.height) / 2)
                buttonPlaceable.placeRelative(width - buttonPlaceable.width, (height - buttonPlaceable.height) / 2)
            } else {
                messagePlaceable.placeRelative(0, 0)
                buttonPlaceable.placeRelative(width - buttonPlaceable.width, messagePlaceable.height + gap)
            }
        }
    }
}

private val MESSAGE_GAP = 12.dp

/**
 * The narrowest the message may be beside the buttons: on a 360 dp phone the lone Import still fits beside it, the
 * title taking two lines, where Import as a copy and Replace together send the buttons under it.
 */
private val MIN_MESSAGE_WIDTH = 140.dp
