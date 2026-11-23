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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.toSize
import com.pandulapeter.campfire.presentation.ui.components.menu.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.NEW_BUTTON_Z_INDEX
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.SharedTabChrome
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.sharedTabChrome

/**
 * The list screens' closed search pill ([SearchableTopAppBar]) on its own, floating over a page that has no bar: the same
 * tonal color, height, and shape, the same [PillIconButton]s in it (each with [overlappingAction]), and their presses
 * drawn across their shares of it ([SearchPillPresses]). It never opens, so it is the closed pill's rectangle around
 * its buttons and nothing else.
 *
 * Given [sharedTabChrome] (made with the pill's color, `surfaceContainerHigh`), it is that part of its tab: while two
 * tabs are swapped the pill (a node as tall as the pill, its buttons reaching past its top and bottom) is drawn over
 * both screens, morphing into the other tab's part or scaling on its own, and the presses drawn under it, which nothing
 * is pressing then, are left behind.
 */
@Composable
internal fun FloatingPill(
    modifier: Modifier = Modifier,
    sharedTabChrome: SharedTabChrome? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val pill = remember { SearchPill() }
    val overflow = remember { PillOverflow() }
    val pillColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val height = with(LocalDensity.current) { FIELD_HEIGHT.toPx() }
    val layoutDirection = LocalLayoutDirection.current
    val bounds = { pill.closedBounds(height = height, detachProgress = 0f, layoutDirection = layoutDirection) }
    CompositionLocalProvider(LocalSearchPill provides pill) {
        Box(
            modifier = modifier
                // As tall as the buttons, which reach past the pill's top and bottom, so its place is unchanged.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val layoutHeight = constraints.constrainHeight(maxOf(placeable.height, overflow.contentHeight))
                    layout(placeable.width, layoutHeight) { placeable.place(0, (layoutHeight - placeable.height) / 2) }
                }
                .onPlaced {
                    pill.bar = it
                    pill.closedActions = Rect(Offset.Zero, it.size.toSize())
                }
                .drawBehind {
                    if (sharedTabChrome?.isSwapping != true) {
                        val pillBounds = bounds()
                        drawRoundRect(
                            color = pillColor,
                            topLeft = pillBounds.topLeft,
                            size = pillBounds.size,
                            cornerRadius = CornerRadius(pillBounds.height / 2),
                        )
                    }
                },
        ) {
            SearchPillPresses(modifier = Modifier.matchParentSize(), pill = pill, closedBounds = bounds, openProgress = { 0f })
            Box(
                modifier = Modifier
                    .sharedTabChrome(chrome = sharedTabChrome, zIndex = NEW_BUTTON_Z_INDEX, isContainerDrawnOutsideSwaps = false)
                    .overflowingPill(overflow, FIELD_HEIGHT),
            ) {
                // The actions of a top app bar are drawn in a quieter color than its content, as the bar's own are.
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    Row(
                        modifier = Modifier.padding(horizontal = ACTION_BUTTON_OVERLAP / 2),
                        verticalAlignment = Alignment.CenterVertically,
                        content = content,
                    )
                }
            }
        }
    }
}

/** How tall the buttons of a [FloatingPill] are, written as they are measured, for the pill to keep their room. */
private class PillOverflow {
    var contentHeight = 0
}

/**
 * Lays a pill [pillHeight] tall around buttons taller than it, which stand centered on it and reach past its top and
 * bottom, so that the node is the pill's own rectangle (the one a tab swap shares, see [sharedTabChrome]). Measured to
 * a size it is given (that rectangle travelling while the tabs swap), it takes that size and keeps the buttons centered.
 */
private fun Modifier.overflowingPill(overflow: PillOverflow, pillHeight: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity))
    overflow.contentHeight = placeable.height
    val width = constraints.constrainWidth(placeable.width)
    val height = constraints.constrainHeight(pillHeight.roundToPx())
    layout(width, height) { placeable.place((width - placeable.width) / 2, (height - placeable.height) / 2) }
}
