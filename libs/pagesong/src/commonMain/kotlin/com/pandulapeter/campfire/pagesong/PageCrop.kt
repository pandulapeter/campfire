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
 * What [PageSong.crop] made of a page's lines. Every index is a position in the list the caller passed.
 *
 * @param holdsSong whether the model sees a song in the lines. When it does not, [keptLines] is every line and the
 * caller converts the page as it would without the model.
 * @param keptLines the lines to convert, in order: the song and its details, including every line the model is not sure
 * is clutter. Never a line past [PageSong.MAX_LINES].
 * @param titleLine the line the model reads as the song's title, if any; it is not among [keptLines] unless the model
 * also gives it a chance of being the song or its details.
 * @param artistLine the line the model reads as the song's artist, if any, kept on the same terms as [titleLine].
 * @param headerLines those of [titleLine] and [artistLine] that head the song rather than stand in it, which a caller
 * reads as the song's title and artist and leaves out of the song: before the first line the model reads as the song, set
 * apart from it by at least one other line, and given no chance of being the song or its details (not in [keptLines]).
 * Empty where the model sees no song.
 * @param metadataLines the lines the model reads as the song's details (key, capo, tempo, time, credits), where the
 * converter's own rules look for them.
 * @param scores each read line's label probabilities, one for each of the first [PageSong.MAX_LINES] lines, for tools
 * and tests; a caller deciding what to keep reads the fields above.
 */
public data class PageCrop(
    val holdsSong: Boolean,
    val keptLines: List<Int>,
    val titleLine: Int?,
    val artistLine: Int?,
    val headerLines: List<Int>,
    val metadataLines: List<Int>,
    val scores: List<PageLineScores>,
) {

    public companion object {

        /**
         * The crop of lines the model has already scored, by the same rule as [PageSong.crop]: for a part of a longer
         * page read whole, such as one song of a document, whose lines the model judged in the whole document's context.
         */
        public fun of(scores: List<PageLineScores>): PageCrop = cropOf(scores, scores.size)
    }
}
