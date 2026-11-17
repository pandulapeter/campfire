/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.syntax

import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.SOURCE_COMMENT

/** Where the file of an attachment line stands on a line, read as generously as [ChordProImageDirectives.sourcesOn] reads a picture's. */
internal object ChordProAttachmentDirectives {

    /**
     * The range of [rawLine] the file of its attachment directive is written in, read in any environment and behind the
     * `#` of a source comment, or null for any other line: what decides which files a song keeps, so a line commented
     * out for a moment still keeps its file.
     */
    fun sourceRangeOn(rawLine: String): IntRange? {
        var start = 0
        while (start < rawLine.length && (rawLine[start].isWhitespace() || rawLine.startsWith(SOURCE_COMMENT, start))) start++
        if (start >= rawLine.length || rawLine[start] != '{') return null
        val directiveLine = rawLine.substring(start).trimEnd()
        val directive = ChordProDirectives.matchDirective(directiveLine) ?: return null
        val source = ChordProMetaItems.attachment(directive)?.source ?: return null
        var index = ChordProDirectives.directiveValueStart(directiveLine) ?: return null
        while (index < directiveLine.length && directiveLine[index].isWhitespace()) index++
        while (index < directiveLine.length && !directiveLine[index].isWhitespace()) index++
        while (index < directiveLine.length && directiveLine[index].isWhitespace()) index++
        return if (directiveLine.startsWith(source, index)) start + index until start + index + source.length else null
    }

    /** The file [sourceRangeOn] finds on [rawLine], or null. */
    fun sourceOn(rawLine: String) = sourceRangeOn(rawLine)?.let { rawLine.substring(it.first, it.last + 1) }
}
