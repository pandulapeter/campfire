/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songCard

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_dot
import org.jetbrains.compose.resources.painterResource

/**
 * What stands between two things on a song card's second line: a dot after the artist, which is a name and has
 * nothing of its own to end it, and only a gap between two values, each of which starts with its own mark, so a dot
 * there would only take letters off the artist.
 */
internal enum class SongCardSeparatorKind { DOT, GAP }

/**
 * The separator in front of a value on a song card's second line, given what stands before it: a gap after another
 * value, a dot after the artist alone, and nothing at the start of the line, where it would separate the value from
 * nothing.
 */
internal fun songCardSeparatorBefore(hasArtist: Boolean, hasValueBefore: Boolean) = when {
    hasValueBefore -> SongCardSeparatorKind.GAP
    hasArtist -> SongCardSeparatorKind.DOT
    else -> null
}

/**
 * A [SongCardSeparatorKind] as it is drawn. It carries no padding of its own: the glyph is a 4dp dot in the middle of a
 * 24dp icon, so the box it sits in is the gap already, and the same gap on both sides of it - anything added here is
 * added to one side only. A narrow window shrinks the box, and with it both gaps, since there every dp of the line is
 * one more letter of the artist. One kind becomes the other as the values before it come and go, the dot fading while
 * its room narrows to the gap's, so the line closes up rather than jumping.
 */
@Composable
internal fun SongCardSeparator(kind: SongCardSeparatorKind) {
    val dotSize = if (isNarrowSongCardWindow) NARROW_DOT_SIZE else DOT_SIZE
    val isDot = kind == SongCardSeparatorKind.DOT
    val width by animateDpAsState(if (isDot) dotSize else if (isNarrowSongCardWindow) NARROW_GAP_WIDTH else GAP_WIDTH)
    val dotAlpha = animateFloatAsState(if (isDot) 1f else 0f)
    Box(
        modifier = Modifier.width(width).height(dotSize),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.requiredSize(dotSize).graphicsLayer { alpha = dotAlpha.value },
            painter = painterResource(Res.drawable.ic_dot),
            contentDescription = null,
        )
    }
}

private val DOT_SIZE = 24.dp
private val NARROW_DOT_SIZE = 16.dp
private val GAP_WIDTH = 8.dp
private val NARROW_GAP_WIDTH = 6.dp
