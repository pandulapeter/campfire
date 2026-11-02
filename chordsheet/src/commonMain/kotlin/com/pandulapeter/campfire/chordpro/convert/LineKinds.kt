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

import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.chord
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.chordTokens
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.furniture
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.rowChord
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.isChordLine
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.labelledRow
import com.pandulapeter.campfire.chordpro.syntax.ChordProTokens
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary

/** What a line of a song is to the converter, decided for each line before the song is written. */
internal enum class LineKind { BLANK, TAB, GRID, COMMENT, SECTION, METADATA, CHORD, LYRIC }

/**
 * The kind of each of a song's lines, given by the first of a list of named rules that has evidence for one, in the order
 * they are listed; a line no rule speaks for is a lyric.
 */
internal object LineKinds {

    /** What a rule says a line is, and whether it says so on the line's own evidence ([isStrong]). */
    data class Evidence(val kind: LineKind, val isStrong: Boolean)

    private class Context(
        val lines: List<Rendered>,
        val features: List<LineFeatures>,
        val candidates: List<List<Token>?>,
        val headings: List<Boolean>,
        val isStaff: List<Boolean>,
        val hasUnambiguousChords: Boolean,
    )

    private val rules: List<(Context, Int) -> Evidence?> = listOf(
        ::blank,
        ::exportLabel,
        ::exportGrid,
        ::exportComment,
        ::staff,
        ::heading,
        ::metadata,
        ::notesOnly,
        ::exportContinuation,
        ::exportOverflow,
        ::severalChords,
        ::singleChordUnderHeading,
        ::chordOverLyric,
    )

    fun of(lines: List<Rendered>, features: List<LineFeatures>, candidates: List<List<Token>?>): List<LineKind> {
        val hasStructure = hasStructure(lines, candidates)
        // A word that is as likely sung is a heading where a chord row or a blank line follows it, as one does a heading.
        val headings = lines.indices.map { index ->
            features[index].heading?.let { !it.isAmbiguous || candidates.getOrNull(index + 1) != null || lines.getOrNull(index + 1)?.text?.isBlank() != false }
                ?: (hasStructure && isBracketedHeading(lines[index].text))
        }
        // Campfire's own export is read by the test it engraves a staff by, which knows every instrument's strings.
        val isStaff = lines.indices.map { index ->
            lines[index].marks != null && ChordProTokens.isStaffLine(lines[index].text) ||
                tab.matches(lines[index].text.trim()) && (lines.getOrNull(index - 1)?.let { tab.matches(it.text.trim()) } == true ||
                    lines.getOrNull(index + 1)?.let { tab.matches(it.text.trim()) } == true)
        }
        val context = Context(lines, features, candidates, headings, isStaff, hasUnambiguousChords(candidates))
        return lines.indices.map { index -> rules.firstNotNullOfOrNull { rule -> rule(context, index) }?.kind ?: LineKind.LYRIC }
    }

    internal fun hasUnambiguousChords(candidates: List<List<Token>?>) = candidates.any { it != null && it.count { word -> rowChord(word.text) } > 1 }

    /** Whether a sheet reads as a song rather than prose: a row of two chords or more, or a heading. */
    internal fun hasStructure(lines: List<Rendered>, candidates: List<List<Token>?>) =
        hasUnambiguousChords(candidates) || lines.any { ChordSheetHeadings.parse(it.text) != null }

    /**
     * Whether [text] is a key alone on its row of Campfire's export ([line] one of it), which the export prints only as
     * a key change, so that it is metadata anywhere in the song, not only among the heading's rows.
     */
    internal fun isKeyChange(line: Rendered, text: String) =
        line.marks != null && labelledRow(text, isHeader = true)?.singleOrNull()?.first == ChordProVocabulary.KEY

    private fun Boolean.gives(kind: LineKind) = if (this) Evidence(kind, isStrong = true) else null

    private fun blank(context: Context, index: Int) = context.lines[index].text.isBlank().gives(LineKind.BLANK)

    private fun exportLabel(context: Context, index: Int) =
        (context.lines[index].marks?.role == ChordSheetCampfireLayout.Role.LABEL).gives(LineKind.SECTION)

    private fun exportGrid(context: Context, index: Int) =
        (context.lines[index].marks?.role == ChordSheetCampfireLayout.Role.GRID).gives(LineKind.GRID)

    private fun exportComment(context: Context, index: Int) = context.lines[index].marks?.role.let {
        it == ChordSheetCampfireLayout.Role.COMMENT || it == ChordSheetCampfireLayout.Role.BOX_COMMENT
    }.gives(LineKind.COMMENT)

    /**
     * A row of chord names right over a staff names its bars, its columns the staff's, so it stays in the tab as it stands
     * rather than being set over a lyric line it was never written for.
     */
    private fun staff(context: Context, index: Int) =
        (context.isStaff[index] || context.candidates[index] != null && context.isStaff.getOrNull(index + 1) == true).gives(LineKind.TAB)

