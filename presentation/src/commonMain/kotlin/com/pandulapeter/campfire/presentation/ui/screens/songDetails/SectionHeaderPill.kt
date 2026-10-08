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

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.songControlHeight

/**
 * The raised pill a section is headed by, which folds the section where it has anything to fold ([toggle]). It is laid
 * out at its own size: the touch target enforcement would grow it to 48dp and push the lines of the section down, just
 * as it would in the lists (see [SectionHeader]).
 */
@Composable
internal fun SectionHeaderPill(
    modifier: Modifier = Modifier,
    header: String,
    toggle: FoldToggle?,
    style: TextStyle,
    chevronSize: Dp,
    chevronDescription: String? = null,
) = CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
    val pillModifier = modifier.offset(x = -HEADER_HORIZONTAL_PADDING)
    val content = @Composable {
        SectionTitle(
            modifier = Modifier.padding(horizontal = HEADER_HORIZONTAL_PADDING, vertical = HEADER_VERTICAL_PADDING),
            header = header,
            toggle = toggle,
            style = style,
            chevronSize = chevronSize,
            chevronDescription = chevronDescription,
        )
    }
    if (toggle == null) {
        Surface(
            modifier = pillModifier,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = HEADER_ELEVATION,
            content = content,
        )
    } else {
        Surface(
            onClick = toggle.onToggled,
            modifier = pillModifier,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = HEADER_ELEVATION,
            content = content,
        )
    }
}

private val HEADER_ELEVATION = 2.dp
private val HEADER_HORIZONTAL_PADDING = 12.dp

/**
 * Above and below a section's name, and so, with the name's own line, the whole of a header pill's height — which is
 * what the controls of the song's own first section are drawn at (see [songControlHeight]).
 */
internal val HEADER_VERTICAL_PADDING = 6.dp
