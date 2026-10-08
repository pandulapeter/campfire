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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * @param isEmphasized Whether the row is an invitation to do something ("New setlist") rather than one entry of a
 *   list of things that can be done, which is what the actions of a song or of the library are.
 * @param horizontalInset What [CheckboxListItem]'s is, for a row that sits among checkboxes.
 */
@Composable
internal fun ActionListItem(
    modifier: Modifier = Modifier,
    title: String,
    icon: Painter,
    isEnabled: Boolean = true,
    isEmphasized: Boolean = true,
    horizontalInset: Dp = 0.dp,
    onClick: () -> Unit,
) = ListItem(
    modifier = modifier
        .clickable(enabled = isEnabled, onClick = onClick)
        .alpha(if (isEnabled) 1f else 0.5f)
        .padding(horizontal = horizontalInset),
    colors = if (isEmphasized) {
        ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.primary,
            leadingIconColor = MaterialTheme.colorScheme.primary,
        )
    } else {
        ListItemDefaults.colors(containerColor = Color.Transparent)
    },
    headlineContent = { Text(title) },
    leadingContent = { Icon(painter = icon, contentDescription = null) },
)
