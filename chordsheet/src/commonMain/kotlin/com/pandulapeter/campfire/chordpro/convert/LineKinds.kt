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
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.furniture
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.rowChord
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.tokens
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.isChordLine
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.labelledRow
import com.pandulapeter.campfire.chordpro.syntax.ChordProTokens
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary

/** What a line of a song is to the converter, decided for each line before the song is written. */
internal enum class LineKind { BLANK, TAB, GRID, COMMENT, SECTION, METADATA, CHORD, LYRIC }

/**
 * The kind of each of a song's lines, given by the first of a list of named rules that has evidence for one, in the order
 * they are listed; a line no rule speaks for is a lyric.
 *
 * A rule that reads a line on its own gives strong evidence. One that guesses from a neighbour (a word as likely sung
 * as a heading, a single chord, a chord row over a lyric) gives weak evidence, its kind without the neighbour, which a
 * second pass keeps or drops by the kinds around it: walking from the last line up, a line's next neighbour is already
 * resolved and its previous one has its first-pass kind. A dropped kind falls to the next rule with evidence.
 */
internal object LineKinds {

    /** What a rule says a line is, and whether it says so on the line's own evidence ([isStrong]). */
    data class Evidence(val kind: LineKind, val isStrong: Boolean)

    private class Context(
        val lines: List<Rendered>,
        val features: List<LineFeatures>,
        val candidates: List<List<Token>?>,
        val isStaff: List<Boolean>,
        val hasStructure: Boolean,
        val hasUnambiguousChords: Boolean,
    )

    /**
     * The kinds around a line in the second pass: the [previous] line's first-pass kind, the [next] line's resolved one,
     * and the next line's first-pass kind ([nextAtFirst]), each null past the song's edge.
     */
    private class Neighbours(val previous: LineKind?, val next: LineKind?, val nextAtFirst: LineKind?)

    /** A rule, and whether its weak evidence stands between its [Neighbours] ([keeps]). */
    private class Rule(val give: (Context, Int) -> Evidence?, val keeps: (Neighbours) -> Boolean = { true })

    private val rules = listOf(
        Rule(::blank),
        Rule(::exportLabel),
        Rule(::label),
        Rule(::exportGrid),
        Rule(::exportComment),
        Rule(::staff),
        Rule(::grid) { it.next != LineKind.LYRIC },
        Rule(::heading) { it.next == null || it.next == LineKind.CHORD || it.next == LineKind.BLANK },
        Rule(::metadata),
        Rule(::notesOnly),
        Rule(::exportContinuation),
        Rule(::exportOverflow),
        Rule(::severalChords),
        Rule(::singleChordUnderHeading) {
            it.previous == LineKind.SECTION && (it.next == null || it.next == LineKind.BLANK || it.next == LineKind.SECTION)
        },
        Rule(::chordOverLyric) { it.next == LineKind.LYRIC && it.nextAtFirst != LineKind.CHORD },
    )

