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
                    if (environments.isEmpty() && end != "tab" && end != "grid") gapBeforeNext = true
                }
                continue
            }

            if (trimmed.isEmpty()) {
                gap()
                continue
            }
            val isLineMode = start == "tab" || start == "grid"
            if (gapBeforeNext || (start != null && !isLineMode) || directive?.name in ChordProSyntax.blockNames ||
                directive?.name == "new_song" || directive?.name == "ns") gap()
            gapBeforeNext = false
            output += if (directive == null) rawLine else trimmed
            when {
                start != null -> environments += start
                directive?.name == "new_song" || directive?.name == "ns" -> {
                    isHeader = true
                    gap()
                }
                directive?.name in breakNames || end != null -> gapBeforeNext = true
            }
        }
        flushMetadata()
        // An unclosed environment is unfinished input: its final blank lines are still part of its literal text.
        if (environments.isEmpty()) while (output.lastOrNull()?.isBlank() == true) output.removeAt(output.lastIndex)
        return if (output.isEmpty()) "" else output.joinToString("\n") + "\n"
    }

    private val breakNames = setOf("chorus", "new_page", "np", "new_physical_page", "npp", "column_break", "colb")
}
