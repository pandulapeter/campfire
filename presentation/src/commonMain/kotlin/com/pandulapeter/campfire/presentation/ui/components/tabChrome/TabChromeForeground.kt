/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.tabChrome

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.LayoutDirection

/**
 * Draws this node over both screens while their tabs are swapped, fading as its screen does: what is drawn on top of a
 * part of [chrome] whose node holds none of it (the search pill's buttons and field, laid out by the bar around the
 * pill's node, see [sharedTabChromeSurface]). A no-op without [chrome].
 *
 * What stands on the part ([partBounds], in this node's own coordinates, never mirrored) rides it as one: moved with the
 * travelling rectangle ([SharedTabChrome.travellingBounds], in the coordinates of the part's node, which is laid out at
 * this one's origin), its start edge on the rectangle's start edge, the way the field's content rides its start edge as
 * the search opens and closes, and clipped to the rectangle's stadium, so the open field's text never hangs past a pill
 * narrowing into the closed one, nor a closed pill's buttons wait outside one still on its way to them; it crossfades
 * with the other tab's ([SharedTabChrome.crossfadeAlpha]). While the part is alone it fades with its screen instead, as
 * does what stands off the part (an open search's close button and the actions beside the field), which stays where it is
 * laid out.
 */
internal fun Modifier.tabChromeForeground(
    chrome: SharedTabChrome?,
    zIndex: Float,
    partBounds: () -> Rect,
): Modifier = if (chrome == null) this else this@tabChromeForeground
        .drawnInTabChromeOverlay(tabChrome = chrome.tabChrome, zIndex = { zIndex }, isInOverlay = { chrome.isSwapping })
        .drawWithContent {
            val part = partBounds()
            if (!chrome.isSwapping || part.isEmpty) {
                drawContent()
            } else {
                val partOutline = stadiumOf(part)
                val counterpart = chrome.counterpart()
                val travelling = chrome.travellingBounds(counterpart)
                if (travelling == null) {
                    withAlpha(chrome.screenAlphaValue) { this@drawWithContent.drawContent() }
                } else {
                    withAlpha(chrome.screenAlphaValue) {
                        clipPath(path = partOutline, clipOp = ClipOp.Difference) { this@drawWithContent.drawContent() }
                    }
                    withAlpha(chrome.crossfadeAlpha) {
                        this@drawWithContent.drawRidingOn(part = part, partOutline = partOutline, travelling = travelling)
                    }
                }
            }
        }

/** Draws what stands on [part] moved onto [travelling], its start edge on the other's, and clipped to both. */
private fun ContentDrawScope.drawRidingOn(part: Rect, partOutline: Path, travelling: Rect) {
    val left = if (layoutDirection == LayoutDirection.Rtl) travelling.right - part.right else travelling.left - part.left
    clipPath(path = stadiumOf(travelling)) {
        translate(left = left, top = travelling.center.y - part.center.y) {
            clipPath(path = partOutline) { this@drawRidingOn.drawContent() }
        }
    }
}
