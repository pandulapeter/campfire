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

/**
 * The plain text the ChordPro program writes of a song (`chordpro --generate=Text`): chords over lyrics in columns, its
 * title on a `-- Title: …` line, each section between `-- Start of verse` and `-- End of verse` markers, and a recalled
 * chorus as the literal `{chorus}` its source held. That one directive would make the whole text look like ChordPro,
 * which the converter hands back untouched, so a text with a section marker is read as the chord sheet it is: the
 * title line as its title, each start marker as the heading of its section, an end marker as the blank line between
 * sections, and the literal recall as a chorus heading with nothing under it.
 */
internal object ChordProTextExport {

    fun isExport(text: String) = text.lineSequence().any { start.matches(it.trim()) }

    fun lines(lines: List<String>): List<String> = if (lines.none { start.matches(it.trim()) }) lines else lines.map { line ->
        val text = line.trim()
        start.matchEntire(text)?.let { "[" + it.groupValues[1].replaceFirstChar(Char::uppercaseChar) + "]" }
            ?: title.matchEntire(text)?.groupValues?.get(1)
            ?: if (end.matches(text)) "" else if (recall.matches(text)) "[Chorus]" else line
    }

    private val start = Regex("^-- Start of ([a-z]+(?: [a-z]+)?)$", RegexOption.IGNORE_CASE)
    private val end = Regex("^-- End of [a-z]+(?: [a-z]+)?$", RegexOption.IGNORE_CASE)
    private val title = Regex("^-- (?:Title|Subtitle): (.+)$", RegexOption.IGNORE_CASE)
    private val recall = Regex("^\\{(?:chorus|refrain)\\}$", RegexOption.IGNORE_CASE)
}
