/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.GridToken

/**
 * How much of a song [SongLyrics] lays out. The screen fits every section into its columns at once, and measures every
 * chorded line on the main thread, which is right for a song, whose few hundred lines fit a few pages, and fatal for a
 * file that is not one: a songbook concatenated into one `.cho`, or a log file dropped into the folder. One of 60 000
 * lines froze a desktop for seconds and took gigabytes; on a phone it is the app being killed. The limits are several
 * times the longest real song (a long tab file is under 1 000 lines and 60 kB), so no song is ever cut.
 */
internal object LayoutBudget {

    const val MAX_LINES = 3_000
    const val MAX_CHARACTERS = 200_000

    /**
     * The sections that fit the budget, and whether anything was left out. It stops at the end of the last section
     * that fits, so that what is shown ends where the song pauses; only a first section that does not fit on its own -
     * a file with no blank line in it, which the parser takes for one paragraph - is cut inside, at a line.
     */
    fun fit(sections: List<RenderSection>): Pair<List<RenderSection>, Boolean> {
        var lines = 0
        var characters = 0
        sections.forEachIndexed { index, section ->
            val sectionLines = (section as? RenderSection.Lines)?.lines?.size ?: 1
            val sectionCharacters = when (section) {
                is RenderSection.Lines -> section.lines.sumOf { it.characterCount() }
                is RenderSection.Comment -> section.text.length
            }
            if (lines + sectionLines > MAX_LINES || characters + sectionCharacters > MAX_CHARACTERS) {
                return if (index == 0 && section is RenderSection.Lines) listOf(section.cut()) to true else sections.subList(0, index) to true
            }
            lines += sectionLines
            characters += sectionCharacters
        }
        return sections to false
    }

    /** The lines of [this] that fit the budget on their own, with the comments between them. */
    private fun RenderSection.Lines.cut(): RenderSection.Lines {
        var lines = 0
        var characters = 0
        val keptParts = mutableListOf<SectionPart>()
        for (part in parts) {
            when (part) {
                is RenderSection.Comment -> {
                    characters += part.text.length
                    if (characters > MAX_CHARACTERS) break
                    keptParts += part
                }
                is SectionPart.Lines -> {
                    val keptLines = part.lines.takeWhile { line ->
                        lines++
                        characters += line.characterCount()
                        lines <= MAX_LINES && characters <= MAX_CHARACTERS
                    }
                    if (keptLines.isNotEmpty()) keptParts += SectionPart.Lines(keptLines)
                    if (keptLines.size < part.lines.size) break
                }
            }
        }
        return copy(parts = keptParts)
    }

    private fun ChordProLine.characterCount(): Int = when (this) {
        is ChordProLine.Lyrics -> text.length + chords.sumOf { it.name.length }
        is ChordProLine.Tab -> text.length
        is ChordProLine.Grid -> tokens.sumOf { token ->
            when (token) {
                is GridToken.Bar -> token.text.length
                is GridToken.Chord -> token.name.length
                GridToken.Beat -> 1
                is GridToken.Repeat -> token.text.length
                is GridToken.Text -> token.text.length
            }
        }
        ChordProLine.Blank -> 0
    }
}
