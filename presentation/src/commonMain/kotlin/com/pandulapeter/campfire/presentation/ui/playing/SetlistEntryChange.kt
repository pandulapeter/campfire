/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.setlist.Setlist

/**
 * The setlist with [change] applied to the entry of [songFileName], or null where it has none: a song a sync run took
 * out of the setlist while its screen stayed open. Each caller decides what that means for it. The entry is found by
 * identity, as the screen names the song the way the library spells it, and keeps the spelling of the file.
 */
internal fun Setlist.withEntry(songFileName: String, change: (Setlist.Entry) -> Setlist.Entry): Setlist? =
    if (entries.none { LibraryFiles.isSameLibraryName(it.songFileName, songFileName) }) {
        null
    } else {
        copy(entries = entries.map { entry -> if (LibraryFiles.isSameLibraryName(entry.songFileName, songFileName)) change(entry) else entry })
    }
