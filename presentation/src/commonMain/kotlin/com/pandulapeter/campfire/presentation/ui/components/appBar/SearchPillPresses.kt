/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.appBar

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import com.pandulapeter.campfire.presentation.ui.components.menu.ACTION_BUTTON_CONTAINER_SIZE
import kotlin.math.roundToInt

/**
 * The ripples of the buttons on the [SearchPill], each filling the button's share of the pill (see [searchPillSegment])
 * where Material would have drawn a circle around its icon. Laid over the whole bar, between the pill and the buttons.
 *
 * @param closedBounds Where the pill stands while the search is closed.
 * @param openProgress How far the search is open, which takes the pill away from under the buttons.
 */
@Composable
internal fun SearchPillPresses(
    modifier: Modifier = Modifier,
    pill: SearchPill,
    closedBounds: () -> Rect,
    openProgress: () -> Float,
) = Box(
    modifier = modifier,
) {
    val containerRadius = with(LocalDensity.current) { ACTION_BUTTON_CONTAINER_SIZE.toPx() } / 2
    val segmentOf = { button: SearchPillButton ->
        val placed = pill.buttons.filterNot { it.bounds.isEmpty }.sortedBy { it.bounds.center.x }
        val index = placed.indexOf(button)
        val pillBounds = closedBounds()
        if (index < 0 || pillBounds.isEmpty) {
            null
        } else {
            searchPillSegment(
                closedPill = pillBounds,
                centers = placed.map { it.bounds.center },
                index = index,
                containerRadius = containerRadius,
                openProgress = openProgress(),
                dividers = listOfNotNull(pill.divider),
            )
        }
    }
    pill.buttons.forEach { button ->
        key(button) {
            SearchPillPress(
                button = button,
                containerRadius = containerRadius,
                segment = { segmentOf(button) },
            )
        }
    }
}

/**
 * One button's ripple, laid out over its share of the pill and clipped to it. Read while laying out and drawing, so that
 * the pill and the buttons moving recompose nothing.
 */
@Composable
private fun SearchPillPress(
    button: SearchPillButton,
    containerRadius: Float,
    segment: () -> SearchPillSegment?,
) {
    val interactions = remember(button) { MutableInteractionSource() }
    LaunchedEffect(button) {
        // A press is measured from the button's own container, which the ripple is no longer drawn in, so it is moved
        // to where it lands in the segment, and its release and cancellation follow the press they end.
        val presses = mutableMapOf<PressInteraction.Press, PressInteraction.Press>()
        button.interactions.interactions.collect { interaction ->
            val forwarded = when (interaction) {
                is PressInteraction.Press -> {
                    val container = button.bounds.center - Offset(containerRadius, containerRadius)
                    val origin = segment()?.bounds?.topLeft ?: container
                    PressInteraction.Press(interaction.pressPosition + container - origin).also { presses[interaction] = it }
                }
                is PressInteraction.Release -> presses.remove(interaction.press)?.let { PressInteraction.Release(it) }
                is PressInteraction.Cancel -> presses.remove(interaction.press)?.let { PressInteraction.Cancel(it) }
                else -> interaction
            }
            forwarded?.let { interactions.emit(it) }
        }
    }
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val bounds = segment()?.bounds ?: Rect.Zero
                val placeable = measurable.measure(
                    Constraints.fixed(
                        width = bounds.width.roundToInt().coerceAtLeast(0),
                        height = bounds.height.roundToInt().coerceAtLeast(0),
                    ),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(x = bounds.left.roundToInt(), y = bounds.top.roundToInt())
                }
            }
            .graphicsLayer {
                val current = segment()
                shape = AbsoluteRoundedCornerShape(
                    topLeft = CornerSize(current?.leftRadius ?: 0f),
                    topRight = CornerSize(current?.rightRadius ?: 0f),
                    bottomRight = CornerSize(current?.rightRadius ?: 0f),
                    bottomLeft = CornerSize(current?.leftRadius ?: 0f),
                )
                clip = true
            }
            .indication(
                interactionSource = interactions,
                indication = ripple(color = MaterialTheme.colorScheme.onSurfaceVariant),
            ),
    )
}
