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
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordPlacement.bracketed
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordPlacement.inline
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordPlacement.isUnaligned
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordPlacement.merge
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
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.note
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.parentheses
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.rowChord
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.tokens
import com.pandulapeter.campfire.chordpro.convert.ChordSheetText.clean
import com.pandulapeter.campfire.chordpro.convert.ChordSheetText.headerValue
import com.pandulapeter.campfire.chordpro.convert.ChordSheetText.headingLabel
import com.pandulapeter.campfire.chordpro.convert.ChordSheetText.isExportFiller
import com.pandulapeter.campfire.chordpro.convert.ChordSheetText.joinRows
import com.pandulapeter.campfire.chordpro.edit.ChordProLiteralText
import com.pandulapeter.campfire.chordpro.syntax.ChordProDirectives
import com.pandulapeter.campfire.chordpro.syntax.ChordProTokens
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary
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
                    line.copy(marks = (line.marks ?: CampfireMarks()).copy(role = roles[index], bodySize = typeSizes.lyrics ?: 0.0, isSetFromRight = isSetFromRight[index]))
                }
            }
        }
        val all = pages.flatten()
        val original = sheet.originalText ?: all.joinToString("\n") { it.text }
        if (isChordPro(original)) return listOf(original)
        val cuts = ChordSheetSongSplit.cuts(pages.map(::splitLines), ChordSheetProfile.of(all.map { it.source }))
        val groups = mutableListOf<MutableList<Rendered>>()
        pages.forEachIndexed { pageIndex, page ->
            page.forEachIndexed { lineIndex, line ->
                val place = ChordSheetSongSplit.Place(pageIndex, lineIndex)
                if (groups.isEmpty() || place in cuts.opens) groups += mutableListOf<Rendered>()
                if (place !in cuts.dropped) groups.last() += cuts.numbers[place]?.let { length -> withoutNumber(line, length) } ?: line
            }
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
        val features = features(lines)
        val kinds = classify(lines, features, candidates).map { if (it == LineKind.METADATA) LineKind.LYRIC else it }
        // A bracketed heading pasted as it is would read as a chord, so it is reason enough to convert lyrics with no chords.
        if (LineKind.CHORD !in kinds && kinds.indices.none { kinds[it] == LineKind.SECTION && lines[it].text.trimStart().startsWith('[') }) return null
        // The line breaks the paste ends with are kept after the section it opened rather than inside it, so that what
        // follows the paste in the song is not pulled into the section, nor the section given an empty last line.
        val trailingBlanks = kinds.takeLastWhile { it == LineKind.BLANK }.size
        val body = lines.size - trailingBlanks
        return (
            writeBody(
                lines = lines.take(body),
                features = features.take(body),
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
                lines[index].text.isBlank() -> LineKind.BLANK
                index in chordRows && candidates[index] != null -> LineKind.CHORD
                else -> LineKind.LYRIC
            }
        }
        return writeBody(lines, features(lines), kinds, candidates, omitted = emptySet(), changes = emptyMap(), convertParentheses = false)
            .joinToString("\n")
    }

    private val sungWord = Regex("\\p{L}{2}")

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

    /**
     * The text of [line], spaced by where its words stand. Where it [dropsPadding] (Campfire's own export), the no-break
     * spaces padding a lyric under a wide chord are left out, and leave no gap that would read as a space: the export
     * writes every space of the words as one. Its continuation marks are left out too, and kept as [CampfireMarks.isContinuation]
     * and [CampfireMarks.isHardCut].
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
        return Rendered(result, positions.take(result.length), line, bidi = bidi?.build(), marks = if (dropsPadding) CampfireMarks(isContinuation = isContinuation, isHardCut = isHardCut) else null)
    }

    /**
     * [page] of Campfire's export with each row marked whose line the row under it continues in the chords' or the lyrics'
     * type ([CampfireMarks.isContinuedBelow]). A marked row set wholly in italics is the rest of a comment or of an italic
     * lyric, which says nothing of the row above being a chord row.
     */
    private fun markRowsContinuedBelow(page: List<Rendered>) = page.mapIndexed { index, line ->
        val next = page.getOrNull(index + 1)
        line.copy(marks = line.marks?.copy(isContinuedBelow = next != null && next.marks?.isContinuation == true && !next.source.spans.filter { it.text.isNotBlank() }.all { it.isItalic }))
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
        line.marks != null && labelledRow(text, isHeader = true)?.singleOrNull()?.first == ChordProVocabulary.KEY

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
     * wrapped lyric's rest is ([CampfireMarks.isContinuation]), and read apart its first row would name a bare value and the
     * rest a lyric. A row continuing a word the column cut inside ([CampfireMarks.isHardCut]) joins with no space.
     */
    private fun joinWrappedDetails(lines: List<Rendered>): List<Rendered> = lines.fold(mutableListOf()) { joined, line ->
        val previous = joined.lastOrNull()
        // The header reaches as far as classify counts it, from the row this one would continue.
        val isHeader = ChordSheetProfile.isInHeader(joined.lastIndex)
        val indent = line.text.length - line.text.trimStart().length
        val text = previous?.let { "${it.text}${line.joint}${line.text.substring(indent)}" }
        if (line.marks?.isContinuation == true && previous != null && text != null && (labelledRow(text, isHeader) != null || isKeyChange(line, text))) {
            val space = if (line.marks.isHardCut) DoubleArray(0) else doubleArrayOf(previous.positions.lastOrNull() ?: 0.0)
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

    /** The [line] without the [length] characters after its leading blanks that number its song in a songbook. */
    private fun withoutNumber(line: Rendered, length: Int): Rendered {
        val start = line.text.indexOfFirst { !it.isWhitespace() }
        val end = start + length
        return line.copy(text = line.text.substring(0, start) + line.text.substring(end), positions = line.positions.copyOf(start) + line.positions.copyOfRange(end, line.positions.size), bidi = null)
    }

    /** The [page]'s lines for [ChordSheetSongSplit], which asks whether a line is a title by its type only of the page's first. */
    private fun splitLines(page: List<Rendered>): List<ChordSheetSongSplit.Line> {
        val lead = page.indexOfFirst { it.text.isNotBlank() }
        return page.mapIndexed { index, line ->
            val isChordRow = line.text.isNotBlank() && chordTokens(line) != null
            val isTitleShaped = line.text.isNotBlank() && !isChordRow && !line.isMarginLabel && !isHeading(line.text) && metadata(line.text) == null
            ChordSheetSongSplit.Line(line.text.trim(), line.source, isChordRow, isTitleShaped, isTitleShaped && index == lead && isTitle(line, page))
        }
    }

    private fun isTitle(line: Rendered, lines: List<Rendered>): Boolean {
        if (line.text.isBlank() || line.isMarginLabel || isHeading(line.text) || metadata(line.text) != null || chordTokens(line) != null) return false
        val median = ChordSheetProfile.medianWithout(lines.filter { it !== line }.map { it.source }) ?: line.source.spans.maxOfOrNull { it.size } ?: 1.0
        return line.source.isHeading || line.source.spans.any { it.size > median * ChordSheetProfile.TITLE_RATIO } ||
            line.source.spans.filter { it.text.isNotBlank() }.let { spans -> spans.isNotEmpty() && spans.all { it.isBold } } && lines.any { chordTokens(it) != null }
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

    /**
     * Whether [line] is set in the type of a [title] that is larger than the body text, which is how a wrapped title
     * continues; a title told apart only by being bold has no such type to recognize its continuation by.
     */
    private fun isTitleContinuation(line: Rendered, title: Rendered, lines: List<Rendered>): Boolean {
        val titleSpans = title.source.spans.filter { it.text.isNotBlank() }
        val titleSize = titleSpans.maxOfOrNull { it.size } ?: return false
        val median = ChordSheetProfile.medianWithout(lines.filter { it !== title && it !== line }.map { it.source }) ?: return false
        val spans = line.source.spans.filter { it.text.isNotBlank() }
        return titleSize > median * ChordSheetProfile.TITLE_RATIO && spans.isNotEmpty() &&
            spans.all { ChordSheetProfile.isSameSize(it.size, titleSize) && it.isBold == titleSpans.first().isBold }
    }

    private fun convertSong(lines: List<Rendered>, guessesTitle: Boolean): String {
        val candidates = chordCandidates(lines)
        val convertParentheses = convertsParentheses(lines)
        val features = features(lines)
        val kinds = classify(lines, features, candidates)
        val styled = lines.any { line -> !isChordLine(line.text) && line.source.spans.any { (it.isBold || it.isRaised) && chord(it.text.trim()) } }
        if (kinds.none { it in listOf(LineKind.CHORD, LineKind.SECTION, LineKind.TAB, LineKind.GRID) } && !convertParentheses && !styled) {
            return collapse(lines.map { ChordProLiteralText.escape(it.text) })
        }
        val header = mutableListOf<String>()
        val omitted = mutableSetOf<Int>()
        val (changes, soundingCapo) = extractHeader(lines, features, kinds, header, omitted)
        if (guessesTitle) extractTitle(lines, kinds, header, omitted)
        val definitions = ChordSheetDefinitions.definitions(
            lines = lines.map { it.text },
            // A shape in parentheses after its chord reads as a chord row with a note, `D7 (x-x-0-2-1-2)`.
            candidates = kinds.indices.filter { (kinds[it] == LineKind.LYRIC || kinds[it] == LineKind.CHORD) && it !in omitted },
            chordName = { name -> ascii(name).takeIf(::isChordOrLatinName) },
        )
        header += definitions.values
        omitted += definitions.keys
        val output = writeBody(lines, features, kinds, candidates, omitted, changes, convertParentheses)
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

    /** Chords written in parentheses inside the lyrics, `(Am)`, are taken for chords only where most parentheses hold one. */
    private fun convertsParentheses(lines: List<Rendered>): Boolean {
        val parenthesized = lines.flatMap { parentheses.findAll(it.text).toList() }
        return parenthesized.isNotEmpty() && parenthesized.count { chord(it.groupValues[1]) } * 2 > parenthesized.size
    }

    private fun hasUnambiguousChords(candidates: List<List<Token>?>) = candidates.any { it != null && it.count { word -> rowChord(word.text) } > 1 }

    /** Whether a sheet reads as a song rather than prose: a row of two chords or more, or a heading. */
    private fun hasStructure(lines: List<Rendered>, candidates: List<List<Token>?>) =
        hasUnambiguousChords(candidates) || lines.any { ChordSheetHeadings.parse(it.text) != null }

    /** What each of a song's [lines] says on its own, read once for every decision about the song. */
    private fun features(lines: List<Rendered>) = lines.mapIndexed { index, line ->
        LineFeatures(
            heading = ChordSheetHeadings.parse(line.text),
            metadata = metadata(line.text),
            headerRow = labelledRow(line.text, isHeader = ChordSheetProfile.isInHeader(index)),
            bodyRow = labelledRow(line.text, isHeader = false),
            look = ChordSheetLook.of(line.source, line.positions.getOrNull(line.text.indexOfFirst { c -> !c.isWhitespace() })),
            isSung = line.marks?.isContinuation != true && sungWord.containsMatchIn(line.text),
        )
    }

    private fun classify(lines: List<Rendered>, features: List<LineFeatures>, candidates: List<List<Token>?>): List<LineKind> {
        val hasUnambiguousChords = hasUnambiguousChords(candidates)
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
        return lines.mapIndexed { index, line ->
            val candidate = candidates[index]
            when {
                line.text.isBlank() -> LineKind.BLANK
                line.marks?.role == ChordSheetCampfireLayout.Role.LABEL -> LineKind.SECTION
                line.marks?.role == ChordSheetCampfireLayout.Role.GRID -> LineKind.GRID
                line.marks?.role == ChordSheetCampfireLayout.Role.COMMENT || line.marks?.role == ChordSheetCampfireLayout.Role.BOX_COMMENT -> LineKind.COMMENT
                // A row of chord names right over a staff names its bars, its columns the staff's, so it stays in the tab
                // as it stands rather than being set over a lyric line it was never written for.
                isStaff[index] || candidate != null && isStaff.getOrNull(index + 1) == true -> LineKind.TAB
                headings[index] || line.isMarginLabel -> LineKind.SECTION
                ChordSheetProfile.isInHeader(index) && (features[index].metadata?.second?.isNotBlank() == true || features[index].headerRow != null) ||
                    features[index].metadata?.first == "copyright" ||
                    features[index].bodyRow != null || isKeyChange(line, line.text) -> LineKind.METADATA
                // A row of notes alone is a candidate only where the row under it continues its line.
                // A row of chords the export marks as the rest of a line is one whatever follows, and so is the row its
                // chords overflowed from; only an export's chords in their own type are candidates, so a wrapped lyric's
                // marked rest that reads as chord names (`a b`) is never taken for one.
                candidate != null && (candidate.isEmpty() ||
                    candidate.isNotEmpty() && line.marks?.isContinuation == true ||
                    candidate.isNotEmpty() && lines.getOrNull(index + 1)?.let { it.marks?.isContinuation == true && candidates.getOrNull(index + 1) != null } == true ||
                    candidate.count { rowChord(it.text) } > 1 ||
                    candidate.count { rowChord(it.text) } == 1 &&
                    candidate.filter { rowChord(it.text) }.all { it.text.first().isUpperCase() } &&
                    headings.getOrNull(index - 1) == true &&
                    lines.getOrNull(index + 1)?.let { it.text.isBlank() || headings[index + 1] } != false ||
                    (hasUnambiguousChords || isStyledChordLine(line, candidate, lines.getOrNull(index + 1))) &&
                    lines.getOrNull(index + 1)?.let { it.text.isNotBlank() && chordTokens(it) == null && !headings[index + 1] && features[index + 1].metadata == null } == true) -> LineKind.CHORD
                else -> LineKind.LYRIC
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
        features: List<LineFeatures>,
        kinds: List<LineKind>,
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
        kinds.forEachIndexed { index, kind -> if (kind == LineKind.METADATA) {
            (features[index].headerRow ?: listOf(features[index].metadata!!)).forEach { (name, value) ->
                when {
                    value.isBlank() || name == TRANSPOSITION || name == NO_CAPO -> Unit
                    name in timingNames && !declared.add(name) -> changes[index] = changes[index].orEmpty() + "{$name: ${headerValue(value)}}"
                    name == ChordProVocabulary.KEY && lines[index].marks != null && !declared.add(name) -> {
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
        kinds: List<LineKind>,
        header: MutableList<String>,
        omitted: MutableSet<Int>,
    ) {
        val first = kinds.indexOfFirst { it != LineKind.BLANK }
        if (first < 0 || kinds[first] != LineKind.LYRIC) return
        // The export's own test reads chords by their type, which a title naming a chord (`A`) is not printed in.
        if (if (lines[first].marks != null) chordRow(lines[first]) != null else isChordLine(lines[first].text)) return
        if (!isTitle(lines[first], lines)) return extractPlainTitle(lines, kinds, first, header, omitted)
        // A title too long for its column is wrapped onto the lines under it, in the same type, which no line of the
        // song is set in: those lines are the rest of the title rather than its credit or its first lyrics.
        var last = first
        while (kinds.getOrNull(last + 1) == LineKind.LYRIC && !lines[last + 1].isHeaderPart && isTitleContinuation(lines[last + 1], lines[first], lines)) last++
        val title = joinRows(lines, first, last)
        // Campfire's export prints the file's title alone, its artist on a line of its own, so there is no page word or
        // credit in it to split off.
        val (name, named) = if (lines[first].marks != null) title to null else ChordSheetTitles.split(title)
        addTitle(header, name, named)
        omitted += first..last
        val artist = last + 1
        // Campfire's export sets the artist in the lyrics' type right under the title, at the heading's spacing, wrapped
        // onto as many rows as it takes, and its first block a heading gap further down: the rows at that spacing are the
        // artist, however many and however long, and nothing else is, not even a short first lyric under a long title.
        if (named == null && lines.getOrNull(artist)?.marks != null && lines[artist].source.baseline != null) {
            val rows = generateSequence(artist) { it + 1 }.takeWhile { index ->
                val line = lines.getOrNull(index)
                val below = line?.source?.baseline
                val above = lines[index - 1].source.baseline
                kinds.getOrNull(index) == LineKind.LYRIC && below != null && above != null &&
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
        val isCampfireArtist = lines.getOrNull(artist)?.marks != null && kinds.getOrNull(artist + 1) == LineKind.METADATA
        if (named == null && kinds.getOrNull(artist) == LineKind.LYRIC && (credit != null || isCampfireArtist || lines[artist].text.length < title.length &&
                lines[artist].source.spans.maxOfOrNull { it.size }?.let { it < (lines[first].source.spans.maxOfOrNull { span -> span.size } ?: 1.0) } == true)) {
            header += "{artist: ${headerValue(credit?.groupValues?.get(1) ?: lines[artist].text.trim())}}"
            omitted += artist
        } else if (kinds.getOrNull(artist) == LineKind.LYRIC && (lines[artist].isHeaderPart || isCenteredUnder(lines[artist], lines[first], lines))) {
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
        kinds: List<LineKind>,
        first: Int,
        header: MutableList<String>,
        omitted: MutableSet<Int>,
    ) {
        val text = lines[first].text.trim()
        if (text.startsWith('[') || text.startsWith('{') || isChordSummary(text) || text.lowercase() in pageWords) return
        val (name, named) = ChordSheetTitles.split(text)
        val credit = lines.getOrNull(first + 1)?.let { credit.matchEntire(it.text.trim()) }
        val creditAt = (first + 1).takeIf {
            named == null && kinds.getOrNull(it) == LineKind.LYRIC && (credit != null ||
                lines[it].text.trim().length < text.length && kinds.getOrNull(it + 1).let { next -> next == LineKind.BLANK || next == LineKind.METADATA })
        }
        var next = (creditAt ?: first) + 1
        while (kinds.getOrNull(next) == LineKind.METADATA) next++
        val structure = kinds.indices.firstOrNull { kinds[it] == LineKind.CHORD || kinds[it] == LineKind.SECTION } ?: return
        if (kinds.getOrNull(next) !in listOf(LineKind.BLANK, LineKind.CHORD, LineKind.SECTION) || structure !in (first + 1)..(first + 5)) return
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
        features: List<LineFeatures>,
        kinds: List<LineKind>,
        candidates: List<List<Token>?>,
        omitted: Set<Int>,
        changes: Map<Int, List<String>>,
        convertParentheses: Boolean,
    ): List<String> = with(BodyWriter()) {
        fun follow(at: Int) { if (features[at].isSung) features[at].look?.let { follow(it, lines[at].text) } }
        val unlabelled = ChordSheetLook.unlabelled(
            // A line with no word (a tab's string or a stray accidental the reading took for a lyric) is not sung.
            looks = lines.indices.map { at ->
                if (kinds[at] != LineKind.LYRIC || at in omitted || !features[at].isSung) null else features[at].look
            },
            breaks = kinds.map { it == LineKind.BLANK || it == LineKind.SECTION || it == LineKind.COMMENT || it == LineKind.TAB || it == LineKind.GRID },
            rows = kinds.map { it == LineKind.CHORD },
        )
        fun openUnlabelled(at: Int) { if (at in unlabelled) openChorus() }
        var index = 0
        while (index < lines.size) {
            changes[index]?.let { pendingChanges += it }
            if (index in omitted) { index++; continue }
            val line = lines[index]
            fun nextContent(after: Int) = ((after + 1)..lines.lastIndex).firstOrNull { kinds[it] != LineKind.BLANK && kinds[it] != LineKind.COMMENT && it !in omitted }
            // A comment over a staff is the tab's own, as a label over it is the tab's.
            if (kinds[index] != LineKind.TAB && !(kinds[index] == LineKind.COMMENT && nextContent(index)?.let { kinds[it] } == LineKind.TAB)) closeTab()
            if (kinds[index] != LineKind.GRID) closeGrid()
            // A change before a heading belongs to the section it heads rather than to the end of the one before.
            if (kinds[index] != LineKind.SECTION && kinds[index] != LineKind.BLANK) flushChanges()
            when (kinds[index]) {
                LineKind.SECTION if line.marks?.role == ChordSheetCampfireLayout.Role.LABEL -> {
                    closeSection()
                    flushChanges()
                    val label = headerValue(line.text)
                    if (output.lastOrNull()?.isNotBlank() == true) output += ""
                    if (nextContent(index)?.let { kinds[it] } == LineKind.TAB) {
                        output += "{start_of_tab: $label}"
                        inTab = true
                    } else {
                        val type = ChordSheetCampfireLayout.sectionType(label)
                        output += "{start_of_$type: $label}"
                        open(type)
                    }
                }
                LineKind.COMMENT -> {
                    var end = index
                    while (end < lines.lastIndex && kinds[end + 1] == LineKind.COMMENT && lines[end + 1].marks?.role == line.marks?.role && end + 1 !in omitted) end++
                    // A comment the next section (or the song's end) follows stands after the section it is printed under.
                    if (!inTab && nextContent(end)?.let { kinds[it] == LineKind.SECTION } != false) {
                        closeSection()
                        if (output.lastOrNull()?.isNotBlank() == true) output += ""
                    }
                    val directive = if (line.marks?.role == ChordSheetCampfireLayout.Role.BOX_COMMENT) "comment_box" else "comment"
                    output += "{$directive: ${headerValue(joinRows(lines, index, end))}}"
                    index = end
                }
                LineKind.GRID -> { if (!inGrid) { output += "{start_of_grid}"; inGrid = true }; output += line.text.trimEnd() }
                LineKind.SECTION -> {
                    closeSection()
                    flushChanges()
                    val label = headerValue(headingLabel(line.text))
                    val type = if (line.isMarginLabel) ChordSheetMarginLabels.type(line.text) else features[index].heading?.type
                    val next = ((index + 1)..lines.lastIndex).firstOrNull { kinds[it] != LineKind.BLANK }
                    // Under a chorus heading, lines neither set the way the song set its chorus apart nor singing its words
                    // are sung after it.
                    val lyric = ((index + 1)..lines.lastIndex).takeWhile { kinds[it] != LineKind.SECTION }.firstOrNull { kinds[it] == LineKind.LYRIC && it !in omitted }
                    val isRecall = type != null && lyric != null && isRecall(type, features[lyric].look, lines[lyric].text)
                    if (type == "chorus" && (next == null || kinds[next] == LineKind.SECTION || isRecall)) output += "{chorus}"
                    else if (type != null) { output += "{start_of_$type: $label}"; open(type) }
                    else {
                        // A heading the parser does not take for a section ends the lyrics before it only after a blank
                        // line; without one it would be a comment inside them, and what follows their continuation.
                        if (output.lastOrNull()?.isNotBlank() == true) output += ""
                        output += "{comment: $label}"
                    }
                }
                LineKind.TAB -> { if (!inTab) { output += "{start_of_tab}"; inTab = true }; output += line.text }
                LineKind.CHORD -> {
                    openUnlabelled(index + 1)
                    val isUnderMarkedLyric = kinds.getOrNull(index + 1) == LineKind.LYRIC && index + 1 !in omitted && lines[index + 1].marks?.isContinuation == true
                    // A chord row the export marks with no marked lyric under it holds the chords the line above overflowed
                    // onto it, so it goes on that line, before the merge would set its chords into an unmarked lyric under it.
                    if (line.marks?.isContinuation == true && !isUnderMarkedLyric) {
                        addLyric(bracketed(candidates[index]!!, chordRow(line), line.marks.isSetFromRight), continues = true, isHardCut = false)
                    } else if (!line.isFromLabel && kinds.getOrNull(index + 1) == LineKind.LYRIC && index + 1 !in omitted && !isUnaligned(line, candidates[index]!!, lines[index + 1])) {
                        follow(index + 1)
                        addLyric(merge(line, lines[index + 1], convertParentheses), lines[index + 1].marks?.isContinuation == true, lines[index + 1].marks?.isHardCut == true)
                        index++
                    } else output += bracketed(candidates[index]!!, chordRow(line), line.marks?.isSetFromRight == true)
                }
                LineKind.LYRIC -> {
                    openUnlabelled(index)
                    follow(index)
                    addLyric(inline(line, convertParentheses), line.marks?.isContinuation == true, line.marks?.isHardCut == true)
                }
                LineKind.BLANK -> output += ""
                LineKind.METADATA -> Unit
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

        /** The lyrics outside a section since one last closed: the verses a section may be set apart from. */
        private val verseLooks = mutableListOf<ChordSheetLook>()
        private var isVerseInterrupted = false

        /** The open section's lyrics so far, and the verses where it opened. */
        private val sectionLooks = mutableListOf<ChordSheetLook>()
        private var sectionVerses: List<ChordSheetLook> = emptyList()

        /** The look each kind of section was first set apart from the verses in, and the words of its lines. */
        private val setApart = mutableMapOf<String, Pair<ChordSheetLook, Set<String>>>()
        private val sectionWords = mutableSetOf<String>()

        /**
         * Whether a heading of [type] whose first lyric is [text] set in [look] recalls the section: the song set that
         * section apart before, and the lyric is neither set that way nor one of its lines written out again.
         */
        fun isRecall(type: String, look: ChordSheetLook?, text: String) = setApart[type]?.let { (setLook, words) ->
            look != null && setLook.isSetApartFrom(look) && words(text) !in words
        } == true

        private fun words(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.joinToString(" ")

        /** A chorus no heading names, set apart by its type alone, which a return to plain type ends. */
        fun openChorus() {
            if (environment != null || inTab || inGrid) return
            if (output.lastOrNull()?.isNotBlank() == true) output += ""
            output += "{start_of_chorus}"
            open("chorus")
            sectionVerses = emptyList()
        }

        fun open(type: String) {
            environment = type
            sectionLooks.clear()
            sectionVerses = verseLooks.toList()
            isVerseInterrupted = true
            sectionWords.clear()
        }

        /**
         * A lyric of [look]: a section set apart from the verses ends where the lyrics return to their look. With no verse
         * before it to compare with, only a return from bold or italic to plain type ends it, since lines of a centred
         * sheet each start somewhere else and a line in another type inside it is a backing vocal or an aside.
         */
        fun follow(look: ChordSheetLook, text: String) {
            if (environment != null && sectionLooks.isNotEmpty()) {
                val opened = sectionLooks.first()
                // With no verse to compare with, plain type is the one look known to be the verses'.
                val ends = if (sectionVerses.isEmpty()) (opened.isBold || opened.isItalic) && !look.isBold && !look.isItalic else {
                    isSetApart(sectionVerses, sectionLooks) && returnsToVerses(look, sectionVerses)
                }
                if (ends) {
                    // A section ending mid-song ends with its last stanza, before the blank lines parting it from the next.
                    val blanks = output.takeLastWhile { it.isBlank() }.size
                    repeat(blanks) { output.removeAt(output.lastIndex) }
                    closeSection(isReturn = true)
                    repeat(blanks) { output += "" }
                }
            }
            if (environment == null) {
                if (isVerseInterrupted) {
                    verseLooks.clear()
                    isVerseInterrupted = false
                }
                verseLooks += look
            } else {
                sectionLooks += look
                sectionWords += words(text)
            }
        }

        /**
         * Whether [section] is set apart from [verses]: its first lyric in another type, or two lyrics or more all starting
         * further in (or out) than the edge the verses share. A lyric alone shows no edge, so one indented by accident
         * sets nothing apart, and a centred sheet's verses share none.
         */
        private fun isSetApart(verses: List<ChordSheetLook>, section: List<ChordSheetLook>): Boolean {
            val verse = verses.last()
            if (section.first().differsInType(verse)) return true
            val edge = ChordSheetLook.sharedEdge(verses) ?: return false
            if (section.size < 2) return false
            val margin = maxOf(section.maxOf { it.size }, verse.size) * 1.5
            val insets = section.map { it.left - edge }
            return insets.all { it > margin } || insets.all { it < -margin }
        }

        /** Whether [look] is back in the verses' type and, where they share an edge, at it. */
        private fun returnsToVerses(look: ChordSheetLook, verses: List<ChordSheetLook>): Boolean {
            val verse = verses.last()
            if (look.differsInType(verse)) return false
            val edge = ChordSheetLook.sharedEdge(verses) ?: return true
            return abs(look.left - edge) <= maxOf(look.size, verse.size) * 1.5
        }

        /**
         * Closes the open section, recording its look only where the song showed it set apart: against verses outside any
         * section, or by a lyric returning from it ([isReturn]). In a song that heads every section the next heading closes
         * a chorus, so nothing is recorded and a later chorus heading over new words is that chorus written out.
         */
        fun closeSection(isReturn: Boolean = false) {
            val type = environment
            if (type != null && sectionLooks.isNotEmpty() && (isReturn || sectionVerses.isNotEmpty() && isSetApart(sectionVerses, sectionLooks))) {
                setApart.getOrPut(type) { sectionLooks.first() to sectionWords.toSet() }
            }
            type?.let { output += "{end_of_$it}" }
            environment = null
            sectionLooks.clear()
        }
        fun flushChanges() { output += pendingChanges; pendingChanges.clear() }
    }

    private fun collapse(lines: List<String>): String {
        val result = mutableListOf<String>()
        for (line in lines) if (line.isNotBlank() || result.lastOrNull()?.isNotBlank() == true) result += line.trimEnd()
        return result.joinToString("\n").trim() + "\n"
    }

    /**
     * How far a row's baseline may stand under the baseline of the row above, in its own line box, and still be a row of
     * Campfire's heading: the artist's rows are 1.45 font sizes apart (about 1.2 of an Inter line box) and the first one
     * about 1.6 under the title's, while the first block stands 2.25 under the heading's last row (1.86; 1.65 where both
     * are drawn in a fallback font's line box of about 1.36 font sizes, the narrowest margin).
     */
    private const val HEADING_ROW = 1.6

    private val embed = Regex("^[0-9]*Embed$")

    /** A share count glued to a lyric's last letter or punctuation; a sung "we embed" keeps its space. */
    private val gluedEmbed = Regex("(?<=[^\\s0-9])[0-9]*Embed$")
    private val bracketedLine = Regex("^\\[([^\\[\\]]+)\\]$")
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
