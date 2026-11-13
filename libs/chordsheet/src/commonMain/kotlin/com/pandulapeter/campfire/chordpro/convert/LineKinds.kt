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
        val isBeatChart: Boolean,
        val isItalicComment: List<Boolean>,
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
        Rule(::label),
        Rule(::staff),
        Rule(::grid) { it.next != LineKind.LYRIC },
        Rule(::heading) { it.next == null || it.next == LineKind.CHORD || it.next == LineKind.BLANK },
        Rule(::metadata),
        Rule(::italicComment),
        Rule(::severalChords),
        Rule(::singleChordUnderHeading) {
            it.previous == LineKind.SECTION && (it.next == null || it.next == LineKind.BLANK || it.next == LineKind.SECTION)
        },
        Rule(::singleChordsAfterRow) { it.next == null || it.next == LineKind.BLANK || it.next == LineKind.SECTION || it.next == LineKind.CHORD },
        Rule(::chordOverLyric) { it.next == LineKind.LYRIC && it.nextAtFirst != LineKind.CHORD },
    )

    fun of(lines: List<Rendered>, features: List<LineFeatures>, candidates: List<List<Token>?>): List<LineKind> {
        val isStaff = lines.indices.map { index ->
            tab.matches(lines[index].text.trim()) && (lines.getOrNull(index - 1)?.let { tab.matches(it.text.trim()) } == true ||
                lines.getOrNull(index + 1)?.let { tab.matches(it.text.trim()) } == true)
        }
        val context = Context(lines, features, candidates, isStaff, hasStructure(lines, candidates), hasUnambiguousChords(candidates), isBeatChart(lines, candidates), italicComments(lines, features, candidates))
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
     * Whether the song places its chords by beats rather than over syllables: its every chord row is a row of bars and
     * beats, and several stand over a line of words. One such row over a lyric may be a chord row marking its bars, its
     * chords over their syllables, but a sheet setting every line under bars and dots is counting time, and taking its
     * cells for columns would set each chord over whatever letter happens to stand under it.
     */
    private fun isBeatChart(lines: List<Rendered>, candidates: List<List<Token>?>): Boolean {
        val isGridRow = lines.map { isGridRow(it.text) }
        val hasOtherChordRow = lines.indices.any { index -> !isGridRow[index] && candidates[index]?.any { rowChord(it.text) } == true }
        val rowsOverWords = lines.indices.count { index ->
            isGridRow[index] && lines.getOrNull(index + 1)?.let { it.text.isNotBlank() && !isGridRow[index + 1] } == true
        }
        return !hasOtherChordRow && rowsOverWords >= MIN_BEAT_ROWS_OVER_WORDS
    }

    private fun Boolean.gives(kind: LineKind) = if (this) Evidence(kind, isStrong = true) else null

    private fun blank(context: Context, index: Int) = context.lines[index].text.isBlank().gives(LineKind.BLANK)

    private fun label(context: Context, index: Int) = context.features[index].isLabel.gives(LineKind.SECTION)

    /**
     * A row of bars holding chords and the beats between them (`| G . . . | C . / . |`), or the rest of one a column
     * wrapped: a chord grid, weak evidence, kept where no lyric follows it, since a chord row over a lyric may mark its
     * bars and beats too, except in a song that counts beats on every row ([isBeatChart]).
     */
    private fun grid(context: Context, index: Int) =
        if (isGridRow(context.lines[index].text)) Evidence(LineKind.GRID, isStrong = context.isBeatChart) else null

    private fun isGridRow(text: String): Boolean {
        val words = tokens(text)
        return words.count { it.text in gridBars } >= 2 && words.any { it.text in gridBeats } && words.any { rowChord(it.text) } &&
            words.all { it.text in gridBars || it.text in gridBeats || rowChord(it.text) }
    }

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
                features.bodyRow != null
            ).gives(LineKind.METADATA)
    }

    private fun italicComment(context: Context, index: Int) = context.isItalicComment[index].gives(LineKind.COMMENT)

    /**
     * Whether each line is a comment by its type (`last time slower`): a line set wholly in italics on its own, in a song
     * whose lyrics are not, before a chord row or a heading. A run of such lines is lyrics set in italics, a chorus as many
     * sheets set one, and so is one under a chord row or before a lyric (a backing vocal); the two lines under a title, or
     * under the song's first line, are its credit and its album.
     */
    private fun italicComments(lines: List<Rendered>, features: List<LineFeatures>, candidates: List<List<Token>?>): List<Boolean> {
        val isItalic = lines.indices.map { index ->
            candidates[index] == null && lines[index].source.spans.filter { it.text.isNotBlank() }.let { spans -> spans.isNotEmpty() && spans.all { it.isItalic } }
        }
        val lyrics = lines.indices.filter { features[it].isSung && candidates[it] == null }
        if (lyrics.count { isItalic[it] } * 2 > lyrics.size) return lines.map { false }
        val profile = ChordSheetProfile.of(lines.map { it.source })
        val filled = lines.indices.filter { lines[it].text.isNotBlank() }
        // Looked up rather than searched for, since a long document has as many lines to ask about as to search.
        val filledAt = IntArray(lines.size) { -1 }.also { at -> filled.forEachIndexed { position, index -> at[index] = position } }
        return lines.indices.map { index ->
            val at = filledAt[index]
            val above = filled.getOrNull(at - 1)
            val isUnderTitle = at < 2 || filled.subList(maxOf(0, at - 2), at).any { other -> lines[other].source.spans.any { profile.isLarger(it.size) } }
            val below = filled.getOrNull(at + 1)
            val opensPart = below != null && (candidates[below] != null || features[below].heading != null || features[below].isLabel || lines[below].isMarginLabel)
            isItalic[index] && above?.let { isItalic[it] || candidates[it] != null } != true && opensPart && !isUnderTitle
        }
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
     * A row of one chord in a run of them right under a row of several chords or a heading (or under runs of them each a
     * blank line apart that start so), and ending at a blank line, a heading, or the song's end: a progression whose last
     * bars, or every bar, stand on lines of their own (`Am Dm` over `Dm`), with no lyric among them for a chord to be set
     * over. Weak evidence, kept where the run goes on below.
     */
    private fun singleChordsAfterRow(context: Context, index: Int): Evidence? {
        fun isSingle(at: Int) = context.candidates.getOrNull(at)?.filter { rowChord(it.text) }?.let { it.size == 1 && it.single().text.first().isUpperCase() } == true
        fun runStart(at: Int) = (at downTo 0).takeWhile(::isSingle).last()
        if (!isSingle(index)) return null
        val last = (index..context.lines.lastIndex).takeWhile(::isSingle).last()
        var above = runStart(index) - 1
        while (above > 0 && context.lines[above].text.isBlank() && isSingle(above - 1)) above = runStart(above - 1) - 1
        val opens = above >= 0 && ((context.candidates[above]?.count { rowChord(it.text) } ?: 0) > 1 || context.features[above].heading != null || context.features[above].isLabel)
        val closes = context.lines.getOrNull(last + 1)?.let { it.text.isBlank() || context.features[last + 1].heading != null } ?: true
        return if (opens && closes) Evidence(LineKind.CHORD, isStrong = false) else null
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
     * over plain lyrics of its size or larger, the way Campfire's export and most songbook layouts set chords, and written
     * with a capital, since a lowercase word is too often a word. A lyric line wrapped in a narrow column is printed
     * as several of these, each with the one chord over its part. The size keeps a songbook index out of it, whose
     * bold "A" over the titles starting with it is a heading set larger than they are; a lyric whose letters fall back
     * on another font than the chords' (a right-to-left script) is written larger than they are.
     */
    private fun isStyledChordLine(line: Rendered, tokens: List<Token>, next: Rendered?): Boolean {
        val spans = line.source.spans.filter { it.text.isNotBlank() }
        val lyrics = next?.source?.spans.orEmpty().filter { it.text.isNotBlank() && !it.isBold }
        return spans.isNotEmpty() && spans.all { it.isBold } && lyrics.isNotEmpty() &&
            tokens.all { furniture(it.text) || it.text.first().isUpperCase() } &&
            spans.all { span -> lyrics.all { ChordSheetProfile.isSameSize(it.size, span.size) || it.size > span.size } }
    }

    /** Rows of bars over words it takes before a song reads as counting beats rather than marking bars over syllables. */
    private const val MIN_BEAT_ROWS_OVER_WORDS = 3

    private val gridBars = setOf("|", "||", "|:", ":|", "||:", ":||", ":|:", "|.")
    private val gridBeats = setOf(".", "/", "%")
    private val bracketedLine = Regex("^\\[([^\\[\\]]+)\\]$")
    private val tab = Regex("^[eEaAbBdDgG](?:[#b])?\\s*\\|[-0-9|hpbrx/\\\\~(). :]+$")
}
