/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.widget

import com.pandulapeter.campfire.data.formats.image.ImageInfo
import com.pandulapeter.campfire.data.model.hashing.Sha256
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.platform.PictureAttempt
import com.pandulapeter.campfire.presentation.ui.platform.PictureEncoding
import com.pandulapeter.campfire.presentation.ui.platform.reencodePicture
import com.pandulapeter.campfire.presentation.ui.platform.reencodedSizeOf

/**
 * The file name of the thumbnail of the cover at [url] in the widgets' covers folder: the address's SHA-256, as the app's
 * own cover copies are named, so a cover several songs name is made once, and a song keeps its thumbnail across a rename.
 */
internal fun widgetCoverName(url: String) = Sha256.hashToHex(url.encodeToByteArray()) + COVER_EXTENSION

/** The cover address of each of [songs] that names one, by its file name; none at all with the Cover art switch off. */
internal fun widgetCoverAddresses(songs: List<Song>, isCoverArtEnabled: Boolean): Map<String, String> = if (isCoverArtEnabled) {
    songs.mapNotNull { song -> song.coverArtUrl?.takeIf { it.isNotBlank() }?.let { song.fileName to it } }.toMap()
} else {
    emptyMap()
}

/**
 * This snapshot with each song's [WidgetSnapshot.Song.cover] named where [addresses] gives it a cover whose thumbnail is
 * [available] in the covers folder, and none everywhere else: a snapshot never names a thumbnail the widgets cannot find.
 */
internal fun WidgetSnapshot.withCovers(addresses: Map<String, String>, available: Set<String>) = copy(
    songs = songs.map { song -> song.copy(cover = addresses[song.fileName]?.let(::widgetCoverName)?.takeIf { it in available }) },
)

/**
 * How far a cover of [width] by [height] pixels is scaled for its thumbnail, as a [PictureAttempt.scale] of the size the
 * re-encoder fits it to: its shorter side down to [WIDGET_COVER_SIDE], since a widget crops it to a square, and never up.
 */
internal fun widgetCoverScale(width: Int, height: Int): Float {
    val fitted = reencodedSizeOf(width, height)
    return minOf(1f, WIDGET_COVER_SIDE.toFloat() / minOf(fitted.width, fitted.height))
}

/**
 * The thumbnail of the cover [bytes] hold, a small JPEG, or null where it is no picture this can read. Small, so that the
 * widgets never come near what a home screen lets them hold: Android's limit on a widget's bitmaps, and the memory an iOS
 * widget extension may use.
 */
internal suspend fun widgetCoverThumbnail(bytes: ByteArray): ByteArray? {
    val info = ImageInfo.of(bytes) ?: return null
    return reencodePicture(
        bytes = bytes,
        attempts = listOf(PictureAttempt(encoding = PictureEncoding.JPEG, quality = THUMBNAIL_QUALITY, scale = widgetCoverScale(info.width, info.height))),
        maxBytes = MAX_THUMBNAIL_BYTES,
    )
}

/**
 * The shorter side of a cover's thumbnail, in pixels: sharp at about three pixels a point or dp for the rows' covers,
 * which are smaller than 40 of them.
 */
internal const val WIDGET_COVER_SIDE = 120

private const val COVER_EXTENSION = ".jpg"
private const val THUMBNAIL_QUALITY = 0.85f
private const val MAX_THUMBNAIL_BYTES = 64L * 1024
