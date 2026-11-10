/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which page width a song's tunes are engraved for: the width the layout placed them at. A staff is never drawn wider
 * than it was engraved, only narrower (scaled down, its words with it), so a tune engraved for the widest column and
 * set in a narrower one came out smaller than the lyrics beside it. Which column the layout gives a tune depends on its
 * height, which depends on the width it was engraved for, so it is engraved first for the narrowest column the page can
 * have, where it is never scaled down, and then again for the width each tune's first system is placed at (the
 * narrowest of them, where tunes sit in columns of different widths), until the layout places it where it was engraved.
 *
 * Engraved for one width, a tune looks shorter at a narrower one (scaled down) than it will be once engraved for it, so
 * the layout can swing between two widths, each one's engraving placing it at the other. A width placed at that was
 * engraved for before, since the page's width or the text size last changed, ends that: the tunes go back to the
 * narrowest column's width and stay there, the one engraving that suits every column.
 *
 * Main thread only. What the layout reports is state, so a new width composes the request again; the rest is the
 * bookkeeping of one composition after another, and gives the same answer however often a composition repeats.
 */
internal class NotationPlacement {

    private var placedWidths by mutableStateOf(emptyMap<AbcTune, Int>())
    private var context: Any? = null
    private val engravedWidths = mutableSetOf<Int>()
    private var lastWidth: Int? = null
    private var isSettled = false

    /** Whether the last [widthFor] knew nothing of where the tunes go, and answered the narrowest column only for that. */
    var isGuessing = false
        private set

    /** Where the layout placed [tune]'s first system: a column [widthPx] pixels wide. */
    fun place(tune: AbcTune, widthPx: Int) {
        if (placedWidths[tune] != widthPx) placedWidths = placedWidths + (tune to widthPx)
    }

    /**
     * The page width, in SVG units of [unitSize] pixels, to engrave [tunes] for on a page [availableWidth] pixels wide
     * whose narrowest column holds [narrowestWidth] units, starting from [rememberedWidth] (what the song was last placed
     * at, on this page at this size) where nothing has been placed yet.
     */
    fun widthFor(tunes: Set<AbcTune>, narrowestWidth: Int, availableWidth: Int, unitSize: Float, rememberedWidth: Int?): Int {
        val context = Triple(narrowestWidth, availableWidth, unitSize)
        if (context != this.context) {
            this.context = context
            engravedWidths.clear()
            isSettled = false
            lastWidth = rememberedWidth
        }
        val placed = placedWidths.filterKeys { it in tunes }.values.minOrNull()?.let { (it / unitSize).toInt() }?.coerceAtLeast(narrowestWidth)
        isGuessing = placed == null && lastWidth == null && !isSettled
        val width = when {
            isSettled -> narrowestWidth
            placed == null -> lastWidth ?: narrowestWidth
            placed != lastWidth && placed in engravedWidths -> {
                isSettled = true
                narrowestWidth
            }
            else -> placed
        }
        engravedWidths += width
        lastWidth = width
        return width
    }
}
