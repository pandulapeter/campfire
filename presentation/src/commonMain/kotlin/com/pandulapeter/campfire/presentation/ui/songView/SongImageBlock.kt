/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_image
import com.pandulapeter.campfire.presentation.resources.song_image_titled
import com.pandulapeter.campfire.presentation.resources.song_image_view
import com.pandulapeter.campfire.presentation.ui.pictures.ImageViewer
import com.pandulapeter.campfire.presentation.ui.pictures.LocalSongImageAdder
import com.pandulapeter.campfire.presentation.ui.pictures.MissingImage
import com.pandulapeter.campfire.presentation.ui.pictures.SongImageSize
import com.pandulapeter.campfire.presentation.ui.pictures.SongPicture
import com.pandulapeter.campfire.presentation.ui.pictures.fittedImageSize
import com.pandulapeter.campfire.presentation.ui.pictures.imageLeftEdge
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The height a picture of a song may take at most, so that it is never cut and is seen whole on one page: the room the
 * page has, which [SongLyrics] provides where the song holds a picture; unspecified where nothing limits it.
 */
internal val LocalSongImageRoom = compositionLocalOf { Dp.Unspecified }

/**
 * The room a picture of [sections] is fitted to, a page being [availableHeight] tall: what a rest shows of a row, which is
 * half a [rowGap] under the row's top, leaving it the other half under it. It is then part of what every measurement
 * depends on, so it is unspecified in a song holding no picture, which keeps its measurements as the room changes.
 */
internal fun songImageRoomOf(sections: List<RenderSection>, availableHeight: Dp, rowGap: Dp) =
    if (availableHeight.isSpecified && sections.any { it is RenderSection.Image }) availableHeight - rowGap else Dp.Unspecified

/**
 * A picture of a song ([RenderSection.Image]), its room known before it is decoded (see [fittedImageSize]): asked how
 * tall it is at a width, it answers the picture fitted to that width and [LocalSongImageRoom], so the layout places it
 * right from the first frame. Selecting it opens it over the whole window. One the library lacks is a [MissingImage],
 * and one whose size is still being read takes that room, empty.
 */
@Composable
internal fun SongImageBlock(
    modifier: Modifier = Modifier,
    section: RenderSection.Image,
) = when (val size = section.size) {
    is SongImageSize.Known -> KnownImage(modifier = modifier, image = section.image, size = size)
    SongImageSize.Missing -> MissingImage(modifier = modifier, name = section.image.src, onAdd = LocalSongImageAdder.current?.let { add -> { add(section.image.src) } })
    null -> MissingImage(modifier = modifier, name = section.image.src, onAdd = null, isShown = false)
}

@Composable
private fun KnownImage(
    modifier: Modifier,
    image: ChordProBlock.Image,
    size: SongImageSize.Known,
) {
    var isViewerOpen by remember { mutableStateOf(false) }
    val description = image.title?.let { textResource(Res.string.song_image_titled, it) } ?: stringResource(Res.string.song_image)
    val room = LocalSongImageRoom.current
    Layout(
        modifier = modifier,
        content = {
            SongPicture(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClickLabel = stringResource(Res.string.song_image_view)) { isViewerOpen = true },
                name = image.src,
                contentDescription = description,
            )
        },
        measurePolicy = remember(image, size, room) { imageMeasurePolicy(image, size, room) },
    )
    if (isViewerOpen) {
        ImageViewer(
            name = image.src,
            size = size,
            contentDescription = description,
            onClose = { isViewerOpen = false },
        )
    }
}

/**
 * How a picture of [size] is measured: as tall as [fittedImageSize] makes it at any width, its least width nothing (any
 * column fits it) and its widest its own size, so a column is never widened for it.
 */
private fun imageMeasurePolicy(image: ChordProBlock.Image, size: SongImageSize.Known, room: Dp) = object : MeasurePolicy {

    private fun IntrinsicMeasureScope.fitted(width: Int) = fittedImageSize(
        image = image,
        pixelWidth = size.width,
        pixelHeight = size.height,
        unit = density,
        columnWidth = if (width == Constraints.Infinity) size.width * density else width.toFloat(),
        maxHeight = if (room.isSpecified) room.toPx() else Float.POSITIVE_INFINITY,
    )

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else ceil(size.width * density).toInt()
        val fitted = fitted(width)
        val pictureWidth = fitted.width.roundToInt()
        val pictureHeight = fitted.height.roundToInt()
        val placeable = measurables.first().measure(Constraints.fixed(pictureWidth, pictureHeight))
        return layout(width, pictureHeight.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeable.place(imageLeftEdge(image.alignment, width.toFloat(), fitted.width).roundToInt(), 0)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = ceil(size.width * density).toInt()

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = fitted(width).height.roundToInt()

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = fitted(width).height.roundToInt()
}
