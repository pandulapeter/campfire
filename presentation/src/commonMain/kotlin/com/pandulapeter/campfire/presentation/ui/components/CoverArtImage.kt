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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_album
import org.jetbrains.compose.resources.painterResource

/**
 * A cover image on its own, drawn whole at the size it is given, over a placeholder of the same size: a tinted square
 * with the album icon in it, which is what shows while the image loads and where it never does. The room is taken
 * from the first frame rather than once the image has arrived, so that nothing around a cover moves when it does —
 * in a list that is being scrolled, images arrive all the time, and every one of them would otherwise push the text
 * next to it aside. The image crossfades in over the placeholder, and one that is already in memory is simply there.
 */
@Composable
internal fun CoverArtImage(
    modifier: Modifier = Modifier,
    url: String,
    shape: Shape = MaterialTheme.shapes.medium,
) = Box(
    modifier = modifier
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    contentAlignment = Alignment.Center,
) {
    Icon(
        modifier = Modifier.fillMaxSize(COVER_ART_PLACEHOLDER_ICON_FRACTION),
        painter = painterResource(Res.drawable.ic_album),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AsyncImage(
        modifier = Modifier.matchParentSize(),
        model = CoverArt(url),
        contentDescription = null,
        contentScale = ContentScale.Crop,
    )
}

/** How much of the placeholder's side its album icon takes up. */
private const val COVER_ART_PLACEHOLDER_ICON_FRACTION = 0.5f
