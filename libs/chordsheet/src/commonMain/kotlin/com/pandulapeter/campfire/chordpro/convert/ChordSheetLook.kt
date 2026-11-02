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

import kotlin.math.abs

/**
 * How a lyric line is set: its type and where its text starts. A sheet sets a chorus apart from the verses by either,
 * bold or italic lyrics or an inset, so where a section set apart returns to the verses' look it has ended, whatever
 * heading comes next.
 */
internal data class ChordSheetLook(val isBold: Boolean, val isItalic: Boolean, val left: Double, val size: Double) {
    /** Whether this look differs from [other] in its type. */
    fun differsInType(other: ChordSheetLook) = isBold != other.isBold || isItalic != other.isItalic

    /**
     * Whether this look differs from [other] in its type or by an inset of more than a character and a half, which
     * plain text's two spaces exceed and a lyric's own unevenness in a PDF does not.
     */
    fun isSetApartFrom(other: ChordSheetLook) = differsInType(other) || abs(left - other.left) > maxOf(size, other.size) * 1.5

    companion object {
        /**
         * The left edge [looks] share within [within] of their size (half a character unless said otherwise); null where
         * they start in different places, as a centred or right-aligned sheet's lines do.
         */
        fun sharedEdge(looks: List<ChordSheetLook>, within: Double = 0.5): Double? {
            val first = looks.firstOrNull() ?: return null
            return first.left.takeIf { looks.all { abs(it.left - first.left) <= maxOf(it.size, first.size) * within } }
        }

        /**
         * The first lines of the runs of a stanza's lyrics set apart from the song's plain verses, two lines at least in the verses' size,
         * which open a chorus no heading names: bold or italic where the song has a plain verse of two lines or more (a
         * song set wholly in one has nothing to set apart). An inset alone does not, with no heading to say what it sets apart: a second
         * column, a bridge, and a centred stanza all start further in. The lines right under the title, before any stanza
         * break, chord row, or plain lyric, are the header's (a credit, an album), so a run there opens nothing. [looks]
         * holds a lyric's look and null for any other line, [breaks] whether a line ends a stanza (a chord row does not),
         * and [rows] whether it is a chord row.
         */
        fun unlabelled(looks: List<ChordSheetLook?>, breaks: List<Boolean>, rows: List<Boolean>): Set<Int> {
            val plain = looks.filterNotNull().filter { !it.isBold && !it.isItalic }.takeIf { it.isNotEmpty() } ?: return emptySet()
            val size = plain.map { it.size }.sorted()[plain.size / 2]
            // A plain footer, credit, or repeat mark under a song set wholly in italic is no verse to set a chorus apart from.
            var plainRun = 0
            val hasVerse = looks.indices.any { index ->
                if (breaks[index]) plainRun = 0
                val look = looks[index]
                if (look != null) plainRun = if (!look.isBold && !look.isItalic) plainRun + 1 else 0
                plainRun >= 2
            }
            if (!hasVerse) return emptySet()
            val starts = mutableSetOf<Int>()
            val run = mutableListOf<Int>()
            var hasStarted = false
            var runStarted = false
            fun finish() {
                if (run.size >= 2 && runStarted) starts += run.first()
                run.clear()
            }
            looks.forEachIndexed { index, look ->
                if (breaks[index]) finish()
                if (breaks[index] || rows[index]) hasStarted = true
                if (look == null) return@forEachIndexed
                // A chorus is sung in the verses' size; smaller print set apart is notes, diagrams, or credits.
                val isApart = ChordSheetProfile.isSameSize(look.size, size) && (look.isBold || look.isItalic)
                if (!look.isBold && !look.isItalic) hasStarted = true
                if (!isApart || run.isNotEmpty() && looks[run.first()]!!.differsInType(look)) finish()
                if (isApart) {
                    if (run.isEmpty()) runStarted = hasStarted
                    run += index
                }
            }
            finish()
            return starts
        }

        /** The look of [line] whose text starts at [left]; null for a line with nothing printed. */
        fun of(line: ChordSheet.Line, left: Double?): ChordSheetLook? {
            val spans = line.spans.filter { it.text.isNotBlank() }
            if (spans.isEmpty() || left == null) return null
            return ChordSheetLook(spans.all { it.isBold }, spans.all { it.isItalic }, left, spans.maxOf { it.size })
        }
    }
}
