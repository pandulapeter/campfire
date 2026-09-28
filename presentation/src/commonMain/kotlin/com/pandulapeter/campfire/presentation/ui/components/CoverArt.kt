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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.LayoutDirection
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
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

/** What a cover image is asked of Coil by: the address a song names, fetched through [GetCoverArtUseCase]. */
internal data class CoverArt(val url: String)

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

/**
 * A cover image that fades out towards the start of the layout, drawn at [alpha] so that it sits behind what is written
 * over it rather than competing with it. Nothing is drawn while it loads or where there is no image to show.
 *
 * The fade is a `DstIn` mask over an offscreen layer, so that it fades the image into whatever is behind it rather than
 * into a color of its own, and it eases in over the whole width ([FADE_STOPS]) rather than running linearly, which
 * would leave a visible edge where the image starts.
 */
@Composable
internal fun FadedCoverArt(
    modifier: Modifier = Modifier,
    url: String,
    alpha: Float,
) = AsyncImage(
    modifier = modifier
        .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            this.alpha = alpha
        }
        .drawWithContent {
            drawContent()
            val stops = if (layoutDirection == LayoutDirection.Rtl) FADE_STOPS.map { (offset, color) -> 1f - offset to color }.reversed() else FADE_STOPS
            drawRect(brush = Brush.horizontalGradient(*stops.toTypedArray()), blendMode = BlendMode.DstIn)
        },
    model = CoverArt(url),
    contentDescription = null,
    contentScale = ContentScale.Crop,
)

/**
 * The mask of [FadedCoverArt] from its start to its end: a smoothstep sampled at a few points, which starts and ends
 * flat, so that neither the start of the image nor the point where it is shown in full can be told from its
 * surroundings.
 */
private val FADE_STOPS = listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f).map { offset ->
    offset to Color.Black.copy(alpha = offset * offset * (3f - 2f * offset))
}

/**
 * A cover image on its own, drawn whole and at full strength, which only takes its room once there is an image to
 * show: an address that answers with nothing leaves no empty square behind. It grows into its place as it arrives, and
 * one that is already in memory is simply there from the first frame.
 */
@Composable
internal fun CoverArtImage(
    modifier: Modifier = Modifier,
    url: String,
) {
    val painter = rememberAsyncImagePainter(model = CoverArt(url), contentScale = ContentScale.Crop)
    val state by painter.state.collectAsState()
    AnimatedVisibility(
        visible = state is AsyncImagePainter.State.Success,
        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
    ) {
        Image(
            modifier = modifier.clip(MaterialTheme.shapes.medium),
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )
    }
}
