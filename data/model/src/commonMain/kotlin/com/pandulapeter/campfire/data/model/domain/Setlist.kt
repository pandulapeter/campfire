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
    /**
     * What the setlist is for, in the user's own words: empty for most of them, and shown under the header of the
     * setlist wherever it is not. It lives in the file next to the title, so it travels through an export, an
     * import or a sync run the way the title does, and the setlists screen's search reads it.
     */
    val description: String,
    /** Higher first, so that the newest setlist is on top. */
    val priority: Int,
    /**
     * Whether the setlist has been put away: it is left out of the setlists screen and of the picker that adds a
     * song to one, until the user asks for the archived ones as well. It lives in the file rather than in the
     * preferences, so that a setlist that was retired on one device is retired on every other one it syncs to.
     */
    val isArchived: Boolean,
    val entries: List<Entry>,
    /**
     * The bytes the file takes up, as the scan listed it or as the last write left it, which is what the library's
     * size on the settings screen adds up. 0 for a setlist that is not in the library yet: one parsed out of an import.
     */
    val size: Long,
    /**
     * The members of the file this version of the app does not know, as the text of a JSON object, or empty where
     * there are none. A later version may add a field to setlists while this one is still installed on another of
     * the user's devices: kept here, that field survives this version's next save of the setlist instead of being
     * dropped from the file and synced away from every device. Opaque to everything but the storage layer.
     */
    val unknownFields: String = "",
) {

    data class Entry(
        val songFileName: String,
        val transposition: Int = 0,
        /** The same as [Setlist.unknownFields], for one entry. */
        val unknownFields: String = "",
    )
}
