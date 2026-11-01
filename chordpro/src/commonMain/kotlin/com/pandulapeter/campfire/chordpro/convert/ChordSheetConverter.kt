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

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChordNames
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.edit.ChordProLiteralText
import com.pandulapeter.campfire.chordpro.syntax.ChordProDirectives
import com.pandulapeter.campfire.chordpro.syntax.ChordProRowMarks
import com.pandulapeter.campfire.chordpro.syntax.ChordProTokens
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.ANNOTATION_MARKER
import kotlin.math.abs
import kotlin.math.roundToInt

/** Deterministic, conservative conversion of positioned chord sheets to ordinary ChordPro. */
public object ChordSheetConverter {
    /** Single-chord cells are unambiguous in alternating rows of a Word chord grid. */
    public fun isChordLine(text: String): Boolean = tokens(text).let { words ->
        words.any { chord(it.text) } && words.all { chord(it.text) || furniture(it.text) }
    }
    /**
     * Returns one text per conservatively detected song. Existing ChordPro is returned untouched, including
     * [ChordSheet.originalText] where supplied. Other prose is escaped so it cannot become ChordPro markup.
     * The caller supplies Unicode NFC normalization; this dependency-free module has no platform normalizer.
     *
     * @param guessesTitle false for a caller that knows the song's title from elsewhere (another app's field): the first
     * lines are then read as the song's lines, the title and the credit under it included.
     */
    public fun convert(sheet: ChordSheet, normalize: (String) -> String = { it }, guessesTitle: Boolean = true): List<String> {
        sheet.originalText?.takeIf(::isChordPro)?.let { return listOf(it) }
        val rendered = sheet.pages.map { page ->
            // Campfire's export prints no header in parts, and its chord rows are told apart by their type, which the
            // split's test by text cannot see: there it would cut a row opening with an annotation at a gap.
            val placedLines = if (sheet.isCampfireExport) page.lines.map { PlacedLine(it) } else ChordSheetHeaderParts.split(page.lines)
            ChordSheetMarginLabels.split(placedLines).map { placed ->
                render(placed.line, normalize, dropsPadding = sheet.isCampfireExport).copy(isMarginLabel = placed.isMarginLabel, isHeaderPart = placed.isHeaderPart)
            }.let { lines -> if (sheet.isCampfireExport) markRowsContinuedBelow(lines) else lines }
        }
        val pages = if (!sheet.isCampfireExport) rendered else {
            val isChordRowLine = rendered.map { page -> page.map { it.text.isNotBlank() && chordRow(it, isCampfire = true) != null } }
            val typeSizes = ChordSheetCampfireLayout.Sizes.of(rendered.map { page -> page.map { it.source } }, isChordRowLine)
            rendered.mapIndexed { pageIndex, page ->
                val roles = ChordSheetCampfireLayout.roles(page.map { it.source }, isChordRowLine[pageIndex], typeSizes)
                val isSetFromRight = ChordSheetCampfireLayout.isSetFromRight(page.map { it.source }, typeSizes.body)
                page.mapIndexed { index, line ->
                    line.copy(role = roles[index], isCampfire = true, bodySize = typeSizes.lyrics ?: 0.0, isSetFromRight = isSetFromRight[index])
                }
            }
        }
        val all = pages.flatten()
        val original = sheet.originalText ?: all.joinToString("\n") { it.text }
        if (isChordPro(original)) return listOf(original)
        val sizes = all.flatMap { line -> line.source.spans.filter { it.text.isNotBlank() }.map { it.size } }.sorted()
        val starts = ChordSheetPageSplit.starts(pages.map(::lead), sizes.getOrNull(sizes.size / 2) ?: 1.0)
        val groups = mutableListOf<MutableList<Rendered>>()
        pages.forEachIndexed { index, page ->
            if (starts[index] == ChordSheetPageSplit.Start.SONG) groups += mutableListOf<Rendered>()
            val lead = page.indexOfFirst { it.text.isNotBlank() }
            groups.last().addAll(if (starts[index] == ChordSheetPageSplit.Start.CONTINUATION) page.drop(lead + 1) else page)
        }
        return groups.map { normalize(convertSong(expandLabels(joinWrappedDetails(withoutInjections(singleSpaced(it)))), guessesTitle)) }.filter { it.isNotBlank() }
    }

    /**
     * The lines a lyrics site puts into a copy of its page, Genius's "You might also like" and the "12Embed" its
     * share count ends one with, glued to the last lyric where the copy lost the line break. Only a song is read for
     * them: a poem may well end in "Embed".
     */
    private fun withoutInjections(lines: List<Rendered>): List<Rendered> {
        if (!hasStructure(lines, chordCandidates(lines))) return lines
        val last = lines.indexOfLast { it.text.isNotBlank() }
        return lines.mapIndexedNotNull { index, line ->
            when {
                line.text.trim().equals("You might also like", ignoreCase = true) -> null
                index != last -> line
                embed.matches(line.text.trim()) -> null
                else -> gluedEmbed.find(line.text)?.let { match ->
                    val end = match.range.first
                    line.copy(text = line.text.substring(0, end), positions = line.positions.copyOf(end))
                } ?: line
            }
        }
    }

    /**
     * A page copied with every line as a paragraph of its own has a blank line after each, which would keep every chord
     * row from the lyric under it. Where no two lines of a song are adjacent, the single blanks are dropped and a run of
     * them is the one stanza break it stands for.
     */
    private fun singleSpaced(lines: List<Rendered>): List<Rendered> {
        val filled = lines.indices.filter { lines[it].text.isNotBlank() }
        if (filled.size < 4 || filled.zipWithNext().any { (a, b) -> b == a + 1 }) return lines
        if (chordCandidates(lines).none { row -> row != null && row.count { rowChord(it.text) } >= 2 }) return lines
        return lines.filterIndexed { index, line ->
            line.text.isNotBlank() || index > 0 && lines[index - 1].text.isBlank() && lines.getOrNull(index - 2)?.text?.isBlank() != true
        }
    }

    /**
     * Lines of plain [text] pasted into a song that already has its header, read the way [convert] reads a sheet: chord
     * rows set into the lyric rows under them (a chord row with no lyrics under it bracketed on a line of its own),
     * headings such as "Verse 1:" made sections, tablature put in its environment, and the rest escaped so it cannot
     * become markup. What only a whole song has is left out: no title or credit is guessed from the first lines, and a
     * line naming a capo, a key or a tempo stays a line of text rather than becoming the song's header in its middle.
     * Null where [text] is ChordPro already or has neither a chord row to set nor a bracketed heading (`[Verse]`, which
     * ChordPro would read as a chord), which is when there is nothing to convert.
     */
    public fun convertFragment(text: String, normalize: (String) -> String = { it }): String? {
        if (isChordPro(text)) return null
        val lines = expandLabels(ChordSheet.ofPlainText(text).pages.single().lines.map { render(it, normalize) })
        val candidates = chordCandidates(lines)
        val kinds = classify(lines, candidates).map { if (it == Kind.METADATA) Kind.LYRIC else it }
        // A bracketed heading pasted as it is would read as a chord, so it is reason enough to convert lyrics with no chords.
        if (Kind.CHORD !in kinds && kinds.indices.none { kinds[it] == Kind.SECTION && lines[it].text.trimStart().startsWith('[') }) return null
        // The line breaks the paste ends with are kept after the section it opened rather than inside it, so that what
        // follows the paste in the song is not pulled into the section, nor the section given an empty last line.
        val trailingBlanks = kinds.takeLastWhile { it == Kind.BLANK }.size
        val body = lines.size - trailingBlanks
        return (
            writeBody(
                lines = lines.take(body),
                kinds = kinds.take(body),
                candidates = candidates.take(body),
                omitted = emptySet(),
                changes = emptyMap(),
                convertParentheses = convertsParentheses(lines),
            ) + List(trailingBlanks) { "" }
            ).joinToString("\n")
    }

    /**
     * Lines of plain [text] a format marks itself (OpenSong's `.` rows), with the rows at [chordRows] (line indexes) set
     * into the lyric line under each the way [convertFragment] sets a chord row, or bracketed on a line of their own
     * where no lyric follows. Nothing is guessed: no other line is taken for chords, a heading, metadata, or tablature,
     * and a marked row holding anything but chords (and the playing note a row may end in) stays a line of text. Every
     * line that is not set is escaped, as [ChordSheetLyrics.escaped] does.
     */
    public fun setChordRows(text: String, chordRows: Set<Int>, normalize: (String) -> String = { it }): String {
        val lines = ChordSheet.ofPlainText(text).pages.single().lines.map { render(it, normalize) }
        val candidates = lines.map(::chordTokens)
        val kinds = lines.indices.map { index ->
            when {
                lines[index].text.isBlank() -> Kind.BLANK
                index in chordRows && candidates[index] != null -> Kind.CHORD
                else -> Kind.LYRIC
            }
        }
        return writeBody(lines, kinds, candidates, omitted = emptySet(), changes = emptyMap(), convertParentheses = false)
            .joinToString("\n")
    }

    /**
     * [isFromLabel] is the chord row a heading's label was followed by on its line, which no lyric is written under;
     * [isContinuation] a lyric row of Campfire's export that continues the line above ([ChordSheetCampfireLayout.CONTINUATION]),
     * and [isHardCut] one that continues a word of it, opening with two marks, which is joined back with no space.
     */
    private data class Rendered(
        val text: String,
        val positions: DoubleArray,
        val source: ChordSheet.Line,
        val isFromLabel: Boolean = false,
        val isMarginLabel: Boolean = false,
        val isHeaderPart: Boolean = false,
        /** What the layout of Campfire's own export says the line is, where the sheet is one ([isCampfire]). */
        val role: ChordSheetCampfireLayout.Role? = null,
        val isCampfire: Boolean = false,
        /** Where the chords over a row holding right-to-left text in reading order hang from. */
        val bidi: ChordSheetBidiAnchors? = null,
        /** The lyrics' size in Campfire's own export ([isCampfire]), which its chords are printed at; 0 where it is unknown. */
        val bodySize: Double = 0.0,
        val isContinuation: Boolean = false,
        val isHardCut: Boolean = false,
        /**
         * Whether the row under this one in Campfire's export continues its line in the chords' or the lyrics' type, which
         * makes a row of notes alone the chord row of that line.
         */
        val isContinuedBelow: Boolean = false,
        /** Whether the line belongs to a song Campfire's export printed from the right, a row of its chords alone too. */
        val isSetFromRight: Boolean = false,
    ) {
        /** What goes between the row this one continues and its text. */
        val joint get() = if (isHardCut) "" else " "
    }
    private data class Token(val text: String, val index: Int)
    private enum class Kind { BLANK, TAB, GRID, COMMENT, SECTION, METADATA, CHORD, LYRIC }

