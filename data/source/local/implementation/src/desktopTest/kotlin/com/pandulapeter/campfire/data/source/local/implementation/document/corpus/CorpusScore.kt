/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.document.corpus

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.chordpro.model.SectionType
import com.pandulapeter.campfire.chordpro.syntax.ChordProMarkup
import com.pandulapeter.campfire.data.model.library.normalizedToNfc
import kotlin.math.abs

/**
 * How close the converter's ChordPro is to the hand-corrected one, as five F1 scores and their mean. Every part is a plain
 * comparison of what both files say, with no tolerance of its own beyond the one character a chord may move, so a score
 * moves only when the output does.
 */
internal data class CorpusScore(
    val lines: Double,
    val chords: Double,
    val sections: Double,
    val songs: Double,
    val header: Double,
) {
    val overall get() = (lines + chords + sections + songs + header) / 5

    companion object {
        val FAILED = CorpusScore(0.0, 0.0, 0.0, 0.0, 0.0)

        fun of(expected: String, actual: String): CorpusScore {
            val expectedSongs = songsOf(expected)
            val actualSongs = songsOf(actual)
            val expectedItems = expectedSongs.flatMap { itemsOf(it.second) }
            val actualItems = actualSongs.flatMap { itemsOf(it.second) }
            val pairs = longestCommonSubsequence(expectedItems.map { it.key }, actualItems.map { it.key })
            val expectedTitles = expectedSongs.map { normalized(it.first.title.orEmpty()) }
            val actualTitles = actualSongs.map { normalized(it.first.title.orEmpty()) }
            return CorpusScore(
                lines = f1(pairs.size, expectedItems.size, actualItems.size),
                chords = chordScore(expectedItems, actualItems, pairs),
                sections = sectionScore(expectedItems, actualItems, pairs),
                songs = f1(longestCommonSubsequence(expectedTitles, actualTitles).size, expectedTitles.size, actualTitles.size),
                header = headerScore(expectedSongs.map { it.first }, actualSongs.map { it.first }, alignedSongs(expectedTitles, actualTitles)),
            )
        }
    }
}

/** One line of a song as the scoring sees it; [key] is what two lines must share to be the same line. */
internal sealed interface CorpusItem {
    val key: String

    data class Section(val type: String) : CorpusItem {
        override val key get() = "section:$type"
    }

    data class Lyric(val text: String, val chords: List<PlacedChord>) : CorpusItem {
        override val key get() = "lyric:$text"
    }

    data class Chords(val names: List<String>) : CorpusItem {
        override val key get() = "chords:${names.joinToString(" ")}"
    }

    data class Comment(val text: String) : CorpusItem {
        override val key get() = "comment:$text"
    }

    data class Tab(val text: String) : CorpusItem {
        override val key get() = "tab:$text"
    }

    data class Grid(val text: String) : CorpusItem {
        override val key get() = "grid:$text"
    }

    /** A chord [name] over the character at [index] of its line's normalized text. */
    data class PlacedChord(val name: String, val index: Int)
}

/** The songs of a ChordPro text whose songs are joined by `{new_song}` lines, each with its header and its blocks. */
internal fun songsOf(text: String) = text.lines()
    .fold(mutableListOf(mutableListOf<String>())) { songs, line ->
        if (line.trim().equals("{new_song}", ignoreCase = true) || line.trim().equals("{ns}", ignoreCase = true)) songs += mutableListOf<String>() else songs.last() += line
        songs
    }
    .map { it.joinToString("\n") }
    .filter { it.isNotBlank() }
    .map { ChordProParser.parse(it).let { song -> song.metadata to song.blocks } }

/**
 * A song's lines in order: a [CorpusItem.Section] where a labelled section opens (an unlabelled paragraph is only blank
 * lines around it, which are skipped like recalls), then one item per line of it.
 */
