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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.em
import com.pandulapeter.campfire.presentation.ui.theme.interFontFamily

/**
 * One image of a store listing, drawn at a density of one so that a dp is a pixel of it: the app's purple running into
 * its icon's orange, [headline] across the top, and [screen] in its [frame] in the room left below, as large as fits.
 * The headline is set as the promotional video's are: Inter Regular in white, letters 5% closer, lines 0.9 apart, and
 * its lines balanced where a translation still wraps, so that no word is left alone on a line of its own. [chips] name
 * what the screen shows ([ListingChips]), at 80% of the headline's size on every canvas, so that both read as the same
 * size from one device's images to the next. Room for half of a chip of two lines is kept above and below the frame
 * whether a shot has chips there or not, so that every image of a row has its device at the same size and place.
 */
@Composable
internal fun ListingImage(
    headline: String,
    frame: FrameKind,
    screen: ImageBitmap,
    isLight: Boolean,
    chips: List<ListingChip>,
) = BoxWithConstraints(
    modifier = Modifier
        .fillMaxSize()
        .background(Brush.linearGradient(listOf(BRAND_PURPLE, BRAND_ORANGE), start = Offset.Zero, end = Offset.Infinite)),
) {
    val fontSize = minOf(maxWidth * 0.072f, maxHeight * 0.053f)
    val margin = minOf(maxWidth, maxHeight) * 0.05f
    val headlineHeight = fontSize * 3.2f
    val chipFontSize = fontSize * 0.8f
    val chipRoom = chipFontSize * CHIP_HALF_HEIGHT
    val aspect = screen.width.toFloat() / screen.height
    val screenWidth = fittedScreenWidth(
        kind = frame,
        aspect = aspect,
        roomWidth = maxWidth - margin * 2,
        roomHeight = maxHeight - headlineHeight - chipRoom - maxOf(margin, chipRoom),
    )
    val frameWidth = screenWidth * frameWidthPerScreenWidth(kind = frame, aspect = aspect)
    val frameHeight = screenWidth * frameHeightPerScreenWidth(kind = frame, aspect = aspect)
    val frameLeft = (maxWidth - frameWidth) / 2
    val frameTop = headlineHeight + chipRoom
    Box(
        modifier = Modifier.fillMaxWidth().height(headlineHeight).padding(horizontal = margin),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = headline,
            style = TextStyle(
                color = Color.White,
                fontFamily = interFontFamily(),
                fontWeight = FontWeight.Normal,
                fontSize = with(LocalDensity.current) { fontSize.toSp() },
                letterSpacing = (-0.05).em,
                lineHeight = 0.9.em,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                lineBreak = LineBreak.Heading,
                textAlign = TextAlign.Center,
            ),
        )
    }
    DeviceFrame(
        modifier = Modifier.offset(x = frameLeft, y = frameTop).width(frameWidth),
        kind = frame,
        screen = screen,
        screenWidth = screenWidth,
        isLight = isLight,
    )
    ListingChips(
        modifier = Modifier.fillMaxSize(),
        chips = chips,
        fontSize = chipFontSize,
        frame = DpRect(left = frameLeft, top = frameTop, right = frameLeft + frameWidth, bottom = frameTop + frameHeight),
        inset = margin * 0.3f,
        // The chips take the other theme than the screen's, so that they stand apart from the app's own controls.
        isLight = !isLight,
    )
}

/**
 * The width of the largest screen of [aspect] whose frame of [kind] fits in [roomWidth] by [roomHeight]: the frame adds
 * its bezel and rim on every side (and a laptop its base below), each a share of the screen's longer side.
 */
internal fun fittedScreenWidth(kind: FrameKind, aspect: Float, roomWidth: Dp, roomHeight: Dp) = minOf(
    roomWidth / frameWidthPerScreenWidth(kind = kind, aspect = aspect),
    roomHeight / frameHeightPerScreenWidth(kind = kind, aspect = aspect),
)

/** How wide the whole frame of [kind] is per unit of its screen's width: a laptop's base is 12% wider than its lid. */
internal fun frameWidthPerScreenWidth(kind: FrameKind, aspect: Float) =
    (1f + (kind.bezel + RIM) * 2 * longPerWidth(aspect)) * if (kind == FrameKind.LAPTOP) 1.12f else 1f

/** How tall the whole frame of [kind] is per unit of its screen's width, a laptop's base included. */
internal fun frameHeightPerScreenWidth(kind: FrameKind, aspect: Float) =
    1f / aspect + ((kind.bezel + RIM) * 2 + kind.baseHeight) * longPerWidth(aspect)

/** The screen's longer side per unit of its width, which every measurement of a frame is a share of. */
private fun longPerWidth(aspect: Float) = if (aspect >= 1f) 1f else 1f / aspect

/** Half the height of a chip of two lines, in its text's size: its two lines, its padding, and the slack of its tilt. */
private const val CHIP_HALF_HEIGHT = 1.6f

/** The app's purple and its icon's orange, which every image of the stores, the README, and the website runs between. */
internal val BRAND_PURPLE = Color(0xFF5A49CA)

internal val BRAND_ORANGE = Color(0xFFF57C00)