    private fun heading(context: Context, index: Int) = (context.headings[index] || context.lines[index].isMarginLabel).gives(LineKind.SECTION)

    private fun metadata(context: Context, index: Int): Evidence? {
        val features = context.features[index]
        return (
            ChordSheetProfile.isInHeader(index) && (features.metadata?.second?.isNotBlank() == true || features.headerRow != null) ||
                features.metadata?.first == "copyright" ||
                features.bodyRow != null || isKeyChange(context.lines[index], context.lines[index].text)
            ).gives(LineKind.METADATA)
    }

    /** A row of notes alone is a candidate only where the row under it continues its line. */
    private fun notesOnly(context: Context, index: Int) = (context.candidates[index]?.isEmpty() == true).gives(LineKind.CHORD)

    /**
     * A row of chords the export marks as the rest of a line is one whatever follows, and so is the row its chords
     * overflowed from ([exportOverflow]); only an export's chords in their own type are candidates, so a wrapped lyric's
     * marked rest that reads as chord names (`a b`) is never taken for one.
     */
    private fun exportContinuation(context: Context, index: Int): Evidence? {
        val candidate = context.candidates[index] ?: return null
        return (candidate.isNotEmpty() && context.lines[index].marks?.isContinuation == true).gives(LineKind.CHORD)
    }

    private fun exportOverflow(context: Context, index: Int): Evidence? {
        val candidate = context.candidates[index] ?: return null
        return (
            candidate.isNotEmpty() &&
                context.lines.getOrNull(index + 1)?.let { it.marks?.isContinuation == true && context.candidates.getOrNull(index + 1) != null } == true
            ).gives(LineKind.CHORD)
    }

    private fun severalChords(context: Context, index: Int): Evidence? {
        val candidate = context.candidates[index] ?: return null
        return (candidate.count { rowChord(it.text) } > 1).gives(LineKind.CHORD)
    }

    private fun singleChordUnderHeading(context: Context, index: Int): Evidence? {
        val candidate = context.candidates[index] ?: return null
        return (
            candidate.count { rowChord(it.text) } == 1 &&
                candidate.filter { rowChord(it.text) }.all { it.text.first().isUpperCase() } &&
                context.headings.getOrNull(index - 1) == true &&
                context.lines.getOrNull(index + 1)?.let { it.text.isBlank() || context.headings[index + 1] } != false
            ).gives(LineKind.CHORD)
    }

    private fun chordOverLyric(context: Context, index: Int): Evidence? {
        val candidate = context.candidates[index] ?: return null
        val line = context.lines[index]
        return (
            (context.hasUnambiguousChords || isStyledChordLine(line, candidate, context.lines.getOrNull(index + 1))) &&
                context.lines.getOrNull(index + 1)?.let {
                    it.text.isNotBlank() && chordTokens(it) == null && !context.headings[index + 1] && context.features[index + 1].metadata == null
                } == true
            ).gives(LineKind.CHORD)
    }

    /**
     * A line in brackets is a heading of a name no list holds (`[Riff]`, `[Primeira Parte]`), where the sheet has
     * structure to say it is a song: in prose brackets as likely hold a note, and a chord in them is the chord.
     */
    private fun isBracketedHeading(text: String): Boolean {
        val content = bracketedLine.matchEntire(text.trim())?.groupValues?.get(1)?.trim() ?: return false
        return content.isNotEmpty() && !chord(content) && !isChordLine(content) && content.split(' ').count { it.isNotEmpty() } <= 6
    }

    /**
     * Whether a line of a single chord is one by its type, where no line of the song holds two to tell: set in bold
     * over plain lyrics of the same size, the way Campfire's export and most songbook layouts set chords, and written
     * with a capital, since a lowercase word is too often a word. A lyric line wrapped in a narrow column is printed
     * as several of these, each with the one chord over its part. The size keeps a songbook index out of it, whose
     * bold "A" over the titles starting with it is a heading set larger than they are. Campfire's export is not held to
     * it: only a row in its chords' type is a candidate there, the title's left out, and a lyric whose letters fall back
     * on another font than the chords' is written at another size than they are.
     */
    private fun isStyledChordLine(line: Rendered, tokens: List<Token>, next: Rendered?): Boolean {
        val spans = line.source.spans.filter { it.text.isNotBlank() }
        val lyrics = next?.source?.spans.orEmpty().filter { it.text.isNotBlank() && !it.isBold }
        return spans.isNotEmpty() && spans.all { it.isBold } && lyrics.isNotEmpty() &&
            tokens.all { furniture(it.text) || it.text.first().isUpperCase() } &&
            (line.marks != null || spans.all { span -> lyrics.all { ChordSheetProfile.isSameSize(it.size, span.size) } })
    }

    private val bracketedLine = Regex("^\\[([^\\[\\]]+)\\]$")
    private val tab = Regex("^[eEaAbBdDgG](?:[#b])?\\s*\\|[-0-9|hpbrx/\\\\~(). :]+$")
}
