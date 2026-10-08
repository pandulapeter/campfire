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

import com.pandulapeter.campfire.chordpro.ChordProVocabulary.TEMPO
import com.pandulapeter.campfire.chordpro.ChordProVocabulary.TIME

/** Formats raw ChordPro without a model round trip, which would discard unsupported directives and comments. */
object ChordProPrettifier {

    /**
     * Orders the opening metadata by the toolbar's header order, separates the header and sections, and uses LF
     * with a final newline. Repeated directives keep their relative order. Source comments and settings anchor
     * metadata groups; body directives (including key changes) stay where they were written. Lyrics and the inside
     * of environments retain their whitespace, including blank lines in tabs, grids and delegated notation.
     *
     * A `{tempo}` or a `{time}` is the exception, since what it means depends on where it stands: the first readable one
     * in the body of a song whose header has none is the song's own value (see [ChordProParser]), so it is moved into
     * the header, where it means the same and every file then states its main values. The others are changes from
     * where they stand, and the ones outside every environment with only blank lines between them are written as one
     * group, the tempo first, heading what follows it the way a section's own first line does — unless a blank line
     * after a group that cut a running paragraph closed that paragraph in the source, which is then kept after it.
     */
    fun prettify(text: String): String {
        val lines = ChordProLines.splitLines(text)
        val hoisted = hoistedTimings(lines)
        var song = 0
        val output = mutableListOf<String>()
        val metadata = mutableListOf<Pair<Int, String>>()
        // A stack rather than ChordProLineScanner's one open environment: an end closes only the innermost one it
        // names, which decides where a gap goes and keeps a nested environment verbatim.
        val environments = mutableListOf<String>()
        var isHeader = true
        var gapBeforeNext = false
        // A legacy heading section or an implicit paragraph is open: a blank line would close it, where a comment or a
        // break standing in it only cuts it in two and leaves the rest its continuation.
        var isImplicitSectionRunning = false
        val timings = mutableListOf<String>()
        // The line after a group of changes follows it directly, since the group is what that line starts with.
        var isHeadedByTimings = false
        // A group written without a blank line before it only cuts the paragraph it stands in, and a blank line after
        // it in the source still closes that paragraph, so it is written after the group rather than swallowed by it.
        var doesGroupCutSection = false
        var doesBlankLineCloseGroup = false

        fun gap() {
            if (output.isNotEmpty() && output.last().isNotBlank()) output += ""
        }

        fun flushTimings() {
            if (timings.isEmpty()) return
            output += timings.sortedBy { ChordProDirectives.matchDirective(it)?.let(ChordProHeaderLayout::metadataKind) != TEMPO }
            timings.clear()
            if (doesBlankLineCloseGroup) gap() else isHeadedByTimings = true
            doesGroupCutSection = false
            doesBlankLineCloseGroup = false
        }

        fun flushMetadata() {
            output += metadata.sortedBy { it.first }.map { it.second }
            metadata.clear()
        }

        for ((index, rawLine) in lines.withIndex()) {
            val trimmed = rawLine.trim()
            val directive = ChordProDirectives.matchDirective(trimmed)
            val start = directive?.name?.let(ChordProEnvironments::startOfEnvironment)
            val end = directive?.name?.let(ChordProEnvironments::endOfEnvironment)
            val delegated = environments.lastOrNull() in ChordProEnvironments.delegateEnvironments

            // Braces and hashes inside delegated notation are that language's syntax, even metadata-shaped ones.
            if (delegated && end != environments.last()) {
                output += rawLine
                continue
            }

            if (isHeader) {
                if (trimmed.isEmpty()) continue
                if (trimmed.startsWith('#')) {
                    flushMetadata()
                    output += rawLine
                    continue
                }
                if (directive != null && start == null && end == null && directive.name !in ChordProHeaderLayout.blockNames &&
                    directive.name != "new_song" && directive.name != "ns") {
                    val kind = ChordProHeaderLayout.metadataKind(directive)
                    if (kind != null || directive.name == "meta") {
                        metadata += (kind?.let(ChordProHeaderLayout.metadataOrder::indexOf)?.takeIf { it >= 0 }
                            ?: ChordProHeaderLayout.metadataOrder.size) to trimmed
                    } else {
                        flushMetadata()
                        output += trimmed
                    }
                    continue
                }
                hoisted[song]?.forEach { hoistedIndex ->
                    val line = lines[hoistedIndex].trim()
                    metadata += ChordProDirectives.matchDirective(line)?.let(ChordProHeaderLayout::metadataKind)?.let(ChordProHeaderLayout.metadataOrder::indexOf)!! to line
                }
                flushMetadata()
                isHeader = false
                gap()
            }

            if (hoisted[song]?.contains(index) == true) continue
            if (environments.isNotEmpty()) {
                output += rawLine
                if (!delegated && start != null) environments += start
                if (end != null) {
                    val index = environments.indexOfLast { it == end }
                    if (index >= 0) environments.subList(index, environments.size).clear()
                    if (environments.isEmpty() && end != "tab" && end != "grid") {
                        gapBeforeNext = true
                        isImplicitSectionRunning = false
                    }
                }
                continue
            }

            if (directive != null && ChordProHeaderLayout.metadataKind(directive) in ChordProHeader.changeableMetadata) {
                // A blank line before the group would close a paragraph the change only cuts, see cutsRunningSection.
                if (timings.isEmpty()) {
                    doesGroupCutSection = isImplicitSectionRunning && !gapBeforeNext
                    if (!doesGroupCutSection) gap()
                }
                gapBeforeNext = false
                timings += trimmed
                continue
            }
            if (timings.isNotEmpty()) {
                if (trimmed.isEmpty()) {
                    if (doesGroupCutSection) {
                        doesBlankLineCloseGroup = true
                        isImplicitSectionRunning = false
                    }
                    continue
                }
                flushTimings()
            }
            if (trimmed.isEmpty()) {
                gap()
                isImplicitSectionRunning = false
                continue
            }
            val isLineMode = start == "tab" || start == "grid"
            val isLegacyHeading = directive != null && directive.name in plainCommentNames &&
                ChordProParser.isLegacyHeading(directive.value.orEmpty())
            // The blank line a block directive and a break get around them would turn the rest of the section into a
            // section of its own, so none is added where they only cut the one that is running. A recall or a heading
            // closes it anyway.
            val cutsRunningSection = isImplicitSectionRunning && directive != null && start == null && directive.name != "chorus" &&
                directive.name in ChordProHeaderLayout.blockNames && !isLegacyHeading
            val isNewSong = directive?.name == "new_song" || directive?.name == "ns"
            if ((!isHeadedByTimings || isNewSong) && (gapBeforeNext || (start != null && !isLineMode) ||
                    (directive?.name in ChordProHeaderLayout.blockNames && !cutsRunningSection) || isNewSong)) gap()
            gapBeforeNext = false
            isHeadedByTimings = false
            output += if (directive == null) rawLine else trimmed
            when {
                directive == null || isLegacyHeading || isLineMode -> isImplicitSectionRunning = true
                start != null || directive.name == "chorus" || directive.name == "new_song" || directive.name == "ns" ->
                    isImplicitSectionRunning = false
            }
            when {
                start != null -> environments += start
                isNewSong -> {
                    isHeader = true
                    song++
                    gap()
                }
                (directive?.name in breakNames && !cutsRunningSection) || end != null -> gapBeforeNext = true
            }
        }
        flushTimings()
        flushMetadata()
        // An unclosed environment is unfinished input: its final blank lines are still part of its literal text.
        if (environments.isEmpty()) while (output.lastOrNull()?.isBlank() == true) output.removeAt(output.lastIndex)
        return if (output.isEmpty()) "" else output.joinToString("\n") + "\n"
    }

