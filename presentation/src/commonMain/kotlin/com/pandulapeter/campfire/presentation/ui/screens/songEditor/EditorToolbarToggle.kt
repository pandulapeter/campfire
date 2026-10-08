/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_editor_hide_shortcuts
import com.pandulapeter.campfire.presentation.resources.song_editor_shortcuts
import com.pandulapeter.campfire.presentation.resources.song_editor_show_shortcuts
import com.pandulapeter.campfire.presentation.ui.components.ExpandChevron

@Composable
internal fun EditorToolbarToggle(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    isLabeled: Boolean,
    isEnabled: Boolean,
    onToggled: () -> Unit,
) {
    val icon: @Composable () -> Unit = {
        ExpandChevron(
            isExpanded = isExpanded,
            contentDescription = stringResource(if (isExpanded) Res.string.song_editor_hide_shortcuts else Res.string.song_editor_show_shortcuts),
        )
    }
    if (isLabeled) {
        // The label names the rows rather than the action, so that it keeps one width while the chevron turns: a
        // "Show" becoming a "Hide" would move the segments next to it on every tap.
        TextButton(
            modifier = modifier,
            enabled = isEnabled,
            onClick = onToggled,
            colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
        ) {
            Text(text = stringResource(Res.string.song_editor_shortcuts))
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            icon()
        }
    } else {
        IconButton(
            modifier = modifier,
            enabled = isEnabled,
            onClick = onToggled,
            content = icon,
        )
    }
}
