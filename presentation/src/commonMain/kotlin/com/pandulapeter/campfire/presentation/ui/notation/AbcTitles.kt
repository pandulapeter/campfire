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
    val title = songTitle?.normalizedTitle()?.takeIf { it.isNotEmpty() } ?: return tune
    val lines = tune.lines()
    val tuneStarts = lines.indices.filter { lines[it].startsWith("X:") }.ifEmpty { listOf(-1) }
    val hidden = tuneStarts.filter { start ->
        val end = tuneStarts.firstOrNull { it > start } ?: lines.size
        val header = lines.subList(start + 1, end).takeWhile { !it.startsWith("K:") }
        header.firstOrNull { it.startsWith("T:") }?.substringAfter(':')?.normalizedTitle() == title
    }.toSet()
    if (hidden.isEmpty()) return tune
    return buildList {
        if (-1 in hidden) add(HIDE_TITLE)
        lines.forEachIndexed { index, line ->
            add(line)
            if (index in hidden) add(HIDE_TITLE)
        }
    }.joinToString(separator = "\n")
}

private fun String.normalizedTitle() = trim().lowercase().split(Regex("\\s+")).joinToString(" ")

private const val HIDE_TITLE = "%%writefields TC 0"
