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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_campfire
import com.pandulapeter.campfire.presentation.ui.theme.interFontFamily
import org.jetbrains.compose.resources.painterResource

/**
 * [composition] drawn at a density of its scale, so that a dp is a pixel of it as laid out: its background, then its
 * elements back to front, a screen showing the render of its slot in [screens] in a light frame where [lightSlots] has
 * its slot.
 */
@Composable
internal fun CompositionImage(
    composition: Composition,
    screens: Map<Int, ImageBitmap>,
    lightSlots: Set<Int>,
) = Box(modifier = Modifier.fillMaxSize().drawBehind { drawRect(composition.background.brush(size.width, size.height)) }) {
    composition.elements.forEach { element ->
        Box(
            modifier = Modifier.offset(element.x.dp, element.y.dp).requiredSize(element.width.dp, element.height.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (element) {
                is Composition.Screen -> {
                    val screen = screens.getValue(element.slot)
                    val aspect = screen.width.toFloat() / screen.height
                    DeviceFrame(
                        modifier = Modifier.rotate(element.rotation),
                        kind = element.kind,
                        screen = screen,
                        screenWidth = minOf(
                            element.width / frameWidthPerScreenWidth(kind = element.kind, aspect = aspect),
                            element.height / frameHeightPerScreenWidth(kind = element.kind, aspect = aspect),
                        ).dp,
                        isLight = element.slot in lightSlots,
                    )
                }
                is Composition.Logo -> Image(
                    painter = painterResource(Res.drawable.ic_campfire),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                is Composition.Caption -> BasicText(
                    text = element.text,
                    style = TextStyle(
                        color = Color.White,
                        fontFamily = interFontFamily(),
                        fontWeight = FontWeight.Normal,
                        fontSize = with(LocalDensity.current) { element.size.dp.toSp() },
                        letterSpacing = (-0.05).em,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}

/** The background's brush on a canvas of [width] by [height]: a radial one reaches past the middle of its longer side. */
private fun Composition.Background.brush(width: Float, height: Float) = when (this) {
    Composition.Background.LINEAR -> Brush.linearGradient(listOf(BRAND_PURPLE, BRAND_ORANGE), start = Offset.Zero, end = Offset(width, height))
    Composition.Background.RADIAL_PURPLE -> radial(BRAND_PURPLE, BRAND_ORANGE, width, height)
    Composition.Background.RADIAL_ORANGE -> radial(BRAND_ORANGE, BRAND_PURPLE, width, height)
}

private fun radial(middle: Color, edge: Color, width: Float, height: Float) =
    Brush.radialGradient(listOf(middle, edge), center = Offset(width / 2, height / 2), radius = maxOf(width, height) * 0.6f)
