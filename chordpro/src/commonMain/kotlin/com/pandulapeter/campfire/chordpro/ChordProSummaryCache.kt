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

/**
 * [ChordProParser.summarize] for a text that is edited one keystroke at a time, which is how the editor keeps its title,
 * artist and key current as they are typed. Most keystrokes are lyrics, and a line of plain lyrics - no directive, no
 * chord, no comment, nothing that reads as a tab or a grid, outside any environment that reads its lines differently -
 * cannot change the summary, so an edit that stays inside one such line returns the summary it had. Anything the cache
 * cannot prove to be that is summarized again from scratch: the answer is always exactly what a full parse would give.
 *
 * One instance follows one text; it is not safe to share between threads.
 */
class ChordProSummaryCache internal constructor(
    private val summarize: (String) -> ChordProSummary,
) {
    constructor() : this(ChordProParser::summarize)

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
            val change = changedSpan(oldText, text)
            val line = safeLine
            if (line != null && change.oldStart >= line.first && change.oldEnd <= line.last &&
                change.newEnd >= line.first && isPlainLyricLine(text, line.first, line.last + text.length - oldText.length)
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

    /** The part of the old text an edit replaced, and where its replacement ends in the new one; the ends are exclusive. */
    private data class ChangedSpan(val oldStart: Int, val oldEnd: Int, val newEnd: Int)

    private fun changedSpan(old: String, new: String): ChangedSpan {
        var start = 0
        while (start < old.length && start < new.length && old[start] == new[start]) start++
        var oldEnd = old.length
        var newEnd = new.length
        while (oldEnd > start && newEnd > start && old[oldEnd - 1] == new[newEnd - 1]) {
            oldEnd--
            newEnd--
        }
        return ChangedSpan(start, oldEnd, newEnd)
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
        if (!isPlainLyricLine(text, start, end)) return null
        var environment: String? = null
        var offset = 0
        while (offset < start) {
            var lineEnd = offset
            while (lineEnd < start && text[lineEnd] != '\n' && text[lineEnd] != '\r') lineEnd++
            val trimmed = text.substring(offset, lineEnd).trim()
            val isDelegated = environment in ChordProSyntax.delegateEnvironments
            if (!(trimmed.startsWith('#') && !isDelegated)) {
                val directive = when {
                    !trimmed.startsWith('{') -> null
                    isDelegated -> ChordProSyntax.matchDelegatedDirective(trimmed)
                    else -> ChordProSyntax.matchDirective(trimmed)
                }
                if (directive != null && !ChordProSyntax.hasSelectorSuffix(directive.name)) {
                    ChordProSyntax.startOfEnvironment(directive.name)?.let { environment = it.lowercase() }
                    ChordProSyntax.endOfEnvironment(directive.name)?.let { environment = null }
                }
            }
            offset = lineEnd + if (text.getOrNull(lineEnd) == '\r' && text.getOrNull(lineEnd + 1) == '\n') 2 else 1
        }
        return if (environment == "tab" || environment == "grid" || environment in ChordProSyntax.delegateEnvironments) null else start..end
    }

    /**
     * Stricter than it has to be: a hyphen or a slash in a lyric line changes nothing either, but they are how tabs and
     * grids are written, and a line holding one is simply parsed again.
     */
    private fun isPlainLyricLine(text: String, start: Int, end: Int): Boolean {
        if (start < 0 || end > text.length || start >= end) return false
        var hasVisibleCharacter = false
        for (offset in start until end) {
            val character = text[offset]
            if (character in "{}[]#|/\\-\r\n") return false
            if (!character.isWhitespace()) hasVisibleCharacter = true
        }
        return hasVisibleCharacter
    }
}
