/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import com.pandulapeter.campfire.presentation.ui.notation.AbcTune
import com.pandulapeter.campfire.presentation.ui.notation.NotationDrawing
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The tunes of one export's source as [printNotationsOf] engraved them, by width and by song file and tune: the drawing,
 * or null for a tune that cannot be rendered, so it is not tried again. An engraving reads nothing else the options
 * change, so an option step that keeps the column's width engraves nothing. Only the last [KEPT_WIDTHS] widths are kept
 * (the one laid out and the one a step back returns to), each dropped whole, since each holds a drawing of every tune
 * of what may be a songbook. Used from the main thread only, like the producer that holds it.
 */
internal class PrintEngravings {
    private val byWidth = LinkedHashMap<Int, MutableMap<Pair<String, AbcTune>, NotationDrawing?>>()

    /**
     * The engraving of [tune] in the song file [fileName] at [width], from [render] the first time it is asked for.
     * A result is recorded only while the caller is still active: the producer is cancelled on every option step, and
     * a null from an engine call interrupted by that is not a tune that cannot be rendered.
     */
    suspend fun drawingOf(width: Int, fileName: String, tune: AbcTune, render: suspend () -> NotationDrawing?): NotationDrawing? {
        val key = fileName to tune
        val drawings = byWidth.remove(width) ?: mutableMapOf()
        byWidth[width] = drawings
        while (byWidth.size > KEPT_WIDTHS) byWidth.remove(byWidth.keys.first())
        if (key in drawings) return drawings[key]
        val drawing = render()
        currentCoroutineContext().ensureActive()
        drawings[key] = drawing
        return drawing
    }

    private companion object {
        const val KEPT_WIDTHS = 2
    }
}
