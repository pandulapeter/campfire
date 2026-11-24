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
     * line): the song and its details, the lines it reads as the title and the artist and, above the song, those it gives a
     * small chance of being either (so the converter still reads the song's header from them), and a blank line between
     * two kept ones. A chord row the model drops is kept too, since it reads a lone closing chord as a page number: inside
     * the song, and after its last kept line only while nothing but blank lines and other such chord rows came between,
     * so an outro's chords stay but a chord diagram block printed under the song ("Chords used", each name over its
     * marks) goes. A row of numerals in a song written in them ([ChordSheetRomanChords.numeralRows]) is such a chord row,
     * which the model, reading the numerals as it reads a page's number, drops as readily, and is kept above the song's
     * first line too, down from its header: no header holds one, and an intro played in them stands there. The whole
     * song where the model
     * sees none in it, or
     * where a line of it is past the lines the model read.
     */
    fun cropped(song: List<Rendered>, scores: List<PageLineScores>?): List<Rendered> {
        if (scores == null) return song
        val origins = song.mapNotNull { it.origin }.distinct().sorted()
        if (origins.isEmpty() || origins.any { it !in scores.indices }) return song
        val printed = song.filter { it.origin != null && it.text.isNotBlank() }.mapTo(mutableSetOf()) { it.origin }
        val blankLines = origins.indices.filterTo(mutableSetOf()) { origins[it] !in printed }
        val crop = PageCrop.of(origins.map { scores[it] }, blankLines)
        if (!crop.holdsSong) return song
        val firstSongLine = crop.scores.indices.first { it !in blankLines && crop.scores[it].label == PageLineLabel.SONG }
        val header = crop.scores.indices.filter { it !in blankLines && isHeader(crop.scores[it], isAboveSong = it < firstSongLine) }
        val kept = (crop.keptLines + header).map { origins[it] }.toSet()
        val span = kept.min()..kept.max()
        val body = origins[firstSongLine]
        val numeralRows = ChordSheetRomanChords.numeralRows(song)
        fun isChordRow(index: Int) = index in numeralRows || ChordSheetConverter.isChordRow(song[index].text)
        var isClosing = true
        return song.filterIndexed { index, line ->
            val origin = line.origin ?: return@filterIndexed true
            when {
                origin in kept -> true
                origin > span.last -> (isClosing && isChordRow(index)).also { isKept ->
                    if (!isKept && line.text.isNotBlank()) isClosing = false
                }
                else -> (line.text.isBlank() || index in numeralRows) && origin in span || origin >= body && isChordRow(index)
            }
        }
    }

    /**
     * A line labelled the title or the artist wherever it is, since one inside the song is most often a lyric naming the
     * song, which must not be lost; and one the model is only unsure of only above the song, where the converter reads the
     * header: below its first song line, such a line is a printed page's running header repeated on the next page.
     */
    private fun isHeader(scores: PageLineScores, isAboveSong: Boolean) =
        scores.label == PageLineLabel.TITLE || scores.label == PageLineLabel.ARTIST || isAboveSong && scores.isUnsure(PageLineLabel.TITLE, PageLineLabel.ARTIST)
}