internal fun itemsOf(blocks: List<ChordProBlock>): List<CorpusItem> = blocks.flatMap { block ->
    when (block) {
        is ChordProBlock.Section -> {
            val opening = if (block.type == SectionType.Paragraph || block.isContinuation) emptyList() else listOf(CorpusItem.Section(sectionKey(block)))
            opening + block.lines.mapNotNull(::itemOf)
        }
        is ChordProBlock.Comment -> normalized(ChordProMarkup.strip(block.text).text).takeIf { it.isNotEmpty() }?.let { listOf(CorpusItem.Comment(it)) }.orEmpty()
        is ChordProBlock.ChorusRecall, is ChordProBlock.Transpose, is ChordProBlock.Timing, ChordProBlock.Break -> emptyList()
    }
}

private fun sectionKey(section: ChordProBlock.Section) = when (val type = section.type) {
    SectionType.Verse -> "verse"
    SectionType.Chorus -> "chorus"
    SectionType.Bridge -> "bridge"
    is SectionType.Custom -> "${normalized(type.name)}:${normalized(section.label.orEmpty())}"
    SectionType.Paragraph -> "paragraph"
}

private fun itemOf(line: ChordProLine): CorpusItem? = when (line) {
    is ChordProLine.Lyrics -> {
        val text = normalized(line.text)
        val chords = line.chords.filterNot { it.isMark }.map { CorpusItem.PlacedChord(chordName(it.name), normalizedIndex(line.text, it.position)) }
        when {
            text.isEmpty() && chords.isEmpty() -> null
            text.isEmpty() -> CorpusItem.Chords(chords.map { it.name })
            else -> CorpusItem.Lyric(text, chords)
        }
    }
    is ChordProLine.Tab -> normalized(line.text).takeIf { it.isNotEmpty() }?.let(CorpusItem::Tab)
    is ChordProLine.Verbatim -> normalized(line.text).takeIf { it.isNotEmpty() }?.let(CorpusItem::Tab)
    is ChordProLine.Grid -> normalized(line.tokens.joinToString(" ", transform = ::gridText)).takeIf { it.isNotEmpty() }?.let(CorpusItem::Grid)
    ChordProLine.Blank -> null
}

private fun gridText(token: GridToken) = when (token) {
    is GridToken.Bar -> token.text
    is GridToken.Chord -> chordName(token.name)
    GridToken.Beat -> "."
    is GridToken.Repeat -> token.text
    is GridToken.Text -> token.text
}

/** NFC, lowercase, whitespace collapsed and trimmed. */
internal fun normalized(text: String) = text.normalizedToNfc().lowercase().split(whitespace).filter { it.isNotEmpty() }.joinToString(" ")

private val whitespace = Regex("\\s+")

/** Where the character at [position] of [text] lands in [normalized] text: the length of the normalized text before it. */
private fun normalizedIndex(text: String, position: Int): Int {
    val prefix = text.take(position).normalizedToNfc().lowercase().trimStart().replace(whitespace, " ")
    return prefix.length.coerceAtMost(normalized(text).length)
}

private fun chordName(name: String) = name.trim().replace('♯', '#').replace('♭', 'b')

/** The index pairs of a longest common subsequence of [expected] and [actual], in order. */
internal fun <T> longestCommonSubsequence(expected: List<T>, actual: List<T>): List<Pair<Int, Int>> {
    val lengths = Array(expected.size + 1) { IntArray(actual.size + 1) }
    for (i in expected.indices.reversed()) for (j in actual.indices.reversed()) {
        lengths[i][j] = if (expected[i] == actual[j]) lengths[i + 1][j + 1] + 1 else maxOf(lengths[i + 1][j], lengths[i][j + 1])
    }
    val pairs = mutableListOf<Pair<Int, Int>>()
    var i = 0
    var j = 0
    while (i < expected.size && j < actual.size) {
        when {
            expected[i] == actual[j] -> pairs += (i++ to j++)
            lengths[i + 1][j] >= lengths[i][j + 1] -> i++
            else -> j++
        }
    }
    return pairs
}

