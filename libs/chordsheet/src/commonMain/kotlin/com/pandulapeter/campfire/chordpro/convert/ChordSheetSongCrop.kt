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

import com.pandulapeter.campfire.pagesong.PageCrop
import com.pandulapeter.campfire.pagesong.PageLineLabel
import com.pandulapeter.campfire.pagesong.PageLineScores

/**
 * One song of a document cropped to the lines the page model keeps of it, after [ChordSheetSongSplit] cut it out: the
 * model reads the whole document, as it was trained to, and each song is held to the page model's crop rule on its own
 * lines, so a songbook's contents page still tells the split where the songs start, and a song the model finds no song
 * in is converted whole.
 */
internal object ChordSheetSongCrop {

    /**
     * The lines of [song] whose sheet lines the model keeps, by [scores] of every line of the document (null keeps every
     * line): the song and its details, the lines it reads as the title and the artist (so the converter still reads the
     * song's header from them), and a blank line between two kept ones. The whole song where the model sees none in it, or
     * where a line of it is past the lines the model read.
     */
    fun cropped(song: List<Rendered>, scores: List<PageLineScores>?): List<Rendered> {
        if (scores == null) return song
        val origins = song.mapNotNull { it.origin }.distinct().sorted()
        if (origins.isEmpty() || origins.any { it !in scores.indices }) return song
        val crop = PageCrop.of(origins.map { scores[it] })
        if (!crop.holdsSong) return song
        val header = crop.scores.indices.filter { isHeader(crop.scores[it]) }
        val kept = (crop.keptLines + header).map { origins[it] }.toSet()
        val span = kept.min()..kept.max()
        val body = origins[crop.scores.indexOfFirst { it.label == PageLineLabel.SONG }]
        return song.filter { line ->
            val origin = line.origin ?: return@filter true
            origin in kept || line.text.isBlank() && origin in span || origin >= body && ChordSheetConverter.isChordRow(line.text)
        }
    }

    private fun isHeader(scores: PageLineScores) = scores.label == PageLineLabel.TITLE || scores.label == PageLineLabel.ARTIST ||
        scores[PageLineLabel.TITLE] + scores[PageLineLabel.ARTIST] >= UNSURE

    private const val UNSURE = 0.02f
}
