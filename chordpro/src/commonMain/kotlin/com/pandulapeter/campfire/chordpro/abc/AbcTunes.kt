/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.abc

/** Cuts an ABC file into its tunes, each a valid tune on its own. */
public object AbcTunes {

    private const val BYTE_ORDER_MARK = '﻿'

    /**
     * The tunes of [text], in order. A tune starts at its `X:` field and ends at the first blank line after its `K:`,
     * as the standard has it; a blank line inside the tune header is read past, since text pasted from a web page often
     * doubles its line breaks, and an `X:` ends the tune before it even with no blank line between them. Blank lines are
     * never part of a tune, so each can be kept in one `{start_of_abc}` block.
     *
     * The file header (the fields before the first `X:`, when its first paragraph is made only of fields and comments)
     * is written into every tune right after its `X:` line, so each tune keeps the composer, meter, or stylesheet the
     * file gave them all, and the tune's own fields, which come later, override it. Free text outside the tunes is
     * dropped, as ABC reads it. A text with no `X:` at all is one tune.
     */
    public fun split(text: String): List<String> {
        val lines = text.lines().map { it.trimStart(BYTE_ORDER_MARK) }
        val firstTune = lines.indexOfFirst(::isTuneStart)
        if (firstTune < 0) return listOfNotNull(tuneFrom(lines, start = 0, header = emptyList())?.second)
        val header = fileHeader(lines.subList(0, firstTune))
        val tunes = mutableListOf<String>()
        var index = firstTune
        while (index < lines.size) {
            if (isTuneStart(lines[index])) {
                val (end, tune) = tuneFrom(lines, start = index, header = header) ?: break
                tunes += tune
                index = end
            } else {
                index++
            }
        }
        return tunes
    }

    private fun isTuneStart(line: String) = AbcLines.field(line)?.letter == 'X'

    /** The tune starting at [start] with [header] after its first line, and the index of the line after it. */
    private fun tuneFrom(lines: List<String>, start: Int, header: List<String>): Pair<Int, String>? {
        val tune = mutableListOf<String>()
        var hasKey = false
        var index = start
        while (index < lines.size) {
            val line = lines[index]
            when {
                line.isBlank() -> if (hasKey) break
                index > start && isTuneStart(line) -> break
                else -> {
                    tune += line
                    if (tune.size == 1 && isTuneStart(line)) tune += header
                    if (AbcLines.field(line)?.letter == 'K') hasKey = true
                }
            }
            index++
        }
        return tune.takeIf { it.isNotEmpty() }?.let { index to it.joinToString("\n") }
    }

    private fun fileHeader(lines: List<String>): List<String> {
        val paragraph = lines.dropWhile { it.isBlank() }.takeWhile { !it.isBlank() }
        return paragraph.takeIf { it.all { line -> AbcLines.isComment(line) || AbcLines.field(line) != null } }.orEmpty()
    }
}
