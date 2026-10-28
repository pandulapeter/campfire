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

/**
 * What the layout of Campfire's own PDF export ([ChordSheet.isCampfireExport]) says about a line, which is read rather
 * than guessed, since the export prints each kind of line in a type of its own: a section's label bold and a point
 * smaller than the lyrics, a grid bold in the monospaced face, a comment in italics, and a boxed comment's text 5 points
 * in from the column, inside a frame the text layer does not carry. Every other line is read as any sheet's is.
 */
internal object ChordSheetCampfireLayout {

    enum class Role { LABEL, GRID, COMMENT, BOX_COMMENT }

    /** The role of each line of a page, by the type it is set in and where it starts. */
    fun roles(lines: List<ChordSheet.Line>, bodySize: Double): List<Role?> {
        // A line's first space counts: a lyric indented by spaces starts at the column, where its first one is.
        val starts = lines.map { line -> line.spans.firstOrNull()?.start }
        return lines.mapIndexed { index, line ->
            val spans = line.spans.filter { it.text.isNotBlank() }
            val start = starts[index]
            when {
                spans.isEmpty() || start == null -> null
                spans.all { it.isBold && it.isMonospace } -> Role.GRID
                spans.all { it.isBold && !it.isMonospace && it.size < bodySize - LABEL_SIZE_STEP / 2 } -> Role.LABEL
                spans.all { it.isItalic } -> Role.COMMENT
                spans.none { it.isBold || it.isItalic || it.isMonospace } && starts.any { other ->
                    other != null && start - other in BOX_INSET - TOLERANCE..BOX_INSET + TOLERANCE
                } && starts.none { other -> other != null && start - other in TOLERANCE..BOX_INSET - TOLERANCE } -> Role.BOX_COMMENT
                else -> null
            }
        }
    }

    /**
     * The section a label opens: a verse, chorus, or bridge by the heading words the converter knows, any other kind
     * the export names by its last word (`Intro`, `Organ solo`), and a verse under its own name otherwise.
     */
    fun sectionType(label: String): String =
        ChordSheetHeadings.parse(label)?.type ?: label.trim().lowercase().split(' ').lastOrNull { it.isNotEmpty() }?.takeIf { it in customTypes } ?: "verse"

    /** The no-break spaces the export pads a lyric with under a chord wider than its syllable, which are not its words. */
    const val PADDING = '\u00A0'

    private val customTypes = setOf("intro", "outro", "solo", "interlude", "instrumental", "coda", "tag", "ending", "break", "riff")

    /** The labels are a point smaller than the lyrics (`PrintRows.labelStyle`). */
    private const val LABEL_SIZE_STEP = 1.0

    /** How far in from its frame a boxed comment's text starts (`PrintRows.boxedRows`), in points. */
    private const val BOX_INSET = 5.0
    private const val TOLERANCE = 0.75
}
