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

import kotlin.math.round

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
 * those hold no song, as every line then is), by the same rule the gate scored the model with.
 */
internal fun cropOf(scores: List<PageLineScores>, lineCount: Int): PageCrop {
    val labels = scores.map { it.label }
    val kept = scores.indices.filter { index ->
        labels[index] == PageLineLabel.SONG ||
            labels[index] == PageLineLabel.METADATA ||
            round((scores[index][PageLineLabel.SONG] + scores[index][PageLineLabel.METADATA]).toDouble() * 10_000) / 10_000 >= UNSURE
    }
    val holdsSong = kept.count { labels[it] == PageLineLabel.SONG } >= MIN_SONG_LINES
    val titleLine = bestLineOf(scores, labels, PageLineLabel.TITLE)
    val artistLine = bestLineOf(scores, labels, PageLineLabel.ARTIST)
    return PageCrop(
        holdsSong = holdsSong,
        keptLines = if (holdsSong) kept else List(lineCount) { it },
        titleLine = titleLine,
        artistLine = artistLine,
        headerLines = if (holdsSong) headerLinesOf(listOfNotNull(titleLine, artistLine), kept, kept.first { labels[it] == PageLineLabel.SONG }) else emptyList(),
        metadataLines = labels.indices.filter { labels[it] == PageLineLabel.METADATA },
        scores = scores,
    )
}

private fun bestLineOf(scores: List<PageLineScores>, labels: List<PageLineLabel>, label: PageLineLabel) =
    labels.indices.filter { labels[it] == label }.maxByOrNull { scores[it][label] }

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
