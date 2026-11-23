/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.importing

import com.pandulapeter.campfire.pagesong.PageCrop

/**
 * Where a page's song is among its lines, as [PageCropper.songLinesOf] found it. Every index is a position in the lines
 * it was asked about.
 *
 * @param keptLines the song and its details, in ascending order, including every line the model is not sure is clutter;
 * the title and artist lines only where the model also gives them a chance of being the song. Never a blank line: the
 * caller keeps the blank lines between kept ones.
 * @param titleLine the line the model reads as the song's title, if any.
 * @param artistLine the line the model reads as the song's artist, if any.
 * @param headerLines those of [titleLine] and [artistLine] that head the song (`PageCrop.headerLines`), which the import
 * reads as its title and artist and leaves out of it; the others stay in the song, since they may be lines of it.
 */
data class PageSongLines(
    val keptLines: List<Int>,
    val titleLine: Int?,
    val artistLine: Int?,
    val headerLines: List<Int>,
) {
    companion object {

        /** Where [crop] finds the song; null where it sees none. */
        fun of(crop: PageCrop): PageSongLines? = if (!crop.holdsSong) null else PageSongLines(
            keptLines = crop.keptLines,
            titleLine = crop.titleLine,
            artistLine = crop.artistLine,
            headerLines = crop.headerLines,
        )
    }
}
