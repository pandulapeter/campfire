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

/** Formats raw ChordPro without a model round trip, which would discard unsupported directives and comments. */
object ChordProPrettifier {

    /**
     * Orders the opening metadata by the toolbar's header order, separates the header and sections, and uses LF
     * with a final newline. Repeated directives keep their relative order. Source comments and settings anchor
     * metadata groups; body directives (including key changes) stay where they were written. Lyrics and the inside
     * of environments retain their whitespace, including blank lines in tabs, grids and delegated notation.
     */
    fun prettify(text: String): String {
        val output = mutableListOf<String>()
        val metadata = mutableListOf<Pair<Int, String>>()
        val environments = mutableListOf<String>()
        var isHeader = true
        var gapBeforeNext = false
        // A legacy heading section or an implicit paragraph is open: a blank line would close it, where a comment or a
        // break standing in it only cuts it in two and leaves the rest its continuation.
        var isImplicitSectionRunning = false

        fun flushMetadata() {
            output += metadata.sortedBy { it.first }.map { it.second }
            metadata.clear()
        }

        fun gap() {
            if (output.isNotEmpty() && output.last().isNotBlank()) output += ""
        }

        for (rawLine in ChordProSyntax.splitLines(text)) {
            val trimmed = rawLine.trim()
            val directive = ChordProSyntax.matchDirective(trimmed)
            val start = directive?.name?.let(ChordProSyntax::startOfEnvironment)
            val end = directive?.name?.let(ChordProSyntax::endOfEnvironment)
            val delegated = environments.lastOrNull() in ChordProSyntax.delegateEnvironments

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
                if (directive != null && start == null && end == null && directive.name !in ChordProSyntax.blockNames &&
                    directive.name != "new_song" && directive.name != "ns") {
                    val kind = ChordProSyntax.metadataKind(directive)
                    if (kind != null || directive.name == "meta") {
                        metadata += (kind?.let(ChordProSyntax.metadataOrder::indexOf)?.takeIf { it >= 0 }
                            ?: ChordProSyntax.metadataOrder.size) to trimmed
                    } else {
                        flushMetadata()
                        output += trimmed
                    }
                    continue
                }
                flushMetadata()
                isHeader = false
                gap()
            }

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
                directive.name in ChordProSyntax.blockNames && !isLegacyHeading
            if (gapBeforeNext || (start != null && !isLineMode) || (directive?.name in ChordProSyntax.blockNames && !cutsRunningSection) ||
                directive?.name == "new_song" || directive?.name == "ns") gap()
            gapBeforeNext = false
            output += if (directive == null) rawLine else trimmed
            when {
                directive == null || isLegacyHeading || isLineMode -> isImplicitSectionRunning = true
                start != null || directive.name == "chorus" || directive.name == "new_song" || directive.name == "ns" ->
                    isImplicitSectionRunning = false
            }
            when {
                start != null -> environments += start
                directive?.name == "new_song" || directive?.name == "ns" -> {
                    isHeader = true
                    gap()
                }
                (directive?.name in breakNames && !cutsRunningSection) || end != null -> gapBeforeNext = true
            }
        }
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
        val oldLines = ChordProSyntax.splitLines(before)
        val newLines = ChordProSyntax.splitLines(after)
        val oldStarts = ChordProSyntax.lineStartOffsets(before)
        val newStarts = ChordProSyntax.lineStartOffsets(after)
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

    private val plainCommentNames = setOf("comment", "c")

    private val breakNames = setOf("chorus", "new_page", "np", "new_physical_page", "npp", "column_break", "colb")
}
