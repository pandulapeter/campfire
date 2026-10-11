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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One tag as a tonal pill. Like a [SectionHeader] it is a label first and a control second, so it is laid out at its
 * own size rather than being grown to the 48dp touch target even where it can be clicked: a row of tags each
 * reserving that much would be taller than the song title above it.
 *
 * @param onClick Null where the pill is only read, which is what the editor's preview shows.
 * @param isOutlined Drawn as an outline rather than filled, for a pill standing on a tile of its own: the tiles of a song's
 *   info are the tone just below the fill, which tells the two apart in neither theme, and an outline reads on any
 *   container, like the outlined link buttons it stands next to there.
 * @param fontScale The song details' zoom, which the pills of its info card follow along with the lyrics; the text, the
 *   mark and the widest a pill may grow scale together, so that a zoomed tag is not ellipsized sooner than at rest.
 */
@Composable
internal fun TagPill(
    modifier: Modifier = Modifier,
    text: String,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: Painter? = null,
    fontScale: Float = 1f,
    isOutlined: Boolean = false,
) {
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isOutlined -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val border = if (isOutlined && !isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    val textStyle = MaterialTheme.typography.labelMedium.scaled(fontScale)
    val chipHeight = tagPillHeight(fontScale)
    val content = @Composable {
        Row(
            // The tag dialog caps what is typed, but a tag that arrives in a file (written in the editor, imported or
            // synced) is as long as its author made it, and in the sideways scrolling row of a song list nothing else
            // would stop one from being wider than the screen.
            modifier = Modifier.height(chipHeight).widthIn(max = TAG_MAX_WIDTH * fontScale).padding(
                start = if (leadingIcon == null) TAG_PADDING else TAG_ICON_INSET,
                end = TAG_PADDING,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    modifier = Modifier.padding(end = TAG_ICON_INSET).size(TAG_ICON_SIZE * fontScale),
                    painter = leadingIcon,
                    contentDescription = null,
                )
            }
            Text(
                modifier = Modifier.weight(1f, fill = false).padding(vertical = TAG_TEXT_PADDING),
                text = text,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        if (onClick == null) {
            Surface(
                modifier = modifier,
                shape = MaterialTheme.shapes.small,
                color = containerColor,
                contentColor = contentColor,
                border = border,
                content = content,
            )
        } else {
            Surface(
                modifier = modifier,
                onClick = onClick,
                shape = MaterialTheme.shapes.small,
                color = containerColor,
                contentColor = contentColor,
                border = border,
                content = content,
            )
        }
    }
}

/**
 * The height of every [TagPill] at [fontScale], with or without an icon, for what stands in a row of them and has to
 * be exactly as tall: the call to action of an empty group of a song's info.
 */
@Composable
internal fun tagPillHeight(fontScale: Float): Dp = with(LocalDensity.current) {
    maxOf(TAG_ICON_SIZE * fontScale, MaterialTheme.typography.labelMedium.scaled(fontScale).lineHeight.toDp() + TAG_TEXT_PADDING * 2)
}

private val TAG_PADDING = 8.dp
private val TAG_TEXT_PADDING = 4.dp
private val TAG_ICON_INSET = 6.dp
private val TAG_ICON_SIZE = 14.dp

/** Wide enough for the longest tag the tag dialog lets through to be shown whole in most scripts, ellipsized past it. */
private val TAG_MAX_WIDTH = 240.dp
