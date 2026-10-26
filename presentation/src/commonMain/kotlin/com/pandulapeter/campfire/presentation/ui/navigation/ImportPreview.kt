/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

import kotlinx.serialization.Serializable

/**
 * What makes a [CampfireDestination.SongDetails] a preview of songs an import has not written yet: its pages are entries
 * of the plan the import screen's report holds (`ImportReport.Review`), drawn from their text, never from the library.
 *
 * @param songIndices The places in the plan's songs of the pages, in their order: the review's song rows as they were
 *   listed when one of them was opened, or the one song of a batch that is nothing else.
 * @param planSongCount How many songs the plan had then. What a batch joining the review adds comes after them, and is
 *   appended to the pages, so that the page being read stays where it is.
 * @param isReviewPager Whether the preview pages through the review's rows, over the import screen, rather than being the
 *   only screen of a batch of one song, which stands in for the import screen and becomes the song's own once imported.
 */
@Serializable
data class ImportPreview(
    val songIndices: List<Int>,
    val planSongCount: Int,
    val isReviewPager: Boolean,
) {
    companion object {
        /** The preview of a batch that is one song and nothing else. */
        val SingleSong = ImportPreview(songIndices = listOf(0), planSongCount = 1, isReviewPager = false)
    }
}
