/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.data.model.domain.Setlist

/**
 * The setlist with the order a drag ended on, [songFileNames] being the order the screen was showing, which a sync run
 * or another write may have overtaken by the time this one runs: an entry the drag never saw must not be moved by it,
 * so the songs it did see are dealt back into the slots they already occupied and everything else stays exactly where
 * it is.
 */
internal fun Setlist.withSongOrder(songFileNames: List<String>): Setlist {
    val reordered = songFileNames.mapNotNull { songFileName ->
        entries.firstOrNull { it.songFileName == songFileName }
    }.iterator()
    val movedSongFileNames = songFileNames.toSet()
    return copy(
        entries = entries.map { entry ->
            if (entry.songFileName in movedSongFileNames && reordered.hasNext()) reordered.next() else entry
        },
    )
}
