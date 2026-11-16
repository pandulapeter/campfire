/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.asPainter
import coil3.request.SuccessResult
import com.pandulapeter.campfire.presentation.ui.pictures.SongImageLoader
import com.pandulapeter.campfire.presentation.ui.pictures.SongImageSize
import com.pandulapeter.campfire.presentation.ui.pictures.imageNamesOf
import com.pandulapeter.campfire.presentation.ui.pictures.loadSizesOf
import com.pandulapeter.campfire.presentation.ui.pictures.songPictureRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * The pictures a PDF's songs name, read through [loader]: their sizes for the layout ([sizesOf]) and their pixels for the
 * drawing, decoded by Coil at the size a page draws them, never at a photo's own. The file's pages decode theirs one page
 * at a time ([decodedFor]), the preview at [PREVIEW_SCALE] pixels a point in [scope], kept for the last
 * [KEPT_PREVIEW_PICTURES] it drew ([previewPainterOf]): a page drawn again on every frame of a zoom decodes nothing.
 */
internal class PrintPictures(
    private val loader: SongImageLoader,
    private val context: PlatformContext,
    private val scope: CoroutineScope,
) {
    /** State, so that a preview drawn before its pictures were decoded is drawn again once they are; null for one that failed. */
    private val previews = mutableStateMapOf<PictureKey, Painter?>()
    private val previewOrder = ArrayDeque<PictureKey>()
    private val decoding = mutableSetOf<PictureKey>()

    /** What the pictures [source]'s songs name were read as, by name, read first where they were not yet. */
    suspend fun sizesOf(source: PrintSource): Map<String, SongImageSize> {
        val texts = source.songs.mapNotNull { it.text }
        val names = texts.flatMapTo(mutableSetOf(), ::imageNamesOf)
        if (names.isEmpty()) return emptyMap()
        loader.loadSizesOf(texts)
        return loader.sizes.value.filterKeys { it in names }
    }

    /** Every picture of [page] decoded for a page image [scale] pixels a point, null for one that cannot be. */
    suspend fun decodedFor(page: PrintPage, scale: Float): Map<PrintImage, Painter?> = page.images.associateWith { decode(it.keyAt(scale)) }

    /** [image] as the preview draws it, or null while it is decoded (which this starts) and where it cannot be. */
    fun previewPainterOf(image: PrintImage): Painter? {
        val key = image.keyAt(PREVIEW_SCALE)
        if (key in previews) return previews[key]
        if (decoding.add(key)) {
            scope.launch {
                val painter = decode(key)
                decoding -= key
                previews[key] = painter
                previewOrder.addLast(key)
                while (previewOrder.size > KEPT_PREVIEW_PICTURES) previews.remove(previewOrder.removeFirst())
            }
        }
        return null
    }

    private suspend fun decode(key: PictureKey): Painter? {
        val request = songPictureRequest(context, loader, key.name, IntSize(key.width, key.height))
        return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.asPainter(context)
    }

    private fun PrintImage.keyAt(scale: Float) = PictureKey(name, ceil(width * scale).toInt().coerceAtLeast(1), ceil(height * scale).toInt().coerceAtLeast(1))

    /** A picture at the size in pixels it is decoded at. */
    private data class PictureKey(val name: String, val width: Int, val height: Int)

    private companion object {
        const val PREVIEW_SCALE = 1.5f
        const val KEPT_PREVIEW_PICTURES = 12
    }
}
