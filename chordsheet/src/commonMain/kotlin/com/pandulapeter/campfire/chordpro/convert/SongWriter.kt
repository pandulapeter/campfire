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

import com.pandulapeter.campfire.chordpro.convert.SongStructure.Line
import com.pandulapeter.campfire.chordpro.convert.SongStructure.Part
import com.pandulapeter.campfire.chordpro.convert.SongStructure.Song

/** A song's body as ChordPro lines: every decision was taken by [SongStructure], so each part is written as it stands. */
internal object SongWriter {

    /** The header, a blank line under it where there is one, and the body. */
    fun write(song: Song): List<String> = write(song.parts).let { body -> if (song.header.isEmpty()) body else song.header + "" + body }

    fun write(parts: List<Part>): List<String> = parts.flatMap(::write)

    fun write(part: Part): List<String> = when (part) {
        is Part.Loose -> part.lines.flatMap(::write)
        is Part.Section -> listOf(start(part)) + part.lines.flatMap(::write) + "{end_of_${part.type}}"
    }

    fun write(line: Line): List<String> = when (line) {
        Line.Blank -> listOf("")
        is Line.Lyric -> listOf(line.text)
        is Line.Chords -> listOf(line.text)
        is Line.Raw -> listOf(line.text)
        is Line.Comment -> listOf(if (line.isBoxed) "{comment_box: ${line.text}}" else "{comment: ${line.text}}")
        is Line.TabRun -> listOf(start(line)) + line.lines.flatMap(::write) + "{end_of_tab}"
        is Line.GridRun -> listOf(line.shape?.let { "{start_of_grid: $it}" } ?: "{start_of_grid}") + line.lines.flatMap(::write) + "{end_of_grid}"
        Line.Recall -> listOf("{chorus}")
        is Line.Change -> listOf(line.directive)
    }

    private fun start(section: Part.Section) = section.label?.let { "{start_of_${section.type}: $it}" } ?: "{start_of_${section.type}}"

    private fun start(run: Line.TabRun) = run.label?.let { "{start_of_tab: $it}" } ?: "{start_of_tab}"
}
