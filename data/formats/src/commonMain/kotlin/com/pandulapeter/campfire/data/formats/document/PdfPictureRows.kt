/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.document

/**
 * The rows of pictures a page draws where it shows no text. Some sites export a song with every lyric and chord drawn as
 * an image, keeping only a header as text, which would import as a song of credits alone; such a document draws many
 * more rows of pictures than lines of text, where a logo, a cover, or a row of chord diagrams is a row or a few beside a
 * whole song of text. A picture as tall as a quarter of the page (a cover, a background, a scanned page under its
 * recognized text) would merge every row into one, and one shorter than [MIN_HEIGHT] is a rule or a spacer, not a line,
 * so neither is counted.
 */
internal object PdfPictureRows {
    /** A picture's extent down the page, as the page is read. */
    data class Picture(val top: Double, val bottom: Double)

    /** At least this many rows of pictures over a document, and more than twice its lines of text, are its body drawn. */
    const val MIN_ROWS = 8

    private const val MIN_HEIGHT = 3.0

    fun textlessRows(pictures: List<Picture>, baselines: List<Double>, height: Double): Int {
        val rows = mutableListOf<Picture>()
        for (picture in pictures.filter { it.bottom - it.top in MIN_HEIGHT..height / 4 }.sortedBy { it.top }) {
            val last = rows.lastOrNull()
            if (last != null && picture.top < last.bottom) rows[rows.lastIndex] = Picture(last.top, maxOf(last.bottom, picture.bottom))
            else rows += picture
        }
        val sorted = baselines.sorted()
        return rows.count { row ->
            val at = sorted.binarySearch(row.top - 1).let { if (it < 0) -it - 1 else it }
            at == sorted.size || sorted[at] > row.bottom + 1
        }
    }

    /** Whether a document of [rows] textless rows of pictures and [lines] lines of text is a body drawn as pictures. */
    fun isDrawn(rows: Int, lines: Int) = rows >= MIN_ROWS && rows > 2 * lines
}
