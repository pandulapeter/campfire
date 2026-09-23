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

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.chordpro.model.SectionType

/**
 * Writes a [ChordProSong] back as canonical ChordPro. The editor works on raw text, so the user's own formatting does
 * not have to survive this; `parse(serialize(parse(x))) == parse(x)` does.
 */
object ChordProSerializer {

    fun serialize(song: ChordProSong): String {
        val chunks = mutableListOf<String>()
        serializeMetadata(song.metadata).takeIf { it.isNotEmpty() }?.let { chunks += it.joinToString("\n") }
        var index = 0
        while (index < song.blocks.size) {
            val block = song.blocks[index]
            if (block is ChordProBlock.Section) {
                // A section a comment, a break or a recall cut in two goes back into the one environment it was read
                // from, with what cut it inside: written as two, it would come back as two sections.
                val end = sectionEnd(song.blocks, index)
                chunks += serializeSection(song.blocks.subList(index, end), song.metadata.transpose)
                index = end
            } else {
                chunks += serializeBlock(block, song.metadata.transpose)
                index++
            }
        }
        return chunks.joinToString("\n\n")
    }

    /** The index after the last continuation of the section at [start]; what follows its last piece is not its own. */
    private fun sectionEnd(blocks: List<ChordProBlock>, start: Int): Int {
        var end = start + 1
        for (index in start + 1 until blocks.size) {
            val block = blocks[index]
            if (block !is ChordProBlock.Section) continue
            if (!block.isContinuation) break
            end = index + 1
        }
        return end
    }

    private fun serializeMetadata(metadata: ChordProMetadata) = buildList {
        metadata.title?.let { add("{title: $it}") }
        metadata.subtitle?.let { add("{subtitle: $it}") }
        metadata.artist?.let { add("{artist: $it}") }
        metadata.composer?.let { add("{composer: $it}") }
        metadata.lyricist?.let { add("{lyricist: $it}") }
        metadata.album?.let { add("{album: $it}") }
        metadata.year?.let { add("{year: $it}") }
        metadata.key?.let { add("{key: $it}") }
        metadata.capo?.let { add("{capo: $it}") }
        metadata.tempo?.let { add("{tempo: $it}") }
        metadata.time?.let { add("{time: $it}") }
        metadata.duration?.let { add("{duration: $it}") }
        metadata.transpose.takeIf { it != 0 }?.let { add("{transpose: $it}") }
        metadata.tags.forEach { add("{${ChordProSyntax.TAG_NAME}: $it}") }
        metadata.languages.forEach { add("{meta: ${ChordProSyntax.LANGUAGE_NAME} $it}") }
        metadata.custom.forEach { (name, values) ->
            values.forEach { value -> add(if (value.isEmpty()) "{meta: $name}" else "{meta: $name $value}") }
        }
    }

    /**
     * @param wholeSongTranspose The `{transpose}` the song opens with, which a modulation is written back on top of:
     *   it is read as the transposition of the rest of the song, not as an addition to the one before it.
     */
    private fun serializeBlock(block: ChordProBlock, wholeSongTranspose: Int): String = when (block) {
        is ChordProBlock.Section -> serializeSection(listOf(block), wholeSongTranspose)
        is ChordProBlock.Transpose -> "{transpose: ${wholeSongTranspose + block.semitones}}"
        is ChordProBlock.ChorusRecall -> block.label?.let { "{chorus: $it}" } ?: "{chorus}"
        is ChordProBlock.Comment -> when (block.style) {
            CommentStyle.PLAIN -> "{comment: ${block.text}}"
            CommentStyle.ITALIC -> "{comment_italic: ${block.text}}"
            CommentStyle.BOX -> "{comment_box: ${block.text}}"
        }

        ChordProBlock.Break -> "{column_break}"
    }

    /** A section and its continuations, with the blocks that stood between them, as [sectionEnd] collects them. */
    private fun serializeSection(pieces: List<ChordProBlock>, wholeSongTranspose: Int): String {
        val section = pieces.first() as ChordProBlock.Section
        // A paragraph has no environment of its own, so a label it carries came from the tablature or grid inside
        // it and has to go back onto that; see `SectionBuilder.openLineMode`.
        if (section.type == SectionType.Paragraph) return serializeLines(pieces, wholeSongTranspose, section.label)
        val body = serializeLines(pieces, wholeSongTranspose)
        val name = environmentName(section.type)
        val header = section.label?.let { "{start_of_$name: $it}" } ?: "{start_of_$name}"
        return if (body.isEmpty()) "$header\n{end_of_$name}" else "$header\n$body\n{end_of_$name}"
    }

