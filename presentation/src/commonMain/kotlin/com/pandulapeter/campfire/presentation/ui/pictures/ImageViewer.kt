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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.ui.components.CampfireIconButton
import com.pandulapeter.campfire.presentation.ui.components.menu.LocalOverlayState
import org.jetbrains.compose.resources.painterResource
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A picture of a song over the whole window, for the small print a column cannot show: pinched or scrolled to zoom, dragged
 * to move, a double tap or click to zoom in and back out, closed by back (Escape) or the close button. Decoded at up to
 * [DECODE_SCALE] times the window, which is what zooming in shows sharp, never at the picture's full size.
 */
@Composable
internal fun ImageViewer(
    name: String,
    size: SongImageSize.Known,
    contentDescription: String,
    onClose: () -> Unit,
) = Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(usePlatformDefaultWidth = false),
) {
    // The viewer is the song view's own rather than a dialog of the view model's, so it counts itself in as an overlay for
    // as long as it is up: otherwise the app's root and the song details screen take the keyboard's focus back from it, and
    // Escape on the desktop or the browser's back button goes back from the screen behind instead of closing the picture.
    val overlayState = LocalOverlayState.current
    DisposableEffect(overlayState) {
        overlayState.onMenuOpened()
        onDispose { overlayState.onMenuClosed() }
    }
    var zoom by remember { mutableStateOf(ImageZoom()) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        val density = LocalDensity.current
        val viewport = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val fitScale = min(viewport.width / size.width, viewport.height / size.height)
        val fitted = Size(size.width * fitScale, size.height * fitScale)
        val decodeSize = IntSize(
            width = min(size.width.toFloat(), fitted.width * DECODE_SCALE).roundToInt().coerceAtLeast(1),
            height = min(size.height.toFloat(), fitted.height * DECODE_SCALE).roundToInt().coerceAtLeast(1),
        )
        SongPicture(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(viewport, fitted) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ -> zoom = zoom.zoomedBy(gestureZoom, pan, centroid, viewport, fitted) }
                }
                .pointerInput(viewport, fitted) {
                    detectTapGestures(onDoubleTap = { point -> zoom = zoom.toggled(point, viewport, fitted) })
                }
                .pointerInput(viewport, fitted) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val change = event.changes.first()
                            zoom = zoom.zoomedBy(exp(-change.scrollDelta.y * WHEEL_ZOOM), Offset.Zero, change.position, viewport, fitted)
                            change.consume()
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                },
            name = name,
            contentDescription = contentDescription,
            decodeSize = decodeSize,
        )
        CampfireIconButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp),
            icon = painterResource(Res.drawable.ic_clear),
            label = stringResource(Res.string.close),
            onClick = onClose,
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
        )
    }
}

/** How many times the window's size the picture is decoded at, which is as far as zooming in stays sharp. */
private const val DECODE_SCALE = 2f

/** How much one step of a mouse wheel zooms, as the exponent's factor of the scroll's delta. */
private const val WHEEL_ZOOM = 0.2f