    /**
     * Maps a caret through formatting, keeping its column on the same non-blank line after header reordering and
     * spacing changes. Like [ChordProTransposer.transposedOffset], it clamps arbitrary offsets; the end of a line stays at
     * the end of it, while a removed blank line or an offset inside a CRLF break moves to the next surviving line, or the
     * end of the document.
     */
    fun prettifiedOffset(before: String, after: String, offset: Int): Int {
        val caret = offset.coerceIn(0, before.length)
        if (before == after) return caret
        val oldLines = ChordProLines.splitLines(before)
        val newLines = ChordProLines.splitLines(after)
        val oldStarts = ChordProLines.lineStartOffsets(before)
        val newStarts = ChordProLines.lineStartOffsets(after)
        val indexed = HashMap<String, MutableList<Int>>()
        newLines.forEachIndexed { index, line ->
            if (line.isNotBlank()) indexed.getOrPut(line.trim()) { mutableListOf() }.add(index)
        }
        val available = indexed.mapValues { LineMatches(it.value.toIntArray()) }
        val matches = IntArray(oldLines.size) { -1 }
        var previous = 0
        oldLines.forEachIndexed { index, line ->
            if (line.isNotBlank()) available[line.trim()]?.take(previous)?.let { match ->
                matches[index] = match
                previous = match
            }
        }
        val line = oldStarts.indexOfLast { it <= caret }.coerceAtLeast(0)
        val column = caret - oldStarts[line]
        val match = matches[line]
        if (match >= 0 && column <= oldLines[line].length) {
            val oldIndent = oldLines[line].indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val newIndent = newLines[match].indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            return (newStarts[match] + (column - oldIndent + newIndent).coerceIn(0, newLines[match].length)).coerceIn(0, after.length)
        }
        val next = ((line + 1)..matches.lastIndex).firstOrNull { matches[it] >= 0 }
        return next?.let { newStarts[matches[it]] } ?: after.length
    }

