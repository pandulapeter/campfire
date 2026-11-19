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
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.toSize
import com.pandulapeter.campfire.presentation.ui.components.menu.ACTION_BUTTON_OVERLAP

/**
 * The list screens' closed search pill ([SearchableTopAppBar]) on its own, floating over a page that has no bar: the same
 * tonal color, height, and shape, the same [PillIconButton]s in it (each with [overlappingAction]), and their presses
 * drawn across their shares of it ([SearchPillPresses]). It never opens, so it is the closed pill's rectangle around
 * its buttons and nothing else.
 */
@Composable
internal fun FloatingPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val pill = remember { SearchPill() }
    val pillColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val height = with(LocalDensity.current) { FIELD_HEIGHT.toPx() }
    val layoutDirection = LocalLayoutDirection.current
    val bounds = { pill.closedBounds(height = height, detachProgress = 0f, layoutDirection = layoutDirection) }
    CompositionLocalProvider(LocalSearchPill provides pill) {
        Box(
            modifier = modifier
                .onPlaced {
                    pill.bar = it
                    pill.closedActions = Rect(Offset.Zero, it.size.toSize())
                }
                .drawBehind {
                    val pillBounds = bounds()
                    drawRoundRect(
                        color = pillColor,
                        topLeft = pillBounds.topLeft,
                        size = pillBounds.size,
                        cornerRadius = CornerRadius(pillBounds.height / 2),
                    )
                },
        ) {
            SearchPillPresses(modifier = Modifier.matchParentSize(), pill = pill, closedBounds = bounds, openProgress = { 0f })
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
