/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import kotlin.math.abs
import kotlin.math.roundToInt

/** Deterministic, conservative conversion of positioned chord sheets to ordinary ChordPro. */
object ChordSheetConverter {
    /** Single-chord cells are unambiguous in alternating rows of a Word chord grid. */
    fun isChordLine(text: String): Boolean = tokens(text).let { words ->
        words.any { chord(it.text) } && words.all { chord(it.text) || furniture(it.text) }
    }
    /**
     * Returns one text per conservatively detected song. Existing ChordPro is returned untouched, including
     * [ChordSheet.originalText] where supplied. Other prose is escaped so it cannot become ChordPro markup.
     * The caller supplies Unicode NFC normalization; this dependency-free module has no platform normalizer.
     */
    fun convert(sheet: ChordSheet, normalize: (String) -> String = { it }): List<String> {
        sheet.originalText?.takeIf(::isChordPro)?.let { return listOf(it) }
        val pages = sheet.pages.map { page -> page.lines.map { render(it, normalize) } }
        val all = pages.flatten()
        val original = sheet.originalText ?: all.joinToString("\n") { it.text }
        if (isChordPro(original)) return listOf(original)
        val starts = pages.map { page -> page.indexOfFirst { it.text.isNotBlank() }.let { index ->
            index >= 0 && isTitle(page[index], page)
        } }
        val groups = mutableListOf<MutableList<Rendered>>()
        pages.forEachIndexed { index, page ->
            if (groups.isEmpty() || starts.count { it } >= 2 && starts[index]) groups += mutableListOf<Rendered>()
            groups.last().addAll(page)
        }
        return groups.map { normalize(convertSong(expandLabels(it))) }.filter { it.isNotBlank() }
    }

