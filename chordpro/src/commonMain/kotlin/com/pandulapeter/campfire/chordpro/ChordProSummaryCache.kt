/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import com.pandulapeter.campfire.chordpro.model.ChordProSummary
import com.pandulapeter.campfire.chordpro.syntax.ChordProDirectives
import com.pandulapeter.campfire.chordpro.syntax.ChordProEnvironments

/**
 * [ChordProParser.summarize] for a text that is edited one keystroke at a time, which is how the editor keeps its title,
 * artist and key current as they are typed. Most keystrokes are lyrics, and the only things a lyric line - not a
 * directive, not a comment, outside any tab, grid or environment that reads its lines differently - hands the summary
 * are the chord names in its brackets and whether it is blank. So an edit that stays inside one such line, outside its
 * brackets, adds or removes no syntax and leaves the line a non-blank lyric line returns the summary it had, chords on
 * the line or not. Anything the cache cannot prove to be that is summarized again from scratch: the answer is always
 * exactly what a full parse would give.
 *
 * One instance follows one text; it is not safe to share between threads.
 */
class ChordProSummaryCache internal constructor(
    private val summarize: (String) -> ChordProSummary,
) {
    /** Follows a text written in [notation], see [ChordProParser.summarize]. */
    constructor(notation: ChordNotation = ChordNotation.STANDARD) : this({ text -> ChordProParser.summarize(text, notation) })

    private var previousText: String? = null
    private var previousSummary: ChordProSummary? = null
    /** The line of the followed text an edit may stay inside, see [safeLineAt]; its `last` is exclusive, a line end. */
    private var safeLine: IntRange? = null

    /** Forgets the text followed so far, so that the next [summaryOf] is a full parse. */
    fun clear() {
        previousText = null
        previousSummary = null
        safeLine = null
    }

    /** The summary of [text], equal to what [ChordProParser.summarize] returns for it. */
    fun summaryOf(text: String): ChordProSummary {
        val oldText = previousText
        val oldSummary = previousSummary
        if (oldText != null && oldSummary != null) {
            if (text === oldText || text == oldText) return oldSummary
            val change = ChordProTextChange.between(oldText, text)
            val line = safeLine
            if (line != null && change.oldStart >= line.first && change.oldEnd <= line.last && change.newEnd >= line.first &&
                isHarmlessEdit(oldText, text, change, line.first) && isLyricLine(text, line.first, line.last + text.length - oldText.length)
            ) {
                previousText = text
                safeLine = line.first..(line.last + text.length - oldText.length)
                return oldSummary
            }
            return scanAndRemember(text, change.newEnd)
        }
        return scanAndRemember(text, 0)
    }

    private fun scanAndRemember(text: String, cursor: Int): ChordProSummary {
        val summary = summarize(text)
        previousText = text
        previousSummary = summary
        safeLine = safeLineAt(text, cursor)
        return summary
    }

    /**
     * The line around [cursor] as a range whose end is exclusive, where editing that line cannot change the summary, or
     * null. The environment the line stands in is worked out the way [ChordProParser.summarize] works it out, and has to
     * stay that way: a line of a tab or a grid is chords to the summary even without a bracket.
     */
    private fun safeLineAt(text: String, cursor: Int): IntRange? {
        val position = cursor.coerceIn(0, text.length)
        var start = position
        while (start > 0 && text[start - 1] != '\n' && text[start - 1] != '\r') start--
        var end = position
        while (end < text.length && text[end] != '\n' && text[end] != '\r') end++
        if (!isLyricLine(text, start, end)) return null
        // ChordProLineScanner's rule, walked over the offsets before the line instead of over a list of every line, since
        // it runs on each keystroke and only needs the environment the line stands in.
        var environment: String? = null
        var offset = 0
        while (offset < start) {
            var lineEnd = offset
            while (lineEnd < start && text[lineEnd] != '\n' && text[lineEnd] != '\r') lineEnd++
            val trimmed = text.substring(offset, lineEnd).trim()
            val isDelegated = environment in ChordProEnvironments.delegateEnvironments
            if (!(trimmed.startsWith('#') && !isDelegated)) {
                val directive = when {
                    !trimmed.startsWith('{') -> null
                    isDelegated -> ChordProDirectives.matchDelegatedDirective(trimmed)
                    else -> ChordProDirectives.matchDirective(trimmed)
                }
                if (directive != null && !ChordProDirectives.hasSelectorSuffix(directive.name)) {
                    ChordProEnvironments.startOfEnvironment(directive.name)?.let { environment = it.lowercase() }
                    ChordProEnvironments.endOfEnvironment(directive.name)?.let { environment = null }
                }
            }
            offset = lineEnd + if (text.getOrNull(lineEnd) == '\r' && text.getOrNull(lineEnd + 1) == '\n') 2 else 1
        }
        return if (environment == "tab" || environment == "grid" || environment in ChordProEnvironments.delegateEnvironments) null else start..end
    }

    /**
     * Non-blank, not a directive and not a `#` comment, which is what makes the summary read it as lyrics; chords are
     * allowed, since only the edit itself is checked for syntax. A line starting with a `{` that is not a directive is
     * lyrics to the parser too, and is simply parsed again.
     */
    private fun isLyricLine(text: String, start: Int, end: Int): Boolean {
        if (start < 0 || end > text.length || start >= end) return false
        var first = start
        while (first < end && text[first].isWhitespace()) first++
        return first < end && text[first] != '{' && text[first] != '#' && (start until end).none { text[it] == '\r' || text[it] == '\n' }
    }

    /**
     * Whether [change] neither removes nor inserts any syntax and starts outside every closed bracket pair of the line
     * that begins at [lineStart], which leaves every bracket pair and its content as it was. Stricter than it has to be:
     * a hyphen or a slash in a lyric line changes nothing either, but they are how tabs and grids are written, and an
     * edit holding one is simply parsed again.
     */
    private fun isHarmlessEdit(old: String, new: String, change: ChordProTextChange, lineStart: Int): Boolean {
        for (offset in change.oldStart until change.oldEnd) if (old[offset] in EDIT_SENSITIVE) return false
        for (offset in change.oldStart until change.newEnd) if (new[offset] in EDIT_SENSITIVE) return false
        // The text before the edit is the same on both sides, so the bracket the edit follows is read from either.
        val open = new.lastIndexOf('[', change.oldStart - 1).takeIf { it >= lineStart } ?: return true
        return new.lastIndexOf(']', change.oldStart - 1) > open
    }

    private companion object {
        const val EDIT_SENSITIVE = "{}[]#|/\\-\r\n"
    }
}
