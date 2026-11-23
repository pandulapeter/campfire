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

import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.ascii
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.chord
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.chordRow
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.chordTokens
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.furniture
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.isBareLatinNote
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.isBareLetter
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.isCharacterColumns
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.isChordOrLatinName
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.isLatinChordBeyondNote
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.isPositioned
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.parentheses
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.rowChord
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.tokens
import com.pandulapeter.campfire.chordpro.convert.ChordSheetMetadata.metadata
import com.pandulapeter.campfire.chordpro.convert.ChordSheetText.clean
import com.pandulapeter.campfire.chordpro.edit.ChordProLiteralText
import com.pandulapeter.campfire.chordpro.syntax.ChordProDirectives
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary
import com.pandulapeter.campfire.pagesong.PageLineScores
import kotlin.math.roundToInt

/** Deterministic, conservative conversion of positioned chord sheets to ordinary ChordPro. */
public object ChordSheetConverter {
    /** Single-chord cells are unambiguous in alternating rows of a Word chord grid. */
    public fun isChordLine(text: String): Boolean = ChordSheetChordWords.rowTokens(text).let { words ->
        words.any { chord(it.text) } && words.all { chord(it.text) || furniture(it.text) }
    }
    /**
     * Returns one text per conservatively detected song. Existing ChordPro is returned untouched, including
     * [ChordSheet.originalText] where supplied. Other prose is escaped so it cannot become ChordPro markup.
     * The caller supplies Unicode NFC normalization; this dependency-free module has no platform normalizer.
     *
     * @param guessesTitle false for a caller that knows the song's title from elsewhere (another app's field): the first
     * lines are then read as the song's lines, the title and the credit under it included.
     * @param pageScores the page model's scores of the document's lines ([ChordSheetPageLines]), by which each song is
     * cropped to its lines before it is converted ([ChordSheetSongCrop]); null converts every line.
     */
    public fun convert(
        sheet: ChordSheet,
        normalize: (String) -> String = { it },
        guessesTitle: Boolean = true,
        pageScores: List<PageLineScores>? = null,
    ): List<String> {
        sheet.originalText?.takeIf(::isChordPro)?.let { return listOf(it) }
        val firstLines = sheet.pages.runningFold(0) { count, page -> count + page.lines.size }
        val pages = sheet.pages.mapIndexed { pageIndex, page ->
            ChordSheetMarginLabels.split(ChordSheetHeaderParts.split(page.lines)).map { placed ->
                render(placed.line, normalize).copy(isMarginLabel = placed.isMarginLabel, isHeaderPart = placed.isHeaderPart, origin = firstLines[pageIndex] + placed.origin)
            }
        }
        val all = pages.flatten()
        val original = sheet.originalText ?: all.joinToString("\n") { it.text }
        if (isChordPro(original)) return listOf(original)
        val profile = ChordSheetProfile.of(all.map { it.source })
        val cuts = ChordSheetSongSplit.cuts(pages.map { splitLines(it, profile) }, profile)
        val groups = mutableListOf<MutableList<Rendered>>()
        pages.forEachIndexed { pageIndex, page ->
            page.forEachIndexed { lineIndex, line ->
                val place = ChordSheetSongSplit.Place(pageIndex, lineIndex)
                if (groups.isEmpty() || place in cuts.opens) groups += mutableListOf<Rendered>()
                if (place !in cuts.dropped) groups.last() += cuts.numbers[place]?.let { length -> withoutNumber(line, length) } ?: line
            }
        }
        return groups.map { ChordSheetSongCrop.cropped(it, pageScores) }
            .map { normalize(convertSong(expandLabels(withoutInjections(singleSpaced(ChordSheetRomanChords.named(it)))), guessesTitle, profile)) }.filter { it.isNotBlank() }
    }

