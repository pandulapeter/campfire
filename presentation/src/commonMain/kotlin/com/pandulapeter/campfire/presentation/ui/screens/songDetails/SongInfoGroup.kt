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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_next
import com.pandulapeter.campfire.presentation.ui.components.tagPillHeight
import com.pandulapeter.campfire.presentation.ui.components.scaled
import org.jetbrains.compose.resources.painterResource

/**
 * One group of [SongInfoBody], a tile of its grouped list ([tileShape]) under its title, if it has one, with a count
 * next to the title when it is more than one.
 *
 * Where [onClick] is given the whole tile is what edits the group, a chevron at its end saying so: one target as large
 * as what it changes, rather than a small button next to every title, and the same gesture for every group. An empty
 * group that can be edited holds a call to action instead ([SongInfoAction], [emptyLabel], its editor's name unless
 * the caller words it for a group that has nothing yet), so that it says what a tap does; one that cannot is left out
 * by the caller. Where [isContentShown] turns false the content folds away rather than vanishing, since
 * emptying a group is something the user did from here.
 */
@Composable
internal fun SongInfoGroup(
    title: String?,
    count: Int,
    fontScale: Float,
    horizontalPadding: Dp,
    tileShape: Shape,
    onClick: (() -> Unit)?,
    onClickLabel: String,
    emptyLabel: String = onClickLabel,
    isContentShown: Boolean = true,
    content: @Composable () -> Unit,
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = horizontalPadding)
        .clip(tileShape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .then(if (onClick == null) Modifier else Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick))
        .heightIn(min = songInfoTileMinHeight(fontScale))
        .padding(
            start = TILE_PADDING * fontScale,
            top = TILE_VERTICAL_PADDING * fontScale,
            bottom = TILE_VERTICAL_PADDING * fontScale,
        ),
    verticalAlignment = Alignment.CenterVertically,
) {
    Column(modifier = Modifier.weight(1f)) {
        if (title != null) {
            SongInfoGroupTitle(
                title = title,
                count = count,
                fontScale = fontScale,
            )
        }
        // A titled group's content sits in a slot as tall as a row of link buttons, centered in it, so that every title
        // stands on the same line of its tile and every row of chips, buttons or the call to action is centered on the
        // same line under it, whichever of them a tile holds.
        Box(
            modifier = if (title == null) {
                Modifier
            } else {
                Modifier.padding(top = TITLE_CONTENT_GAP * fontScale).heightIn(min = songInfoLinkRowHeight(fontScale))
            },
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                AnimatedVisibility(
                    visible = isContentShown,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    content()
                }
                AnimatedVisibility(
                    visible = !isContentShown && onClick != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    SongInfoAction(
                        text = emptyLabel,
                        fontScale = fontScale,
                    )
                }
            }
        }
    }
    if (onClick == null) {
        Box(modifier = Modifier.padding(end = TILE_PADDING * fontScale))
    } else {
        Icon(
            modifier = Modifier.padding(horizontal = TILE_CHEVRON_PADDING * fontScale).size(TILE_CHEVRON_SIZE * fontScale),
            painter = painterResource(Res.drawable.ic_next),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * What an empty group, or the details beside a cover, hold where the song says nothing for them yet: what a tap does,
 * in the primary color behind a plus, as plain text rather than a pill, since it takes no tap of its own and a pill
 * would ask for one. It is exactly as tall as a row of the chips it stands in for, so that a tile does not change
 * height as its first chip arrives.
 */
@Composable
internal fun SongInfoAction(
    modifier: Modifier = Modifier,
    text: String,
    fontScale: Float,
) = Row(
    modifier = modifier.height(tagPillHeight(fontScale)),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(
        modifier = Modifier.padding(end = 6.dp * fontScale).size(SONG_INFO_ACTION_ICON_SIZE * fontScale),
        painter = painterResource(Res.drawable.ic_add),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.scaled(fontScale),
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The height no tile of the sheet is shorter than, whatever it holds: that of the links tile with a single link, the
 * tallest single row any group has, so that the tiles of one list are of one height until one of them needs more and
 * a group does not grow as its first link arrives. A titled tile is that tall by its own layout; an untitled one, the
 * details and the defaults, is held to it, what it holds centered in it.
 */
@Composable
private fun songInfoTileMinHeight(fontScale: Float): Dp = with(LocalDensity.current) {
    MaterialTheme.typography.labelLarge.scaled(fontScale).lineHeight.toDp()
} + TITLE_CONTENT_GAP * fontScale + songInfoLinkRowHeight(fontScale) + TILE_VERTICAL_PADDING * fontScale * 2

/**
 * A group's title, set as small as the labels of the details tiles: the content under it is what is read, and a title
 * row of its own height cost every tile a line.
 */
@Composable
private fun SongInfoGroupTitle(
    modifier: Modifier = Modifier,
    title: String,
    count: Int,
    fontScale: Float,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.scaled(fontScale),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (count > 1) {
        Text(
            modifier = Modifier.padding(start = 8.dp),
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge.scaled(fontScale),
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * The shape of the tile at [index] of [count] in the sheet's grouped list: the outer corners of the list rounded like a
 * card, the corners between two tiles barely, so that the tiles read as one list cut into rows.
 */
internal fun songInfoTileShape(index: Int, count: Int) = RoundedCornerShape(
    topStart = if (index == 0) OUTER_CORNER else INNER_CORNER,
    topEnd = if (index == 0) OUTER_CORNER else INNER_CORNER,
    bottomStart = if (index == count - 1) OUTER_CORNER else INNER_CORNER,
    bottomEnd = if (index == count - 1) OUTER_CORNER else INNER_CORNER,
)

private val TILE_PADDING = 16.dp
private val TILE_VERTICAL_PADDING = 10.dp
private val TITLE_CONTENT_GAP = 2.dp
private val TILE_CHEVRON_PADDING = 12.dp
private val TILE_CHEVRON_SIZE = 20.dp
private val SONG_INFO_ACTION_ICON_SIZE = 16.dp
private val OUTER_CORNER = 20.dp
private val INNER_CORNER = 4.dp
