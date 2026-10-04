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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Holds a visible card in screen coordinates only while the search owns the scroll position. A scroll takes the position
 * over, but the room kept at the end of the list ([trailingHeaderCount]) outlives the anchoring until it has left the
 * screen, or the search changes what it was kept for: dropping it while it is in sight pulls the end of the list up
 * under the finger.
 */
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
    private var trailingSpaceContents: Any? = null
    /**
     * State rather than a plain field, since the spacer at the end of the list reads it while it is measured: the room
     * being released has to measure the spacer again, or it would stay at the end of the results. It is kept after a
     * scroll lets the anchor go, until [releaseTrailingSpaceIfOutOfSight] finds the spacer off the screen, the results
     * change or the search is opened or closed.
     */
    var trailingHeaderCount by mutableIntStateOf(0)
        private set

    fun update(open: Boolean, contents: Any, isScrolling: Boolean, inset: Int, capture: () -> Snapshot?) {
        // A scroll may already be running when the mode changes: its start notification cannot cancel an anchor
        // that did not exist yet. Consume the mode change without capturing, so becoming idle cannot arm it later.
        // New results or the search being toggled bring the headers back or change the list, so the room kept at its end
        // has nothing left to keep.
        val isChange = open != isOpen || (trailingSpaceContents != null && trailingSpaceContents != contents)
        if (isScrolling) {
            letGoForScroll()
            if (isChange) releaseTrailingSpace()
            isOpen = open
            return
        }
        if (this.contents != null && this.contents != contents) cancel()
        if (index == null && isChange) releaseTrailingSpace()
        if (open == isOpen) return
        isOpen = open
        if (index == null) {
            val card = capture() ?: return
            index = card.cardIndex
            screenTop = card.cardTop + inset
            this.contents = contents
            trailingSpaceContents = contents
            trailingHeaderCount = card.trailingHeaderCount
            originalPosition = if (open) card.position else null
        }
        lastInset = null
    }

    fun positionFor(inset: Int, isScrolling: Boolean): Position? {
        // Check again at measurement time: a drag/fling can start after composition, before the observer runs.
        // Cancellation is permanent for this transition; merely skipping a frame would restore a stale position.
        if (isScrolling) {
            letGoForScroll()
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

    /** Lets a scroll take the position over, keeping the room at the end of the list; calling it again changes nothing. */
    fun letGoForScroll() {
        contents = null
        index = null
        originalPosition = null
        lastInset = null
    }

    /**
     * Releases the room kept at the end of the list once the spacer holding it is out of sight, where taking it away moves
     * nothing on screen. Only once the anchor has let go: while it still holds a position, the spacer may not have been
     * laid out yet, and releasing it then would take away the room the position needs.
     */
    fun releaseTrailingSpaceIfOutOfSight(isSpaceVisible: Boolean) {
        if (index == null && !isSpaceVisible) releaseTrailingSpace()
    }

    private fun cancel() {
        letGoForScroll()
        releaseTrailingSpace()
    }

    private fun releaseTrailingSpace() {
        trailingHeaderCount = 0
        trailingSpaceContents = null
    }
}
