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

/**
 * [tune] with the title and composer left out of every tune whose first title (`T:`) is the song's own [songTitle], case
 * and spacing aside: a song imported from an `.abc` file is named by its tune, and the page already heads it with that
 * name. A tune named otherwise (one of a medley, a set of reels) keeps its name, which is the only place it is given.
 * Done by a `%%writefields` line at the top of that tune's header, which abc2svg applies to that tune alone.
 */
internal fun abcTuneWithoutSongTitle(tune: String, songTitle: String?): String {
    val lines = tune.lines()
    val hidden = firstTitlesOf(lines).filter { (_, title) -> title.isSongTitle(songTitle) }.map { it.first }.toSet()
    if (hidden.isEmpty()) return tune
    return buildList {
        if (-1 in hidden) add(HIDE_TITLE)
        lines.forEachIndexed { index, line ->
            add(line)
            if (index in hidden) add(HIDE_TITLE)
        }
    }.joinToString(separator = "\n")
}

/**
 * The titles [tune] shows: the first `T:` of each tune it holds, but one naming the song ([songTitle]), which
 * [abcTuneWithoutSongTitle] leaves to the page.
 */
internal fun abcOwnTitles(tune: String, songTitle: String?): List<String> =
    firstTitlesOf(tune.lines()).mapNotNull { (_, title) -> title?.trim()?.takeIf { it.isNotEmpty() && !title.isSongTitle(songTitle) } }

/** The first title (`T:`) in the header of each tune of [lines], by the line its `X:` stands on (-1 for a tune with none). */
private fun firstTitlesOf(lines: List<String>): List<Pair<Int, String?>> {
    val tuneStarts = lines.indices.filter { lines[it].startsWith("X:") }.ifEmpty { listOf(-1) }
    return tuneStarts.map { start ->
        val end = tuneStarts.firstOrNull { it > start } ?: lines.size
        val header = lines.subList(start + 1, end).takeWhile { !it.startsWith("K:") }
        start to header.firstOrNull { it.startsWith("T:") }?.substringAfter(':')
    }
}

/** Whether [this] title is [songTitle], case and spacing aside. */
private fun String?.isSongTitle(songTitle: String?): Boolean {
    val title = songTitle?.normalizedTitle()?.takeIf { it.isNotEmpty() } ?: return false
    return this?.normalizedTitle() == title
}

private fun String.normalizedTitle() = trim().lowercase().split(Regex("\\s+")).joinToString(" ")

private const val HIDE_TITLE = "%%writefields TC 0"
