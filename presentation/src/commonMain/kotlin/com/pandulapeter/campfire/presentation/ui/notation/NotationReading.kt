/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import com.pandulapeter.campfire.presentation.localization.withTexts

/**
 * What a screen reader hears of an engraved tune instead of its ABC source: the titles it shows, its chords as the page
 * names them, and its words.
 */
internal data class NotationReading(val titles: List<String>, val chords: List<String>, val words: String?)

/**
 * The reading of [tune] engraved with [chordNames]: the first `T:` of each tune it holds but one naming the song
 * ([songTitle], left to the page, see [abcTuneWithoutSongTitle]); the chords in order, a chord repeated straight after
 * itself said once; and the words: each tune's `w:` lines verse by verse (the k-th `w:` line under a line of music is
 * verse k, so verse 1 is read through the tune before verse 2, as it is sung), a `+:` line continuing the line above,
 * then its `W:` lines; ABC's syllable marks undone (`-` joins syllables, `_`, `*` and `|` say nothing, `~` is a space,
 * `\-` a hyphen).
 */
internal fun notationReadingOf(tune: String, songTitle: String?, chordNames: List<String>): NotationReading {
    val words = tuneWordsOf(tune).flatMap { it.wordsInOrder() }.joinToString(" ").collapsedSpaces()
    return NotationReading(
        titles = abcOwnTitles(tune, songTitle),
        chords = chordNames.filterIndexed { index, name -> index == 0 || name != chordNames[index - 1] },
        words = words.ifEmpty { null },
    )
}

/** [this] as one sentence per part, the [heading] standing in for a tune that names itself nothing. */
internal fun NotationReading.describedAs(heading: String, chordsTemplate: String, wordsTemplate: String): String = listOfNotNull(
    titles.joinToString(", ").ifEmpty { heading },
    chords.takeIf { it.isNotEmpty() }?.let { chordsTemplate.withTexts(it.joinToString(", ")) },
    words?.let { wordsTemplate.withTexts(it) },
).joinToString(". ")

/** The words of one tune of an ABC text: its verses, each a list of `w:` lines in order, and its `W:` lines. */
private class TuneWords {
    val verses = mutableListOf<MutableList<String>>()
    val afterwords = mutableListOf<String>()

    fun wordsInOrder() = verses.map { it.joinToString(" ") } + afterwords
}

/** The words of every tune [tune] holds, split at each `X:`. */
private fun tuneWordsOf(tune: String): List<TuneWords> {
    val tunes = mutableListOf(TuneWords())
    var verse = 0
    // The field a `+:` line continues: the verse a `w:` line went to, or -1 after a `W:` line.
    var continued: Int? = null
    tune.lines().forEach { line ->
        val field = FIELD.matchEntire(line)?.groupValues
        val current = tunes.last()
        when {
            line.startsWith("%") || line.isBlank() -> Unit
            field == null -> {
                verse = 0
                continued = null
            }

            field[1] == "X" -> {
                if (current.verses.isNotEmpty() || current.afterwords.isNotEmpty()) tunes += TuneWords()
                verse = 0
                continued = null
            }

            field[1] == "w" -> {
                while (current.verses.size <= verse) current.verses += mutableListOf<String>()
                current.verses[verse] += field[2].withoutSyllableMarks()
                continued = verse++
            }

            field[1] == "W" -> {
                current.afterwords += field[2].trim()
                continued = -1
            }

            field[1] == "+" -> when (val target = continued) {
                null -> Unit
                -1 -> current.afterwords[current.afterwords.lastIndex] += " " + field[2].trim()
                else -> current.verses[target][current.verses[target].lastIndex] += " " + field[2].withoutSyllableMarks()
            }

            else -> continued = null
        }
    }
    return tunes
}

/** A line of `w:` words as they are sung: syllables joined, the marks that only align them dropped. */
private fun String.withoutSyllableMarks() = replace("\\-", HYPHEN_PLACEHOLDER)
    .replace("-", "")
    .replace("_", "")
    .replace("*", "")
    .replace("|", "")
    .replace('~', ' ')
    .replace(HYPHEN_PLACEHOLDER, "-")
    .collapsedSpaces()

private fun String.collapsedSpaces() = trim().split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")

/** An ABC field line: a letter (or `+`) and a colon, then its value. */
private val FIELD = Regex("([A-Za-z+]):(.*)")
private val WHITESPACE = Regex("\\s+")
private const val HYPHEN_PLACEHOLDER = "\u0000"