/** 2·matched / (expected + actual), and 1 when neither side has anything to match. */
internal fun f1(matched: Int, expected: Int, actual: Int) = if (expected + actual == 0) 1.0 else 2.0 * matched / (expected + actual)

private fun chordScore(expected: List<CorpusItem>, actual: List<CorpusItem>, pairs: List<Pair<Int, Int>>): Double {
    val found = pairs.sumOf { (i, j) ->
        val expectedItem = expected[i]
        val actualItem = actual[j]
        when {
            expectedItem is CorpusItem.Lyric && actualItem is CorpusItem.Lyric -> foundChords(expectedItem.chords, actualItem.chords)
            expectedItem is CorpusItem.Chords -> expectedItem.names.size
            else -> 0
        }
    }
    return f1(found, expected.sumOf(::chordCount), actual.sumOf(::chordCount))
}

private fun chordCount(item: CorpusItem) = when (item) {
    is CorpusItem.Lyric -> item.chords.size
    is CorpusItem.Chords -> item.names.size
    else -> 0
}

/** The expected chords that have an actual one of the same name within one character, each actual chord used once. */
private fun foundChords(expected: List<CorpusItem.PlacedChord>, actual: List<CorpusItem.PlacedChord>): Int {
    val unused = actual.toMutableList()
    return expected.count { chord ->
        val match = unused.filter { it.name == chord.name && abs(it.index - chord.index) <= 1 }.minByOrNull { abs(it.index - chord.index) }
        match != null && unused.remove(match)
    }
}

/** A section opening is right where the line after it is matched and has an opening of the same type right before it. */
private fun sectionScore(expected: List<CorpusItem>, actual: List<CorpusItem>, pairs: List<Pair<Int, Int>>): Double {
    val matches = pairs.toMap()
    val right = expected.indices.count { i ->
        val opening = boundaryBefore(expected, i) ?: return@count false
        val j = matches[i] ?: return@count false
        boundaryBefore(actual, j) == opening
    }
    return f1(right, expected.indices.count { boundaryBefore(expected, it) != null }, actual.indices.count { boundaryBefore(actual, it) != null })
}

private fun boundaryBefore(items: List<CorpusItem>, index: Int) =
    if (items[index] is CorpusItem.Section) null else (items.getOrNull(index - 1) as? CorpusItem.Section)?.type

/**
 * The songs paired for the header: those whose titles match, and between two such pairs, songs paired in order where
 * both sides have as many, so a song whose title came out wrong still has its other fields compared.
 */
private fun alignedSongs(expected: List<String>, actual: List<String>): List<Pair<Int, Int>> {
    val anchors = longestCommonSubsequence(expected, actual)
    val pairs = mutableListOf<Pair<Int, Int>>()
    var previous = -1 to -1
    for (anchor in anchors + (expected.size to actual.size)) {
        val expectedGap = previous.first + 1 until anchor.first
        val actualGap = previous.second + 1 until anchor.second
        if (expectedGap.count() == actualGap.count()) pairs += expectedGap.zip(actualGap)
        if (anchor.first < expected.size) pairs += anchor
        previous = anchor
    }
    return pairs
}

private fun headerScore(expected: List<ChordProMetadata>, actual: List<ChordProMetadata>, pairs: List<Pair<Int, Int>>): Double {
    val right = pairs.sumOf { (i, j) ->
        val expectedFields = headerFields(expected[i])
        val actualFields = headerFields(actual[j])
        expectedFields.count { (name, value) -> actualFields[name] == value }
    }
    return f1(right, expected.sumOf { headerFields(it).size }, actual.sumOf { headerFields(it).size })
}

private fun headerFields(metadata: ChordProMetadata) = mapOf(
    "title" to metadata.title,
    "subtitle" to metadata.subtitle,
    "artist" to metadata.artist,
    "key" to metadata.key,
    "capo" to metadata.capo?.toString(),
    "tempo" to metadata.tempo,
    "time" to metadata.time,
).mapValues { normalized(it.value.orEmpty()) }.filterValues { it.isNotEmpty() }
