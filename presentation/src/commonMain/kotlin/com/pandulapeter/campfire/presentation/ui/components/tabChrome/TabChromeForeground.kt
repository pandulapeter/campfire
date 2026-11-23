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

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.LayoutDirection

/**
 * Draws this node over both screens while their tabs are swapped, fading as its screen does: what is drawn on top of a
 * part of [chrome] whose node holds none of it (the search pill's buttons and field, laid out by the bar around the
 * pill's node). A no-op without [chrome].
 *
 * What stands on the part ([partBounds], in this node's own coordinates, never mirrored) rides it as one: moved with the
 * travelling rectangle ([travellingBounds], the same coordinates), its start edge on the rectangle's start edge, the way
 * the field's content rides its start edge as the search opens and closes, and clipped to the rectangle's stadium, so
 * the open field's text never hangs past a pill narrowing into the closed one, nor a closed pill's buttons wait outside
 * one still on its way to them; it crosses with the other tab's as the part's own icons do. While the part is alone it
 * scales and fades with it instead. What stands off the part (an open
 * search's close button and the actions beside the field) stays where it is laid out and only fades.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun Modifier.tabChromeForeground(
    chrome: SharedTabChrome?,
    zIndex: Float,
    partBounds: () -> Rect,
    travellingBounds: () -> Rect?,
): Modifier = if (chrome == null) this else with(chrome.sharedTransitionScope) {
    this@tabChromeForeground
        .renderInSharedTransitionScopeOverlay(zIndexInOverlay = zIndex, renderInOverlay = { chrome.isSwapping })
        .drawWithContent {
            val part = partBounds()
            if (!chrome.isSwapping || part.isEmpty) {
                drawContent()
            } else {
                val partOutline = stadiumOf(part)
                withAlpha(chrome.foregroundAlpha) {
                    clipPath(path = partOutline, clipOp = ClipOp.Difference) { this@drawWithContent.drawContent() }
                }
                val travelling = travellingBounds()
                if (chrome.isAlone || travelling == null) {
                    withAlpha(chrome.foregroundAlpha) {
                        scale(scale = chrome.aloneScale, pivot = part.center) {
                            clipPath(path = partOutline) { this@drawWithContent.drawContent() }
                        }
                    }
                } else {
                    withAlpha(chrome.matchedContentAlpha) {
                        this@drawWithContent.drawRidingOn(part = part, partOutline = partOutline, travelling = travelling)
                    }
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

/** Draws [block] as one layer [alpha] opaque, so that overlapping parts of it do not show through each other. */
private fun DrawScope.withAlpha(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha <= 0f) return
    drawContext.canvas.saveLayer(bounds = Rect(Offset.Zero, size), paint = Paint().apply { this.alpha = alpha })
    block()
    drawContext.canvas.restore()
}

private fun stadiumOf(bounds: Rect) = Path().apply { addRoundRect(RoundRect(rect = bounds, cornerRadius = CornerRadius(bounds.height / 2))) }
