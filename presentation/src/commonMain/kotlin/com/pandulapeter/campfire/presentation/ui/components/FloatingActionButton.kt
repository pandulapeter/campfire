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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The primary action of a list screen, dealt in and out with the screen it belongs to rather than living in the app's
 * chrome, so that it arrives with the screen it acts on instead of animating on its own while the screen slides.
 *
 * Wide windows get the label next to the icon; on a phone it would take a third of the row, so there it stays the
 * icon's content description. Every screen goes through this one button, so they can never drift apart.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CampfireFloatingActionButton(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    settledWidth: Dp,
    contentPadding: PaddingValues,
    icon: Painter,
    label: String,
    onClick: () -> Unit
) = AnimatedVisibility(
    modifier = modifier.padding(
        end = contentPadding.calculateEndPadding(LocalLayoutDirection.current) + FAB_MARGIN,
        bottom = contentPadding.calculateBottomPadding() + FAB_MARGIN
    ),
    visible = isVisible,
    enter = fadeIn() + scaleIn(),
    exit = fadeOut() + scaleOut()
) {
    val isExtended = settledWidth >= EXTENDED_FAB_MIN_WIDTH
    val iconContent = @Composable {
        Icon(
            painter = icon,
            contentDescription = if (isExtended) null else label
        )
    }
    if (isExtended) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            icon = iconContent,
            text = { Text(label) }
        )
    } else {
        FloatingActionButton(onClick = onClick) { iconContent() }
    }
}

/** From this width on the button has room for its label without crowding the list next to it. */
private val EXTENDED_FAB_MIN_WIDTH = 600.dp
private val FAB_MARGIN = 16.dp

/** The bottom padding a list needs so that its last row can scroll out from under the button. */
internal val FAB_CLEARANCE = 88.dp
