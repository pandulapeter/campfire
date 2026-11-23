/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chrome

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color

/**
 * Draws a dialog's scrim over everything this draws, as dark as [coverage] says it is covered, for the deck the export
 * screen slides over by hand. Clamped, since the spring it follows can overshoot either end. The destinations' own
 * transitions darken the screen underneath with a veil of [coveredScreenScrimColor] instead.
 */
internal fun Modifier.coveredScreenScrim(color: Color, coverage: () -> Float) = drawWithContent {
    drawContent()
    val alpha = coverage().coerceIn(0f, 1f) * COVERED_SCREEN_SCRIM_ALPHA
    if (alpha > 0f) {
        drawRect(color = color, alpha = alpha)
    }
}

/** What a fully covered screen is darkened to: Material's scrim behind a dialog or a modal sheet. */
internal fun coveredScreenScrimColor(scrim: Color) = scrim.copy(alpha = COVERED_SCREEN_SCRIM_ALPHA)

private const val COVERED_SCREEN_SCRIM_ALPHA = 0.32f
