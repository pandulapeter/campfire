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
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_open_in_new
import org.jetbrains.compose.resources.painterResource

/**
 * @param isEnabled False for a link that leads nowhere yet, which stays in its list, dimmed, with [description]
 *   saying why: a store the app has not reached.
 * @param onClick Null for a row of a list of links that is not one itself - the web build's own entry, whose address
 *   is the page it is already on - which is drawn at full strength, but with nothing to tap and no mark saying
 *   that it opens something.
 */
@Composable
internal fun LinkListItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: Painter,
    isEnabled: Boolean = true,
    onClick: (() -> Unit)?,
) = ListItem(
    modifier = (if (onClick == null) modifier else modifier.clickable(enabled = isEnabled, onClick = onClick))
        .alpha(if (isEnabled) 1f else 0.5f),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    supportingContent = description?.let { { Text(it) } },
    leadingContent = { Icon(painter = icon, contentDescription = null) },
    trailingContent = onClick?.let {
        {
            Icon(
                painter = painterResource(Res.drawable.ic_open_in_new),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    },
)
