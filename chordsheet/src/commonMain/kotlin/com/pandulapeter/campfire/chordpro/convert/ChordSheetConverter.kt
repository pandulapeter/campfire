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
import com.pandulapeter.campfire.chordpro.convert.ChordSheetText.clean
import com.pandulapeter.campfire.chordpro.edit.ChordProLiteralText
import com.pandulapeter.campfire.chordpro.syntax.ChordProDirectives
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary
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
        val pages = sheet.pages.map { page ->
            ChordSheetMarginLabels.split(ChordSheetHeaderParts.split(page.lines)).map { placed ->
                render(placed.line, normalize).copy(isMarginLabel = placed.isMarginLabel, isHeaderPart = placed.isHeaderPart)
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
        return groups.map { normalize(convertSong(expandLabels(withoutInjections(singleSpaced(it))), guessesTitle, profile)) }.filter { it.isNotBlank() }
    }

    /**
     * The lines a lyrics site puts into a copy of its page, Genius's "You might also like" and the "12Embed" its
     * share count ends one with, glued to the last lyric where the copy lost the line break. Only a song is read for
     * them: a poem may well end in "Embed".
     */
    private fun withoutInjections(lines: List<Rendered>): List<Rendered> {
        if (!LineKinds.hasStructure(lines, chordCandidates(lines))) return lines
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
     * A label with a colon (or the semicolon some sites print instead) is metadata whatever follows it, except a label of
     * another language that is an ordinary word too ("Tom", "Metro", "Mesure"), which takes a value of its field's shape
     * only. Without a colon only a value of the field's own shape is
     * (`Capo 2`, `Key G`, `Time 4/4`), since a lyric may well start with one of these words: "By the rivers of
     * Babylon" and "Time after time" are sung, not credited. A value that cannot be read comes back blank, and its line
     * then stays a line of text rather than vanishing ([LineKinds]).
     */
    internal fun metadata(text: String): Pair<String, String>? {
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
    internal fun labelledRow(text: String, isHeader: Boolean): List<Pair<String, String>>? {
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

    /** Whether a capo [value] is Campfire's PDF saying the chords under it are named as they sound (`2 (chords as they sound)`). */
    private fun isSoundingCapo(value: String) = parentheses.find(value)?.groupValues?.get(1)?.trim()?.lowercase() in soundingCapoMarkers

    /**
     * The key a capo [value] names as its shapes' in Campfire's PDF (`2 (Em shapes)`, `2 (Em-Griffe)`), cut from the
     * original text so its case survives (`em` is no chord); null where the parentheses hold no such marker.
     */
    internal fun shapesKeyOf(value: String): String? {
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
    internal fun capo(value: String): String = Regex("[0-9]+").find(value)?.value
        ?: romanCapo.matchEntire(value.uppercase())?.groupValues?.get(1)?.let { roman -> (romanNumerals.indexOf(roman) + 1).toString() }
        ?: ""

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

    private fun collapse(lines: List<String>): String {
        val result = mutableListOf<String>()
        for (line in lines) if (line.isNotBlank() || result.lastOrNull()?.isNotBlank() == true) result += line.trimEnd()
        return result.joinToString("\n").trim() + "\n"
    }

    private val embed = Regex("^[0-9]*Embed$")

    /** A share count glued to a lyric's last letter or punctuation; a sung "we embed" keeps its space. */
    private val gluedEmbed = Regex("(?<=[^\\s0-9])[0-9]*Embed$")
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
    internal val timingNames = setOf("tempo", "time")
    private val labelGap = Regex("\\s{2,}")
    private val bpm = Regex("^([0-9]+)\\s+BPM$", RegexOption.IGNORE_CASE)

    /** What [labelledRow] names a transposition, which no directive is written for. */
    internal const val TRANSPOSITION = "transposition"

    /** What [metadata] names a line saying the song is played without a capo, which is the default and is written as nothing. */
    internal const val NO_CAPO = "no capo"

    /** What [metadata] names a capo marked as one the chords are named as they sound over, `2 (chords as they sound)`. */
    internal const val CAPO_SOUNDING = "capo sounding"

    /** What [metadata] names a capo marked with the key of the shapes the chords under it are, `2 (Em shapes)`. */
    internal const val CAPO_SHAPES = "capo shapes"

    /** What [metadata] names a credit (`By:`, `Words and music by:`), whose value may end in the year of the song. */
    internal const val CREDIT = "credit"
}
