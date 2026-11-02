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
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.tokens
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.CAPO_SHAPES
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.CAPO_SOUNDING
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.CREDIT
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.NO_CAPO
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.TRANSPOSITION
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.capo
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.isChordLine
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.isHeading
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.metadata
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.shapesKeyOf
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.timingNames
import com.pandulapeter.campfire.chordpro.convert.ChordSheetText.headerValue
import com.pandulapeter.campfire.chordpro.convert.ChordSheetText.joinRows
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary
import kotlin.math.abs

/** What a song's first lines and its metadata lines say of it, taken out of its body into its header. */
internal object SongHeader {

    /**
     * The header the metadata lines make: [entries] in the order the lines name them, the lines at [omitted], the
     * [changes] of tempo, time, and key by the line they stand at, and the capo of a sheet whose chords are named as they
     * sound over it ([CAPO_SOUNDING]), 0 for any other.
     */
    data class HeaderFields(val entries: List<String>, val omitted: Set<Int>, val changes: Map<Int, List<String>>, val soundingCapo: Int)

    /**
     * The header the title makes: [leading] goes before every other entry (the title, an artist split off it, a
     * subtitle), [trailing] after the metadata's (a credit, the artist rows of Campfire's export).
     */
    data class TitleLines(val leading: List<String>, val trailing: List<String>, val omitted: Set<Int>)

    /** No title: a song whose caller knows its title from elsewhere, or whose first lines name none. */
    val noTitle = TitleLines(emptyList(), emptyList(), emptySet())