    private data class Rendered(val text: String, val positions: DoubleArray, val source: ChordSheet.Line)
    private data class Token(val text: String, val index: Int)
    private enum class Kind { BLANK, TAB, SECTION, METADATA, CHORD, LYRIC }

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
            '\u00ad', '\u200b', '\u200c', '\u200d', '\ufeff', '\u007f', in '\u202a'..'\u202e', in '\u2066'..'\u2069' -> Unit
            '\ufb00' -> append("ff")
            '\ufb01' -> append("fi")
            '\ufb02' -> append("fl")
            '\ufb03' -> append("ffi")
            '\ufb04' -> append("ffl")
            '\ufb05', '\ufb06' -> append("st")
            else -> if (c.code >= 0x20 || c.isWhitespace()) append(if (c.isWhitespace() || c == '\u00a0' || c in '\u2000'..'\u200a' || c == '\u202f') ' ' else c)
        }
    }

    private fun render(line: ChordSheet.Line, normalize: (String) -> String): Rendered {
        val text = StringBuilder()
        val positions = Positions(line.spans.sumOf { it.text.length })
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
                    repeat(count) { index -> text.append(' '); positions.add(before.end + index * width) }
                }
            }
            value.forEachIndexed { index, c -> text.append(c); positions.add(span.start + index * width) }
            previous = span
        }
        val result = text.toString().trimEnd()
        return Rendered(result, positions.take(result.length), line)
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
                ChordProSyntax.isKnownDirective(line.trim()) || ChordProSyntax.brackets(line).any {
                    it.content.none(Char::isWhitespace) && isChordOrLatinName(it.content) &&
                        line.getOrNull(it.range.last + 1)?.let { next ->
                            next in 'A'..'Z' || next in 'a'..'z' || next in '\u00c0'..'\u024f' || next in '\u0370'..'\u04ff'
                        } == true
                }
            }) return true
        val inline = lines.count { line -> ChordProSyntax.brackets(line).any { chord(it.content) } }
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
    private fun furniture(value: String) = value in listOf("|", "||", "|:", ":|", "-", "/", "%", "N.C.", "NC", "(", ")") || repeat.matches(value)
    private fun chordTokens(line: Rendered): List<Token>? = tokens(line.text).takeIf { words ->
        words.any { chord(it.text) } && words.all { chord(it.text) || furniture(it.text) }
    }
    /** The punctuation that marks an extracted heading is not part of the section's name. */
    private fun headingLabel(text: String) = text.trim().removeSurrounding("[", "]").removeSurrounding("(", ")").removeSuffix(":").trim()

    private fun section(text: String): String? = section.find(headingLabel(text))
        ?.takeIf { it.range.first == 0 }?.groupValues?.get(1)?.lowercase()
    private fun sectionType(text: String): String? = when (section(text)) {
        "verse", "versszak" -> "verse"
        "chorus", "refrain", "refr\u00e9n", "ref.", "r." -> "chorus"
        "bridge", "pre-chorus" -> "bridge"
        else -> null
    }
    /**
     * A label with a colon is metadata whatever follows it. Without one only a value of the field's own shape is
     * (`Capo 2`, `Key G`, `Time 4/4`), since a lyric may well start with one of these words: "By the rivers of
     * Babylon" and "Time after time" are sung, not credited.
     */
    private fun metadata(text: String): Pair<String, String>? {
        val match = metadata.matchEntire(text.trim()) ?: unlabelledMetadata.matchEntire(text.trim())?.takeIf {
            val value = it.groupValues[2].trim()
            when (it.groupValues[1].lowercase()) {
                "capo", "tempo" -> value.first().isDigit()
                "time" -> timeSignature.matches(value)
                "key", "hangnem" -> isChordOrLatinName(ascii(value))
                else -> true
            }
        }
        if (match != null) {
            val name = when (match.groupValues[1].lowercase()) {
                "hangnem" -> "key"
                "temp\u00f3" -> "tempo"
                "\u00fctemmutat\u00f3" -> "time"
                "el\u0151ad\u00f3", "by", "words and music by" -> "artist"
                else -> match.groupValues[1].lowercase()
            }
            val value = match.groupValues[2].trim()
            // A tempo is a number of beats, which Campfire's PDF prints with its unit: "96 BPM".
            return name to when (name) {
                "capo" -> Regex("[0-9]+").find(value)?.value.orEmpty()
                "tempo" -> Regex("^[0-9]+").find(value)?.value ?: value
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
                    "tempo" -> value.first().isDigit()
                    "time" -> timeSignature.matches(value)
                    "key" -> isHeader && isChordOrLatinName(ascii(value))
                    "capo" -> isHeader && value.isNotEmpty()
                    TRANSPOSITION -> isHeader
                    else -> false
                }
            }
        }
    }

    private fun expandLabels(lines: List<Rendered>): List<Rendered> = lines.flatMap { line ->
        val colon = line.text.indexOf(':')
        if (colon > 0 && section(line.text.substring(0, colon)) != null) {
            val after = colon + 1
            val rest = line.copy(text = line.text.substring(after), positions = line.positions.copyOfRange(after, line.positions.size))
            if (chordTokens(rest) != null) listOf(line.copy(text = line.text.substring(0, colon), positions = line.positions.copyOf(colon)), rest) else listOf(line)
        } else listOf(line)
    }

    private fun isTitle(line: Rendered, lines: List<Rendered>): Boolean {
        if (line.text.isBlank() || section(line.text) != null || metadata(line.text) != null || chordTokens(line) != null) return false
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
     * bold "A" over the titles starting with it is a heading set larger than they are.
     */
    private fun isStyledChordLine(line: Rendered, tokens: List<Token>, next: Rendered?): Boolean {
        val spans = line.source.spans.filter { it.text.isNotBlank() }
        val lyrics = next?.source?.spans.orEmpty().filter { it.text.isNotBlank() && !it.isBold }
        return spans.isNotEmpty() && spans.all { it.isBold } && lyrics.isNotEmpty() &&
            tokens.all { furniture(it.text) || it.text.first().isUpperCase() } &&
            spans.all { span -> lyrics.all { abs(it.size - span.size) < span.size * 0.05 } }
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

    private fun convertSong(lines: List<Rendered>): String {
        // A row of bare note words is as likely a sung "La la la" as a row of major chords, so it counts as chords only in a
        // sheet whose other rows show it is written in Latin, or where its words stand apart the way chords are set over
        // the syllables they fall on, which a sung line never is.
        val hasLatinChords = lines.any { line -> tokens(line.text).any { isLatinChordBeyondNote(it.text) } }
        val candidates = lines.map(::chordTokens).map { row ->
            row?.takeUnless { !hasLatinChords && !isPositioned(it) && it.filter { word -> chord(word.text) }.all { word -> isBareLatinNote(word.text) } }
        }
        val hasUnambiguousChords = candidates.any { it != null && it.count { word -> chord(word.text) } > 1 }
        val parenthesized = lines.flatMap { parentheses.findAll(it.text).toList() }
        val convertParentheses = parenthesized.isNotEmpty() && parenthesized.count { chord(it.groupValues[1]) } * 2 > parenthesized.size
        val kinds = lines.mapIndexed { index, line ->
            when {
                line.text.isBlank() -> Kind.BLANK
                tab.matches(line.text.trim()) && (lines.getOrNull(index - 1)?.let { tab.matches(it.text.trim()) } == true ||
                    lines.getOrNull(index + 1)?.let { tab.matches(it.text.trim()) } == true) -> Kind.TAB
                section(line.text) != null -> Kind.SECTION
                index < 15 && (metadata(line.text) != null || labelledRow(line.text, isHeader = true) != null) ||
                    labelledRow(line.text, isHeader = false) != null -> Kind.METADATA
                candidates[index] != null && (candidates[index]!!.count { chord(it.text) } > 1 ||
                    candidates[index]!!.count { chord(it.text) } == 1 &&
                    candidates[index]!!.filter { chord(it.text) }.all { it.text.first().isUpperCase() } &&
                    lines.getOrNull(index - 1)?.let { section(it.text) != null } == true &&
                    lines.getOrNull(index + 1)?.let { it.text.isBlank() || section(it.text) != null } != false ||
                    (hasUnambiguousChords || isStyledChordLine(line, candidates[index]!!, lines.getOrNull(index + 1))) &&
                    lines.getOrNull(index + 1)?.let { it.text.isNotBlank() && chordTokens(it) == null && section(it.text) == null && metadata(it.text) == null } == true) -> Kind.CHORD
                else -> Kind.LYRIC
            }
        }
        val styled = lines.any { line -> !isChordLine(line.text) && line.source.spans.any { (it.isBold || it.isRaised) && chord(it.text.trim()) } }
        if (kinds.none { it in listOf(Kind.CHORD, Kind.SECTION, Kind.TAB) } && !convertParentheses && !styled) {
            return collapse(lines.map { ChordProLiteralText.escape(it.text) })
        }
        val header = mutableListOf<String>()
        val omitted = mutableSetOf<Int>()
        // The first tempo and time signature are the song's; a later one is a change from where it stands, which is
        // written there, as Campfire's own PDF prints one.
        val changes = mutableMapOf<Int, List<String>>()
        val declared = mutableSetOf<String>()
        kinds.forEachIndexed { index, kind -> if (kind == Kind.METADATA) {
            (labelledRow(lines[index].text, isHeader = index < 15) ?: listOf(metadata(lines[index].text)!!)).forEach { (name, value) ->
                when {
                    value.isBlank() || name == TRANSPOSITION -> Unit
                    name in timingNames && !declared.add(name) -> changes[index] = changes[index].orEmpty() + "{$name: ${headerValue(value)}}"
                    else -> header += "{$name: ${headerValue(value)}}"
                }
            }
            omitted += index
        } }
        val first = kinds.indexOfFirst { it != Kind.BLANK }
        if (first >= 0 && kinds[first] == Kind.LYRIC && !isChordLine(lines[first].text) && (isTitle(lines[first], lines) ||
                kinds.getOrNull(first + 1) == Kind.BLANK && kinds.indexOf(Kind.CHORD) in (first + 2)..(first + 4))) {
            // A title too long for its column is wrapped onto the lines under it, in the same type, which no line of the
            // song is set in: those lines are the rest of the title rather than its credit or its first lyrics.
            var last = first
            while (kinds.getOrNull(last + 1) == Kind.LYRIC && isTitleContinuation(lines[last + 1], lines[first], lines)) last++
            val title = (first..last).joinToString(" ") { lines[it].text.trim() }
            val split = title.split(Regex(" +[-\u2013\u2014] +"), limit = 2)
            if (split.size == 2) {
                header.add(0, "{artist: ${headerValue(split[0])}}")
                header.add(0, "{title: ${headerValue(split[1])}}")
            } else header.add(0, "{title: ${headerValue(title)}}")
            omitted += first..last
            val artist = last + 1
            // Straight under the title, "by Someone" is the credit it reads as, which it is not as a line of lyrics.
            val credit = lines.getOrNull(artist)?.let { credit.matchEntire(it.text.trim()) }
            if (split.size == 1 && kinds.getOrNull(artist) == Kind.LYRIC && (credit != null || lines[artist].text.length < title.length &&
                    lines[artist].source.spans.maxOfOrNull { it.size }?.let { it < (lines[first].source.spans.maxOfOrNull { span -> span.size } ?: 1.0) } == true)) {
                header += "{artist: ${headerValue(credit?.groupValues?.get(1) ?: lines[artist].text.trim())}}"
                omitted += artist
            }
        }
        val output = mutableListOf<String>()
        var environment: String? = null
        var inTab = false
        fun closeTab() { if (inTab) { output += "{end_of_tab}"; inTab = false } }
        fun closeSection() { environment?.let { output += "{end_of_$it}" }; environment = null }
        val pendingChanges = mutableListOf<String>()
        fun flushChanges() { output += pendingChanges; pendingChanges.clear() }
        var index = 0
        while (index < lines.size) {
            changes[index]?.let { pendingChanges += it }
            if (index in omitted) { index++; continue }
            val line = lines[index]
            if (kinds[index] != Kind.TAB) closeTab()
            // A change before a heading belongs to the section it heads rather than to the end of the one before.
            if (kinds[index] != Kind.SECTION && kinds[index] != Kind.BLANK) flushChanges()
            when (kinds[index]) {
                Kind.SECTION -> {
                    closeSection()
                    flushChanges()
                    val label = headerValue(headingLabel(line.text))
                    val type = sectionType(line.text)
                    val next = ((index + 1)..lines.lastIndex).firstOrNull { kinds[it] != Kind.BLANK }
                    if (type == "chorus" && (next == null || kinds[next] == Kind.SECTION)) output += "{chorus}"
                    else if (type != null) { output += "{start_of_$type: ${label}}"; environment = type }
                    else {
                        // A heading the parser does not take for a section ends the lyrics before it only after a blank
                        // line; without one it would be a comment inside them, and what follows their continuation.
                        if (output.lastOrNull()?.isNotBlank() == true) output += ""
                        output += "{comment: ${label}}"
                    }
                }
                Kind.TAB -> { if (!inTab) { output += "{start_of_tab}"; inTab = true }; output += line.text }
                Kind.CHORD -> {
                    if (kinds.getOrNull(index + 1) == Kind.LYRIC && index + 1 !in omitted) {
                        output += merge(line, lines[index + 1], convertParentheses)
                        index++
                    } else output += candidates[index]!!.joinToString(" ") { if (chord(it.text)) "[${ascii(it.text.removeSurrounding("(", ")"))}]" else it.text }
                }
                Kind.LYRIC -> output += inline(line, convertParentheses)
                Kind.BLANK -> output += ""
                Kind.METADATA -> Unit
            }
            index++
        }
        closeTab()
        closeSection()
        flushChanges()
        return collapse(header + if (header.isEmpty()) output else listOf("") + output)
    }

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

    private fun merge(chords: Rendered, lyrics: Rendered, convertParentheses: Boolean): String {
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
        for (word in chordTokens(chords).orEmpty().filter { chord(it.text) }) {
            val x = chords.positions.getOrNull(word.index) ?: 0.0
            var at = (firstIndex(suffixMin.size) { suffixMin[it] > x } - 1).coerceAtLeast(0)
            if (lyrics.positions.isEmpty()) at = 0
            else {
                val lastWidth = lyrics.source.spans.lastOrNull()?.let { (it.end - it.start) / it.text.length.coerceAtLeast(1) } ?: 1.0
                if (x >= lyrics.positions.last() + lastWidth) at = lyrics.text.length
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
            at = at.coerceAtLeast(previous)
            previous = at
            insertions.getOrPut(at) { mutableListOf() } += "[${ascii(word.text.removeSurrounding("(", ")"))}]"
        }
        val escaped = ChordProLiteralText.escape(lyrics.text)
        val merged = buildString {
            var carried: List<String> = emptyList()
            for (index in 0..escaped.length) {
                val due = insertions[index].orEmpty()
                if (splitsSurrogate(escaped, index)) carried = due
                else (carried + due).takeIf { it.isNotEmpty() }?.let { values ->
                    // A chord carried past a final emoji belongs to it, so only a chord beyond the lyrics is set apart.
                    if (index == escaped.length && carried.isEmpty() && isNotEmpty() && last() != ' ') append(' ')
                    append(values.joinToString(if (index == escaped.length) " " else ""))
                    carried = emptyList()
                }
                if (index < escaped.length) append(escaped[index])
            }
        }
        // Inline conversion uses different offsets after insertion; convert those tokens in the merged string.
        return if (convertParentheses) parentheses.replace(merged) { if (chord(it.groupValues[1])) "[${ascii(it.groupValues[1])}]" else it.value } else merged
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

    private fun headerValue(value: String) = value.replace('{', '(').replace('}', ')').replace('\n', ' ').trim()
    private fun collapse(lines: List<String>): String {
        val result = mutableListOf<String>()
        for (line in lines) if (line.isNotBlank() || result.lastOrNull()?.isNotBlank() == true) result += line.trimEnd()
        return result.joinToString("\n").trim() + "\n"
    }

    private val token = Regex("\\|:?|:\\||[^\\s|]+")
    private val parentheses = Regex("\\(([^()]+)\\)")
    private val repeat = Regex("\\(?([0-9]+x|x[0-9]+)\\)?", RegexOption.IGNORE_CASE)
    private val section = Regex("^(Verse|Chorus|Refrain|Bridge|Pre-Chorus|Intro|Outro|Solo|Interlude|Instrumental|Versszak|Refr\u00e9n|Ref\\.|R\\.)(?:\\s*[0-9]+)?$", RegexOption.IGNORE_CASE)
    private val metadata = Regex("^(Capo|Key|Hangnem|Tempo|Temp\u00f3|Time|\u00dctemmutat\u00f3|Artist|El\u0151ad\u00f3|words and music by|by)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val transposition = Regex("^(Transposition|Transzpon\u00e1l\u00e1s)\\s*:\\s*([+-]?[0-9]+)$", RegexOption.IGNORE_CASE)
    private val unlabelledMetadata = Regex("^(Capo|Key|Hangnem|Tempo|Time|words and music by)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val timeSignature = Regex("[0-9]+/[0-9]+")
    private val timingNames = setOf("tempo", "time")
    private val labelGap = Regex("\\s{2,}")
    private val credit = Regex("^(?:words and music )?by\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val bpm = Regex("^([0-9]+)\\s+BPM$", RegexOption.IGNORE_CASE)
    private val tab = Regex("^[eEaAbBdDgG](?:[#b])?\\s*\\|[-0-9|hpbrx/\\\\~(). :]+$")

    /** What [labelledRow] names a transposition, which no directive is written for. */
    private const val TRANSPOSITION = "transposition"
}
