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

import kotlin.math.exp

/**
 * Finds the song on a page: labels each line with the page model (`tools/page-model`) and crops the page to the song and
 * its details, keeping every line the model is not sure is clutter. It never changes a line's text: it only answers which
 * lines to keep, so what the caller converts is the page's own lines.
 *
 * Built once from the weights `tools/page-model/export.py` writes, which the caller reads from wherever it ships them;
 * [crop] holds no state between calls and may be called from several threads at once. The work grows with the lines'
 * characters (about a tenth of a second for a page of three hundred lines on a desktop), so call it off the main thread.
 *
 * @param weights the weights file's bytes, parsed once here.
 * @throws IllegalArgumentException when [weights] is not a page model's weights of the version this library reads, or
 * is cut short or too long for the shape it names.
 */
public class PageSong(
    weights: ByteArray,
) {
    private val model = PageModel(PageModelWeights(weights))

    /**
     * Labels the first [MAX_LINES] of [lines] and crops them; lines past that are never kept, as a song is rarely that far
     * down a page. An empty list answers a crop holding no song.
     */
    public fun crop(lines: List<PageLine>, source: PageLineSource): PageCrop =
        cropOf(scores = logitsOf(lines.take(MAX_LINES), source).map(::scoresOf), lineCount = lines.size)

    internal fun logitsOf(lines: List<PageLine>, source: PageLineSource) = model.logitsOf(lines, source)

    private fun scoresOf(logits: FloatArray): PageLineScores {
        val largest = logits.max()
        val exponents = FloatArray(logits.size) { exp(logits[it] - largest) }
        val sum = exponents.sum()
        return PageLineScores(FloatArray(exponents.size) { exponents[it] / sum })
    }

    public companion object {

        /** How many of a page's lines are read; the rest are never kept. */
        public const val MAX_LINES: Int = 1500
    }
}