    /** Primitive storage keeps an allowed large text file from allocating one boxed Double per character. */
    private class Positions(capacity: Int) {
        private var values = DoubleArray(capacity.coerceAtLeast(16))
        private var size = 0
        fun add(value: Double) {
            if (size == values.size) values = values.copyOf(values.size + values.size / 2)
            values[size++] = value
        }
        fun take(count: Int) = if (count == values.size) values else values.copyOf(count)
    }

    private fun clean(text: String): String = buildString {
        for (c in text) when (c) {
            '\u00ad', '\u200b', '\u200c', '\u200d', '\u2060', '\ufeff', '\u007f', in '\u202a'..'\u202e', in '\u2066'..'\u2069' -> Unit
            '\ufb00' -> append("ff")
            '\ufb01' -> append("fi")
            '\ufb02' -> append("fl")
            '\ufb03' -> append("ffi")
            '\ufb04' -> append("ffl")
            '\ufb05', '\ufb06' -> append("st")
            else -> if (c.code >= 0x20 || c.isWhitespace()) append(if (c.isWhitespace() || c == '\u00a0' || c in '\u2000'..'\u200a' || c == '\u202f') ' ' else c)
        }
    }

    /**
     * The text of [line], spaced by where its words stand. Where it [dropsPadding] (Campfire's own export), the no-break
     * spaces padding a lyric under a wide chord are left out, and leave no gap that would read as a space: the export
     * writes every space of the words as one. Its continuation marks are left out too, and kept as [Rendered.isContinuation]
     * and [Rendered.isHardCut].
     */
    private fun render(line: ChordSheet.Line, normalize: (String) -> String, dropsPadding: Boolean = false): Rendered {
        val text = StringBuilder()
        val positions = Positions(line.spans.sumOf { it.text.length })
        val bidi = if (line.spans.any { it.pieces.isNotEmpty() }) ChordSheetBidiAnchors.Builder() else null
        var previous: ChordSheet.Span? = null
        for (span in line.spans) {
            val value = normalize(clean(if (dropsPadding) span.text.filterNot(::isExportFiller) else span.text))
            if (value.isEmpty()) {
                if (dropsPadding && span.text.isNotEmpty()) previous = span
                continue
            }
            val width = ((span.end - span.start) / value.length).coerceAtLeast(0.01)
            previous?.let { before ->
                val gap = span.start - before.end
                // The font size keeps letter spacing out of it: a PDF positions every glyph on its own, and a narrow
                // one's own advance is less than the extra space a document may put between all of its letters.
                if ((gap > maxOf(width * 0.3, span.size * 0.15) || span.start == before.start && chord(value) && chord(before.text)) && text.lastOrNull() != ' ' && value.first() != ' ') {
                    val count = if (span.isMonospace) (gap / width).roundToInt().coerceIn(1, 1000) else 1
                    repeat(count) { index ->
                        text.append(' ')
                        positions.add(before.end + index * width)
                        bidi?.gap()
                    }
                }
            }
            // A span written in reading order over glyphs drawn otherwise has its characters where its glyphs are.
            val drawn = ChordSheetBidiAnchors.starts(span, value)
            value.forEachIndexed { index, c -> text.append(c); positions.add(drawn?.get(index) ?: (span.start + index * width)) }
            bidi?.span(span, { if (dropsPadding && isExportFiller(it)) "" else clean(it.toString()) }, value, width)
            previous = span
        }
        val result = text.toString().trimEnd()
        // The marks open the row's first text in its reading order, which in a right-to-left song's row of chords, laid
        // out from the right, is not the first span on the page: no other text of a row opens with one.
        val opening = if (dropsPadding) line.spans.firstOrNull { it.text.firstOrNull() == ChordSheetCampfireLayout.CONTINUATION }?.text.orEmpty() else ""
        val isContinuation = opening.firstOrNull() == ChordSheetCampfireLayout.CONTINUATION
        val isHardCut = opening.startsWith("${ChordSheetCampfireLayout.CONTINUATION}${ChordSheetCampfireLayout.CONTINUATION}")
        return Rendered(result, positions.take(result.length), line, bidi = bidi?.build(), isContinuation = isContinuation, isHardCut = isHardCut)
    }

    /**
     * [page] of Campfire's export with each row marked whose line the row under it continues in the chords' or the lyrics'
     * type ([Rendered.isContinuedBelow]). A marked row set wholly in italics is the rest of a comment or of an italic
     * lyric, which says nothing of the row above being a chord row.
     */
    private fun markRowsContinuedBelow(page: List<Rendered>) = page.mapIndexed { index, line ->
        val next = page.getOrNull(index + 1)
        line.copy(isContinuedBelow = next != null && next.isContinuation && !next.source.spans.filter { it.text.isNotBlank() }.all { it.isItalic })
    }

    private fun isExportFiller(character: Char) = character == ChordSheetCampfireLayout.PADDING || character == ChordSheetCampfireLayout.CONTINUATION

