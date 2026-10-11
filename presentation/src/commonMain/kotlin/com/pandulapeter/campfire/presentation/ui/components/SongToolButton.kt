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

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A round button of the song's own first section, opening a tool the song is played with — the metronome panel, the
 * tuner sheet — next to the values that tool is about. It is the tonal circle the steppers beside it are pills of, and
 * as tall as they are ([songControlHeight]), so that it reads as one more control of the same row rather than as a bar
 * button that wandered into the song; its icon grows with the song's text as theirs do, its padding does not.
 *
 * As with the steppers, the drawn size is not the touch target: Compose still extends the press to the minimum touch
 * target size on a touch screen, while the layout keeps the pill's height.
 *
 * @param isSelected Draws the circle in the secondary container color, the way a toggle that is on is drawn, for the
 * metronome while its panel is up and the tuner while its sheet is open.
 * @param icon Drawn at [songToolIconSize] for the same [fontScale].
 */
@Composable
internal fun SongToolButton(
    modifier: Modifier = Modifier,
    height: Dp,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides height) {
        Surface(
            modifier = modifier.size(height),
            onClick = onClick,
            shape = CircleShape,
            color = containerColor,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
    }
}

/** How large a [SongToolButton]'s icon is drawn at [fontScale]: the steppers' icons, scaled the same way. */
internal fun songToolIconSize(fontScale: Float) = SONG_TOOL_ICON_SIZE * fontScale

private val SONG_TOOL_ICON_SIZE = 20.dp
