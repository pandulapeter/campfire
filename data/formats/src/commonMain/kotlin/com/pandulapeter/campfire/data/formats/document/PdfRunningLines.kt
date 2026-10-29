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
     * to open two pages (`Verse 2`, `Verse 6`) is the song's own. A line that is a page count of its own everywhere
     * (`Page 1 of 2`) is a running one whatever its numbers, since a songbook counts each song's pages; never a bare
     * `1/2`, which is how a time signature at the top of each song reads too.
     */
    fun runningLines(pages: List<Page>): Set<String> {
        if (pages.size < 2) return emptySet()
        val numbersBySignature = mutableMapOf<String, MutableMap<Int, List<Long>>>()
        val isOwnPageCountBySignature = mutableMapOf<String, Boolean>()
        pages.forEachIndexed { index, page ->
            page.edges.filter { it.text.isNotBlank() }.forEach { line ->
                val signature = signature(line, page.height)
                numbersBySignature.getOrPut(signature) { mutableMapOf() }
                    .getOrPut(index) { number.findAll(line.text).map { it.value.take(MAX_DIGITS).toLong() }.toList() }
                isOwnPageCountBySignature[signature] = isOwnPageCountBySignature[signature] != false && isOwnPageCount(line.text.trim())
            }
        }
        return numbersBySignature.filter { (signature, numbers) ->
            numbers.size >= 2 && numbers.size * 2 >= pages.size && (isOwnPageCountBySignature[signature] == true || isRunningNumbers(numbers))
        }.keys
    }

    /** Whether each number of a line, by page index, is the same on every page or rises with the page. */
    private fun isRunningNumbers(numbers: Map<Int, List<Long>>) = numbers.values.first().indices.all { position ->
        val offsets = numbers.map { (index, values) -> values.getOrNull(position)?.minus(index) }
        numbers.values.map { it.getOrNull(position) }.distinct().size == 1 || offsets.distinct().size == 1
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

    /** Whether [text] is nothing but a count of some pages, `Page 1 of 2` or `Page 1/2`, its page within them. */
    private fun isOwnPageCount(text: String) = ownPageCountPattern.matchEntire(text)?.groupValues?.drop(1)?.filter { it.isNotEmpty() }?.let { (page, total) ->
        val number = page.take(MAX_DIGITS).toLong()
        number in 1..total.take(MAX_DIGITS).toLong()
    } ?: false

    private val number = Regex("[0-9]+")
    private const val MAX_DIGITS = 15
    private val ownPageCountPattern = Regex("(?:page\\s+)?([0-9]+)\\s+of\\s+([0-9]+)|page\\s+([0-9]+)\\s*/\\s*([0-9]+)", RegexOption.IGNORE_CASE)
    private val pageCountPattern = Regex("(?:page\\s+)?([0-9]+)(?:\\s*/\\s*|\\s+of\\s+)([0-9]+)", RegexOption.IGNORE_CASE)
}