    /**
     * A file that holds a single directive ChordPro defines, or a chord in brackets written against the syllable it is
     * sung on (`[G]Hello`), is ChordPro: neither occurs in a chord-over-lyrics sheet or in prose, and treating such a
     * file as prose would escape every chord in it. Brackets that stand apart from the words only count in a majority
     * of the lines, since a `[C]` in prose is as likely a footnote or an annotation.
     */
    private fun isChordPro(text: String): Boolean {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.any { line ->
                ChordProDirectives.isKnownDirective(line.trim()) || ChordProDirectives.brackets(line).any {
                    it.content.none(Char::isWhitespace) && isChordOrLatinName(it.content) &&
                        line.getOrNull(it.range.last + 1)?.let { next ->
                            next in 'A'..'Z' || next in 'a'..'z' || next in '\u00c0'..'\u024f' || next in '\u0370'..'\u04ff'
                        } == true
                }
            }) return true
        val inline = lines.count { line -> ChordProDirectives.brackets(line).any { chord(it.content) } }
        return inline > 0 && inline * 2 >= lines.size
    }

    private fun tokens(text: String): List<Token> = token.findAll(text).map { Token(it.value, it.range.first) }.toList()
    private fun ascii(value: String) = value.replace('\u266f', '#').replace('\u266d', 'b')
    private fun chord(value: String): Boolean {
        val name = ascii(value).removeSurrounding("(", ")")
        return isChordOrLatinName(name) ||
            name.firstOrNull() in 'a'..'h' && ChordProChordNames.isChordName(name.replaceFirstChar { it.uppercase() })
    }

    /**
     * Whether [name] is a chord in the standard notation or in the Latin one, which a Spanish, French or Italian sheet
     * is written in; the import brings it into the standard notation afterwards, with the rest of the song.
     */
    private fun isChordOrLatinName(name: String) = ChordProChordNames.isChordName(name) || ChordProChordNames.latinExpanded(name) != null
    private fun isBareLatinNote(value: String) = ascii(value).removeSurrounding("(", ")").let { ChordProChordNames.latinNoteLength(it) == it.length }
    private fun isLatinChordBeyondNote(value: String) = ascii(value).removeSurrounding("(", ")").let { ChordProChordNames.latinExpanded(it) != null && !isBareLatinNote(it) }
    private fun isPositioned(row: List<Token>) = row.zipWithNext().any { (a, b) -> b.index - (a.index + a.text.length) >= 2 }
    private fun furniture(value: String) = value in listOf("|", "||", "|:", ":|", "||:", ":||", ":|:", "-", "/", "%", "(", ")", "...", "\u2026", ".") || value in noChord || ChordProRowMarks.repeatCount.matches(value)
    private fun chordTokens(line: Rendered): List<Token>? = chordRow(line)?.tokens

    /**
     * A row of chords, and the playing note it may end in, `G   D   (let ring)`. The note is kept apart from the
     * tokens, which every rule that judges the row reads, and is written after the line rather than set as a chord.
     * [notes] are the ones printed before or between the chords, `(x) C D` or `G D (to v:) Em` (a songbook's `[*…]`
     * annotations), each one word to four in parentheses, set where they stand like the chords.
     */
    private class ChordRow(val tokens: List<Token>, val annotation: String?, val notes: List<Token>)

    /**
     * Campfire's export ([isCampfire]) prints a song's annotations (`[*twice]`) in italics among a row's bold chords, so
     * there a run of italic words is a note wherever it stands, in parentheses or not. A line set in italics throughout
     * is all notes, so it is never taken for chords, unless the row under it continues its line
     * ([Rendered.isContinuedBelow]): it is then the chord row of a lyric's continuation, or an annotation opening a line,
     * printed alone over the padding its width needs where a narrow column wraps the lyric right after that padding.
     * There a word is a chord only where it is printed in the chords' type, bold under the title's size, or in a tab's
     * monospaced chord line, so that a lyric `a b c d` and a title `A` stay what they are; where the lyrics' size is
     * unknown, the text alone decides.
     */
    private fun chordRow(line: Rendered, isCampfire: Boolean = line.isCampfire): ChordRow? = chordRow(
        text = line.text,
        allowsNotesOnly = isCampfire && line.isContinuedBelow,
        isNote = if (!isCampfire) null else { word ->
            word.text.indices.all { offset ->
                val x = line.positions.getOrNull(word.index + offset)
                x != null && line.source.spans.any { it.isItalic && x >= it.start && x < it.end }
            }
        },
        isChord = if (!isCampfire || line.bodySize <= 0.0) null else { word ->
            word.text.indices.all { offset ->
                val x = line.positions.getOrNull(word.index + offset)
                x != null && line.source.spans.any {
                    (it.isMonospace || it.isBold && it.size < line.bodySize * ChordSheetCampfireLayout.TITLE_SIZE_RATIO) && x >= it.start - 0.01 && x < it.end
                }
            }
        },
    )

    /** Whether [text] is a row of chords, the notes a songbook prints among them included. */
    internal fun isChordRow(text: String) = chordRow(text) != null

    private fun chordRow(
        text: String,
        isNote: ((Token) -> Boolean)? = null,
        allowsNotesOnly: Boolean = false,
        isChord: ((Token) -> Boolean)? = null,
    ): ChordRow? {
        val words = tokens(text)
        val start = words.indexOfLast { it.text.startsWith('(') }.takeIf { start ->
            start > 0 && words.size - start <= 4 && words.last().text.endsWith(')') && words.drop(start).none { rowChord(it.text) || furniture(it.text) }
        } ?: words.size
        val row = mutableListOf<Token>()
        val notes = mutableListOf<Token>()
        var index = 0
        while (index < start) {
            val word = words[index]
            val end = if (isNote?.invoke(word) == true) {
                (index until start).takeWhile { isNote(words[it]) }.last()
            } else if (word.text.startsWith('(') && !rowChord(word.text)) {
                (index until minOf(start, index + 4)).firstOrNull { words[it].text.endsWith(')') }
                    ?.takeIf { end -> (index..end).none { rowChord(words[it].text) } }
            } else null
            if (end == null) { row += word; index++; continue }
            notes += Token(text.substring(word.index, words[end].index + words[end].text.length), word.index)
            index = end + 1
        }
        if (allowsNotesOnly && row.isEmpty() && notes.isNotEmpty()) return ChordRow(row, null, notes)
        if (row.none { rowChord(it.text) } || !row.all { rowChord(it.text) || furniture(it.text) }) return null
        if (isChord != null && row.any { rowChord(it.text) && !isChord(it) }) return null
        return ChordRow(row, text.substring(words.getOrNull(start)?.index ?: text.length).trim().ifEmpty { null }, notes)
    }

    /** A note printed among a row's chords, written as the annotation ChordPro sets over a lyric the way it sets a chord. */
    private fun note(value: String) = "[$ANNOTATION_MARKER${value.removeSurrounding("(", ")").replace('[', '(').replace(']', ')').trim()}]"

    /** A chord of a chord row, where a star marks the one a footnote under the song explains (`C*`). */
    private fun rowChord(value: String) = chord(value.removeSuffix("*").removeSuffix("*"))
    private fun rowChordName(value: String) = ascii(value.removeSuffix("*").removeSuffix("*").removeSurrounding("(", ")"))
    /** The punctuation that marks an extracted heading is not part of the section's name. */
    private fun headingLabel(text: String) = ChordSheetHeadings.parse(text)?.label
        ?: text.trim().removeSurrounding("[", "]").removeSurrounding("(", ")").removeSuffix(":").trim()

    /** A heading that is one without the lines around it to tell, which is what a single line can be judged by. */
    private fun isHeading(text: String) = ChordSheetHeadings.parse(text)?.isAmbiguous == false

    /**
     * A line in brackets is a heading of a name no list holds (`[Riff]`, `[Primeira Parte]`), where the sheet has
     * structure to say it is a song: in prose brackets as likely hold a note, and a chord in them is the chord.
     */
    private fun isBracketedHeading(text: String): Boolean {
        val content = bracketedLine.matchEntire(text.trim())?.groupValues?.get(1)?.trim() ?: return false
        return content.isNotEmpty() && !chord(content) && !isChordLine(content) && content.split(' ').count { it.isNotEmpty() } <= 6
    }
    /**
     * A label with a colon (or the semicolon some sites print instead) is metadata whatever follows it, except a label of
     * another language that is an ordinary word too ("Tom", "Metro", "Mesure"), which takes a value of its field's shape
     * only. Without a colon only a value of the field's own shape is
     * (`Capo 2`, `Key G`, `Time 4/4`), since a lyric may well start with one of these words: "By the rivers of
     * Babylon" and "Time after time" are sung, not credited. A value that cannot be read comes back blank, and its line
     * then stays a line of text rather than vanishing ([classify]).
     */
    private fun metadata(text: String): Pair<String, String>? {
        if (noCapo.matches(text.trim())) return NO_CAPO to NO_CAPO
        val match = metadata.matchEntire(text.trim()) ?: unlabelledMetadata.matchEntire(text.trim())?.takeIf {
            val value = it.groupValues[2].trim()
            when (it.groupValues[1].lowercase()) {
                "tempo" -> value.first().isDigit()
                "time" -> timeSignature.matches(value)
                in keyLabels -> isChordOrLatinName(ascii(value))
                in capoLabels -> value.replace(capoPreposition, "").let { it.firstOrNull()?.isDigit() == true || romanCapo.matches(it.uppercase()) }
                else -> true
            }
        }
        if (match != null) {
            val label = match.groupValues[1].lowercase().replace(" & ", " and ")
            val name = when (label) {
                in keyLabels -> "key"
                in capoLabels -> "capo"
                "temp\u00f3", "bpm", "\u05d8\u05de\u05e4\u05d5" -> "tempo"
                "\u00fctemmutat\u00f3", in foreignTimeLabels -> "time"
                "el\u0151ad\u00f3" -> "artist"
                "by", "words and music by" -> CREDIT
                "song" -> "title"
                else -> label
            }
            val value = match.groupValues[2].trim()
            // A tempo is a number of beats, which Campfire's PDF prints with its unit: "96 BPM".
            // A capo marked with its shapes' key is read so only where its fret reads too: `Capo: II (G shapes)` stays
            // the line of text an unreadable capo is.
            val capoValue = value.replace(capoPreposition, "")
            if (name == "capo" && shapesKeyOf(capoValue) != null && capo(capoValue).isNotBlank()) return CAPO_SHAPES to capoValue
            return (if (name == "capo" && isSoundingCapo(value)) CAPO_SOUNDING else name) to when (name) {
                "capo" -> capo(capoValue)
                "tempo" -> Regex("^[0-9]+").find(value)?.value ?: value.takeIf { label in setOf("tempo", "temp\u00f3", "bpm") }.orEmpty()
                // A time signature label of another language is as ordinary a word ("Metro", "Mesure"), so it is read
                // with a value of a time signature only, as the foreign key labels are.
                "time" -> value.takeIf { label !in foreignTimeLabels || timeSignature.matches(value) }.orEmpty()
                // The labels the sites outside the English-speaking world write are read with a value of a key only,
                // since a line such as "Tom: here we go" is as likely a cue in the lyrics.
                "key" -> value.takeIf { label in setOf("key", "hangnem") || isChordOrLatinName(ascii(value)) }.orEmpty()
                else -> value
            }
        }
        bpm.matchEntire(text.trim())?.let { return "tempo" to it.groupValues[1] }
        if (text.trim().startsWith('\u00a9')) return "copyright" to text.trim().removePrefix("\u00a9").trim()
        return null
    }

    /**
     * The values a line names, where it names nothing else, each set apart from the next by a gap: the row
     * `Key: A   Transposition: +2   Capo: 2   Tempo: 96 BPM   Time: 3/4` Campfire's PDF heads a song with ([isHeader]),
     * or `Tempo: 90   Time: 3/4`, which anywhere in a song is the change the PDF prints one as. Labelled with a colon
     * and with a value of the field's shape, since anywhere in a song a line starting with "Time" is far more likely sung
     * than credited. A transposition is among them for the caller to leave out rather than write: the chords it is
     * printed over are already in the key it took them to.
     */
    private fun labelledRow(text: String, isHeader: Boolean): List<Pair<String, String>>? {
        val parts = text.trim().split(labelGap)
        return parts.mapNotNull { part ->
            transposition.matchEntire(part)?.let { TRANSPOSITION to it.groupValues[2] } ?: metadata.matchEntire(part)?.let { metadata(part) }
        }.takeIf { entries ->
            entries.size == parts.size && entries.all { (name, value) ->
                when (name) {
                    "tempo" -> value.firstOrNull()?.isDigit() == true
                    "time" -> timeSignature.matches(value)
                    "key" -> isHeader && isChordOrLatinName(ascii(value))
                    "capo", CAPO_SOUNDING, CAPO_SHAPES -> isHeader && value.isNotEmpty()
                    TRANSPOSITION -> isHeader
                    else -> false
                }
            }
        }
    }

    /**
     * Whether [text] is a key alone on its row of Campfire's export ([line] one of it), which the export prints only as
     * a key change, so that it is metadata anywhere in the song, not only among the heading's rows.
     */
    private fun isKeyChange(line: Rendered, text: String) =
        line.isCampfire && labelledRow(text, isHeader = true)?.singleOrNull()?.first == ChordProVocabulary.KEY

    /** Whether a capo [value] is Campfire's PDF saying the chords under it are named as they sound (`2 (chords as they sound)`). */
    private fun isSoundingCapo(value: String) = parentheses.find(value)?.groupValues?.get(1)?.trim()?.lowercase() in soundingCapoMarkers

    /**
     * The key a capo [value] names as its shapes' in Campfire's PDF (`2 (Em shapes)`, `2 (Em-Griffe)`), cut from the
     * original text so its case survives (`em` is no chord); null where the parentheses hold no such marker.
     */
    private fun shapesKeyOf(value: String): String? {
        val marked = parentheses.find(value)?.groupValues?.get(1)?.trim() ?: return null
        val lowercase = marked.lowercase()
        return shapesCapoMarkers.firstNotNullOfOrNull { (prefix, suffix) ->
            if (lowercase.length > prefix.length + suffix.length && lowercase.startsWith(prefix) && lowercase.endsWith(suffix)) {
                marked.substring(prefix.length, marked.length - suffix.length).trim().takeIf { isChordOrLatinName(ascii(it)) }
            } else {
                null
            }
        }
    }

    /** The fret a capo value names, in digits or in the Roman numerals many sites write it in; blank where it names none. */
    private fun capo(value: String): String = Regex("[0-9]+").find(value)?.value
        ?: romanCapo.matchEntire(value.uppercase())?.groupValues?.get(1)?.let { roman -> (romanNumerals.indexOf(roman) + 1).toString() }
        ?: ""

    /**
     * [lines] with each row of labelled values that Campfire's export wrapped in a narrow column joined back into one: a
     * value wider than the column (`Capo: 2 (chords as they sound)`) goes on in the rows under it, each marked as a
     * wrapped lyric's rest is ([Rendered.isContinuation]), and read apart its first row would name a bare value and the
     * rest a lyric. A row continuing a word the column cut inside ([Rendered.isHardCut]) joins with no space.
     */
    private fun joinWrappedDetails(lines: List<Rendered>): List<Rendered> = lines.fold(mutableListOf()) { joined, line ->
        val previous = joined.lastOrNull()
        // The header is the song's first 15 rows, as classify counts them.
        val isHeader = joined.size <= 15
        val indent = line.text.length - line.text.trimStart().length
        val text = previous?.let { "${it.text}${line.joint}${line.text.substring(indent)}" }
        if (line.isContinuation && previous != null && text != null && (labelledRow(text, isHeader) != null || isKeyChange(line, text))) {
            val space = if (line.isHardCut) DoubleArray(0) else doubleArrayOf(previous.positions.lastOrNull() ?: 0.0)
            val positions = previous.positions + space + line.positions.copyOfRange(indent, line.positions.size)
            joined[joined.lastIndex] = previous.copy(text = text, positions = positions, bidi = null)
        } else {
            joined += line
        }
        joined
    }

    /**
     * Splits a heading's label from the chord row written after it on its line, `Verse 1: G D Em C` or
     * `[Intro] G D Em C`. The row is the section's progression, so it stays a line of its own rather than being set by
     * its columns into the lyric under it.
     */
    private fun expandLabels(lines: List<Rendered>): List<Rendered> = lines.flatMap { line ->
        val colon = line.text.indexOf(':')
        val bracket = line.text.indexOf(']').takeIf { line.text.trimStart().startsWith('[') && line.text.getOrNull(it + 1) == ' ' } ?: -1
        // The colon is passed with the label, since a word that is also sung, "Hook: C G", is a heading by it.
        val end = when {
            colon > 0 && isHeading(line.text.substring(0, colon + 1)) -> colon
            bracket > 0 && ChordSheetHeadings.parse(line.text.substring(0, bracket + 1)) != null -> bracket + 1
            else -> return@flatMap listOf(line)
        }
        val after = if (end == colon) colon + 1 else end
        val rest = line.copy(text = line.text.substring(after), positions = line.positions.copyOfRange(after, line.positions.size), isFromLabel = true, bidi = null)
        if (chordTokens(rest) != null) listOf(line.copy(text = line.text.substring(0, end), positions = line.positions.copyOf(end)), rest) else listOf(line)
    }

    private fun lead(page: List<Rendered>): ChordSheetPageSplit.Lead? {
        val index = page.indexOfFirst { it.text.isNotBlank() }.takeIf { it >= 0 } ?: return null
        val line = page[index]
        val next = page.drop(index + 1).firstOrNull { it.text.isNotBlank() && chordTokens(it) == null }
        return ChordSheetPageSplit.Lead(
            text = line.text.trim(),
            line = line.source,
            isTitle = isTitle(line, page),
            isFollowedByBold = next?.source?.spans?.filter { it.text.isNotBlank() }?.let { spans -> spans.isNotEmpty() && spans.all { it.isBold } } == true,
        )
    }

    private fun isTitle(line: Rendered, lines: List<Rendered>): Boolean {
        if (line.text.isBlank() || line.isMarginLabel || isHeading(line.text) || metadata(line.text) != null || chordTokens(line) != null) return false
        val bodySizes = lines.filter { it !== line }.flatMap { it.source.spans }.map { it.size }.sorted()
        val median = bodySizes.getOrNull(bodySizes.size / 2) ?: line.source.spans.maxOfOrNull { it.size } ?: 1.0
        return line.source.isHeading || line.source.spans.any { it.size > median * 1.2 } ||
            line.source.spans.isNotEmpty() && line.source.spans.all { it.isBold } && lines.any { chordTokens(it) != null }
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
            (line.isCampfire || spans.all { span -> lyrics.all { abs(it.size - span.size) < span.size * 0.05 } })
    }

    /**
     * Whether [line] is set in the type of a [title] that is larger than the body text, which is how a wrapped title
     * continues; a title told apart only by being bold has no such type to recognize its continuation by.
     */
    private fun isTitleContinuation(line: Rendered, title: Rendered, lines: List<Rendered>): Boolean {
        val titleSpans = title.source.spans.filter { it.text.isNotBlank() }
        val titleSize = titleSpans.maxOfOrNull { it.size } ?: return false
        val bodySizes = lines.filter { it !== title && it !== line }.flatMap { it.source.spans }.map { it.size }.sorted()
        val median = bodySizes.getOrNull(bodySizes.size / 2) ?: return false
        val spans = line.source.spans.filter { it.text.isNotBlank() }
        return titleSize > median * 1.2 && spans.isNotEmpty() &&
            spans.all { abs(it.size - titleSize) < titleSize * 0.05 && it.isBold == titleSpans.first().isBold }
    }

    private fun convertSong(lines: List<Rendered>, guessesTitle: Boolean): String {
        val candidates = chordCandidates(lines)
        val convertParentheses = convertsParentheses(lines)
        val kinds = classify(lines, candidates)
        val styled = lines.any { line -> !isChordLine(line.text) && line.source.spans.any { (it.isBold || it.isRaised) && chord(it.text.trim()) } }
        if (kinds.none { it in listOf(Kind.CHORD, Kind.SECTION, Kind.TAB, Kind.GRID) } && !convertParentheses && !styled) {
            return collapse(lines.map { ChordProLiteralText.escape(it.text) })
        }
        val header = mutableListOf<String>()
        val omitted = mutableSetOf<Int>()
        val (changes, soundingCapo) = extractHeader(lines, kinds, header, omitted)
        if (guessesTitle) extractTitle(lines, kinds, header, omitted)
        val definitions = ChordSheetDefinitions.definitions(
            lines = lines.map { it.text },
            // A shape in parentheses after its chord reads as a chord row with a note, `D7 (x-x-0-2-1-2)`.
            candidates = kinds.indices.filter { (kinds[it] == Kind.LYRIC || kinds[it] == Kind.CHORD) && it !in omitted },
            chordName = { name -> ascii(name).takeIf(::isChordOrLatinName) },
        )
        header += definitions.values
        omitted += definitions.keys
        val output = writeBody(lines, kinds, candidates, omitted, changes, convertParentheses)
        // A credit printed both under the title and as a labelled line names the song once.
        val distinct = header.distinct()
        val keyChanges = output.count { it.startsWith("{${ChordProVocabulary.KEY}: ") }
        return asCapoChart(collapse(distinct + if (distinct.isEmpty()) output else listOf("") + output), soundingCapo, keyChanges)
    }

    /**
     * [song] as the capo chart it was printed from, where its chords are named as they sound over a [capo]: a Campfire
     * file's chords under a `{capo}` are the shapes played above it, so they and the key move down by the capo, while a
     * tab's frets, printed counted from the capo, stay. The move reads only the standard notation (a Latin name is no
     * chord to it), so the text is brought into it first, as the import would bring it afterwards. The song's last
     * [keyChanges] `{key}` lines are the key changes written in its body, and each part from one on is moved apart, spelled
     * by its own key moved down, which the move reads off the part's opening `{key}`: one move of the whole would spell
     * a flat key's chorus by the sharp key the song starts in.
     */
    private fun asCapoChart(song: String, capo: Int, keyChanges: Int): String {
        if (capo == 0) return song
        val lines = ChordProNotation.convertText(song, ChordNotation.STANDARD, ChordNotation.STANDARD).split('\n')
        val cuts = lines.indices.filter { lines[it].startsWith("{${ChordProVocabulary.KEY}: ") }.takeLast(keyChanges).filter { it > 0 }
        return (listOf(0) + cuts + lines.size).zipWithNext { from, until ->
            ChordProTransposer.transposeText(lines.subList(from, until).joinToString("\n"), -capo, capo = -capo)
        }.joinToString("\n")
    }

    /**
     * The rows that may be chords. A row of bare note words is as likely a sung "La la la" as a row of major chords, so
     * it counts as chords only in a sheet whose other rows show it is written in Latin, or where its words stand apart
     * the way chords are set over the syllables they fall on, which a sung line never is.
     */
    private fun chordCandidates(lines: List<Rendered>): List<List<Token>?> {
        val hasLatinChords = lines.any { line -> tokens(line.text).any { isLatinChordBeyondNote(it.text) } }
        val rows = lines.map(::chordTokens)
        // A row of bare capitals typed one space apart is as likely a lettered list ("A B C D"); a chord beyond a bare
        // letter elsewhere, or a heading, says the sheet is a song. Measured rows (a PDF's, a Word document's) are
        // spaced by their glyphs' positions rather than their text, so the guard reads character columns only.
        val hasRicherChords = rows.any { row -> row?.any { rowChord(it.text) && !isBareLetter(it.text) } == true }
        val hasHeading = lines.any { ChordSheetHeadings.parse(it.text) != null }
        return rows.mapIndexed { index, row ->
            row?.takeUnless { it.isNotEmpty() && !hasLatinChords && !isPositioned(it) && it.filter { word -> rowChord(word.text) }.all { word -> isBareLatinNote(word.text) } }
                ?.takeUnless {
                    !hasRicherChords && !hasHeading && it.size >= 2 && isCharacterColumns(lines[index]) && !isPositioned(it) &&
                        it.all { word -> isBareLetter(word.text) }
                }
        }
    }

    private fun isBareLetter(value: String) = value.length == 1 && value[0] in 'A'..'G'

    /** Text laid out in characters, whose columns are where its chords stand; a measured row's spacing says nothing. */
    private fun isCharacterColumns(line: Rendered) = line.source.spans.all { it.isMonospace }

    /** Chords written in parentheses inside the lyrics, `(Am)`, are taken for chords only where most parentheses hold one. */
    private fun convertsParentheses(lines: List<Rendered>): Boolean {
        val parenthesized = lines.flatMap { parentheses.findAll(it.text).toList() }
        return parenthesized.isNotEmpty() && parenthesized.count { chord(it.groupValues[1]) } * 2 > parenthesized.size
    }

    /**
     * A chord row with no lyric under it. Its playing note is written as an annotation, so that it stands on the row with
     * the chords rather than as a lyric under them; one after a lyric line stays text, since the line has words already.
     * A row [isSetFromRight] is read from its right end, where the export starts it.
     */
    private fun bracketed(row: List<Token>, chordRow: ChordRow?, isSetFromRight: Boolean) = (row + chordRow?.notes.orEmpty())
        .sortedBy { if (isSetFromRight) -it.index else it.index }.joinToString(" ") {
        when {
            it in chordRow?.notes.orEmpty() -> note(it.text)
            rowChord(it.text) -> "[${rowChordName(it.text)}]"
            else -> it.text
        }
    } +
        chordRow?.annotation?.let { note ->
            val inner = note.removeSurrounding("(", ")").replace('[', '(').replace(']', ')').trim()
            " " + if (inner.isEmpty()) ChordProLiteralText.escape(note) else "[$ANNOTATION_MARKER$inner]"
        }.orEmpty()

    private fun hasUnambiguousChords(candidates: List<List<Token>?>) = candidates.any { it != null && it.count { word -> rowChord(word.text) } > 1 }

    /** Whether a sheet reads as a song rather than prose: a row of two chords or more, or a heading. */
    private fun hasStructure(lines: List<Rendered>, candidates: List<List<Token>?>) =
        hasUnambiguousChords(candidates) || lines.any { ChordSheetHeadings.parse(it.text) != null }

    private fun classify(lines: List<Rendered>, candidates: List<List<Token>?>): List<Kind> {
        val hasUnambiguousChords = hasUnambiguousChords(candidates)
        val parsed = lines.map { ChordSheetHeadings.parse(it.text) }
        val hasStructure = hasStructure(lines, candidates)
        // A word that is as likely sung is a heading where a chord row or a blank line follows it, as one does a heading.
        val headings = lines.indices.map { index ->
            parsed[index]?.let { !it.isAmbiguous || candidates.getOrNull(index + 1) != null || lines.getOrNull(index + 1)?.text?.isBlank() != false }
                ?: (hasStructure && isBracketedHeading(lines[index].text))
        }
        // Campfire's own export is read by the test it engraves a staff by, which knows every instrument's strings.
        val isStaff = lines.indices.map { index ->
            lines[index].isCampfire && ChordProTokens.isStaffLine(lines[index].text) ||
                tab.matches(lines[index].text.trim()) && (lines.getOrNull(index - 1)?.let { tab.matches(it.text.trim()) } == true ||
                    lines.getOrNull(index + 1)?.let { tab.matches(it.text.trim()) } == true)
        }
        return lines.mapIndexed { index, line ->
            val candidate = candidates[index]
            when {
                line.text.isBlank() -> Kind.BLANK
                line.role == ChordSheetCampfireLayout.Role.LABEL -> Kind.SECTION
                line.role == ChordSheetCampfireLayout.Role.GRID -> Kind.GRID
                line.role == ChordSheetCampfireLayout.Role.COMMENT || line.role == ChordSheetCampfireLayout.Role.BOX_COMMENT -> Kind.COMMENT
                // A row of chord names right over a staff names its bars, its columns the staff's, so it stays in the tab
                // as it stands rather than being set over a lyric line it was never written for.
                isStaff[index] || candidate != null && isStaff.getOrNull(index + 1) == true -> Kind.TAB
                headings[index] || line.isMarginLabel -> Kind.SECTION
                index < 15 && (metadata(line.text)?.second?.isNotBlank() == true || labelledRow(line.text, isHeader = true) != null) ||
                    metadata(line.text)?.first == "copyright" ||
                    labelledRow(line.text, isHeader = false) != null || isKeyChange(line, line.text) -> Kind.METADATA
                // A row of notes alone is a candidate only where the row under it continues its line.
                // A row of chords the export marks as the rest of a line is one whatever follows, and so is the row its
                // chords overflowed from; only an export's chords in their own type are candidates, so a wrapped lyric's
                // marked rest that reads as chord names (`a b`) is never taken for one.
                candidate != null && (candidate.isEmpty() ||
                    candidate.isNotEmpty() && line.isContinuation ||
                    candidate.isNotEmpty() && lines.getOrNull(index + 1)?.let { it.isContinuation && candidates.getOrNull(index + 1) != null } == true ||
                    candidate.count { rowChord(it.text) } > 1 ||
                    candidate.count { rowChord(it.text) } == 1 &&
                    candidate.filter { rowChord(it.text) }.all { it.text.first().isUpperCase() } &&
                    headings.getOrNull(index - 1) == true &&
                    lines.getOrNull(index + 1)?.let { it.text.isBlank() || headings[index + 1] } != false ||
                    (hasUnambiguousChords || isStyledChordLine(line, candidate, lines.getOrNull(index + 1))) &&
                    lines.getOrNull(index + 1)?.let { it.text.isNotBlank() && chordTokens(it) == null && !headings[index + 1] && metadata(it.text) == null } == true) -> Kind.CHORD
                else -> Kind.LYRIC
            }
        }
    }

    /**
     * Takes the metadata lines into [header] and [omitted], and returns the changes of tempo and time, by the line they
     * stand at, and the capo of a sheet whose chords are named as they sound over it ([CAPO_SOUNDING]), 0 for any other.
     * A capo naming its shapes' key ([CAPO_SHAPES]) makes that key the song's, in place of the sounding one the row
     * names beside it: the chords under it are those shapes.
     */
    private fun extractHeader(
        lines: List<Rendered>,
        kinds: List<Kind>,
        header: MutableList<String>,
        omitted: MutableSet<Int>,
    ): Pair<Map<Int, List<String>>, Int> {
        // The first tempo and time signature are the song's; a later one is a change from where it stands, which is
        // written there, as Campfire's own PDF prints one. So is a later key of Campfire's export, which prints one only
        // as a key change, unless it names the key already in force; another sheet's second key row stays in the
        // header, more likely a repetition or a second notation than a change.
        val changes = mutableMapOf<Int, List<String>>()
        val declared = mutableSetOf<String>()
        var keyInForce: String? = null
        var soundingCapo = 0
        var shapesKey: String? = null
        kinds.forEachIndexed { index, kind -> if (kind == Kind.METADATA) {
            (labelledRow(lines[index].text, isHeader = index < 15) ?: listOf(metadata(lines[index].text)!!)).forEach { (name, value) ->
                when {
                    value.isBlank() || name == TRANSPOSITION || name == NO_CAPO -> Unit
                    name in timingNames && !declared.add(name) -> changes[index] = changes[index].orEmpty() + "{$name: ${headerValue(value)}}"
                    name == ChordProVocabulary.KEY && lines[index].isCampfire && !declared.add(name) -> {
                        val key = headerValue(ascii(value))
                        if (key != keyInForce) changes[index] = changes[index].orEmpty() + "{$name: $key}"
                        keyInForce = key
                    }
                    name == CREDIT || name == "artist" -> {
                        // An artist's name may end in a number (`The 1975`), so only a comma sets a year off it.
                        val split = (if (name == CREDIT) creditYear else commaYear).matchEntire(value)
                        if (split == null) {
                            header += "{artist: ${headerValue(value)}}"
                        } else {
                            split.destructured.let { (artist, year) ->
                                header += "{artist: ${headerValue(artist)}}"
                                header += "{year: $year}"
                            }
                        }
                    }
                    name == CAPO_SHAPES -> {
                        header += "{capo: ${capo(value)}}"
                        shapesKey = headerValue(ascii(shapesKeyOf(value)!!))
                    }
                    name == CAPO_SOUNDING -> {
                        header += "{capo: $value}"
                        // A run of digits too long for a fret is no capo chart to move the chords of.
                        soundingCapo = value.toIntOrNull() ?: 0
                    }
                    // The export draws a key's accidentals as signs, and a file keeps them as `#` and `b`.
                    name == ChordProVocabulary.KEY -> {
                        keyInForce = headerValue(ascii(value))
                        header += "{$name: $keyInForce}"
                    }
                    else -> header += "{$name: ${headerValue(value)}}"
                }
            }
            omitted += index
        } }
        shapesKey?.let { key ->
            val keyEntry = header.indexOfFirst { it.startsWith("{${ChordProVocabulary.KEY}: ") }
            if (keyEntry >= 0) {
                header[keyEntry] = "{${ChordProVocabulary.KEY}: $key}"
            } else {
                header.add(header.indexOfFirst { it.startsWith("{capo: ") }.coerceAtLeast(0), "{${ChordProVocabulary.KEY}: $key}")
            }
        }
        return changes to soundingCapo
    }

    /** Takes the title the song opens with, and the credit under it, into [header] and [omitted]. */
    private fun extractTitle(
        lines: List<Rendered>,
        kinds: List<Kind>,
        header: MutableList<String>,
        omitted: MutableSet<Int>,
    ) {
        val first = kinds.indexOfFirst { it != Kind.BLANK }
        if (first < 0 || kinds[first] != Kind.LYRIC) return
        // The export's own test reads chords by their type, which a title naming a chord (`A`) is not printed in.
        if (if (lines[first].isCampfire) chordRow(lines[first]) != null else isChordLine(lines[first].text)) return
        if (!isTitle(lines[first], lines)) return extractPlainTitle(lines, kinds, first, header, omitted)
        // A title too long for its column is wrapped onto the lines under it, in the same type, which no line of the
        // song is set in: those lines are the rest of the title rather than its credit or its first lyrics.
        var last = first
        while (kinds.getOrNull(last + 1) == Kind.LYRIC && !lines[last + 1].isHeaderPart && isTitleContinuation(lines[last + 1], lines[first], lines)) last++
        val title = joinRows(lines, first, last)
        // Campfire's export prints the file's title alone, its artist on a line of its own, so there is no page word or
        // credit in it to split off.
        val (name, named) = if (lines[first].isCampfire) title to null else ChordSheetTitles.split(title)
        addTitle(header, name, named)
        omitted += first..last
        val artist = last + 1
        // Campfire's export sets the artist in the lyrics' type right under the title, at the heading's spacing, wrapped
        // onto as many rows as it takes, and its first block a heading gap further down: the rows at that spacing are the
        // artist, however many and however long, and nothing else is, not even a short first lyric under a long title.
        if (named == null && lines.getOrNull(artist)?.isCampfire == true && lines[artist].source.baseline != null) {
            val rows = generateSequence(artist) { it + 1 }.takeWhile { index ->
                val line = lines.getOrNull(index)
                val below = line?.source?.baseline
                val above = lines[index - 1].source.baseline
                kinds.getOrNull(index) == Kind.LYRIC && below != null && above != null &&
                    below - above < (line.source.spans.filter { it.text.isNotBlank() }.maxOfOrNull { it.size } ?: 0.0) * HEADING_ROW
            }.toList()
            if (rows.isNotEmpty()) {
                header += "{artist: ${headerValue(joinRows(lines, rows.first(), rows.last()))}}"
                omitted += rows
            }
            return
        }
        // Straight under the title, "by Someone" is the credit it reads as, which it is not as a line of lyrics.
        val credit = lines.getOrNull(artist)?.let { credit.matchEntire(it.text.trim()) }
        // Read without baselines, an export's artist is still told by the row of the song's key, tempo, and time under it,
        // where no lyric ever stands, so there a name longer than the title is the artist all the same.
        val isCampfireArtist = lines.getOrNull(artist)?.isCampfire == true && kinds.getOrNull(artist + 1) == Kind.METADATA
        if (named == null && kinds.getOrNull(artist) == Kind.LYRIC && (credit != null || isCampfireArtist || lines[artist].text.length < title.length &&
                lines[artist].source.spans.maxOfOrNull { it.size }?.let { it < (lines[first].source.spans.maxOfOrNull { span -> span.size } ?: 1.0) } == true)) {
            header += "{artist: ${headerValue(credit?.groupValues?.get(1) ?: lines[artist].text.trim())}}"
            omitted += artist
        } else if (kinds.getOrNull(artist) == Kind.LYRIC && (lines[artist].isHeaderPart || isCenteredUnder(lines[artist], lines[first], lines))) {
            // The part printed beside the title, or the line centred under a centred one, is the subtitle the ChordPro
            // app sets there.
            header.add(if (named == null) 1 else 2, "{subtitle: ${headerValue(lines[artist].text.trim())}}")
            omitted += artist
        }
    }

    /**
     * The title of a text with no type to tell it by: a first line that the song's structure follows closely, after
     * its credit and the metadata lines the page heads the song with, either straight away or after a blank line. A
     * line that opens with markup, a summary of the chords used, or a bare "Chords" is the page's furniture instead.
     */
    private fun extractPlainTitle(
        lines: List<Rendered>,
        kinds: List<Kind>,
        first: Int,
        header: MutableList<String>,
        omitted: MutableSet<Int>,
    ) {
        val text = lines[first].text.trim()
        if (text.startsWith('[') || text.startsWith('{') || isChordSummary(text) || text.lowercase() in pageWords) return
        val (name, named) = ChordSheetTitles.split(text)
        val credit = lines.getOrNull(first + 1)?.let { credit.matchEntire(it.text.trim()) }
        val creditAt = (first + 1).takeIf {
            named == null && kinds.getOrNull(it) == Kind.LYRIC && (credit != null ||
                lines[it].text.trim().length < text.length && kinds.getOrNull(it + 1).let { next -> next == Kind.BLANK || next == Kind.METADATA })
        }
        var next = (creditAt ?: first) + 1
        while (kinds.getOrNull(next) == Kind.METADATA) next++
        val structure = kinds.indices.firstOrNull { kinds[it] == Kind.CHORD || kinds[it] == Kind.SECTION } ?: return
        if (kinds.getOrNull(next) !in listOf(Kind.BLANK, Kind.CHORD, Kind.SECTION) || structure !in (first + 1)..(first + 5)) return
        addTitle(header, name, named)
        omitted += first
        if (creditAt != null) {
            header += "{artist: ${headerValue(credit?.groupValues?.get(1) ?: lines[creditAt].text.trim())}}"
            omitted += creditAt
        }
    }

    /** Whether [line] is centred under a centred [title], which a lyric set against the left margin never is. */
    private fun isCenteredUnder(line: Rendered, title: Rendered, lines: List<Rendered>): Boolean {
        fun ink(rendered: Rendered) = rendered.source.spans.filter { it.text.isNotBlank() }.takeIf { spans -> spans.isNotEmpty() && spans.none { it.isMonospace } }
        val titleInk = ink(title) ?: return false
        val lineInk = ink(line) ?: return false
        val size = titleInk.maxOf { it.size }
        val margin = lines.mapNotNull { rendered -> rendered.source.spans.firstOrNull { it.text.isNotBlank() }?.start }.minOrNull() ?: return false
        return titleInk.first().start > margin + size * 2 && lineInk.first().start > margin + size * 2 &&
            abs((lineInk.first().start + lineInk.last().end) - (titleInk.first().start + titleInk.last().end)) / 2 <= size
    }

    private fun addTitle(header: MutableList<String>, title: String, artist: String?) {
        artist?.let { header.add(0, "{artist: ${headerValue(it)}}") }
        header.add(0, "{title: ${headerValue(title)}}")
    }

    /** `Chords used: G D Em C`: a label whose value is mostly chords lists them rather than naming the song. */
    private fun isChordSummary(text: String): Boolean {
        val colon = text.indexOf(':').takeIf { it >= 0 } ?: return false
        val words = tokens(text.substring(colon + 1))
        return words.isNotEmpty() && words.count { chord(it.text) } * 2 >= words.size
    }

    private fun writeBody(
        lines: List<Rendered>,
        kinds: List<Kind>,
        candidates: List<List<Token>?>,
        omitted: Set<Int>,
        changes: Map<Int, List<String>>,
        convertParentheses: Boolean,
    ): List<String> = with(BodyWriter()) {
        var index = 0
        while (index < lines.size) {
            changes[index]?.let { pendingChanges += it }
            if (index in omitted) { index++; continue }
            val line = lines[index]
            fun nextContent(after: Int) = ((after + 1)..lines.lastIndex).firstOrNull { kinds[it] != Kind.BLANK && kinds[it] != Kind.COMMENT && it !in omitted }
            // A comment over a staff is the tab's own, as a label over it is the tab's.
            if (kinds[index] != Kind.TAB && !(kinds[index] == Kind.COMMENT && nextContent(index)?.let { kinds[it] } == Kind.TAB)) closeTab()
            if (kinds[index] != Kind.GRID) closeGrid()
            // A change before a heading belongs to the section it heads rather than to the end of the one before.
            if (kinds[index] != Kind.SECTION && kinds[index] != Kind.BLANK) flushChanges()
            when (kinds[index]) {
                Kind.SECTION if line.role == ChordSheetCampfireLayout.Role.LABEL -> {
                    closeSection()
                    flushChanges()
                    val label = headerValue(line.text)
                    if (output.lastOrNull()?.isNotBlank() == true) output += ""
                    if (nextContent(index)?.let { kinds[it] } == Kind.TAB) {
                        output += "{start_of_tab: $label}"
                        inTab = true
                    } else {
                        val type = ChordSheetCampfireLayout.sectionType(label)
                        output += "{start_of_$type: $label}"
                        environment = type
                    }
                }
                Kind.COMMENT -> {
                    var end = index
                    while (end < lines.lastIndex && kinds[end + 1] == Kind.COMMENT && lines[end + 1].role == line.role && end + 1 !in omitted) end++
                    // A comment the next section (or the song's end) follows stands after the section it is printed under.
                    if (!inTab && nextContent(end)?.let { kinds[it] == Kind.SECTION } != false) {
                        closeSection()
                        if (output.lastOrNull()?.isNotBlank() == true) output += ""
                    }
                    val directive = if (line.role == ChordSheetCampfireLayout.Role.BOX_COMMENT) "comment_box" else "comment"
                    output += "{$directive: ${headerValue(joinRows(lines, index, end))}}"
                    index = end
                }
                Kind.GRID -> { if (!inGrid) { output += "{start_of_grid}"; inGrid = true }; output += line.text.trimEnd() }
                Kind.SECTION -> {
                    closeSection()
                    flushChanges()
                    val label = headerValue(headingLabel(line.text))
                    val type = if (line.isMarginLabel) ChordSheetMarginLabels.type(line.text) else ChordSheetHeadings.parse(line.text)?.type
                    val next = ((index + 1)..lines.lastIndex).firstOrNull { kinds[it] != Kind.BLANK }
                    if (type == "chorus" && (next == null || kinds[next] == Kind.SECTION)) output += "{chorus}"
                    else if (type != null) { output += "{start_of_$type: $label}"; environment = type }
                    else {
                        // A heading the parser does not take for a section ends the lyrics before it only after a blank
                        // line; without one it would be a comment inside them, and what follows their continuation.
                        if (output.lastOrNull()?.isNotBlank() == true) output += ""
                        output += "{comment: $label}"
                    }
                }
                Kind.TAB -> { if (!inTab) { output += "{start_of_tab}"; inTab = true }; output += line.text }
                Kind.CHORD -> {
                    val isUnderMarkedLyric = kinds.getOrNull(index + 1) == Kind.LYRIC && index + 1 !in omitted && lines[index + 1].isContinuation
                    // A chord row the export marks with no marked lyric under it holds the chords the line above overflowed
                    // onto it, so it goes on that line, before the merge would set its chords into an unmarked lyric under it.
                    if (line.isContinuation && !isUnderMarkedLyric) {
                        addLyric(bracketed(candidates[index]!!, chordRow(line), line.isSetFromRight), continues = true, isHardCut = false)
                    } else if (!line.isFromLabel && kinds.getOrNull(index + 1) == Kind.LYRIC && index + 1 !in omitted && !isUnaligned(line, candidates[index]!!, lines[index + 1]) &&
                        isLyricOf(line, lines[index + 1])
                    ) {
                        addLyric(merge(line, lines[index + 1], convertParentheses), lines[index + 1].isContinuation, lines[index + 1].isHardCut)
                        index++
                    } else output += bracketed(candidates[index]!!, chordRow(line), line.isSetFromRight)
                }
                Kind.LYRIC -> addLyric(inline(line, convertParentheses), line.isContinuation, line.isHardCut)
                Kind.BLANK -> output += ""
                Kind.METADATA -> Unit
            }
            index++
        }
        closeTab()
        closeGrid()
        closeSection()
        flushChanges()
        output
    }

    /** The body of the song as it is written, with the environment and the changes still open around the next line. */
    private class BodyWriter {
        val output = mutableListOf<String>()

        /**
         * A lyric line, or where it [continues] the one before (a row the export wrapped), the rest of that line, with no
         * space between where the row [isHardCut] inside a word.
         */
        fun addLyric(line: String, continues: Boolean, isHardCut: Boolean) {
            val previous = output.lastOrNull()
            val joint = if (isHardCut) "" else " "
            if (continues && previous != null && previous.isNotBlank() && !previous.startsWith('{')) output[output.lastIndex] = "$previous$joint${line.trimStart()}"
            else output += line
        }
        var environment: String? = null
        var inTab = false
        var inGrid = false
        val pendingChanges = mutableListOf<String>()
        fun closeTab() { if (inTab) { output += "{end_of_tab}"; inTab = false } }
        fun closeGrid() { if (inGrid) { output += "{end_of_grid}"; inGrid = false } }
        fun closeSection() { environment?.let { output += "{end_of_$it}" }; environment = null }
        fun flushChanges() { output += pendingChanges; pendingChanges.clear() }
    }

    /** Whether [lyric] is the lyric of the chord row [chords] over it, which only Campfire's export says by where it stands. */
    private fun isLyricOf(chords: Rendered, lyric: Rendered) = !chords.isCampfire || ChordSheetCampfireLayout.isLyricOf(chords.source, lyric.source)

    /**
     * A row of three or more chords typed one space apart over a lyric more than twice its length lost its columns
     * when it was copied, so placing its chords by them would set them all into the lyric's first word. It is written
     * above the lyric instead; two chords over a short line may well be meant where they stand.
     */
    private fun isUnaligned(row: Rendered, tokens: List<Token>, lyric: Rendered) = isCharacterColumns(row) && !isPositioned(tokens) &&
        tokens.count { rowChord(it.text) } >= 3 && lyric.text.trim().length > row.text.trim().length * 2

    private fun inline(line: Rendered, convertParentheses: Boolean): String {
        // Escape prose first, then insert only the chord tokens the classifier accepted.
        val replacements = mutableListOf<Triple<Int, Int, String>>()
        if (convertParentheses) parentheses.findAll(line.text).filter { chord(it.groupValues[1]) }.forEach {
            replacements += Triple(it.range.first, it.range.last + 1, ascii(it.groupValues[1]))
        }
        val styledRuns = mutableListOf<ChordSheet.Span>()
        var run: ChordSheet.Span? = null
        val runText = StringBuilder()
        fun flushRun() {
            run?.let { styledRuns += it.copy(text = runText.toString()) }
            run = null
            runText.clear()
        }
        for (span in line.source.spans) {
            if (!span.isBold && !span.isRaised) { flushRun(); continue }
            val previous = run
            if (previous == null || previous.isBold != span.isBold || previous.isRaised != span.isRaised ||
                abs(previous.end - span.start) >= span.size * 0.1) {
                flushRun()
                run = span
            } else run = previous.copy(end = span.end)
            runText.append(span.text)
        }
        flushRun()
        val chordLine = isChordLine(line.text)
        // Stacked chords are padded back over the glyphs before them, so positions are only searched by halving where
        // they never go back; anywhere else the linear scan is the one that finds the right glyph.
        val isMonotone = line.positions.indices.drop(1).all { line.positions[it - 1] <= line.positions[it] }
        for (span in styledRuns) if (!chordLine && chord(span.text.trim())) {
            val start = if (isMonotone) {
                firstIndex(line.positions.size) { line.positions[it] > span.start - 0.01 }
                    .takeIf { it < line.positions.size && line.positions[it] < span.start + 0.01 } ?: -1
            } else line.positions.indexOfFirst { abs(it - span.start) < 0.01 }
            if (start >= 0) replacements += Triple(start, start + clean(span.text).length, ascii(span.text.trim()))
        }
        val text = ChordProLiteralText.escape(line.text)
        return buildString {
            var cursor = 0
            for ((start, end, value) in replacements.sortedBy { it.first }) {
                // A replacement overlapping the one before it would rewrite text that is already a chord, so it is dropped.
                if (start < cursor || end > text.length || splitsSurrogate(text, start) || splitsSurrogate(text, end)) continue
                append(text, cursor, start)
                append("[$value]")
                cursor = end
            }
            append(text, cursor, text.length)
        }
    }

    /**
     * The lyric without the dashes the ChordPro app fills a chord's width with where the chord has no syllable of its
     * own (`tonight[Db][Eb7]` prints as "tonight---" under them): a run of two or more ending the line, under a chord.
     */
    private fun withoutChordFill(chords: Rendered, lyrics: Rendered): Rendered {
        val text = lyrics.text.trimEnd()
        val start = text.indexOfLast { it != '-' } + 1
        if (text.length - start < 2 || start > 0 && text[start - 1] == ' ') return lyrics
        val x = lyrics.positions.getOrNull(start) ?: return lyrics
        val chordPositions = chordRow(chords)?.tokens.orEmpty().filter { rowChord(it.text) }.mapNotNull { chords.positions.getOrNull(it.index) }
        if (chordPositions.none { it >= x - 0.01 }) return lyrics
        return lyrics.copy(
            text = text.substring(0, start),
            positions = lyrics.positions.copyOf(start),
            source = lyrics.source.copy(spans = lyrics.source.spans.filter { it.start < x - 0.01 }),
        )
    }

    private fun merge(chords: Rendered, lyricLine: Rendered, convertParentheses: Boolean): String {
        val lyrics = withoutChordFill(chords, lyricLine)
        val insertions = mutableMapOf<Int, MutableList<String>>()
        val starts = tokens(lyrics.text).map { it.index }
        val positions = lyrics.positions
        // The binary searches below need positions and spans that never go back, which padding stacked chords breaks;
        // such a line keeps the linear searches, which are right for any order.
        val isMonotone = positions.indices.drop(1).all { positions[it - 1] <= positions[it] }
        val spans = lyrics.source.spans
        val spansSorted = spans.indices.drop(1).all { spans[it - 1].start <= spans[it].start }
        val suffixMin = positions.copyOf()
        for (index in suffixMin.lastIndex - 1 downTo 0) suffixMin[index] = minOf(suffixMin[index], suffixMin[index + 1])
        val prefixMaxEnd = DoubleArray(spans.size)
        for (index in spans.indices) prefixMaxEnd[index] = maxOf(spans[index].end, prefixMaxEnd.getOrNull(index - 1) ?: Double.NEGATIVE_INFINITY)
        var previous = 0
        val row = chordRow(chords)
        val notes = row?.notes.orEmpty()
        // Campfire's own export sets a chord exactly over the character it is sung on, or right after the line's last one,
        // so where it stands is read as it is rather than moved to the nearest word.
        val lyricsEnd = lyrics.source.spans.lastOrNull { it.text.isNotBlank() }?.end
        // Another producer than Campfire hangs a chord from its syllable by eye, or by the columns of a plain text, so
        // the edge it hangs from is looked for within an em of the lyric's type rather than at the export's exact point.
        val bidiTolerance = if (lyrics.isCampfire) EXACT_POSITION else lyrics.source.spans.firstOrNull { it.pieces.isNotEmpty() }?.size ?: 1.0
        var isAttachedAtEnd = false
        // Bar lines and repeat counts mean nothing over a lyric, but "no chord" says where the playing stops.
        // Chords set on one character, the line's end above all, are written in the order they are read, which in a row
        // set from the right runs from its right end.
        val readsFromRight = chords.isSetFromRight && lyrics.bidi != null
        for (word in (row?.tokens.orEmpty().filter { rowChord(it.text) || it.text in noChord } + notes).sortedBy { if (readsFromRight) -it.index else it.index }) {
            val x = chords.positions.getOrNull(word.index) ?: 0.0
            var at = (firstIndex(suffixMin.size) { suffixMin[it] > x } - 1).coerceAtLeast(0)
            val exact = if (lyrics.isCampfire) positions.indices.firstOrNull { abs(positions[it] - x) <= EXACT_POSITION } else null
            // A row holding right-to-left text runs both ways, so its chords are set where they stand rather than in the
            // order they are read off the chord row, which is left to right.
            val bidi = lyrics.bidi?.indexOf(chordStart = x, chordEnd = chordEnd(chords, word), length = lyrics.text.length, tolerance = bidiTolerance)
            if (bidi != null) {
                at = bidi.coerceAtMost(lyrics.text.length)
                if (bidi == lyrics.text.length) isAttachedAtEnd = true
            } else if (exact != null) at = exact
            else if (lyrics.isCampfire && lyricsEnd != null && abs(lyricsEnd - x) <= EXACT_POSITION) {
                at = lyrics.text.length
                isAttachedAtEnd = true
            } else if (lyrics.positions.isEmpty()) at = 0
            else {
                val lastWidth = lyrics.source.spans.lastOrNull()?.let { (it.end - it.start) / it.text.length.coerceAtLeast(1) } ?: 1.0
                if (x >= lyrics.positions.last() + lastWidth - 0.01) at = lyrics.text.length
                else {
                    val nearest = if (isMonotone) {
                        val right = firstIndex(starts.size) { positions[starts[it]] >= x }
                        val left = if (right > 0) firstIndex(starts.size) { positions[starts[it]] >= positions[starts[right - 1]] } else -1
                        when {
                            left < 0 -> starts.getOrNull(right)
                            right == starts.size || abs(positions[starts[left]] - x) <= abs(positions[starts[right]] - x) -> starts[left]
                            else -> starts[right]
                        }
                    } else starts.minByOrNull { abs(positions[it] - x) }
                    nearest?.let { start ->
                        val position = positions[start]
                        val span = if (spansSorted) {
                            spans.getOrNull(firstIndex(spans.size) { prefixMaxEnd[it] > position })?.takeIf { it.start <= position }
                        } else spans.firstOrNull { position >= it.start && position < it.end }
                        val width = span?.let { (it.end - it.start) / it.text.length.coerceAtLeast(1) } ?: 1.0
                        if (abs(lyrics.positions[start] - x) <= width * 1.05 || lyrics.text.getOrNull(at) == ' ' && start == at + 1) at = start
                    }
                }
            }
            if (bidi == null) at = at.coerceAtLeast(previous)
            previous = at
            insertions.getOrPut(at) { mutableListOf() } += if (word in notes) note(word.text) else "[${rowChordName(word.text)}]"
        }
        var escaped = ChordProLiteralText.escape(lyrics.text)
        // A lyric printed a few columns in from the chord over it starts where that chord does, which is all its indent
        // says; written as it is, it would be sung after a pause of spaces. Two chords or more in the indent are played
        // before the words, so it is kept where they would otherwise run together.
        val indent = escaped.indexOfFirst { !it.isWhitespace() }
        if (indent > 0 && insertions.keys.count { it < indent } <= 1) {
            val moved = insertions.entries.sortedBy { it.key }.groupBy({ (it.key - indent).coerceAtLeast(0) }, { it.value })
            insertions.clear()
            moved.forEach { (at, values) -> insertions[at] = values.flatten().toMutableList() }
            escaped = escaped.substring(indent)
        }
        val merged = buildString {
            var carried: List<String> = emptyList()
            for (index in 0..escaped.length) {
                val due = insertions[index].orEmpty()
                if (splitsSurrogate(escaped, index)) carried = due
                else (carried + due).takeIf { it.isNotEmpty() }?.let { values ->
                    // A chord carried past a final emoji belongs to it, so only a chord beyond the lyrics is set apart.
                    if (index == escaped.length && carried.isEmpty() && isNotEmpty() && last() != ' ' && !isAttachedAtEnd) append(' ')
                    append(values.joinToString(if (index == escaped.length) " " else ""))
                    carried = emptyList()
                }
                if (index < escaped.length) append(escaped[index])
            }
        }
        // Inline conversion uses different offsets after insertion; convert those tokens in the merged string.
        val converted = if (convertParentheses) parentheses.replace(merged) { if (chord(it.groupValues[1])) "[${ascii(it.groupValues[1])}]" else it.value } else merged
        return converted + row?.annotation?.let { " " + ChordProLiteralText.escape(it) }.orEmpty()
    }

    /** Where the last character of [word] on the chord row [chords] ends. */
    private fun chordEnd(chords: Rendered, word: Token): Double {
        val last = (word.index + word.text.length - 1).coerceAtMost(chords.positions.lastIndex)
        val position = chords.positions.getOrNull(last) ?: return 0.0
        val span = chords.source.spans.lastOrNull { position >= it.start - 0.01 && position < it.end + 0.01 }
        // The span's width is shared by the characters render keeps of it, the export's fillers left out.
        val kept = { span: ChordSheet.Span -> clean(if (chords.isCampfire) span.text.filterNot(::isExportFiller) else span.text) }
        return position + (span?.let { (it.end - it.start) / kept(it).length.coerceAtLeast(1) } ?: 0.0)
    }

    /** An insertion or replacement must leave a supplementary character whole. */
    private fun splitsSurrogate(text: String, index: Int) = index > 0 && index < text.length &&
        text[index - 1].isHighSurrogate() && text[index].isLowSurrogate()

    /** The first true element of a monotone predicate, or the size when none matches. */
    private inline fun firstIndex(size: Int, matches: (Int) -> Boolean): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (matches(middle)) high = middle else low = middle + 1
        }
        return low
    }

    /** The text of [lines] from [first] to [last], one value the export wrapped: a row continuing a word joins with no space. */
    private fun joinRows(lines: List<Rendered>, first: Int, last: Int) =
        ((first + 1)..last).fold(lines[first].text.trim()) { text, index -> text + lines[index].joint + lines[index].text.trim() }

    private fun headerValue(value: String) = value.replace('{', '(').replace('}', ')').replace('\n', ' ').trim()
    private fun collapse(lines: List<String>): String {
        val result = mutableListOf<String>()
        for (line in lines) if (line.isNotBlank() || result.lastOrNull()?.isNotBlank() == true) result += line.trimEnd()
        return result.joinToString("\n").trim() + "\n"
    }

    /**
     * How far under the row above, in its own size, a row of Campfire's heading stands at most: the heading's rows are
     * 1.45 times the font size apart, about 1.2 to 1.35 of a span's size, and the gap before the first block adds 0.8
     * times the font size.
     */
    private const val HEADING_ROW = 1.6

    /** How close to a character a chord of Campfire's own export stands to be set on it, in points. */
    private const val EXACT_POSITION = 0.75

    /** A word, or a bar line or repeat sign standing for itself even where a chord is glued to it (`||:Am`). */
    private val noChord = setOf("N.C.", "NC")
    private val token = Regex("\\|\\|:?|:\\|:|:\\|\\|?|\\|:?|[^\\s|]+")
    private val embed = Regex("^[0-9]*Embed$")

    /** A share count glued to a lyric's last letter or punctuation; a sung "we embed" keeps its space. */
    private val gluedEmbed = Regex("(?<=[^\\s0-9])[0-9]*Embed$")
    private val bracketedLine = Regex("^\\[([^\\[\\]]+)\\]$")
    private val parentheses = Regex("\\(([^()]+)\\)")
    private val keyLabels = setOf(
        "key", "key of", "hangnem", "tom", "tono", "tonalidad", "tonart", "tonalit\u00e9", "tonalit\u00e0", "tonacja", "toonsoort",
        "tonalitate", "\u05d8\u05d5\u05e0\u05dc\u05d9\u05d5\u05ea",
    )
    private val capoLabels = setOf(
        "capo", "capotraste", "cejilla", "kapodaster", "capodastre", "kapo", "capodastru", "capotasto", "\u05e7\u05d0\u05e4\u05d5",
    )

    /**
     * The words Campfire's PDF puts in parentheses after a capo whose chords are named as they sound, in every language
     * the app prints them in (`print_capo_sounding`), lowercase.
     */
    private val soundingCapoMarkers = setOf(
        "chords as they sound",
        "hangz\u00f3 akkordok",
        "acorduri cum sun\u0103",
        "klingende akkorde",
        "acordes como suenan",
        "accords en son r\u00e9el",
        "accordi come suonano",
        "\u05d0\u05e7\u05d5\u05e8\u05d3\u05d9\u05dd \u05e9\u05e0\u05e9\u05de\u05e2\u05d9\u05dd",
    )

    /**
     * The words around the key Campfire's PDF puts in parentheses after a capo whose chords are its shapes, in every
     * language the app prints them in (`song_details_capo_shapes`), as prefix and suffix pairs, lowercase.
     */
    private val shapesCapoMarkers = listOf(
        "" to " shapes",
        "" to " fog\u00e1sok",
        "pozi\u021bii de " to "",
        "" to "-griffe",
        "formas de " to "",
        "doigt\u00e9s de " to "",
        "diteggiature di " to "",
        "\u05e6\u05d5\u05e8\u05d5\u05ea " to "",
    )

    /** The time signature labels of the app's languages besides English and Hungarian, all words a lyric may open with too. */
    private val foreignTimeLabels = setOf("m\u0103sur\u0103", "taktart", "comp\u00e1s", "mesure", "metro", "\u05de\u05e9\u05e7\u05dc")
    private val metadata = Regex(
        "^(Capo|Capotraste|Cejilla|Kapodaster|Capodastre|Kapo|Capodastru|Capotasto|\u05e7\u05d0\u05e4\u05d5|" +
            "Key|Hangnem|Tom|Tono|Tonalidad|Tonart|Tonalit\u00e9|Tonalit\u00e0|Tonacja|Toonsoort|Tonalitate|\u05d8\u05d5\u05e0\u05dc\u05d9\u05d5\u05ea|" +
            "Tempo|Temp\u00f3|BPM|\u05d8\u05de\u05e4\u05d5|Time|\u00dctemmutat\u00f3|M\u0103sur\u0103|Taktart|Comp\u00e1s|Mesure|Metro|\u05de\u05e9\u05e7\u05dc|" +
            "Artist|El\u0151ad\u00f3|Song|Title|Album|Year|Composer|Lyricist|words (?:and|&) music by|by)" +
            "\\s*[:;]\\s*(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val transposition = Regex(
        "^(Transposition|Transzpon\u00e1l\u00e1s|Transpunere|Transposici\u00f3n|Trasposizione|" +
            "\u05d8\u05e8\u05e0\u05e1\u05e4\u05d5\u05d6\u05d9\u05e6\u05d9\u05d4)\\s*:\\s*([+-]?[0-9]+)$",
        RegexOption.IGNORE_CASE,
    )
    private val unlabelledMetadata = Regex(
        "^(Capo|Capotraste|Cejilla|Kapodaster|Capodastre|Kapo|Capodastru|Capotasto|\u05e7\u05d0\u05e4\u05d5|" +
            "Key of|Key|Hangnem|Tonalidad|Tonart|Tonalit\u00e9|Tonalit\u00e0|Tonacja|Toonsoort|Tonalitate|\u05d8\u05d5\u05e0\u05dc\u05d9\u05d5\u05ea|" +
            "Tempo|Time|words (?:and|&) music by)\\s+(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val capoPreposition = Regex("^(?:on|at)\\s+", RegexOption.IGNORE_CASE)
    private val romanNumerals = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
    private val romanCapo = Regex("^(XII|XI|X|IX|IV|V?I{1,3}|V)(?:\\s+(?:FRET|TRASTE|TASTO|BUND|BUNDSTAB))?$")
    private val noCapo = Regex("^(?:No|Sin|Senza|Nincs) capo$", RegexOption.IGNORE_CASE)
    private val timeSignature = Regex("[0-9]+/[0-9]+")
    private val timingNames = setOf("tempo", "time")
    private val labelGap = Regex("\\s{2,}")
    private val pageWords = setOf("chords", "tab", "lyrics")
    private val credit = Regex("^(?:words (?:and|&) music )?by\\s+(.+)$", RegexOption.IGNORE_CASE)
    /** A credit ending in the year of the song, `Words & music by David Bowie 1972`. */
    private val creditYear = Regex("^(.+?),?\\s+((?:1[5-9]|20)[0-9]{2})$")
    /** An artist's name followed by the year of the song, which only a comma tells apart from a name: `David Bowie, 1972`. */
    private val commaYear = Regex("^(.+?),\\s+((?:1[5-9]|20)[0-9]{2})$")
    private val bpm = Regex("^([0-9]+)\\s+BPM$", RegexOption.IGNORE_CASE)
    private val tab = Regex("^[eEaAbBdDgG](?:[#b])?\\s*\\|[-0-9|hpbrx/\\\\~(). :]+$")

    /** What [labelledRow] names a transposition, which no directive is written for. */
    private const val TRANSPOSITION = "transposition"

    /** What [metadata] names a line saying the song is played without a capo, which is the default and is written as nothing. */
    private const val NO_CAPO = "no capo"

    /** What [metadata] names a capo marked as one the chords are named as they sound over, `2 (chords as they sound)`. */
    private const val CAPO_SOUNDING = "capo sounding"

    /** What [metadata] names a capo marked with the key of the shapes the chords under it are, `2 (Em shapes)`. */
    private const val CAPO_SHAPES = "capo shapes"

    /** What [metadata] names a credit (`By:`, `Words and music by:`), whose value may end in the year of the song. */
    private const val CREDIT = "credit"
}
