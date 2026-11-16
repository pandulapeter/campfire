/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.pictures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext

/**
 * The picture a song names as [name], read through [LocalSongImageLoader] and decoded by Coil at the size it is laid out
 * at (Coil takes the size from the constraints), never at the picture's own: a phone's photo decoded whole would hold
 * tens of megabytes for a column a few hundred pixels wide. Whoever places it has already reserved its room from the
 * picture's header, so it arrives into it, crossfading as covers do, and moves nothing.
 *
 * @param decodeSize The pixels to decode it at instead, where it is drawn larger than laid out (zoomed in).
 */
@Composable
internal fun SongPicture(
    modifier: Modifier = Modifier,
    name: String,
    contentDescription: String?,
    decodeSize: IntSize? = null,
) {
    val loader = LocalSongImageLoader.current
    val context = LocalPlatformContext.current
    val request = remember(loader, name, context, decodeSize) { songPictureRequest(context, loader, name, decodeSize) }
    AsyncImage(
        modifier = modifier,
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
    )
}
