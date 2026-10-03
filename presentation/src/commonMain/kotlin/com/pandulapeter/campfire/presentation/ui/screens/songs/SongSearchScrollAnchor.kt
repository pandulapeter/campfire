/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

/** Holds a visible card in screen coordinates only while the search owns the scroll position. */
internal class SongSearchScrollAnchor(private var isOpen: Boolean) {
    data class Position(val index: Int, val offset: Int)

    data class Snapshot(
        val cardIndex: Int,
        val cardTop: Int,
        val position: Position,
        val trailingHeaderCount: Int,
    )

    private var contents: Any? = null
    private var index: Int? = null
    private var screenTop = 0
    private var originalPosition: Position? = null
    private var lastInset: Int? = null
    var trailingHeaderCount = 0
        private set

    fun update(open: Boolean, contents: Any, isScrolling: Boolean, inset: Int, capture: () -> Snapshot?) {
        // A scroll may already be running when the mode changes: its start notification cannot cancel an anchor
        // that did not exist yet. Consume the mode change without capturing, so becoming idle cannot arm it later.
        if (isScrolling) {
            cancel()
            isOpen = open
            return
        }
        if (this.contents != null && this.contents != contents) cancel()
        if (open == isOpen) return
        isOpen = open
        if (index == null) {
            val card = capture() ?: return
            index = card.cardIndex
            screenTop = card.cardTop + inset
            this.contents = contents
            trailingHeaderCount = card.trailingHeaderCount
            originalPosition = if (open) card.position else null
        }
        lastInset = null
    }

    fun positionFor(inset: Int, isScrolling: Boolean): Position? {
        // Check again at measurement time: a drag/fling can start after composition, before the observer runs.
        // Cancellation is permanent for this transition; merely skipping a frame would restore a stale position.
        if (isScrolling) {
            cancel()
            return null
        }
        val cardIndex = index ?: return null
        if (lastInset == inset) return null
        lastInset = inset
        return if (!isOpen && inset == 0 && originalPosition != null) {
            originalPosition
        } else {
            Position(cardIndex, inset - screenTop)
        }
    }

    fun cancel() {
        contents = null
        index = null
        originalPosition = null
        lastInset = null
        trailingHeaderCount = 0
    }
}