    fun isTitle(line: Rendered, lines: List<Rendered>): Boolean {
        if (line.text.isBlank() || line.isMarginLabel || isHeading(line.text) || metadata(line.text) != null || chordTokens(line) != null) return false
        val median = ChordSheetProfile.medianWithout(lines.filter { it !== line }.map { it.source }) ?: line.source.spans.maxOfOrNull { it.size } ?: 1.0
        return line.source.isHeading || line.source.spans.any { it.size > median * ChordSheetProfile.TITLE_RATIO } ||
            line.source.spans.filter { it.text.isNotBlank() }.let { spans -> spans.isNotEmpty() && spans.all { it.isBold } } && lines.any { chordTokens(it) != null }
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

    /**
     * The metadata lines of the song taken into its header. A capo naming its shapes' key ([CAPO_SHAPES]) makes that key
     * the song's, in place of the sounding one the row names beside it: the chords under it are those shapes.
     */
    fun fields(
        lines: List<Rendered>,
        features: List<LineFeatures>,
        kinds: List<LineKind>,
    ): HeaderFields {
        val header = mutableListOf<String>()
        val omitted = mutableSetOf<Int>()
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
        return HeaderFields(header, omitted, changes, soundingCapo)
    }

    /** The title the song opens with, and the credit under it. */
    fun title(
        lines: List<Rendered>,
        kinds: List<LineKind>,
    ): TitleLines {
        val leading = mutableListOf<String>()
        val trailing = mutableListOf<String>()
        val omitted = mutableSetOf<Int>()
        val first = kinds.indexOfFirst { it != LineKind.BLANK }
        if (first < 0 || kinds[first] != LineKind.LYRIC) return noTitle
        // The export's own test reads chords by their type, which a title naming a chord (`A`) is not printed in.
        if (if (lines[first].marks != null) chordRow(lines[first]) != null else isChordLine(lines[first].text)) return noTitle
        if (!isTitle(lines[first], lines)) return plainTitle(lines, kinds, first)
        // A title too long for its column is wrapped onto the lines under it, in the same type, which no line of the
        // song is set in: those lines are the rest of the title rather than its credit or its first lyrics.
        var last = first
        while (kinds.getOrNull(last + 1) == LineKind.LYRIC && !lines[last + 1].isHeaderPart && isTitleContinuation(lines[last + 1], lines[first], lines)) last++
        val title = joinRows(lines, first, last)
        // Campfire's export prints the file's title alone, its artist on a line of its own, so there is no page word or
        // credit in it to split off.
        val (name, named) = if (lines[first].marks != null) title to null else ChordSheetTitles.split(title)
        addTitle(leading, name, named)
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
                trailing += "{artist: ${headerValue(joinRows(lines, rows.first(), rows.last()))}}"
                omitted += rows
            }
            return TitleLines(leading, trailing, omitted)
        }
        // Straight under the title, "by Someone" is the credit it reads as, which it is not as a line of lyrics.
        val credit = lines.getOrNull(artist)?.let { credit.matchEntire(it.text.trim()) }
        // Read without baselines, an export's artist is still told by the row of the song's key, tempo, and time under it,
        // where no lyric ever stands, so there a name longer than the title is the artist all the same.
        val isCampfireArtist = lines.getOrNull(artist)?.marks != null && kinds.getOrNull(artist + 1) == LineKind.METADATA
        if (named == null && kinds.getOrNull(artist) == LineKind.LYRIC && (credit != null || isCampfireArtist || lines[artist].text.length < title.length &&
                lines[artist].source.spans.maxOfOrNull { it.size }?.let { it < (lines[first].source.spans.maxOfOrNull { span -> span.size } ?: 1.0) } == true)) {
            trailing += "{artist: ${headerValue(credit?.groupValues?.get(1) ?: lines[artist].text.trim())}}"
            omitted += artist
        } else if (kinds.getOrNull(artist) == LineKind.LYRIC && (lines[artist].isHeaderPart || isCenteredUnder(lines[artist], lines[first], lines))) {
            // The part printed beside the title, or the line centred under a centred one, is the subtitle the ChordPro
            // app sets there.
            leading += "{subtitle: ${headerValue(lines[artist].text.trim())}}"
            omitted += artist
        }
        return TitleLines(leading, trailing, omitted)
    }

    /**
     * The title of a text with no type to tell it by: a first line that the song's structure follows closely, after
     * its credit and the metadata lines the page heads the song with, either straight away or after a blank line. A
     * line that opens with markup, a summary of the chords used, or a bare "Chords" is the page's furniture instead.
     */
    private fun plainTitle(
        lines: List<Rendered>,
        kinds: List<LineKind>,
        first: Int,
    ): TitleLines {
        val leading = mutableListOf<String>()
        val trailing = mutableListOf<String>()
        val omitted = mutableSetOf<Int>()
        val text = lines[first].text.trim()
        if (text.startsWith('[') || text.startsWith('{') || isChordSummary(text) || text.lowercase() in pageWords) return noTitle
        val (name, named) = ChordSheetTitles.split(text)
        val credit = lines.getOrNull(first + 1)?.let { credit.matchEntire(it.text.trim()) }
        val creditAt = (first + 1).takeIf {
            named == null && kinds.getOrNull(it) == LineKind.LYRIC && (credit != null ||
                lines[it].text.trim().length < text.length && kinds.getOrNull(it + 1).let { next -> next == LineKind.BLANK || next == LineKind.METADATA })
        }
        var next = (creditAt ?: first) + 1
        while (kinds.getOrNull(next) == LineKind.METADATA) next++
        val structure = kinds.indices.firstOrNull { kinds[it] == LineKind.CHORD || kinds[it] == LineKind.SECTION } ?: return noTitle
        if (kinds.getOrNull(next) !in listOf(LineKind.BLANK, LineKind.CHORD, LineKind.SECTION) || structure !in (first + 1)..(first + 5)) return noTitle
        addTitle(leading, name, named)
        omitted += first
        if (creditAt != null) {
            trailing += "{artist: ${headerValue(credit?.groupValues?.get(1) ?: lines[creditAt].text.trim())}}"
            omitted += creditAt
        }
        return TitleLines(leading, trailing, omitted)
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

    /**
     * How far a row's baseline may stand under the baseline of the row above, in its own line box, and still be a row of
     * Campfire's heading: the artist's rows are 1.45 font sizes apart (about 1.2 of an Inter line box) and the first one
     * about 1.6 under the title's, while the first block stands 2.25 under the heading's last row (1.86; 1.65 where both
     * are drawn in a fallback font's line box of about 1.36 font sizes, the narrowest margin).
     */
    private const val HEADING_ROW = 1.6

    private val pageWords = setOf("chords", "tab", "lyrics")
    private val credit = Regex("^(?:words (?:and|&) music )?by\\s+(.+)$", RegexOption.IGNORE_CASE)
    /** A credit ending in the year of the song, `Words & music by David Bowie 1972`. */
    private val creditYear = Regex("^(.+?),?\\s+((?:1[5-9]|20)[0-9]{2})$")
    /** An artist's name followed by the year of the song, which only a comma tells apart from a name: `David Bowie, 1972`. */
    private val commaYear = Regex("^(.+?),\\s+((?:1[5-9]|20)[0-9]{2})$")
}
