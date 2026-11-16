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
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import okio.Buffer

/**
 * The picture a song names as [name], read through [LocalSongImageLoader] and decoded by Coil at the size it is laid out
 * at (Coil takes the size from the constraints), never at the picture's own: a phone's photo decoded whole would hold
 * tens of megabytes for a column a few hundred pixels wide. Whoever places it has already reserved its room from the
 * picture's header, so it arrives into it, crossfading as covers do, and moves nothing.
 *
 * Its decoded copies are kept in Coil's memory cache under the loader's [SongImageLoader.cacheKey] and the name, with a
 * fetcher of their own on the request rather than on the app's image loader, so a screen that brings its own loader
 * needs nothing registered.
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
    val request = remember(loader, name, context, decodeSize) {
        ImageRequest.Builder(context)
            .data(SongPictureData(name))
            .fetcherFactory(SongPictureFetcher.Factory(loader))
            .memoryCacheKey("${loader.cacheKey}/$name")
            .apply { decodeSize?.let { size(it.width, it.height) } }
            .build()
    }
    AsyncImage(
        modifier = modifier,
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
    )
}

/** What a song's picture is asked of Coil by. */
private class SongPictureData(val name: String)

private class SongPictureFetcher(private val data: SongPictureData, private val options: Options, private val loader: SongImageLoader) : Fetcher {

    /** A picture with nothing to show is an error to Coil, which leaves its room as it is. */
    override suspend fun fetch(): FetchResult {
        val bytes = loader.loadImage(data.name) ?: throw IllegalStateException("No picture called ${data.name}.")
        return SourceFetchResult(
            source = ImageSource(source = Buffer().write(bytes), fileSystem = options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val loader: SongImageLoader) : Fetcher.Factory<SongPictureData> {
        override fun create(data: SongPictureData, options: Options, imageLoader: ImageLoader) = SongPictureFetcher(data, options, loader)
    }
}
