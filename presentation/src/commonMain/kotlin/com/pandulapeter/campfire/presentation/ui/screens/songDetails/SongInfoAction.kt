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

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_edit
import org.jetbrains.compose.resources.painterResource

/**
 * The button next to a group's title that edits it: a pencil, or a plus where the group is empty. As tall as the title
 * row, so that a group reads the same with it and without it; the touch target still reaches beyond it.
 */
@Composable
internal fun SongInfoAction(
    hasValues: Boolean,
    contentDescription: String,
    fontScale: Float,
    onClick: () -> Unit,
) = IconButton(
    modifier = Modifier.size(SONG_INFO_TITLE_HEIGHT * fontScale),
    onClick = onClick,
) {
    Icon(
        modifier = Modifier.size(EDIT_ICON_SIZE * fontScale),
        painter = painterResource(if (hasValues) Res.drawable.ic_edit else Res.drawable.ic_add),
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
    )
}

internal val EDIT_ICON_SIZE = 18.dp
internal val SONG_INFO_TITLE_HEIGHT = 32.dp
