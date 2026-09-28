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
 * What the cover search is asked: the record a song came out on, found by its [album] where there is one and by the
 * song's own [title] otherwise, either narrowed down by the [artist]. Blank fields are left out of the question.
 */
data class CoverArtQuery(
    val artist: String,
    val album: String,
    val title: String,
) {
    /** Whether there is anything to search by at all: an artist on its own names a discography, not a record. */
    val isSearchable get() = album.isNotBlank() || title.isNotBlank()
}
