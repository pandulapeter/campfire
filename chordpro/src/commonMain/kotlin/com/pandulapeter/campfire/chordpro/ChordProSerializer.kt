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
        song.blocks.forEach { block -> chunks += serializeBlock(block) }
        return chunks.joinToString("\n\n")
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
        metadata.custom.forEach { (name, values) ->
            values.forEach { value -> add(if (value.isEmpty()) "{meta: $name}" else "{meta: $name $value}") }
        }
    }

    private fun serializeBlock(block: ChordProBlock) = when (block) {
        is ChordProBlock.Section -> serializeSection(block)
        is ChordProBlock.ChorusRecall -> block.label?.let { "{chorus: $it}" } ?: "{chorus}"
        is ChordProBlock.Comment -> when (block.style) {
            CommentStyle.PLAIN -> "{comment: ${block.text}}"
            CommentStyle.ITALIC -> "{comment_italic: ${block.text}}"
            CommentStyle.BOX -> "{comment_box: ${block.text}}"
        }

        ChordProBlock.Break -> "{column_break}"
    }

    private fun serializeSection(section: ChordProBlock.Section): String {
        val body = section.lines.joinToString("\n") { serializeLine(it) }
        if (section.type == SectionType.Paragraph) return body
        val name = environmentName(section.type)
        val header = section.label?.let { "{start_of_$name: $it}" } ?: "{start_of_$name}"
        return if (body.isEmpty()) "$header\n{end_of_$name}" else "$header\n$body\n{end_of_$name}"
    }

    private fun environmentName(type: SectionType) = when (type) {
        SectionType.Verse -> "verse"
        SectionType.Chorus -> "chorus"
        SectionType.Bridge -> "bridge"
        SectionType.Tab -> "tab"
        SectionType.Grid -> "grid"
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
