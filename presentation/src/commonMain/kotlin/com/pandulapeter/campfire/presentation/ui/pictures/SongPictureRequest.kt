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

import androidx.compose.ui.unit.IntSize
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import okio.Buffer

/**
 * What the picture called [name] is asked of Coil by, read through [loader] and decoded at [decodeSize] where one is
 * given (else at the size it is laid out at). Its decoded copies are kept in Coil's memory cache under the loader's
 * [SongImageLoader.cacheKey] and the name, with a fetcher of their own on the request rather than on the app's image
 * loader, so a screen that brings its own loader needs nothing registered.
 */
internal fun songPictureRequest(context: PlatformContext, loader: SongImageLoader, name: String, decodeSize: IntSize? = null) =
    ImageRequest.Builder(context)
        .data(SongPictureData(name))
        .fetcherFactory(SongPictureFetcher.Factory(loader))
        .memoryCacheKey("${loader.cacheKey}/$name")
        .apply { decodeSize?.let { size(it.width, it.height) } }
        .build()

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
