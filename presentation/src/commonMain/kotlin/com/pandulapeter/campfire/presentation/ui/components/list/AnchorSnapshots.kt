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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo

/**
 * What every item a [ListAnchor] narrates last drew, and where, so that the items a change pushes off screen can still
 * be drawn travelling off it after the grid has stopped drawing them ([ListAnchor.animateFrom]). A lazy grid composes
 * only what it shows, and by the time it has measured a change the items that left are no longer drawn, so what they
 * looked like has to be kept beforehand: each item draws through a layer of its own, kept while the item is attached,
 * and after that for as long as a change holds it.
 */
internal class AnchorSnapshots {
    private val entries = mutableMapOf<Any, Entry>()
    private var graphicsContext: GraphicsContext? = null
    private var overlayCoordinates: LayoutCoordinates? = null

    private class Entry(val layer: GraphicsLayer) {
        var coordinates: LayoutCoordinates? = null
        var isAttached = false
        var holdCount = 0
    }

    fun attach(key: Any, graphicsContext: GraphicsContext) {
        this.graphicsContext = graphicsContext
        entries.getOrPut(key) { Entry(graphicsContext.createGraphicsLayer()) }.isAttached = true
    }

    fun detach(key: Any) {
        val entry = entries[key] ?: return
        entry.isAttached = false
        entry.coordinates = null
        releaseIfUnused(key, entry)
    }

    fun layerOf(key: Any) = entries[key]?.layer

    fun place(key: Any, coordinates: LayoutCoordinates) {
        entries[key]?.coordinates = coordinates
    }

    fun placeOverlay(coordinates: LayoutCoordinates) {
        overlayCoordinates = coordinates
    }

    /**
     * Keeps the layers of the items with [keys] that are attached until [release], with where each was drawn in the
     * overlay ([drawingAnchorDepartures]), taken before the grid measures the change: the positions include whatever a
     * running change still moves them by, which is where the eye last saw them.
     */
    fun hold(keys: Collection<Any>): Map<Any, AnchorSnapshot> {
        val overlay = overlayCoordinates?.takeIf { it.isAttached } ?: return emptyMap()
        return keys.mapNotNull { key ->
            val entry = entries[key] ?: return@mapNotNull null
            val coordinates = entry.coordinates?.takeIf { it.isAttached } ?: return@mapNotNull null
            entry.holdCount++
            key to AnchorSnapshot(layer = entry.layer, position = overlay.localPositionOf(coordinates, Offset.Zero))
        }.toMap()
    }

    fun release(keys: Collection<Any>) = keys.forEach { key ->
        val entry = entries[key] ?: return@forEach
        entry.holdCount--
        releaseIfUnused(key, entry)
    }

    private fun releaseIfUnused(key: Any, entry: Entry) {
        if (entry.isAttached || entry.holdCount > 0) return
        entries.remove(key)
        graphicsContext?.releaseGraphicsLayer(entry.layer)
    }
}

/** What an item last drew ([layer]), and where in the overlay ([position]). */
internal class AnchorSnapshot(val layer: GraphicsLayer, val position: Offset)

/** Draws the item with [key] through its layer in [snapshots], and tells them where it is. */
internal fun Modifier.anchorSnapshot(snapshots: AnchorSnapshots, key: Any) = this then AnchorSnapshotElement(snapshots, key)

/**
 * Draws the items a running change of [anchor] pushes off screen over the grid this modifies, which is where their
 * positions are taken from ([AnchorSnapshots.hold]).
 */
internal fun Modifier.drawingAnchorDepartures(anchor: ListAnchor, listState: LazyGridState) =
    this then AnchorDeparturesElement(anchor, listState)

private data class AnchorSnapshotElement(val snapshots: AnchorSnapshots, val key: Any) : ModifierNodeElement<AnchorSnapshotNode>() {
    override fun create() = AnchorSnapshotNode(snapshots, key)

    override fun update(node: AnchorSnapshotNode) = node.update(snapshots, key)

    override fun InspectorInfo.inspectableProperties() {
        name = "anchorSnapshot"
    }
}

private class AnchorSnapshotNode(private var snapshots: AnchorSnapshots, private var key: Any) : Modifier.Node(), DrawModifierNode, LayoutAwareModifierNode {
    override fun onAttach() = snapshots.attach(key, requireGraphicsContext())

    override fun onDetach() = snapshots.detach(key)

    fun update(snapshots: AnchorSnapshots, key: Any) {
        if (snapshots === this.snapshots && key == this.key) return
        if (isAttached) this.snapshots.detach(this.key)
        this.snapshots = snapshots
        this.key = key
        if (isAttached) snapshots.attach(key, requireGraphicsContext())
    }

    override fun onPlaced(coordinates: LayoutCoordinates) = snapshots.place(key, coordinates)

    override fun ContentDrawScope.draw() {
        val layer = snapshots.layerOf(key) ?: return drawContent()
        layer.record { this@draw.drawContent() }
        drawLayer(layer)
    }
}

private data class AnchorDeparturesElement(
    val anchor: ListAnchor,
    val listState: LazyGridState,
) : ModifierNodeElement<AnchorDeparturesNode>() {
    override fun create() = AnchorDeparturesNode(anchor, listState)

    override fun update(node: AnchorDeparturesNode) {
        node.anchor = anchor
        node.listState = listState
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "drawingAnchorDepartures"
    }
}

private class AnchorDeparturesNode(
    var anchor: ListAnchor,
    var listState: LazyGridState,
) : Modifier.Node(), DrawModifierNode, LayoutAwareModifierNode {
    override fun onPlaced(coordinates: LayoutCoordinates) = anchor.snapshots.placeOverlay(coordinates)

    override fun ContentDrawScope.draw() {
        drawContent()
        with(anchor) { drawDepartures(listState) }
    }
}
