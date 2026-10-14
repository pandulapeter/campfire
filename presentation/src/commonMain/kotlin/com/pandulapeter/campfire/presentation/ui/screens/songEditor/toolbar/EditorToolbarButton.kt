/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_expand
import org.jetbrains.compose.resources.painterResource

/**
 * One button of the [EditorToolbar]'s rows, which takes no focus, see there.
 *
 * @param hasMenu Whether the button opens a menu of choices rather than writing something at once, which a chevron
 *   after its label says.
 */
@Composable
internal fun EditorToolbarButton(
    modifier: Modifier = Modifier,
    label: String,
    isEnabled: Boolean,
    hasMenu: Boolean = false,
    onClick: () -> Unit,
) = CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
    Surface(
        modifier = modifier.focusProperties { canFocus = false }.alpha(if (isEnabled) 1f else DISABLED_ALPHA),
        onClick = onClick,
        enabled = isEnabled,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(
                start = BUTTON_HORIZONTAL_PADDING,
                end = if (hasMenu) BUTTON_MENU_END_PADDING else BUTTON_HORIZONTAL_PADDING,
                top = BUTTON_VERTICAL_PADDING,
                bottom = BUTTON_VERTICAL_PADDING,
            ),
            horizontalArrangement = Arrangement.spacedBy(BUTTON_ICON_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            if (hasMenu) {
                Icon(
                    modifier = Modifier.size(BUTTON_ICON_SIZE),
                    painter = painterResource(Res.drawable.ic_expand),
                    contentDescription = null,
                )
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.5f
private val BUTTON_HORIZONTAL_PADDING = 12.dp
private val BUTTON_MENU_END_PADDING = 8.dp
private val BUTTON_VERTICAL_PADDING = 8.dp
private val BUTTON_ICON_GAP = 2.dp
private val BUTTON_ICON_SIZE = 18.dp
