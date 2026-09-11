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
 * One `*.setlist.json` file in the library. The transposition of a song lives in the entry rather than in the user
 * preferences, so that it travels with the setlist when the library is exported.
 */
data class Setlist(
    /** The file name inside the setlists directory, extension included. Unique, and the identity of the setlist. */
    val fileName: String,
    val title: String,
    /** Higher first, so that the newest setlist is on top. */
    val priority: Int,
    /**
     * Whether the setlist has been put away: it is left out of the setlists screen and of the picker that adds a
     * song to one, until the user asks for the archived ones as well. It lives in the file rather than in the
     * preferences, so that a setlist that was retired on one device is retired on every other one it syncs to.
     */
    val isArchived: Boolean,
    val entries: List<Entry>,
) {

    data class Entry(
        val songFileName: String,
        val transposition: Int = 0,
    )
}
