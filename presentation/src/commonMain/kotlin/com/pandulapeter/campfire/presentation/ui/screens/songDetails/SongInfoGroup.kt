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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One group of [SongInfoBody] under its title, if it has one, with a count next to the title when it is more than one
 * and the [action] that edits the group after that. Where [isContentShown] is false the group is its title row alone,
 * the content folding away rather than vanishing, since emptying a group is something the user did from this sheet.
 */
@Composable
internal fun SongInfoGroup(
    title: String?,
    count: Int,
    fontScale: Float,
    horizontalPadding: Dp,
    action: (@Composable () -> Unit)? = null,
    isContentShown: Boolean = true,
    content: @Composable () -> Unit,
) = Column(modifier = Modifier.fillMaxWidth()) {
    if (title != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SONG_INFO_TITLE_HEIGHT * fontScale)
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.scaled(fontScale),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (count > 1) {
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = count.toString(),
                    style = MaterialTheme.typography.titleSmall.scaled(fontScale),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (action != null) {
                Box(modifier = Modifier.padding(start = 4.dp * fontScale)) {
                    action()
                }
            }
        }
    }
    AnimatedVisibility(
        visible = isContentShown,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Box(modifier = Modifier.padding(start = horizontalPadding, top = if (title == null) 0.dp else 4.dp * fontScale, end = horizontalPadding)) {
            content()
        }
    }
}
