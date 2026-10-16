/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

/**
 * A row of Settings that leads somewhere: a link, or a flow such as setting the app up again.
 *
 * The icon sits beside the [ListItem] rather than in its leading slot, since the list item puts its leading content at
 * the top once the description wraps to a second line, and these rows keep it centered whatever their text's length.
 * The padding before the icon and the list item's own start padding after it add up to the leading slot's spacing.
 *
 * @param isEnabled False for a link that leads nowhere yet, which stays in its list, dimmed, with [description]
 *   saying why: a store the app has not reached.
 * @param onClick Null for a row of a list of links that is not one itself - the web build's own entry, whose address
 *   is the page it is already on - which is drawn at full strength, but with nothing to tap.
 */
@Composable
internal fun LinkListItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: Painter,
    isEnabled: Boolean = true,
    onClick: (() -> Unit)?,
) = Row(
    modifier = (if (onClick == null) modifier else modifier.clickable(enabled = isEnabled, onClick = onClick))
        .alpha(if (isEnabled) 1f else 0.5f),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(
        modifier = Modifier.padding(start = 16.dp),
        painter = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ListItem(
        modifier = Modifier.weight(1f),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
    )
}