    /** Equal lines keep their order through formatting; separate cursors retain reordered header lines for fallback. */
    private class LineMatches(private val indices: IntArray) {
        private val used = BooleanArray(indices.size)
        private var first = 0
        private var forward = 0
        private var previousMinimum = 0

        fun take(minimum: Int): Int? {
            while (first < indices.size && used[first]) first++
            if (first == indices.size) return null
            if (minimum < previousMinimum) forward = first
            previousMinimum = minimum
            forward = maxOf(forward, first)
            while (forward < indices.size && (used[forward] || indices[forward] < minimum)) forward++
            val chosen = if (forward < indices.size) forward++ else first
            used[chosen] = true
            return indices[chosen]
        }
    }

    /**
     * The lines of each song of [lines], by the index of the song in the file, that [prettify] moves into its header: the
     * first readable `{tempo}` and `{time}` of the body of a song whose header has no line of that kind — an empty header
     * line counting as one. That line is the song's own value to the parser, so where it stands it is no change at all.
     * Not from inside an environment handed to another program, where braces are that program's text. Songs are counted
     * as [prettify] counts them, a `{new_song}` inside an open environment being that environment's text.
     */
    private fun hoistedTimings(lines: List<String>): Map<Int, List<Int>> {
        val hoisted = mutableMapOf<Int, List<Int>>()
        var song = 0
        var isHeader = true
        val headerKinds = mutableSetOf<String>()
        val found = mutableMapOf<String, Int>()
        // The same stack as prettify's, not ChordProLineScanner's rule, so the two agree about where a song is in an environment.
        val environments = mutableListOf<String>()
        fun finishSong() {
            found.filterKeys { it !in headerKinds }.values.sorted().takeIf { it.isNotEmpty() }?.let { hoisted[song] = it }
            found.clear()
            headerKinds.clear()
        }
        lines.forEachIndexed { index, rawLine ->
            val trimmed = rawLine.trim()
            val delegated = environments.lastOrNull() in ChordProEnvironments.delegateEnvironments
            val directive = if (delegated) ChordProDirectives.matchDelegatedDirective(trimmed) else ChordProDirectives.matchDirective(trimmed)
            val end = directive?.name?.let(ChordProEnvironments::endOfEnvironment)
            if (delegated) {
                if (end != null) environments.removeAt(environments.lastIndex)
                return@forEachIndexed
            }
            if (environments.isEmpty() && (directive?.name == "new_song" || directive?.name == "ns")) {
                finishSong()
                song++
                isHeader = true
                return@forEachIndexed
            }
            val kind = directive?.let(ChordProHeaderLayout::metadataKind)
            if (isHeader) {
                if (trimmed.isEmpty() || trimmed.startsWith('#')) return@forEachIndexed
                if (directive != null && !ChordProHeaderLayout.startsBody(directive) && ChordProEnvironments.endOfEnvironment(directive.name) == null) {
                    kind?.let(headerKinds::add)
                    return@forEachIndexed
                }
                isHeader = false
            }
            directive?.name?.let(ChordProEnvironments::startOfEnvironment)?.let { environments += it.lowercase() }
            if (end != null) environments.indexOfLast { it == end.lowercase() }.takeIf { it >= 0 }?.let { environments.subList(it, environments.size).clear() }
            val value = directive?.let { (ChordProMetaItems.standardMeta(it) ?: it).value }
            val isReadable = (kind == TEMPO || kind == TIME) && value != null && ChordProMetaItems.isReadableValue(kind, value)
            if (isReadable && kind != null && kind !in found) found[kind] = index
        }
        finishSong()
        return hoisted
    }

    private val plainCommentNames = setOf("comment", "c")

    private val breakNames = setOf("chorus", "new_page", "np", "new_physical_page", "npp", "column_break", "colb")
}
