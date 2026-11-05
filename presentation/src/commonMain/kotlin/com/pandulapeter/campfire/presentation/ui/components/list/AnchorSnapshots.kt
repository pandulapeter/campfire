/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.list

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo

/**
 * What every item a [ListAnchor] narrates last drew, so that the items a change pushes off screen can still be drawn
 * travelling off it after the grid has stopped drawing them ([ListAnchor.animateFrom]). A lazy grid composes only what
 * it shows, and by the time it has measured a change the items that left are no longer drawn, so what they looked like
 * has to be kept beforehand: each item draws through a layer of its own. A layer a change holds is the change's until
 * it lets go: an item that leaves or is reused for another key meanwhile draws through a new one. Where an item is, is
 * asked only as a change starts, so scrolling costs nothing.
 */
internal class AnchorSnapshots {
    private val nodes = mutableMapOf<Any, AnchorSnapshotNode>()
    private val holdCounts = mutableMapOf<GraphicsLayer, Int>()
    private var graphicsContext: GraphicsContext? = null
    private var overlay: AnchorDeparturesNode? = null

    fun attach(node: AnchorSnapshotNode) {
        nodes[node.key] = node
    }

    fun detach(node: AnchorSnapshotNode) {
        if (nodes[node.key] === node) nodes.remove(node.key)
        node.takeLayer()?.let { if (it !in holdCounts) graphicsContext?.releaseGraphicsLayer(it) }
    }

    fun createLayer(graphicsContext: GraphicsContext): GraphicsLayer {
        this.graphicsContext = graphicsContext
        return graphicsContext.createGraphicsLayer()
    }

    fun attachOverlay(node: AnchorDeparturesNode?) {
        overlay = node
    }

    /**
     * Keeps what the items with [keys] last drew until [release], with where each is drawn in the overlay
     * ([drawingAnchorDepartures]), taken before the grid measures the change: the positions include whatever a running
     * change still moves them by, which is where the eye last saw them.
     */
    fun hold(keys: Collection<Any>): Map<Any, AnchorSnapshot> {
        val overlayCoordinates = overlay?.takeIf { it.isAttached }?.requireLayoutCoordinates() ?: return emptyMap()
        return keys.mapNotNull { key ->
            val node = nodes[key]?.takeIf { it.isAttached } ?: return@mapNotNull null
            val layer = node.layer ?: return@mapNotNull null
            holdCounts[layer] = (holdCounts[layer] ?: 0) + 1
            key to AnchorSnapshot(key = key, layer = layer, position = overlayCoordinates.localPositionOf(node.requireLayoutCoordinates(), Offset.Zero))
        }.toMap()
    }

    /** Lets go of what [hold] kept, releasing every layer no item draws through any more. */
    fun release(snapshots: Collection<AnchorSnapshot>) = snapshots.forEach { snapshot ->
        val count = (holdCounts[snapshot.layer] ?: return@forEach) - 1
        if (count > 0) {
            holdCounts[snapshot.layer] = count
        } else {
            holdCounts.remove(snapshot.layer)
            if (nodes[snapshot.key]?.layer !== snapshot.layer) graphicsContext?.releaseGraphicsLayer(snapshot.layer)
        }
    }
}

/** What the item with [key] last drew ([layer]), and where in the overlay ([position]). */
internal class AnchorSnapshot(val key: Any, val layer: GraphicsLayer, val position: Offset)

/** Draws the item with [key] through a layer of its own, kept in [snapshots]. */
internal fun Modifier.anchorSnapshot(snapshots: AnchorSnapshots, key: Any) = this then AnchorSnapshotElement(snapshots, key)

/**
 * Draws the items a running change of [anchor] pushes off screen over the grid this modifies, which is where their
 * positions are taken from ([AnchorSnapshots.hold]).
 */
internal fun Modifier.drawingAnchorDepartures(anchor: ListAnchor, listState: LazyGridState) = this then AnchorDeparturesElement(anchor, listState)

private data class AnchorSnapshotElement(val snapshots: AnchorSnapshots, val key: Any) : ModifierNodeElement<AnchorSnapshotNode>() {
    override fun create() = AnchorSnapshotNode(snapshots, key)

    override fun update(node: AnchorSnapshotNode) = node.update(snapshots, key)

    override fun InspectorInfo.inspectableProperties() {
        name = "anchorSnapshot"
    }
}

/** Draws its item through [layer], made on its first draw and again after [takeLayer]. */
internal class AnchorSnapshotNode(private var snapshots: AnchorSnapshots, key: Any) : Modifier.Node(), DrawModifierNode {
    var key = key
        private set
    var layer: GraphicsLayer? = null
        private set

    override fun onAttach() = snapshots.attach(this)

    override fun onDetach() = snapshots.detach(this)

    fun update(snapshots: AnchorSnapshots, key: Any) {
        if (snapshots === this.snapshots && key == this.key) return
        if (isAttached) this.snapshots.detach(this)
        this.snapshots = snapshots
        this.key = key
        if (isAttached) snapshots.attach(this)
    }

    fun takeLayer() = layer.also { layer = null }

    override fun ContentDrawScope.draw() {
        val layer = layer ?: snapshots.createLayer(requireGraphicsContext()).also { layer = it }
        layer.record { this@draw.drawContent() }
        drawLayer(layer)
    }
}

private data class AnchorDeparturesElement(val anchor: ListAnchor, val listState: LazyGridState) : ModifierNodeElement<AnchorDeparturesNode>() {
    override fun create() = AnchorDeparturesNode(anchor, listState)

    override fun update(node: AnchorDeparturesNode) = node.update(anchor, listState)

    override fun InspectorInfo.inspectableProperties() {
        name = "drawingAnchorDepartures"
    }
}

internal class AnchorDeparturesNode(private var anchor: ListAnchor, private var listState: LazyGridState) : Modifier.Node(), DrawModifierNode {
    override fun onAttach() = anchor.snapshots.attachOverlay(this)

    override fun onDetach() = anchor.snapshots.attachOverlay(null)

    fun update(anchor: ListAnchor, listState: LazyGridState) {
        if (anchor !== this.anchor && isAttached) {
            this.anchor.snapshots.attachOverlay(null)
            anchor.snapshots.attachOverlay(this)
        }
        this.anchor = anchor
        this.listState = listState
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        with(anchor) { drawDepartures(listState) }
    }
}
