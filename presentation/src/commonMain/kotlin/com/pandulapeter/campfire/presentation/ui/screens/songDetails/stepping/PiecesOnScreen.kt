/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping

import com.pandulapeter.campfire.presentation.ui.songView.SongPiece
import com.pandulapeter.campfire.presentation.ui.songView.SongRows

/**
 * The pieces of the song ([SongRows.pieces]) a scroll at [offset] shows on a screen [viewport] long, in the order they
 * were placed, or null before the song has been laid out. What [window] covers is no part of the screen: the bars and
 * the step row at its end, and at its top the edge's fade, but only as far as the scroll is past the top of the row it is
 * in, since a rested row starts unfaded right under it. A piece is on the screen once half a line of it is, or half of
 * it where it is shorter than that, so that a line cut at the screen's end by a few pixels, which the next page starts
 * with, says nothing of this one, while a line still being read is never left out.
 */
internal fun piecesOnScreen(offset: Int, viewport: Int, rows: SongRows, window: ReadingWindow): List<SongPiece>? {
    if (rows.pieces.isEmpty() || viewport <= 0) return null
    val top = offset + minOf(window.top, rows.scrolledIntoRow(offset).coerceAtLeast(0))
    val bottom = offset + viewport - window.bottom
    return rows.pieces.filter { piece ->
        val shown = minOf(piece.end, bottom) - maxOf(piece.start, top)
        shown > 0 && shown * 2 >= minOf(piece.end - piece.start, window.lineHeight).coerceAtLeast(1)
    }
}
