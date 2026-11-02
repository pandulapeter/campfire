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

/** The text a sheet's lines are cleaned to and the values the converter writes from it. */
internal object ChordSheetText {
    internal fun clean(text: String): String = buildString {
        for (c in text) when (c) {
            '\u00ad', '\u200b', '\u200c', '\u200d', '\u2060', '\ufeff', '\u007f', in '\u202a'..'\u202e', in '\u2066'..'\u2069' -> Unit
            '\ufb00' -> append("ff")
            '\ufb01' -> append("fi")
            '\ufb02' -> append("fl")
            '\ufb03' -> append("ffi")
            '\ufb04' -> append("ffl")
            '\ufb05', '\ufb06' -> append("st")
            else -> if (c.code >= 0x20 || c.isWhitespace()) append(if (c.isWhitespace() || c == '\u00a0' || c in '\u2000'..'\u200a' || c == '\u202f') ' ' else c)
        }
    }

    internal fun isExportFiller(character: Char) = character == ChordSheetCampfireLayout.PADDING || character == ChordSheetCampfireLayout.CONTINUATION

    /** The punctuation that marks an extracted heading is not part of the section's name. */
    internal fun headingLabel(text: String) = ChordSheetHeadings.parse(text)?.label
        ?: text.trim().removeSurrounding("[", "]").removeSurrounding("(", ")").removeSuffix(":").trim()

    /** The text of [lines] from [first] to [last], one value the export wrapped: a row continuing a word joins with no space. */
    internal fun joinRows(lines: List<Rendered>, first: Int, last: Int) =
        ((first + 1)..last).fold(lines[first].text.trim()) { text, index -> text + lines[index].joint + lines[index].text.trim() }

    internal fun headerValue(value: String) = value.replace('{', '(').replace('}', ')').replace('\n', ' ').trim()
}
