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
     * `1/2`, which is how a time signature at the top of each song reads too. A line in the top margin that counts no
     * pages and is set over the same line of the body on every page it opens is a passage the song repeats (a chorus
     * printed again under its label), never a header, which stands over whatever each page holds.
     */
    fun runningLines(pages: List<Page>): Set<String> {
        if (pages.size < 2) return emptySet()
        val numbersBySignature = mutableMapOf<String, MutableMap<Int, List<Long>>>()
        val isOwnPageCountBySignature = mutableMapOf<String, Boolean>()
        val bodyLinesUnderBySignature = mutableMapOf<String, MutableSet<String?>>()
        pages.forEachIndexed { index, page ->
            // Searched for every line of the top margin, which may hold as many lines as the page.
            val body = page.lines.filter { it.text.isNotBlank() }.sortedBy { it.y }
            page.edges.filter { it.text.isNotBlank() }.forEach { line ->
                val signature = signature(line, page.height)
                if (line.y < page.height / 2) bodyLinesUnderBySignature.getOrPut(signature) { mutableSetOf() } += bodyLineUnder(line, page, body)
                numbersBySignature.getOrPut(signature) { mutableMapOf() }
                    .getOrPut(index) { number.findAll(line.text).map { it.value.take(MAX_DIGITS).toLong() }.toList() }
                isOwnPageCountBySignature[signature] = isOwnPageCountBySignature[signature] != false && isOwnPageCount(line.text.trim())
            }
        }
        return numbersBySignature.filter { (signature, numbers) ->
            numbers.size >= 2 && numbers.size * 2 >= pages.size && (isOwnPageCountBySignature[signature] == true || isRunningNumbers(numbers)) &&
                (numbers.values.distinct().size > 1 || bodyLinesUnderBySignature[signature]?.singleOrNull() == null)
        }.keys
    }

    /**
     * The line set next under [line] on [page], at its place and in its own words, where that is a line of the body,
     * outside the margins. [body] is the page's lines that are not blank, from the top.
     */
    private fun bodyLineUnder(line: Line, page: Page, body: List<Line>): String? {
        val size = line.spans.maxOfOrNull { it.size } ?: 1.0
        var low = 0
        var high = body.size
        while (low < high) { val middle = (low + high) / 2; if (body[middle].y - line.y > size * 0.5) high = middle else low = middle + 1 }
        val under = body.getOrNull(low) ?: return null
        return if (isEdge(under.y, page.height)) null else "${signature(under, page.height)}:${under.text.trim()}"
    }

    /** Whether each number of a line, by page index, is the same on every page or rises with the page. */
    private fun isRunningNumbers(numbers: Map<Int, List<Long>>) = numbers.values.first().indices.all { position ->
        val offsets = numbers.map { (index, values) -> values.getOrNull(position)?.minus(index) }
        numbers.values.map { it.getOrNull(position) }.distinct().size == 1 || offsets.distinct().size == 1
    }

    /**
     * Whether every one of [pages], two at least, ends in a footer a wide bottom margin lifts out of it (an album and its
     * year under each page of a song): the lowest line of each page outside the margins, in its lower half, the same
     * words at the same place on all of them. The song's own last line differs from page to page.
     */
    fun hasLiftedFooter(pages: List<Page>): Boolean {
        if (pages.size < 2) return false
        val footers = pages.map { page -> liftedFooter(page)?.let { "${signature(it, page.height)}:${it.text.trim()}" } }
        return footers.first() != null && footers.distinct().size == 1
    }

    /** The lowest line of [page] outside its margins and in its lower half, where a lifted footer stands. */
    fun liftedFooter(page: Page) = page.lines.filter { it.text.isNotBlank() && !isEdge(it.y, page.height) }.maxByOrNull { it.y }
        ?.takeIf { it.y > page.height / 2 }

    /**
     * Whether a baseline is one of [lines]', within a fifth of an em of it: a dropped running line takes its whole
     * baseline. Searched rather than compared with each of them, since a margin may hold as many lines as the page.
     */
    fun onBaselineOf(lines: List<Line>): (Double) -> Boolean {
        val bands = lines.map { line ->
            val reach = (line.spans.maxOfOrNull { it.size } ?: 1.0) * 0.22
            line.y - reach to line.y + reach
        }.sortedBy { it.first }
        val furthest = bands.map { it.second }.runningReduce(::maxOf)
        return { y ->
            var low = 0
            var high = bands.size
            while (low < high) { val middle = (low + high) / 2; if (bands[middle].first <= y) low = middle + 1 else high = middle }
            low > 0 && furthest[low - 1] >= y
        }
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