    /**
     * The lines a lyrics site puts into a copy of its page, Genius's "You might also like" and the "12Embed" its
     * share count ends one with, glued to the last lyric where the copy lost the line break, and a chord site's chrome
     * around the song ([ChordSheetSiteDetails.withoutChrome]). Only a song is read for them: a poem may well end in "Embed".
     */
    private fun withoutInjections(lines: List<Rendered>): List<Rendered> {
        if (!LineKinds.hasStructure(lines, chordCandidates(lines))) return lines
        val kept = ChordSheetSiteDetails.withoutChrome(lines)
        val last = kept.indexOfLast { it.text.isNotBlank() }
        return kept.mapIndexedNotNull { index, line ->
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
        val features = LineFeatures.of(lines)
        val kinds = LineKinds.of(lines, features, candidates).map { if (it == LineKind.METADATA) LineKind.LYRIC else it }
        // A bracketed heading pasted as it is would read as a chord, so it is reason enough to convert lyrics with no chords.
        if (LineKind.CHORD !in kinds && kinds.indices.none { kinds[it] == LineKind.SECTION && lines[it].text.trimStart().startsWith('[') }) return null
        // The line breaks the paste ends with are kept after the section it opened rather than inside it, so that what
        // follows the paste in the song is not pulled into the section, nor the section given an empty last line.
        val trailingBlanks = kinds.takeLastWhile { it == LineKind.BLANK }.size
        val body = lines.size - trailingBlanks
        return (
            SongWriter.write(
                SongStructure.of(
                    lines = lines.take(body),
                    features = features.take(body),
                    kinds = kinds.take(body),
                    candidates = candidates.take(body),
                    omitted = emptySet(),
                    changes = emptyMap(),
                    convertParentheses = convertsParentheses(lines),
                ),
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
                lines[index].text.isBlank() -> LineKind.BLANK
                index in chordRows && candidates[index] != null -> LineKind.CHORD
                else -> LineKind.LYRIC
            }
        }
        val parts = SongStructure.of(lines, LineFeatures.of(lines), kinds, candidates, omitted = emptySet(), changes = emptyMap(), convertParentheses = false)
        return SongWriter.write(parts).joinToString("\n")
    }

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

    /** The text of [line], spaced by where its words stand. */
    private fun render(line: ChordSheet.Line, normalize: (String) -> String): Rendered {
        val text = StringBuilder()
        val positions = Positions(line.spans.sumOf { it.text.length })
        val bidi = if (line.spans.any { it.pieces.isNotEmpty() }) ChordSheetBidiAnchors.Builder() else null
        var previous: ChordSheet.Span? = null
        for (span in line.spans) {
            val value = normalize(clean(span.text))
            if (value.isEmpty()) continue
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
            bidi?.span(span, { clean(it.toString()) }, value, width)
            previous = span
        }
        val result = text.toString().trimEnd()
        return Rendered(result, positions.take(result.length), line, bidi = bidi?.build())
    }

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

    /** Whether [text] is a row of chords, the notes a songbook prints among them included. */
    internal fun isChordRow(text: String) = chordRow(text) != null

    /** A heading that is one without the lines around it to tell, which is what a single line can be judged by. */
    internal fun isHeading(text: String) = ChordSheetHeadings.parse(text)?.isAmbiguous == false

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

    /** The [line] without the [length] characters after its leading blanks that number its song in a songbook. */
    private fun withoutNumber(line: Rendered, length: Int): Rendered {
        val start = line.text.indexOfFirst { !it.isWhitespace() }
        val end = start + length
        return line.copy(text = line.text.substring(0, start) + line.text.substring(end), positions = line.positions.copyOf(start) + line.positions.copyOfRange(end, line.positions.size), bidi = null)
    }

    /** The [page]'s lines for [ChordSheetSongSplit], which asks whether a line is a title by its type only of the page's first. */
    private fun splitLines(page: List<Rendered>, profile: ChordSheetProfile): List<ChordSheetSongSplit.Line> {
        val lead = page.indexOfFirst { it.text.isNotBlank() }
        return page.mapIndexed { index, line ->
            val isChordRow = line.text.isNotBlank() && chordTokens(line) != null
            val isTitleShaped = line.text.isNotBlank() && !isChordRow && !line.isMarginLabel && !isHeading(line.text) && metadata(line.text) == null
            ChordSheetSongSplit.Line(line.text.trim(), line.source, isChordRow, isTitleShaped, isTitleShaped && index == lead && SongHeader.isTitle(line, page, profile))
        }
    }

    private fun convertSong(lines: List<Rendered>, guessesTitle: Boolean, profile: ChordSheetProfile): String {
        val candidates = chordCandidates(lines)
        val convertParentheses = convertsParentheses(lines)
        val features = LineFeatures.of(lines)
        val kinds = LineKinds.of(lines, features, candidates)
        val styled = lines.any { line -> !isChordLine(line.text) && line.source.spans.any { (it.isBold || it.isRaised) && chord(it.text.trim()) } }
        if (kinds.none { it in listOf(LineKind.CHORD, LineKind.SECTION, LineKind.TAB, LineKind.GRID) } && !convertParentheses && !styled) {
            return collapse(lines.map { ChordProLiteralText.escape(it.text) })
        }
        val fields = SongHeader.fields(lines, features, kinds)
        val title = if (guessesTitle) SongHeader.title(lines, kinds, profile) else SongHeader.noTitle
        val omitted = fields.omitted + title.omitted
        val definitions = ChordSheetDefinitions.definitions(
            lines = lines.map { it.text },
            // A shape in parentheses after its chord reads as a chord row with a note, `D7 (x-x-0-2-1-2)`.
            candidates = kinds.indices.filter { (kinds[it] == LineKind.LYRIC || kinds[it] == LineKind.CHORD) && it !in omitted },
            chordName = { name -> ascii(name).takeIf(::isChordOrLatinName) },
        )
        val song = SongStructure.Song(
            // A credit printed both under the title and as a labelled line names the song once.
            header = (title.leading + fields.entries + title.trailing + definitions.values).distinct(),
            parts = SongStructure.of(lines, features, kinds, candidates, omitted + definitions.keys, fields.changes, convertParentheses),
        )
        val output = SongWriter.write(song)
        // The key lines after the header and the blank line under it are the key changes the body writes.
        val keyChanges = output.drop(if (song.header.isEmpty()) 0 else song.header.size + 1).count { it.startsWith("{${ChordProVocabulary.KEY}: ") }
        return asCapoChart(collapse(output), fields.soundingCapo, keyChanges)
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

    /** Chords written in parentheses inside the lyrics, `(Am)`, are taken for chords only where most parentheses hold one. */
    private fun convertsParentheses(lines: List<Rendered>): Boolean {
        val parenthesized = lines.flatMap { parentheses.findAll(it.text).toList() }
        return parenthesized.isNotEmpty() && parenthesized.count { chord(it.groupValues[1]) } * 2 > parenthesized.size
    }

    private fun collapse(lines: List<String>): String {
        val result = mutableListOf<String>()
        for (line in lines) if (line.isNotBlank() || result.lastOrNull()?.isNotBlank() == true) result += line.trimEnd()
        return result.joinToString("\n").trim() + "\n"
    }

    private val embed = Regex("^[0-9]*Embed$")

    /** A share count glued to a lyric's last letter or punctuation; a sung "we embed" keeps its space. */
    private val gluedEmbed = Regex("(?<=[^\\s0-9])[0-9]*Embed$")
}
