/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

import com.pandulapeter.campfire.pagesong.PageLine
import com.pandulapeter.campfire.pagesong.PageLineLayout

/**
 * A document's lines as the page model (`:libs:pagesong`) reads them, one for each line of each of its pages, in order.
 * This is the one definition of a document line for the model: the trainer's documents were dumped through it
 * (`PageCorpusDocumentsDump`), so a change here changes the model's inputs, and the model must be retrained on it.
 */
public object ChordSheetPageLines {

    /** A gap wider than this many ems between two spans is a space in the line's text, which keeps a chord row's chords apart. */
    private const val GAP = 0.25

    /**
     * Each line of [sheet] with its layout: its largest type over the document's body size (the median of its printed
     * spans'), bold, italic, or monospace where every printed span is, its left edge over its page's printed width, its
     * column, its page, and whether the document marks it a heading.
     */
    public fun of(sheet: ChordSheet): List<PageLine> {
        val sizes = sheet.pages.flatMap { page -> page.lines.flatMap { line -> line.spans.filter { it.text.isNotBlank() }.map { it.size } } }.sorted()
        val body = sizes.getOrNull(sizes.size / 2)?.takeIf { it > 0 } ?: 1.0
        return sheet.pages.flatMapIndexed { pageIndex, page ->
            val left = page.lines.flatMap { it.spans }.minOfOrNull { it.start } ?: 0.0
            val right = page.lines.flatMap { it.spans }.maxOfOrNull { it.end } ?: 1.0
            page.lines.map { line -> lineOf(line, pageIndex, body, left, (right - left).takeIf { it > 0 } ?: 1.0) }
        }
    }

    private fun lineOf(line: ChordSheet.Line, page: Int, body: Double, left: Double, width: Double): PageLine {
        val spans = line.spans.filter { it.text.isNotEmpty() }.sortedBy { it.start }
        val text = buildString {
            spans.forEachIndexed { index, span ->
                val previous = spans.getOrNull(index - 1)
                if (previous != null && span.start - previous.end > span.size * GAP && !endsWith(' ') && !span.text.startsWith(' ')) append(' ')
                append(span.text)
            }
        }
        val printed = spans.filter { it.text.isNotBlank() }
        return PageLine(
            text = text,
            layout = PageLineLayout(
                page = page,
                column = line.column,
                size = ((printed.maxOfOrNull { it.size } ?: body) / body).toFloat(),
                isBold = printed.isNotEmpty() && printed.all { it.isBold },
                isItalic = printed.isNotEmpty() && printed.all { it.isItalic },
                isMonospace = printed.isNotEmpty() && printed.all { it.isMonospace },
                indent = (((printed.minOfOrNull { it.start } ?: left) - left) / width).toFloat(),
                isHeading = line.isHeading,
            ),
        )
    }
}
