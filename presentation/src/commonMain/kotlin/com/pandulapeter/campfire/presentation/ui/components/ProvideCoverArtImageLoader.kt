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

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.request.crossfade
import com.pandulapeter.campfire.domain.api.useCases.GetCoverArtUseCase
import okio.Buffer
import org.koin.mp.KoinPlatform

/**
 * Makes Coil load [CoverArt] through [GetCoverArtUseCase] and nothing else, once for the whole app.
 *
 * Coil has no network artifact here: the bytes come from the use case, which keeps the offline copy and goes through
 * the one HTTP client the app has, so Coil only decodes them, keeps what it decoded in its memory cache for scrolling,
 * and crossfades what did not come from there — an image already in memory appears at once. It gets no disk cache of
 * its own, since the use case's copy is that, on all four platforms, where Coil's would be one that forgets by size
 * and does not exist in the browser.
 */
@Composable
internal fun ProvideCoverArtImageLoader() = setSingletonImageLoaderFactory { context -> createCoverArtImageLoader(context) }

private fun createCoverArtImageLoader(context: PlatformContext) = ImageLoader.Builder(context)
    .components {
        add(CoverArtKeyer())
        add(CoverArtFetcher.Factory(getCoverArt = KoinPlatform.getKoin().get()))
    }
    .diskCache(null)
    .crossfade(true)
    .build()

/** A cover is the same image wherever it is shown, so its address is the whole of its memory cache key. */
private class CoverArtKeyer : Keyer<CoverArt> {
    override fun key(data: CoverArt, options: Options) = data.url
}

private class CoverArtFetcher(
    private val data: CoverArt,
    private val options: Options,
    private val getCoverArt: GetCoverArtUseCase,
) : Fetcher {

    /** A cover with nothing to show is an error to Coil, which is what leaves the place empty. */
    override suspend fun fetch(): FetchResult {
        val bytes = getCoverArt(data.url) ?: throw CoverArtUnavailableException(data.url)
        return SourceFetchResult(
            source = ImageSource(source = Buffer().write(bytes), fileSystem = options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val getCoverArt: GetCoverArtUseCase) : Fetcher.Factory<CoverArt> {
        override fun create(data: CoverArt, options: Options, imageLoader: ImageLoader) = CoverArtFetcher(data, options, getCoverArt)
    }
}

private class CoverArtUnavailableException(url: String) : Exception("No cover art at $url.")
