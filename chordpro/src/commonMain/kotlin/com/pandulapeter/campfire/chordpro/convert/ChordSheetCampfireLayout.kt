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

import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.language.writingDirectionOf

/**
 * What the layout of Campfire's own PDF export ([ChordSheet.isCampfireExport]) says about a line, which is read rather
 * than guessed, since the export prints each kind of line in a type of its own: a section's label bold and a point
 * smaller than the lyrics, a grid bold in the monospaced face, a comment in italics, and a boxed comment's text 5 points
 * in from the lines around it, on the song's side, inside a frame the text layer does not carry. Every other line is
 * read as any sheet's is.
 */
internal object ChordSheetCampfireLayout {

    enum class Role { LABEL, GRID, COMMENT, BOX_COMMENT }

    /**
     * The role of each line of a page, by the type it is set in and where it starts or, in a song printed from the
     * right, where it ends. [isChordRow] says which lines are rows of chords, the converter's own test of one.
     */
    fun roles(lines: List<ChordSheet.Line>, isChordRow: List<Boolean>, bodySize: Double): List<Role?> {
        // A line's first space counts: a lyric indented by spaces starts at the column, where its first one is.
        val starts = lines.map { line -> line.spans.firstOrNull()?.start }
        // Reading order is not the order on the page, so a right-to-left row ends where its rightmost glyph does.
        val ends = lines.map { line -> line.spans.filter { it.text.isNotBlank() }.maxOfOrNull { it.end } }
        val songs = songsOf(lines, bodySize)
        val roles = mutableListOf<Role?>()
        lines.forEachIndexed { index, line ->
            val spans = line.spans.filter { it.text.isNotBlank() }
            val start = starts[index]
            val end = ends[index]
            val song = songs.first { index in it }
            roles += when {
                spans.isEmpty() || start == null || end == null -> null
                spans.all { it.isBold && it.isMonospace } -> Role.GRID
                spans.all { it.isBold && !it.isMonospace && it.size < bodySize - LABEL_SIZE_STEP / 2 } -> Role.LABEL
                spans.all { it.isItalic } -> Role.COMMENT.takeUnless { isItalicLyric(lines, isChordRow, roles, index) }
                !line.isPlain() -> null
                isFlushRight(lines, starts, ends, song, index) -> Role.BOX_COMMENT.takeIf { isBoxed(song.mapNotNull { ends[it] }.map { -it }, -end) }
                isBoxed(song.mapNotNull { starts[it] }, start) -> Role.BOX_COMMENT
                else -> null
            }
        }
        return roles
    }

    /**
     * Whether a line wholly in italics is a lyric set in `<i>` rather than a comment, which differs from it only by a
     * gray the text layer does not keep. The two are told apart by the line before: a lyric under its chords is one row
     * of the export, its text 1.3 times its size under them, while a comment after a row of chords alone (an intro's,
     * then `x2`) is the next row, 1.45 times its size under. A lyric wrapped onto the next row is a lyric too; a comment
     * printed right under an all-italic lyric is then read as one more of its lines, the rarer of the two to meet. A
     * line whose position is unknown, or one higher on the page than the line before (the next column), stays a
     * comment.
     */
    private fun isItalicLyric(lines: List<ChordSheet.Line>, isChordRow: List<Boolean>, roles: List<Role?>, index: Int): Boolean {
        val previous = (index - 1 downTo 0).firstOrNull { other -> lines[other].spans.any { it.text.isNotBlank() } } ?: return false
        val baseline = lines[index].baseline ?: return false
        val gap = baseline - (lines[previous].baseline ?: return false)
        val size = lines[index].spans.filter { it.text.isNotBlank() }.maxOf { it.size }
        return when {
            gap <= 0.0 -> false
            isChordRow[previous] -> gap < size * LYRIC_UNDER_CHORDS
            roles[previous] == null && lines[previous].spans.filter { it.text.isNotBlank() }.all { it.isItalic } -> gap < size * WRAPPED_ROW
            else -> false
        }
    }

    /**
     * Whether a line's [edge] sits 5 points in from some other line's and none lies between the two: the box against the
     * lines around it rather than the column, which also finds a box inside a chorus, whose lines are indented by more.
     * The edges of a song printed from the right are passed negated, so that "in" is always the larger value.
     */
    private fun isBoxed(edges: List<Double>, edge: Double) = edges.any { other -> edge - other in BOX_INSET - TOLERANCE..BOX_INSET + TOLERANCE } &&
        edges.none { other -> edge - other in TOLERANCE..BOX_INSET - TOLERANCE }

