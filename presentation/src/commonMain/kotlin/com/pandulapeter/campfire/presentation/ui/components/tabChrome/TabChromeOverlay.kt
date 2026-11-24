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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext

/**
 * The one node that draws the parts of two tabs being swapped over both screens ([drawnInTabChromeOverlay]), in their
 * order, each at its own place. Laid over the screens after them in the same parent. It draws each part's layer, never a
 * copy of what is in it, so a part recorded again later in the frame (the layers are updated in no particular order)
 * shows its new drawing without this node drawing again. Places are in this node's own coordinates rather than the
 * root's: the deck it shares with the screens slides under the export screen, which would otherwise move a part twice.
 */
@Composable
internal fun TabChromeOverlay(
    modifier: Modifier,
    tabChrome: TabChrome,
) {
    val placement = remember { OverlayPlacement() }
    Spacer(
        modifier = modifier
            .onPlaced { placement.coordinates = it }
            .drawBehind {
                val overlay = placement.coordinates?.takeIf { it.isAttached } ?: return@drawBehind
                tabChrome.overlayParts
                    .filter { it.isInOverlay() }
                    .sortedBy { it.zIndex() }
                    .forEach { part ->
                        val layer = part.layer ?: return@forEach
                        val coordinates = part.coordinates?.takeIf { it.isAttached } ?: return@forEach
                        val position = overlay.localPositionOf(coordinates, Offset.Zero)
                        translate(left = position.x, top = position.y) { drawLayer(layer) }
                    }
            },
    )
}

/** Where [TabChromeOverlay] is laid out, which it draws the parts relative to. */
private class OverlayPlacement {
    var coordinates: LayoutCoordinates? = null
}

/**
 * Draws this node and what follows it in the chain over both screens, at [zIndex] among the other parts, through
 * [TabChromeOverlay] while [isInOverlay], instead of in place: its drawing is recorded into a layer of its own there,
 * which the overlay draws after the screens.
 */
internal fun Modifier.drawnInTabChromeOverlay(
    tabChrome: TabChrome,
    zIndex: () -> Float,
    isInOverlay: () -> Boolean,
): Modifier = this then TabChromeOverlayElement(tabChrome, zIndex, isInOverlay)

private data class TabChromeOverlayElement(
    val tabChrome: TabChrome,
    val zIndex: () -> Float,
    val isInOverlay: () -> Boolean,
) : ModifierNodeElement<TabChromeOverlayNode>() {
    override fun create() = TabChromeOverlayNode(tabChrome, zIndex, isInOverlay)

    override fun update(node: TabChromeOverlayNode) {
        node.tabChrome = tabChrome
        node.zIndex = zIndex
        node.isInOverlay = isInOverlay
    }
}

/** One part of [TabChrome.overlayParts], from attached to detached. */
internal class TabChromeOverlayNode(
    tabChrome: TabChrome,
    var zIndex: () -> Float,
    var isInOverlay: () -> Boolean,
) : Modifier.Node(),
    DrawModifierNode,
    LayoutAwareModifierNode {
    var tabChrome = tabChrome
        set(value) {
            if (field === value) return
            if (isAttached) {
                field.overlayParts.remove(this)
                value.overlayParts.add(this)
            }
            field = value
        }
    var layer: GraphicsLayer? = null
        private set
    var coordinates: LayoutCoordinates? = null
        private set

    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer()
        tabChrome.overlayParts.add(this)
    }

    override fun onDetach() {
        tabChrome.overlayParts.remove(this)
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
        coordinates = null
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    override fun ContentDrawScope.draw() {
        val layer = layer?.takeIf { isInOverlay() }
        if (layer == null) {
            drawContent()
        } else {
            layer.record { this@draw.drawContent() }
        }
    }
}
