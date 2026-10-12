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
 * Who wrote a song, as a catalogue credits the work a recording is a performance of: every name once, in the order the
 * catalogue lists them, joined with commas. Null where it names nobody for that role.
 */
data class SongCredits(
    val composer: String?,
    val lyricist: String?,
)
