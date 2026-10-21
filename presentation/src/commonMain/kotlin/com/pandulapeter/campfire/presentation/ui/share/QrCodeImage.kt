/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * [code] drawn black on a white card whatever the theme, the quiet zone a reader needs around the symbol being the card's
 * own margin: a camera reads dark modules on a light ground, and a dark theme's inverted symbol is one many do not.
 */
@Composable
internal fun QrCodeImage(
    modifier: Modifier = Modifier,
    code: QrCode,
    contentDescription: String,
) = Canvas(
    modifier
        .aspectRatio(1f)
        .background(Color.White, RoundedCornerShape(12.dp))
        .semantics { this.contentDescription = contentDescription },
) {
    val modules = code.size + 2 * QUIET_ZONE
    // Whole pixels per module where the card allows, and a symbol starting on a whole pixel, since a module drawn across a
    // pixel's edge blurs into its neighbour and two dark ones side by side show a hairline seam between them.
    val moduleSize = floor(size.minDimension / modules).coerceAtLeast(1f)
    val origin = Offset(floor((size.width - moduleSize * code.size) / 2), floor((size.height - moduleSize * code.size) / 2))
    for (y in 0 until code.size) {
        for (x in 0 until code.size) {
            if (code.isDark(x, y)) {
                drawRect(Color.Black, topLeft = origin + Offset(x * moduleSize, y * moduleSize), size = Size(moduleSize, moduleSize))
            }
        }
    }
}

/** The modules of white a reader needs on every side of a symbol, as the saved picture has them. */
private const val QUIET_ZONE = 4
