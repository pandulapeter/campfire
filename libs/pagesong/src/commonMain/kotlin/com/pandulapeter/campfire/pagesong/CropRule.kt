/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.pagesong

/**
 * The lowest chance of being the song or its details that keeps a line. The gate (`tools/page-model/gate.py`, its
 * `unsure` rule) judged the model with it: a song line the model is not sure of stays at the cost of a little clutter.
 * The gate compares the chance rounded to four decimals, and so does the crop, so a retrain judged there crops the same
 * here.
 */
internal const val UNSURE = 0.02

/** Fewer lines the model calls the song than this, and it does not see one: the gate's `MIN_SONG_LINES`. */
internal const val MIN_SONG_LINES = 4

/**
 * The crop of a page of [lineCount] lines whose first lines the model scored as [scores] (the rest are kept only where
 * those hold no song, as every line then is), by the same rule the gate scored the model with. The [blankLines] (indices
 * into [scores]) are never kept, counted, or named by the model's say: it was not trained on them and the gate scores
 * only printed lines, so what it answers for one is noise. A caller puts back the blank lines between kept ones.
 *
 * The [staffLines] (a tab's strings, `e|--0--|`) after the song's first line are kept whatever the model says, and so are
 * the few printed lines between two of them ([STAFF_GAP] at most: a part's name, `(Guitar 1)`, a player's note): a page
 * that prints a long tab between players' notes (Ultimate Guitar's) loses the model's sense of where the song goes on,
 * and a staff, or what is printed between two, is next to never a page's clutter.
 */
internal fun cropOf(scores: List<PageLineScores>, lineCount: Int, blankLines: Set<Int>, staffLines: Set<Int> = emptySet()): PageCrop {
    val labels = scores.map { it.label }
    val kept = scores.indices.filter { index ->
        index !in blankLines && (
            labels[index] == PageLineLabel.SONG ||
                labels[index] == PageLineLabel.METADATA ||
                scores[index].isUnsure(PageLineLabel.SONG, PageLineLabel.METADATA)
            )
    }
    val holdsSong = kept.count { labels[it] == PageLineLabel.SONG } >= MIN_SONG_LINES
    val firstSongLine = kept.firstOrNull { labels[it] == PageLineLabel.SONG }
    val staves = if (!holdsSong || firstSongLine == null) emptyList() else staffLines.filter { it > firstSongLine && it in scores.indices }.sorted()
    val betweenStaves = staves.zipWithNext().filter { (a, b) -> b - a - 1 <= STAFF_GAP }.flatMap { (a, b) -> (a + 1) until b }
    val tab = (staves + betweenStaves).filter { it !in blankLines }
    val titleLine = bestLineOf(scores, labels, PageLineLabel.TITLE, blankLines)
    val artistLine = bestLineOf(scores, labels, PageLineLabel.ARTIST, blankLines)
    return PageCrop(
        holdsSong = holdsSong,
        keptLines = if (holdsSong) (kept + tab).distinct().sorted() else List(lineCount) { it },
        titleLine = titleLine,
        artistLine = artistLine,
        headerLines = if (holdsSong && firstSongLine != null) headerLinesOf(listOfNotNull(titleLine, artistLine), kept, firstSongLine) else emptyList(),
        scores = scores,
    )
}

private fun bestLineOf(scores: List<PageLineScores>, labels: List<PageLineLabel>, label: PageLineLabel, blankLines: Set<Int>) =
    labels.indices.filter { labels[it] == label && it !in blankLines }.maxByOrNull { scores[it][label] }

/**
 * Those of [named] (the title and artist lines) that head a song starting at [firstSongLine]. A title or artist line inside
 * or after the song, one the model also gives a chance of being the song, and any of them where the last one runs straight
 * into the song's first line are left where they are, read as possibly a line of the song the model misjudged: a lyric
 * read as a title most often opens the song itself, and a song line left out would be lost.
 */
private fun headerLinesOf(named: List<Int>, kept: List<Int>, firstSongLine: Int): List<Int> {
    val before = named.filter { it < firstSongLine }
    if (before.isEmpty() || before.max() + 1 == firstSongLine) return emptyList()
    return before.filter { it !in kept }.sorted()
}

/** How many lines between two tab staves the crop keeps with them: a part's name and a player's note or two. */
internal const val STAFF_GAP = 8

/**
 * The lines of [texts] that are a tab staff's string: a string's name, a bar line, and a run of the staff's dashes (or a
 * let-ring's tildes), whatever the playing marks among them (`4s9`, `[2]`, `*7*-FILL`), which the model's own feature,
 * held to what it was trained on, does not all take.
 */
internal fun staffLinesOf(texts: List<String>): Set<Int> = texts.indices.filterTo(mutableSetOf()) { index ->
    val text = texts[index]
    staffStart.containsMatchIn(text) && text.count { it == '-' || it == '~' } >= 8 && text.count { it == '|' } >= 2
}

private val staffStart = Regex("^[ \t]*[A-Ga-g][#b]?[ \t]*\\|")
