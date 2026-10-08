/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.document

import com.pandulapeter.campfire.data.source.local.implementation.document.PdfLineLayout.Line
import com.pandulapeter.campfire.data.source.local.implementation.document.PdfTextExtractor.Page

/** The headers and footers a document repeats on its pages, and its page numbers, which are left out of the text. */
internal object PdfRunningLines {
    fun signature(line: Line, height: Double): String {
        val text = line.text.replace(Regex("[0-9]+"), "#").trim()
        val x = ((line.spans.firstOrNull()?.start ?: 0.0) / 5).toInt()
        val y = (line.y / height * 100).toInt()
        return "$x:$y:$text"
    }
    fun runningLines(pages: List<Page>): Set<String> {
        if (pages.size < 2) return emptySet()
        val counts = mutableMapOf<String, Int>()
        for (page in pages) page.lines.filter { it.y < page.height * 0.08 || it.y > page.height * 0.92 }
            .map { signature(it, page.height) }.toSet().forEach { counts[it] = (counts[it] ?: 0) + 1 }
        return counts.filterValues { it >= 2 && it * 2 >= pages.size }.keys
    }

    /**
     * A footer like `2 / 3` is only taken for a page number when both numbers are this page's own: a chord sheet's first
     * line near the top of the page is often a bare time signature (`3/4`, `6/8`), which a looser match would drop.
     */
    fun isPageCount(text: String, page: Int, pageCount: Int) =
        pageCountPattern.matchEntire(text)?.destructured?.let { (number, total) -> number == "$page" && total == "$pageCount" } ?: false

    private val pageCountPattern = Regex("([0-9]+)\\s*/\\s*([0-9]+)")
}