    fun of(lines: List<Rendered>, features: List<LineFeatures>, candidates: List<List<Token>?>): List<LineKind> {
        // Campfire's own export is read by the test it engraves a staff by, which knows every instrument's strings.
        val isStaff = lines.indices.map { index ->
            lines[index].marks != null && ChordProTokens.isStaffLine(lines[index].text) ||
                tab.matches(lines[index].text.trim()) && (lines.getOrNull(index - 1)?.let { tab.matches(it.text.trim()) } == true ||
                    lines.getOrNull(index + 1)?.let { tab.matches(it.text.trim()) } == true)
        }
        val context = Context(lines, features, candidates, isStaff, hasStructure(lines, candidates), hasUnambiguousChords(candidates))
        val firstPass = lines.indices.map { index -> rules.firstNotNullOfOrNull { rule -> rule.give(context, index) }?.kind ?: LineKind.LYRIC }
        val kinds = firstPass.toMutableList()
        for (index in lines.indices.reversed()) {
            val neighbours = Neighbours(firstPass.getOrNull(index - 1), kinds.getOrNull(index + 1), firstPass.getOrNull(index + 1))
            kinds[index] = rules.firstNotNullOfOrNull { rule ->
                rule.give(context, index)?.takeIf { it.isStrong || rule.keeps(neighbours) }
            }?.kind ?: LineKind.LYRIC
        }
        return kinds
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

    private fun label(context: Context, index: Int) = context.features[index].isLabel.gives(LineKind.SECTION)

    private fun exportGrid(context: Context, index: Int) =
        (context.lines[index].marks?.role == ChordSheetCampfireLayout.Role.GRID).gives(LineKind.GRID)

    /**
     * A row of bars holding chords and the beats between them (`| G . . . | C . / . |`), or the rest of one a column
     * wrapped: a chord grid, weak evidence, kept where no lyric follows it, since a chord row over a lyric may mark its
     * bars and beats too.
     */
    private fun grid(context: Context, index: Int): Evidence? {
        val words = tokens(context.lines[index].text)
        val bars = words.count { it.text in gridBars }
        val isGrid = bars >= 2 && words.any { it.text in gridBeats } && words.any { rowChord(it.text) } &&
            words.all { it.text in gridBars || it.text in gridBeats || rowChord(it.text) }
        return if (isGrid) Evidence(LineKind.GRID, isStrong = false) else null
    }

    private fun exportComment(context: Context, index: Int) = context.lines[index].marks?.role.let {
        it == ChordSheetCampfireLayout.Role.COMMENT || it == ChordSheetCampfireLayout.Role.BOX_COMMENT
    }.gives(LineKind.COMMENT)

    /**
     * A row of chord names right over a staff names its bars, its columns the staff's, so it stays in the tab as it stands
     * rather than being set over a lyric line it was never written for.
     */
    private fun staff(context: Context, index: Int) =
        (context.isStaff[index] || context.candidates[index] != null && context.isStaff.getOrNull(index + 1) == true).gives(LineKind.TAB)

    /**
     * A heading, a margin label, or a bracketed name. A word that is as likely sung is weak evidence, kept where a chord
     * row, a blank line, or the song's end follows it, as one does a heading.
     */
    private fun heading(context: Context, index: Int): Evidence? {
        val heading = context.features[index].heading
        return when {
            context.lines[index].isMarginLabel -> Evidence(LineKind.SECTION, isStrong = true)
            heading != null -> Evidence(LineKind.SECTION, isStrong = !heading.isAmbiguous)
            else -> (context.hasStructure && isBracketedHeading(context.lines[index].text)).gives(LineKind.SECTION)
        }
    }

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

    /**
     * A row of one chord, written with a capital: weak evidence, kept under a heading where nothing is set under it, a
     * blank line, a heading, or the song's end following it.
     */
    private fun singleChordUnderHeading(context: Context, index: Int): Evidence? {
        val chords = context.candidates[index]?.filter { rowChord(it.text) } ?: return null
        return if (chords.size == 1 && chords.all { it.text.first().isUpperCase() }) Evidence(LineKind.CHORD, isStrong = false) else null
    }

    /**
     * A chord row, where the song has a row of several chords or the row is set as chords are: weak evidence, kept over a
     * lyric, unless that line was a chord row in the first pass: a chord row dropped to a lyric (a diagram's name over a
     * blank line) is no lyric to set chords over.
     */
    private fun chordOverLyric(context: Context, index: Int): Evidence? {
        val candidate = context.candidates[index] ?: return null
        val isChordRow = context.hasUnambiguousChords || isStyledChordLine(context.lines[index], candidate, context.lines.getOrNull(index + 1))
        return if (isChordRow) Evidence(LineKind.CHORD, isStrong = false) else null
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

    private val gridBars = setOf("|", "||", "|:", ":|", "||:", ":||", ":|:", "|.")
    private val gridBeats = setOf(".", "/", "%")
    private val bracketedLine = Regex("^\\[([^\\[\\]]+)\\]$")
    private val tab = Regex("^[eEaAbBdDgG](?:[#b])?\\s*\\|[-0-9|hpbrx/\\\\~(). :]+$")
}
