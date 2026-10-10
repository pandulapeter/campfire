/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.print.PrintDocument
import com.pandulapeter.campfire.presentation.ui.print.PrintPage
import com.pandulapeter.campfire.presentation.ui.print.PrintRenderer

/**
 * One page, white under a hairline border, as large as fits in the canvas less what [fit] keeps clear at its edges at a
 * zoom of 1 and the zoom of [pageView] times that otherwise, moved to the point of it [pageView] looks at; the canvas cuts
 * off whatever of it reaches past its edges.
 */
@Composable
internal fun PrintPageCanvas(
    modifier: Modifier,
    document: PrintDocument,
    page: PrintPage,
    renderer: PrintRenderer,
    fit: PageFit,
    pageView: PageView,
    description: String,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier
            .clipToBounds()
            // A picture of a page, named by its number: its texts are in layout order, chord after lyric fragment after
            // padding, which read aloud is noise, and the song itself is readable in the viewer.
            .semantics {
                contentDescription = description
                role = Role.Image
            },
    ) {
        val area = fit.area(size, this, layoutDirection)
        val bounds = pageBounds(size, area, document.width / document.height, pageView)
        clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
            drawRect(Color.White, bounds.topLeft, bounds.size)
            translate(bounds.left, bounds.top) { renderer.draw(this, page, bounds.width / document.width) }
        }
        val borderWidth = 1.dp.toPx()
        drawRect(
            color = borderColor,
            topLeft = bounds.topLeft + Offset(borderWidth / 2f, borderWidth / 2f),
            size = Size(bounds.width - borderWidth, bounds.height - borderWidth),
            style = Stroke(borderWidth),
        )
    }
}
