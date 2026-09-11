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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_language
import org.jetbrains.compose.resources.painterResource

/**
 * What a song is filed under, wherever those are only read: under its title in a song list. They wrap onto at most
 * [maxLines] rows and whatever is left over is clipped, so that a row of a list cannot grow taller because somebody
 * filed one song under a dozen labels — which is also why the languages come first, being the ones a reader scanning
 * a mixed library is looking for.
 */
@Composable
internal fun SongLabels(
    modifier: Modifier = Modifier,
    languages: List<String>,
    tags: List<String>,
    maxLines: Int = 1,
) = TagFlowRow(
    modifier = modifier,
    maxLines = maxLines,
) {
    // A language carries the mark the song details header gives it, since it is the one label here that is not the
    // user's own word for the song: without it a pill reading "Magyar" is a tag somebody typed, and there is no
    // telling the two apart in a list.
    languages.forEach { code ->
        TagPill(
            text = languageLabel(code),
            leadingIcon = painterResource(Res.drawable.ic_language),
        )
    }
    tags.forEach { tag -> TagPill(text = tag) }
}

/**
 * The layout every group of tags is laid out in. A tag is a word of whatever length its author chose, so they are
 * flowed rather than put in a row that would have to scroll or a grid whose columns would all be as wide as the
 * longest one.
 */
@Composable
internal fun TagFlowRow(
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    content: @Composable FlowRowScope.() -> Unit,
) = FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(TAG_GAP),
    verticalArrangement = Arrangement.spacedBy(TAG_GAP),
    maxLines = maxLines,
    content = content,
)

/**
 * One tag as a tonal pill. Like a [SectionHeader] it is a label first and a control second, so it is laid out at its
 * own size rather than being grown to the 48dp touch target even where it can be clicked: a row of tags each
 * reserving that much would be taller than the song title above it.
 *
 * @param onClick Null where the pill is only read, which is what a song list and the editor's preview show.
 * @param onTrailingIconClick Answers a click on [trailingIcon] alone - taking a tag off the song being played, which
 *   a tap that only meant to read the tag must not do.
 */
@Composable
internal fun TagPill(
    modifier: Modifier = Modifier,
    text: String,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: Painter? = null,
    trailingIcon: Painter? = null,
    trailingIconContentDescription: String? = null,
    onTrailingIconClick: (() -> Unit)? = null,
) {
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = @Composable {
        Row(
            modifier = Modifier.padding(
                start = if (leadingIcon == null) TAG_PADDING else TAG_ICON_INSET,
                end = if (trailingIcon == null) TAG_PADDING else TAG_ICON_INSET,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    modifier = Modifier.padding(end = TAG_ICON_INSET).size(TAG_ICON_SIZE),
                    painter = leadingIcon,
                    contentDescription = null,
                )
            }
            Text(
                modifier = Modifier.padding(vertical = TAG_TEXT_PADDING),
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingIcon != null) {
                // The icon itself is as small as the pill's text, so what can be clicked is the box around it: a
                // tag is taken off by a tap on a 14dp glyph otherwise, which on a phone is a matter of luck.
                Box(
                    modifier = Modifier
                        .padding(start = TAG_ICON_INSET)
                        .clip(CircleShape)
                        .clickable(enabled = onTrailingIconClick != null) { onTrailingIconClick?.invoke() }
                        .size(TAG_TRAILING_ICON_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        modifier = Modifier.size(TAG_ICON_SIZE),
                        painter = trailingIcon,
                        contentDescription = trailingIconContentDescription,
                    )
                }
            }
        }
    }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        if (onClick == null) {
            Surface(
                modifier = modifier,
                shape = MaterialTheme.shapes.small,
                color = containerColor,
                contentColor = contentColor,
                content = content,
            )
        } else {
            Surface(
                modifier = modifier,
                onClick = onClick,
                shape = MaterialTheme.shapes.small,
                color = containerColor,
                contentColor = contentColor,
                content = content,
            )
        }
    }
}

/** The gap between two tags, horizontally and between the rows they wrap onto. */
internal val TAG_GAP = 4.dp

private val TAG_PADDING = 8.dp
private val TAG_TEXT_PADDING = 4.dp
private val TAG_ICON_INSET = 6.dp
private val TAG_ICON_SIZE = 14.dp
private val TAG_TRAILING_ICON_SIZE = 22.dp
