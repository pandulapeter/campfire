/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp

/**
 * [screen] in a device frame of [kind], its screen [screenWidth] wide. Every measurement is a share of the screen's
 * longer side, as in the promotional video's frames, so a frame looks the same at any size: a phone's bezel is 1.8% of
 * its height, a tablet's 3%, and a foldable opened flat is a phone held on its side, as thin all round. [isLight] draws the light frame, which a shot in the light theme takes so that the frame
 * does not outweigh the screen.
 */
@Composable
internal fun DeviceFrame(
    modifier: Modifier = Modifier,
    kind: FrameKind,
    screen: ImageBitmap,
    screenWidth: Dp,
    isLight: Boolean,
) {
    val screenHeight = screenWidth * screen.height / screen.width
    val long = maxOf(screenWidth, screenHeight)
    val bezel = long * kind.bezel
    val body = if (isLight) Color(0xFFD9D5E2) else Color(0xFF0A090D)
    val rim = if (isLight) Color(0xFFBDB7C9) else Color(0xFF2E2A38)
    val outer = when (kind) {
        FrameKind.LAPTOP -> RoundedCornerShape(topStart = long * 0.03f, topEnd = long * 0.03f, bottomStart = long * 0.008f, bottomEnd = long * 0.008f)
        else -> RoundedCornerShape(long * kind.outerRadius)
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .background(rim, outer)
                .padding(long * RIM)
                .background(body, outer)
                .padding(bezel),
        ) {
            Image(
                bitmap = screen,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.size(screenWidth, screenHeight).clip(RoundedCornerShape(long * kind.innerRadius)),
            )
        }
        if (kind == FrameKind.LAPTOP) {
            LaptopBase(width = screenWidth + (bezel + long * RIM) * 2, long = long, isLight = isLight)
        }
    }
}

/** The front edge of a laptop's base, wider than its lid, which it stands on, lit from above in the lid's own color. */
@Composable
private fun LaptopBase(width: Dp, long: Dp, isLight: Boolean) = Box(
    modifier = Modifier
        .requiredSize(width = width * 1.12f, height = long * 0.035f)
        .background(
            brush = Brush.verticalGradient(if (isLight) listOf(Color(0xFFE6E3EC), Color(0xFFBDB7C9)) else listOf(Color(0xFF2E2A38), Color(0xFF0A090D))),
            shape = RoundedCornerShape(bottomStart = long * 0.03f, bottomEnd = long * 0.03f),
        ),
)

/** How much of a frame's height [DeviceFrame] adds below its lid, as a share of the screen's longer side. */
internal val FrameKind.baseHeight get() = if (this == FrameKind.LAPTOP) 0.035f else 0f

internal val FrameKind.bezel
    get() = when (this) {
        FrameKind.PHONE, FrameKind.FOLDABLE -> 0.018f
        FrameKind.TABLET -> 0.03f
        FrameKind.LAPTOP -> 0.022f
    }

private val FrameKind.outerRadius
    get() = when (this) {
        FrameKind.PHONE, FrameKind.FOLDABLE -> 0.075f
        FrameKind.TABLET -> 0.045f
        FrameKind.LAPTOP -> 0.03f
    }

private val FrameKind.innerRadius
    get() = when (this) {
        FrameKind.PHONE, FrameKind.FOLDABLE -> 0.062f
        FrameKind.TABLET -> 0.022f
        FrameKind.LAPTOP -> 0.006f
    }

/** The thin rim around the bezel, 3 pixels on the video's phone of 1,180. */
internal const val RIM = 0.0026f