    /**
     * The lines of a section, with each run of tablature or grid lines wrapped in the environment that says how it
     * is written. They are runs rather than sections of their own, so a solo written as a line of chords over a tab
     * comes back out as one section with a `{start_of_tab}` in the middle of it.
     *
     * The lines of every piece are walked together with the blocks between the pieces, in the order the file had them.
     * A block is written inside a tab or grid environment that the next line after it is still in, which is where the
     * parser found it: a Campfire 3 `{comment: Verse 2}` written outside the tab it stood in would come back as a heading.
     */
    private fun serializeLines(pieces: List<ChordProBlock>, wholeSongTranspose: Int, environmentLabel: String? = null) = buildList {
        val items: List<Any> = pieces.flatMap { piece -> if (piece is ChordProBlock.Section) piece.lines else listOf(piece) }
        var openEnvironment: String? = null
        var label = environmentLabel
        items.forEachIndexed { index, item ->
            if (item is ChordProBlock) {
                val nextLine = items.subList(index + 1, items.size).firstOrNull { it is ChordProLine } as ChordProLine?
                // A tab stays open across the block only where the tab line after it is still in the same environment.
                val keepsEnvironmentOpen = when (openEnvironment) {
                    "tab" -> (nextLine as? ChordProLine.Tab)?.continuesEnvironment == true
                    else -> nextLine?.let { lineEnvironmentName(it, openEnvironment) } == openEnvironment
                }
                if (openEnvironment != null && !keepsEnvironmentOpen) {
                    add("{end_of_$openEnvironment}")
                    openEnvironment = null
                }
                add(serializeBlock(item, wholeSongTranspose))
                return@forEachIndexed
            }
            val line = item as ChordProLine
            val environment = lineEnvironmentName(line, openEnvironment)
            // A tab line that starts an environment of its own is written in one, even straight after another.
            val startsTab = line is ChordProLine.Tab && !line.continuesEnvironment && openEnvironment == "tab"
            if (environment != openEnvironment || startsTab) {
                openEnvironment?.let { add("{end_of_$it}") }
                environment?.let { name ->
                    add(label?.let { "{start_of_$name: $it}" } ?: "{start_of_$name}")
                    label = null
                }
                openEnvironment = environment
            }
            add(serializeLine(line))
        }
        openEnvironment?.let { add("{end_of_$it}") }
    }.joinToString("\n")

    /**
     * The environment a line has to be written inside, or null for the lines that need none. A blank line stays in the
     * [openEnvironment] it is found in: outside one, a blank line ends the paragraph it is in on the way back in, so
     * closing a tab around it would come back as two sections where there was one.
     */
    private fun lineEnvironmentName(line: ChordProLine, openEnvironment: String?) = when (line) {
        is ChordProLine.Tab -> "tab"
        is ChordProLine.Grid -> "grid"
        ChordProLine.Blank -> openEnvironment
        else -> null
    }

    private fun environmentName(type: SectionType) = when (type) {
        SectionType.Verse -> "verse"
        SectionType.Chorus -> "chorus"
        SectionType.Bridge -> "bridge"
        is SectionType.Custom -> type.name
        SectionType.Paragraph -> "verse"
    }

    private fun serializeLine(line: ChordProLine) = when (line) {
        is ChordProLine.Lyrics -> serializeLyrics(line)
        is ChordProLine.Tab -> line.text
        is ChordProLine.Grid -> line.tokens.joinToString(" ") { serializeGridToken(it) }
        ChordProLine.Blank -> ""
    }

    private fun serializeLyrics(line: ChordProLine.Lyrics) = buildString {
        var consumedUntil = 0
        line.chords.sortedBy { it.position }.forEach { chord ->
            val position = chord.position.coerceIn(consumedUntil, line.text.length)
            append(line.text, consumedUntil, position)
            append(if (chord.isAnnotation) "[*${chord.name}]" else "[${chord.name}]")
            consumedUntil = position
        }
        append(line.text, consumedUntil, line.text.length)
    }

    private fun serializeGridToken(token: GridToken) = when (token) {
        is GridToken.Bar -> token.text
        is GridToken.Chord -> token.name
        GridToken.Beat -> "."
        is GridToken.Repeat -> token.text
        is GridToken.Text -> token.text
    }
}
