/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.abc

import com.pandulapeter.campfire.chordpro.edit.ChordProLiteralText
import com.pandulapeter.campfire.chordpro.syntax.ChordProDirectives

/**
 * Writes one ABC tune as a ChordPro song: its header as directives, its chord symbols as a chord grid, its `W:` words
 * as verses, and the tune itself, whole and unchanged, in a `{start_of_abc}` block. The grid and the verses are a
 * one-off translation; the ABC block stays the source, and editing it later regenerates nothing.
 */
public object AbcToChordPro {

    private const val COMMENT_PREFIX = "%"

    /**
     * The song [tune] (one tune, as [AbcTunes.split] cuts it) becomes. Total: a text that isn't ABC still gives a song,
     * its every line kept in the block.
     *
     * @param fallbackTitle the `{title}` for a tune with no `T:`, such as the name of the file it came from.
     */
    public fun convert(tune: String, fallbackTitle: String? = null): AbcSong {
        val lines = tune.lines().dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        val headerEnd = headerEnd(lines)
        val header = fields(lines.subList(0, headerEnd))
        val titles = header.filter { it.letter == 'T' }.map { AbcTextStrings.decode(it.value) }.filter { it.isNotBlank() }
        val title = titles.firstOrNull() ?: fallbackTitle?.trim()?.takeIf { it.isNotEmpty() }
        val grid = AbcGrid.lines(AbcBars.lines(lines.subList(headerEnd, lines.size)))
        val text = listOf(
            headerLines(header, title, subtitle = titles.getOrNull(1)),
            grid.takeIf { it.isNotEmpty() }?.let { listOf("{start_of_grid}") + it + "{end_of_grid}" }.orEmpty(),
            verses(fields(lines)).flatMap { verse -> listOf("{start_of_verse}") + verse + "{end_of_verse}" + "" }.dropLast(1),
            abcBlock(lines),
        ).filter { it.isNotEmpty() }.joinToString("\n\n") { it.joinToString("\n") }
        return AbcSong(text = text, title = title, hasChords = grid.isNotEmpty())
    }

    /** The index of the first line of the tune body: the one after the `K:` that ends the header, or the first line of music. */
    private fun headerEnd(lines: List<String>): Int {
        lines.forEachIndexed { index, line ->
            val field = AbcLines.field(line)
            if (field?.letter == 'K') return index + 1
            if (field == null && line.isNotBlank() && !AbcLines.isComment(line)) return index
        }
        return lines.size
    }

    /** The fields of [lines], in order, a `+:` line joined to the field before it as ABC continues one. */
    private fun fields(lines: List<String>): List<AbcLines.Field> {
        val fields = mutableListOf<AbcLines.Field>()
        lines.forEach { line ->
            val field = AbcLines.field(line) ?: return@forEach
            val previous = fields.lastOrNull()
            if (field.letter == AbcLines.CONTINUATION) {
                if (previous != null) fields[fields.lastIndex] = previous.copy(value = "${previous.value} ${field.value}".trim())
            } else {
                fields += field
            }
        }
        return fields
    }

    /**
     * The directives, in the header order ChordPro's `metadataOrder` has, so Prettify moves none of them. `C:` is the
     * artist, since the songs list and the file name are artist and title. Only a header field counts: a `K:` or an
     * `M:` in the body changes the music, never the song's opening key, and a `T:` there names a part.
     */
    private fun headerLines(header: List<AbcLines.Field>, title: String?, subtitle: String?): List<String> {
        fun last(letter: Char) = header.lastOrNull { it.letter == letter }?.value
        val artist = header.firstOrNull { it.letter == 'C' }?.value?.let(AbcTextStrings::decode)
        val key = header.firstOrNull { it.letter == 'K' }?.value?.let(AbcFieldValues::key)
        val tags = header.filter { it.letter == 'R' }.map { AbcTextStrings.decode(it.value) }
        return listOfNotNull(
            title?.let { "{title: $it}" },
            subtitle?.let { "{subtitle: $it}" },
            artist?.takeIf { it.isNotBlank() }?.let { "{artist: $it}" },
            key?.let { "{key: $it}" },
            last('Q')?.let(AbcFieldValues::tempo)?.let { "{tempo: $it}" },
            last('M')?.let(AbcFieldValues::time)?.let { "{time: $it}" },
        ) + tags.filter { it.isNotBlank() }.map { "{tag: $it}" }
    }

    /**
     * The words printed after the tune, one verse per run of `W:` lines, a blank one starting the next. The `w:` lines
     * aligned to the notes stay in the ABC block only. A verse line is escaped as prose, so a `[` in it is no chord.
     */
    private fun verses(fields: List<AbcLines.Field>): List<List<String>> {
        val verses = mutableListOf<List<String>>()
        var verse = mutableListOf<String>()
        fields.filter { it.letter == 'W' }.forEach { field ->
            if (field.value.isBlank()) {
                if (verse.isNotEmpty()) verses += verse
                verse = mutableListOf()
            } else {
                verse += ChordProLiteralText.escape(AbcTextStrings.decode(field.value))
            }
        }
        if (verse.isNotEmpty()) verses += verse
        return verses
    }

    /**
     * The tune in its block. A line ChordPro would read as a directive there (an `{end_of_abc}` closing the block
     * early, any directive with a colon) can't occur in valid ABC; one that does is made an ABC comment.
     */
    private fun abcBlock(lines: List<String>) = listOf("{start_of_abc}") +
        lines.map { line -> if (ChordProDirectives.matchDelegatedDirective(line.trim()) != null) COMMENT_PREFIX + line else line } +
        "{end_of_abc}"
}
