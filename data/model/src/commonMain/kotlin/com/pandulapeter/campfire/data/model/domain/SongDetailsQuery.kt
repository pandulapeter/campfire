/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * What the song details lookup is asked: a song by its [title], narrowed down by its [artist] and the [album] it is on
 * where those are known. Blank fields are left out of the question.
 */
data class SongDetailsQuery(
    val title: String,
    val artist: String,
    val album: String,
) {
    /** Whether there is anything to search by at all: an artist on its own names a discography, not a song. */
    val isSearchable get() = title.isNotBlank()
}