    /**
     * The lines of each song on the page, cut at the titles, since one page may end a song printed from the left and
     * start one printed from the right. The lines before the first title are the song the page continues.
     */
    private fun songsOf(lines: List<ChordSheet.Line>, bodySize: Double): List<IntRange> {
        val titles = lines.indices.filter { index ->
            val spans = lines[index].spans.filter { it.text.isNotBlank() }
            spans.isNotEmpty() && spans.all { it.isBold && !it.isMonospace && it.size >= bodySize * TITLE_SIZE_RATIO }
        }
        return (listOf(0) + titles + lines.size).distinct().zipWithNext { from, until -> from until until }
    }

    /**
     * Whether the song holding the line at [index] is printed flush right. The export sets every row of a right-to-left
     * song from the right, an English comment or lyric in it too, so the side is the song's, never the line's own
     * letters: more of its plain lines end at its right edge than start at its left one, and a tie (one line, lines of
     * one width) goes to the letters of those lines. Only the lines beside the candidate count, so that each column of
     * a two-column page is judged by its own edges.
     */
    private fun isFlushRight(lines: List<ChordSheet.Line>, starts: List<Double?>, ends: List<Double?>, song: IntRange, index: Int): Boolean {
        val from = starts[index] ?: return false
        val until = ends[index] ?: return false
        val column = song.mapNotNull { other ->
            val otherStart = starts[other]
            val otherEnd = ends[other]
            if (otherStart != null && otherEnd != null && lines[other].isPlain() && otherStart < until && otherEnd > from) Triple(other, otherStart, otherEnd) else null
        }
        val left = column.minOf { it.second }
        val right = column.maxOf { it.third }
        val flushLeft = column.count { it.second - left <= TOLERANCE }
        val flushRight = column.count { right - it.third <= TOLERANCE }
        return if (flushRight != flushLeft) flushRight > flushLeft else {
            writingDirectionOf(column.joinToString(" ") { (other) -> lines[other].spans.joinToString("") { it.text } }) == WritingDirection.RIGHT_TO_LEFT
        }
    }

    /** Whether a line is set in the lyrics' type: text, none of it bold, italic, or monospaced. */
    private fun ChordSheet.Line.isPlain() = spans.filter { it.text.isNotBlank() }.let { spans ->
        spans.isNotEmpty() && spans.none { it.isBold || it.isItalic || it.isMonospace }
    }

    /**
     * The section a label opens: a verse, chorus, bridge, or pre-chorus by the heading word it opens with, in any language
     * the app prints its labels in ([ChordSheetHeadings.labelType]), any other kind the export names by its last word
     * (`Intro`, `Organ solo`) or the app's label for it, and a verse under its own name otherwise.
     */
    fun sectionType(label: String): String = ChordSheetHeadings.labelType(label)
        ?: label.trim().lowercase().split(' ').lastOrNull { it.isNotEmpty() }?.let { word -> word.takeIf { it in customTypes } ?: localizedTypes[word] }
        ?: "verse"

    /** The no-break spaces the export pads a lyric with under a chord wider than its syllable, which are not its words. */
    const val PADDING = '\u00A0'

    private val customTypes = setOf("intro", "outro", "solo", "interlude", "instrumental", "coda", "tag", "ending", "break", "riff")

    /** The app's labels for the kinds [customTypes] names, where its language spells them otherwise. */
    private val localizedTypes = mapOf(
        "sz\u00f3l\u00f3" to "solo",
        "assolo" to "solo",
        "\u05e4\u05ea\u05d9\u05d7\u05d4" to "intro",
        "\u05e1\u05d5\u05dc\u05d5" to "solo",
        "\u05e1\u05d9\u05d5\u05dd" to "outro",
    )

    /** The labels are a point smaller than the lyrics (`PrintRows.labelStyle`). */
    private const val LABEL_SIZE_STEP = 1.0

    /** How far in from its frame a boxed comment's text starts (`PrintRows.boxedRows`), in points. */
    private const val BOX_INSET = 5.0
    private const val TOLERANCE = 0.75

    /**
     * Between a lyric's distance under its chords (1.3 times its size, `PrintLyricRows`) and the next row's (1.45 times,
     * `PrintRows`).
     */
    private const val LYRIC_UNDER_CHORDS = 1.375

    /** Just over the distance between rows (`PrintRows`, 1.45 times the size), which a wrapped lyric continues at. */
    private const val WRAPPED_ROW = 1.5

    /** A song's title is set at 1.6 times the lyrics' size (`PrintRows.titleStyle`), over anything else in bold. */
    private const val TITLE_SIZE_RATIO = 1.3
}
