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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced

/**
 * Makes this node the part [chrome] stands for, see [SharedTabChrome]: a button whose container the node draws, the
 * stadium of its height in [SharedTabChrome]'s color behind what it holds. While two tabs swap, the node is drawn over
 * both screens; matched, the container is drawn at the travelling rectangle by the copy being left, and what the node
 * holds is moved onto that rectangle's middle and clipped to it, so a circle turning into a pill keeps its icon centered
 * in it; alone, the whole node scales and fades around its middle. A no-op without [chrome].
 *
 * @param zIndex Where it is drawn among the other parts over the screens: the search pill under the bar's content under
 *   the New button. The copy being opened is drawn over the one being left, whose container is the one drawn, so that
 *   both icons stand on it.
 * @param isContainerDrawnOutsideSwaps Whether the node draws its container whenever it is shown, or only while the tabs
 *   swap, its screen drawing it otherwise (under the presses of a pill's buttons, which this node holds).
 * @param isRectangular Whether the part is clipped to its rectangle rather than to a stadium: one with no container of
 *   its own (the tools' capo, a label before a stepper), whose label a stadium's rounded ends would cut into.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun Modifier.sharedTabChrome(
    chrome: SharedTabChrome?,
    zIndex: Float,
    isContainerDrawnOutsideSwaps: Boolean,
    isRectangular: Boolean = false,
): Modifier = if (chrome == null) this else with(chrome.sharedTransitionScope) {
    this@sharedTabChrome
        // Outside the layer below, so that the rectangle is the part's as laid out, never as scaled.
        .onPlaced { chrome.coordinates = it }
        .renderInSharedTransitionScopeOverlay(
            zIndexInOverlay = zIndex + if (chrome.isEntering) ENTERING_Z_INDEX_OFFSET else 0f,
            renderInOverlay = { chrome.isSwapping },
        )
        .graphicsLayer {
            if (chrome.isAlone) {
                val scale = chrome.aloneScale
                scaleX = scale
                scaleY = scale
                alpha = chrome.aloneAlpha
            }
        }
        .drawWithContent {
            val travelling = chrome.travellingBounds()
            if (travelling == null) {
                if (isContainerDrawnOutsideSwaps || chrome.isSwapping) {
                    chrome.currentContainerColor?.let { drawRoundRect(color = it, cornerRadius = CornerRadius(size.height / 2)) }
                }
                drawContent()
            } else {
                val outline = if (isRectangular) Path().apply { addRect(travelling) } else stadiumOf(travelling)
                chrome.currentContainerColor?.let { drawPath(path = outline, color = it) }
                clipPath(path = outline) {
                    withAlpha(chrome.matchedContentAlpha) {
                        scale(scale = chrome.matchedContentScale, pivot = travelling.center) {
                            translate(left = travelling.center.x - size.width / 2, top = travelling.center.y - size.height / 2) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
                }
            }
        }
}

/**
 * Makes this node, laid over the whole of a bar, the part [chrome] stands for where the part is only a stretch of the
 * bar drawn behind its buttons (the search pill, see [rememberSharedTabChrome]'s `localBounds`): it draws nothing outside
 * a swap of two tabs, when the bar draws the pill itself, and the pill over both screens during one, at the travelling
 * rectangle while matched (by the copy being left only) and scaled around its own middle while alone. A no-op
 * without [chrome].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun Modifier.sharedTabChromeSurface(
    chrome: SharedTabChrome?,
    zIndex: Float,
    bounds: () -> Rect,
): Modifier = if (chrome == null) this else with(chrome.sharedTransitionScope) {
    this@sharedTabChromeSurface
        .onPlaced { chrome.coordinates = it }
        .renderInSharedTransitionScopeOverlay(zIndexInOverlay = zIndex, renderInOverlay = { chrome.isSwapping })
        .drawBehind {
            if (!chrome.isSwapping) return@drawBehind
            val color = chrome.currentContainerColor ?: return@drawBehind
            val travelling = chrome.travellingBounds()
            if (travelling != null) {
                drawPath(path = stadiumOf(travelling), color = color)
            } else {
                val own = bounds()
                if (own.isEmpty) return@drawBehind
                scale(scale = chrome.aloneScale, pivot = own.center) { drawPath(path = stadiumOf(own), color = color, alpha = chrome.aloneAlpha) }
            }
        }
}

/** Draws [block] as one layer [alpha] opaque, so that overlapping parts of it do not show through each other. */
internal fun DrawScope.withAlpha(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha <= 0f) return
    drawContext.canvas.saveLayer(bounds = Rect(Offset.Zero, size), paint = Paint().apply { this.alpha = alpha })
    block()
    drawContext.canvas.restore()
}

/** The outline of a part: a stadium as round as its height allows. */
internal fun stadiumOf(bounds: Rect) = Path().apply { addRoundRect(RoundRect(rect = bounds, cornerRadius = CornerRadius(bounds.height / 2))) }

/** What puts the screen being opened's copy of a part over the one being left's, both drawn in the same rectangle. */
private const val ENTERING_Z_INDEX_OFFSET = 0.1f

/** The order the parts are drawn in over both screens, see [sharedTabChrome]. */
internal const val SEARCH_PILL_Z_INDEX = 1f
internal const val SEARCH_PILL_CONTENT_Z_INDEX = 2f
internal const val NEW_BUTTON_Z_INDEX = 3f
internal const val TOOL_LINE_Z_INDEX = 4f
