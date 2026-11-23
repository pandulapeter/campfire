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
 * The crop of a page of [lineCount] lines whose first lines the model scored as [scores] (the rest are never kept), by
 * the same rule the gate scored the model with.
 */
internal fun cropOf(scores: List<PageLineScores>, lineCount: Int): PageCrop {
    val labels = scores.map { it.label }
    val kept = scores.indices.filter { index ->
        labels[index] == PageLineLabel.SONG ||
            labels[index] == PageLineLabel.METADATA ||
            round((scores[index][PageLineLabel.SONG] + scores[index][PageLineLabel.METADATA]).toDouble() * 10_000) / 10_000 >= UNSURE
    }
    val holdsSong = kept.count { labels[it] == PageLineLabel.SONG } >= MIN_SONG_LINES
    return PageCrop(
        holdsSong = holdsSong,
        keptLines = if (holdsSong) kept else List(lineCount) { it },
        titleLine = bestLineOf(scores, labels, PageLineLabel.TITLE),
        artistLine = bestLineOf(scores, labels, PageLineLabel.ARTIST),
        metadataLines = labels.indices.filter { labels[it] == PageLineLabel.METADATA },
        scores = scores,
    )
}

private fun bestLineOf(scores: List<PageLineScores>, labels: List<PageLineLabel>, label: PageLineLabel) =
    labels.indices.filter { labels[it] == label }.maxByOrNull { scores[it][label] }
