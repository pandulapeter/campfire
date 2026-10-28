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

import com.pandulapeter.campfire.data.formats.document.PdfLineLayout.Line
import com.pandulapeter.campfire.data.formats.document.PdfTextExtractor.Page

/** The headers and footers a document repeats on its pages, and its page numbers, which are left out of the text. */
internal object PdfRunningLines {
    fun signature(line: Line, height: Double): String {
        val text = line.text.replace(number, "#").trim()
        val x = ((line.spans.firstOrNull()?.start ?: 0.0) / 5).toInt()
        val y = (line.y / height * 100).toInt()
        return "$x:$y:$text"
    }
    /**
     * The signatures of the lines repeated at the same place on at least half of the pages, and on two at least. A
     * number in one is the same on every page or counts the pages (`Page 3`, `3 of 9`): a section heading that happens
     * to open two pages (`Verse 2`, `Verse 6`) is the song's own.
     */
    fun runningLines(pages: List<Page>): Set<String> {
        if (pages.size < 2) return emptySet()
        val numbersBySignature = mutableMapOf<String, MutableMap<Int, List<Long>>>()
        pages.forEachIndexed { index, page ->
            page.edges.filter { it.text.isNotBlank() }.forEach { line ->
                numbersBySignature.getOrPut(signature(line, page.height)) { mutableMapOf() }
                    .getOrPut(index) { number.findAll(line.text).map { it.value.take(MAX_DIGITS).toLong() }.toList() }
            }
        }
        return numbersBySignature.filterValues { numbers ->
            numbers.size >= 2 && numbers.size * 2 >= pages.size && numbers.values.first().indices.all { position ->
                val offsets = numbers.map { (index, values) -> values.getOrNull(position)?.minus(index) }
                numbers.values.map { it.getOrNull(position) }.distinct().size == 1 || offsets.distinct().size == 1
            }
        }.keys
    }

    /** Whether a line at [y] is in the top or the bottom margin, where a running line is printed. */
    fun isEdge(y: Double, height: Double) = y < height * 0.08 || y > height * 0.92

    /**
     * A footer like `2 / 3` or `Page 2 of 3` is only taken for a page number when both numbers are this page's own: a
     * chord sheet's first line near the top of the page is often a bare time signature (`3/4`, `6/8`), which a looser
     * match would drop.
     */
    fun isPageCount(text: String, page: Int, pageCount: Int) =
        pageCountPattern.matchEntire(text)?.destructured?.let { (number, total) -> number == "$page" && total == "$pageCount" } ?: false

    private val number = Regex("[0-9]+")
    private const val MAX_DIGITS = 15
    private val pageCountPattern = Regex("(?:page\\s+)?([0-9]+)(?:\\s*/\\s*|\\s+of\\s+)([0-9]+)", RegexOption.IGNORE_CASE)
}
